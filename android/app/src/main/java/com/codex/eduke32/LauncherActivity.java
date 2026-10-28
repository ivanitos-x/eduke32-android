package com.codex.eduke32;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.content.ClipData;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.ScrollView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.CRC32;

public final class LauncherActivity extends Activity {
    private static final int PICK_GAME_DATA = 1001;
    private static final int PICK_GAME_FOLDER = 1002;
    private static final int PICK_SAVE_FOLDER = 1003;
    private File gameData;
    private File gameDirMarker;
    private GamePreset preset;

    private File baseConfigDir() { return new File(getFilesDir(), ".config/eduke32"); }
    private File presetConfigDir() {
        return preset == GamePreset.BLOODCM
                ? new File(baseConfigDir(), "presets/bloodcm") : baseConfigDir();
    }
    private void choosePreset() {
        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        float density = getResources().getDisplayMetrics().density;
        int inset = (int)(20*density);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(inset,inset,inset,inset);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF101216);
        background.setCornerRadius(24*density);
        panel.setBackground(background);
        TextView heading = new TextView(this);
        heading.setText("Game preset");
        heading.setTextColor(0xFFF4F6F8);
        heading.setTextSize(23);
        heading.setTypeface(null,android.graphics.Typeface.BOLD);
        panel.addView(heading,new LinearLayout.LayoutParams(-1,-2));
        TextView caption = new TextView(this);
        caption.setText("Choose a game. Select its files in Game folder & mods.");
        caption.setTextColor(0xFFADB5C0);
        caption.setTextSize(13);
        caption.setPadding(0,(int)(6*density),0,(int)(16*density));
        panel.addView(caption,new LinearLayout.LayoutParams(-1,-2));
        for (GamePreset option : GamePreset.values()) {
            boolean selected = option == preset;
            String detail = option == GamePreset.BLOODCM ? "Blood Crossmatching" : "Original game & Duke mods";
            addLauncherAction(panel, selected ? "✓" : option == GamePreset.BLOODCM ? "BCM" : "D3D",
                    option.title, selected ? "Selected · " + detail : detail, () -> {
                        getSharedPreferences("launcher_settings",MODE_PRIVATE).edit()
                                .putString("game_preset",option.id).apply();
                        dialog.dismiss();
                        showLauncher();
                    }, selected ? 0xFF153049 : 0xFF1A222D,0xFFF4F6F8,0xFFADB5C0,0xFF70B3FF,density);
        }
        Button close = new Button(this);
        close.setText("Close");
        styleLauncherButton(close,0xFF1A222D,0xFFF4F6F8,density);
        close.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams=new LinearLayout.LayoutParams(-1,(int)(48*density));
        closeParams.topMargin=(int)(12*density);
        panel.addView(close,closeParams);
        ScrollView scroll=new ScrollView(this);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        Window window=dialog.getWindow();
        if (window!=null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.65f);
        }
        dialog.show();
        if (window!=null) {
            int width=Math.min(getResources().getDisplayMetrics().widthPixels-(int)(24*density),(int)(520*density));
            int heightLimit=getResources().getDisplayMetrics().heightPixels-(int)(48*density);
            panel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            window.setLayout(width,Math.min(panel.getMeasuredHeight(),heightLimit));
            window.setGravity(Gravity.CENTER);
        }
    }

    private static File childIgnoringCase(File directory, String name) {
        if (directory == null) return null;
        File exact = new File(directory, name);
        if (exact.exists()) return exact;
        File[] children = directory.listFiles();
        if (children != null) for (File child : children)
            if (child.getName().equalsIgnoreCase(name)) return child;
        return null;
    }
    private static boolean isBloodCmFolder(File directory) {
        File grp = childIgnoringCase(directory, "BloodCM.grp");
        File code = childIgnoringCase(directory, "blcons");
        File con = childIgnoringCase(code, "bloodcm.con");
        return grp != null && grp.isFile() && grp.length() > 16 && con != null && con.isFile();
    }

    private void launchPreset(boolean polymer) { launchPreset(polymer,false); }

    private void launchPreset(boolean polymer, boolean emergency) {
        DiagnosticApp.stage("Launch requested: preset="+preset.id+" polymer="+polymer+" emergency="+emergency);
        try {
            File base = baseConfigDir();
            if (!base.isDirectory() && !base.mkdirs()) throw new java.io.IOException("Cannot create config folder");
            try (FileOutputStream output = new FileOutputStream(new File(base, "android-game-preset.txt"))) {
                output.write(preset.id.getBytes(StandardCharsets.UTF_8));
            }
            if (!emergency && !writeRendererConfig(polymer)) throw new java.io.IOException("Could not save renderer settings");
        } catch (Exception error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        String signature = preset.id + ":" + polymer + ":" + readGameDir() + ":safe=" + emergency;
        android.content.SharedPreferences settings = getSharedPreferences("launcher_settings", MODE_PRIVATE);
        android.app.ActivityManager manager = (android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
        int runningPid = 0;
        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes != null) for (android.app.ActivityManager.RunningAppProcessInfo process : processes)
            if ((getPackageName() + ":game").equals(process.processName)) runningPid = process.pid;
        final int gamePid = runningPid;
        Runnable start = () -> {
            settings.edit().putString("running_game_signature", signature).apply();
            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra("game_preset", preset.id);
            intent.putExtra("emergency_start",emergency);
            startActivity(intent);
        };
        if (gamePid != 0 && !signature.equals(settings.getString("running_game_signature", ""))) {
            new AlertDialog.Builder(this).setTitle("Restart game?")
                    .setMessage("Apply the selected preset, folder and renderer by restarting the game. Unsaved progress will be lost.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Restart", (dialog, which) -> {
                        android.os.Process.killProcess(gamePid);
                        new android.os.Handler(getMainLooper()).postDelayed(start, 300);
                    }).show();
        } else start.run();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        File configDir = new File(getFilesDir(), ".config/eduke32");
        gameData = new File(configDir, "DUKE3D.GRP");
        gameDirMarker = new File(configDir, "android-game-dir.txt");
        showLauncher();
    }

    private void showLauncher() {
        preset = GamePreset.fromId(getSharedPreferences("launcher_settings", MODE_PRIVATE)
                .getString("game_preset", "duke3d"));
        gameDirMarker = new File(baseConfigDir(), preset.directoryMarker);
        String folder = readGameDir();
        final int background = 0xFF101216;
        final int surface = 0xFF1A222D;
        final int accent = 0xFF0878FF;
        final int textPrimary = 0xFFF4F6F8;
        final int textSecondary = 0xFFADB5C0;
        final int blue = 0xFF70B3FF;
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * density);

        Window window = getWindow();
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(background);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        ImageView banner = new ImageView(this);
        banner.setAdjustViewBounds(true);
        banner.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (preset == GamePreset.BLOODCM) {
            File group = folder == null ? null : childIgnoringCase(new File(folder), "BloodCM.grp");
            Bitmap artwork = BloodCmArtwork.load(group, new File(getCacheDir(), "bloodcm-banner"));
            if (artwork != null) banner.setImageBitmap(artwork);
            else banner.setVisibility(View.GONE);
        } else {
        try (InputStream imageInput = getAssets().open("game.bmp")) {
            Bitmap source = BitmapFactory.decodeStream(imageInput);
            if (source != null) {
                Matrix rotation = new Matrix();
                rotation.postRotate(90f);
                Bitmap rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), rotation, true);
                banner.setImageBitmap(rotated);
                source.recycle();
            }
        } catch (Exception ignored) { }
        }
        page.addView(banner, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, (int) (15 * density), pad, pad);
        page.addView(content, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText(preset == GamePreset.BLOODCM ? "BloodCM" : "EDuke32");
        title.setTextSize(27);
        title.setTextColor(textPrimary);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView appVersion = new TextView(this);
        String versionName;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            versionName = "unknown";
        }
        appVersion.setText("Android port · v" + versionName);
        appVersion.setTextSize(12);
        appVersion.setTextColor(textSecondary);
        content.addView(appVersion, new LinearLayout.LayoutParams(-1, -2));


        boolean gameReady = preset == GamePreset.BLOODCM
                ? folder != null && isBloodCmFolder(new File(folder)) : gameData.isFile() || folder != null;
        TextView status = new TextView(this);
        status.setText(gameReady ? "✓  Game data ready · "
                + (preset == GamePreset.BLOODCM ? "BloodCM" : detectGrpVersion(folder))
                : preset == GamePreset.BLOODCM ? "Choose the BloodCM folder in Game folder & mods"
                : "Select game data to get started");
        status.setTextSize(14);
        status.setTextColor(gameReady ? 0xFFB7C4D3 : textSecondary);
        status.setPadding(0, (int) (5 * density), 0, (int) (9 * density));
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));

        addLauncherAction(content, "GAME", "Game preset", preset.title, () -> choosePreset(), surface, textPrimary, textSecondary, blue, density);
        if (preset == GamePreset.DUKE3D)
            addLauncherAction(content, "GRP", "Choose game data", "Select your DUKE3D.GRP", () -> chooseGameData(), surface, textPrimary, textSecondary, blue, density);
        addLauncherAction(content, "MOD", "Game folder & mods", (preset == GamePreset.BLOODCM ? "Select the folder containing BloodCM.grp and blcons" : "Manage game files and custom content"), () -> chooseGameFolder(), surface, textPrimary, textSecondary, blue, density);
        addLauncherAction(content, "PAD", "Arrange controls", "Customize touch controls and layout", () -> startActivity(new Intent(this, ControlLayoutActivity.class)), surface, textPrimary, textSecondary, blue, density);
        addLauncherAction(content, "CFG", "Reset config", "Restore default game settings", () -> confirmResetConfig(), surface, textPrimary, textSecondary, blue, density);
        addLauncherAction(content, "SAVE", "Saves & settings folder",
                SaveFolderSync.configured(this,preset.id)?"Folder selected · automatic save backups":"Choose a persistent folder before reinstalling",
                () -> {Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(pick,PICK_SAVE_FOLDER);},surface,textPrimary,textSecondary,blue,density);
        addLauncherAction(content, "DIAG", "Emergency launch", "Classic · fresh config · audio disabled", () -> {
            if(gameReady) launchPreset(false,true);
            else Toast.makeText(this,"Select game data first",Toast.LENGTH_LONG).show();
        }, surface, textPrimary, textSecondary, blue, density);
        addLauncherAction(content, "LOG", "Send log", "Share engine, Android and crash diagnostics", () -> shareLog(), surface, textPrimary, textSecondary, blue, density);

        android.content.SharedPreferences launcherSettings =
                getSharedPreferences("launcher_settings", MODE_PRIVATE);
        LinearLayout rendererCard = new LinearLayout(this);
        rendererCard.setOrientation(LinearLayout.HORIZONTAL);
        rendererCard.setGravity(Gravity.CENTER_VERTICAL);
        int rendererInset = (int) (14 * density);
        rendererCard.setPadding(rendererInset, (int) (11 * density), rendererInset, (int) (11 * density));
        GradientDrawable rendererShape = new GradientDrawable();
        rendererShape.setColor(surface);
        rendererShape.setCornerRadius(16 * density);
        rendererCard.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), rendererShape, null));
        rendererCard.setClickable(true);
        rendererCard.setFocusable(true);

        LinearLayout rendererCopy = new LinearLayout(this);
        rendererCopy.setOrientation(LinearLayout.VERTICAL);
        TextView rendererTitle = new TextView(this);
        rendererTitle.setText("Polymer renderer");
        rendererTitle.setTextSize(15);
        rendererTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        rendererTitle.setTextColor(textPrimary);
        rendererCopy.addView(rendererTitle, new LinearLayout.LayoutParams(-1, -2));
        TextView rendererSubtitle = new TextView(this);
        rendererSubtitle.setTextSize(12);
        rendererSubtitle.setTextColor(textSecondary);
        rendererCopy.addView(rendererSubtitle, new LinearLayout.LayoutParams(-1, -2));
        rendererCard.addView(rendererCopy, new LinearLayout.LayoutParams(0, -2, 1f));

        ToggleSwitch polymerToggle = new ToggleSwitch(this);
        polymerToggle.setContentDescription("Enable Polymer renderer");
        polymerToggle.setChecked(launcherSettings.getBoolean(preset.rendererKey, false));
        Runnable updateRendererSubtitle = () -> rendererSubtitle.setText(polymerToggle.isChecked()
                ? "Enhanced lighting · experimental"
                : "Classic renderer · best compatibility");
        updateRendererSubtitle.run();
        polymerToggle.setOnCheckedChangeListener((button, checked) -> {
            launcherSettings.edit().putBoolean(preset.rendererKey, checked).apply();
            updateRendererSubtitle.run();
        });
        rendererCard.addView(polymerToggle, new LinearLayout.LayoutParams(-2, -2));
        rendererCard.setOnClickListener(view -> polymerToggle.setChecked(!polymerToggle.isChecked()));
        LinearLayout.LayoutParams rendererParams = new LinearLayout.LayoutParams(-1, -2);
        rendererParams.topMargin = (int) (10 * density);
        content.addView(rendererCard, rendererParams);

        LinearLayout fireCard=new LinearLayout(this);
        fireCard.setGravity(Gravity.CENTER_VERTICAL);
        fireCard.setPadding(rendererInset,(int)(12*density),rendererInset,(int)(12*density));
        GradientDrawable fireShape=new GradientDrawable();
        fireShape.setColor(surface); fireShape.setCornerRadius(16*density);
        fireCard.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF),fireShape,null));
        LinearLayout fireCopy=new LinearLayout(this); fireCopy.setOrientation(LinearLayout.VERTICAL);
        TextView fireTitle=new TextView(this); fireTitle.setText("Volume + to fire");
        fireTitle.setTextColor(textPrimary); fireTitle.setTextSize(15);
        fireTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        fireCopy.addView(fireTitle);
        TextView fireHint=new TextView(this); fireHint.setText("Hold to shoot · gameplay only");
        fireHint.setTextColor(textSecondary); fireHint.setTextSize(12); fireCopy.addView(fireHint);
        fireCard.addView(fireCopy,new LinearLayout.LayoutParams(0,-2,1));
        ToggleSwitch fireToggle=new ToggleSwitch(this);
        fireToggle.setContentDescription("Volume up button fires in gameplay");
        fireToggle.setChecked(launcherSettings.getBoolean("volume_up_fire",false));
        fireToggle.setOnCheckedChangeListener((button,checked)->launcherSettings.edit().putBoolean("volume_up_fire",checked).apply());
        fireCard.addView(fireToggle);
        fireCard.setOnClickListener(view->fireToggle.setChecked(!fireToggle.isChecked()));
        LinearLayout.LayoutParams fireParams=new LinearLayout.LayoutParams(-1,-2);
        fireParams.topMargin=(int)(10*density); content.addView(fireCard,fireParams);

        addPerformanceToggle(content,launcherSettings,surface,textPrimary,textSecondary,blue,density);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(pad, (int) (10 * density), pad, (int) (12 * density));
        footer.setBackgroundColor(background);
        Button launch = new Button(this);
        launch.setText("▶   Launch " + preset.title);
        launch.setEnabled(gameReady);
        launch.setOnClickListener(view -> launchPreset(polymerToggle.isChecked()));
        styleLauncherButton(launch, accent, textPrimary, density);
        footer.addView(launch, new LinearLayout.LayoutParams(-1, (int) (58 * density)));
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void addPerformanceToggle(LinearLayout content,android.content.SharedPreferences settings,
            int surface,int textPrimary,int textSecondary,int blue,float density) {
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);
        int inset=(int)(14*density);card.setPadding(inset,(int)(12*density),inset,(int)(12*density));
        GradientDrawable shape=new GradientDrawable();shape.setColor(surface);shape.setCornerRadius(16*density);
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF),shape,null));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(this);title.setText("Prefer performance cores");title.setTextColor(textPrimary);title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));copy.addView(title);
        TextView hint=new TextView(this);hint.setText("Experimental - Polymer render thread");hint.setTextColor(textSecondary);hint.setTextSize(12);copy.addView(hint);
        card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        ToggleSwitch toggle=new ToggleSwitch(this);toggle.setContentDescription("Prefer performance CPU cores");
        toggle.setChecked(settings.getBoolean("prefer_big_cores",false));
        toggle.setOnCheckedChangeListener((button,checked)->settings.edit().putBoolean("prefer_big_cores",checked).apply());
        card.addView(toggle);card.setOnClickListener(view->toggle.setChecked(!toggle.isChecked()));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=(int)(10*density);content.addView(card,params);
    }

    private boolean writeRendererConfig(boolean enablePolymer) {
        File configDir = presetConfigDir();
        if (!configDir.isDirectory() && !configDir.mkdirs()) return false;
        File configFile = new File(configDir, preset.configName);
        String config = "";
        try (FileInputStream input = new FileInputStream(configFile)) {
            byte[] data = new byte[(int) Math.min(configFile.length(), 1024 * 1024)];
            int count = input.read(data);
            if (count > 0) config = new String(data, 0, count, StandardCharsets.UTF_8);
        } catch (java.io.FileNotFoundException ignored) {
        } catch (Exception error) {
            return false;
        }
        if (preset == GamePreset.BLOODCM) {
            config = setConfigValue(config, "Setup", "ForceSetup", "0");
            config = setConfigValue(config, "Setup", "NoAutoLoad", "1");
            File bindings = new File(configDir, "bloodcm-android_settings.cfg");
            if (bindings.isFile() && bindings.length() < 1024*1024) {
                try {
                    byte[] bytes=new byte[(int)bindings.length()];
                    try (java.io.DataInputStream input=new java.io.DataInputStream(new FileInputStream(bindings))) { input.readFully(bytes); }
                    String saved=new String(bytes,StandardCharsets.UTF_8);
                    String oldBank=new File(baseConfigDir(), "GeneralUser GS SoftSynth v1.44.sf2").getAbsolutePath();
                    String updated=saved.replace("mus_sf2_bank \""+oldBank+"\"", "mus_sf2_bank \"generaluser.sf2\"");
                    if (!saved.equals(updated)) try (FileOutputStream output=new FileOutputStream(bindings)) { output.write(updated.getBytes(StandardCharsets.UTF_8)); }
                } catch (Exception error) { return false; }
            }
            if (!bindings.exists()) {
                try (FileOutputStream output = new FileOutputStream(bindings)) {
                    String bank = "generaluser.sf2";
                    output.write(("mus_device 4\nmus_sf2_bank \"" + bank + "\"\n").getBytes(StandardCharsets.UTF_8));
                } catch (Exception error) { return false; }
            }
        }
        config = setConfigValue(config, "Screen Setup", "Polymer", enablePolymer ? "1" : "0");
        config = setConfigValue(config, "Screen Setup", "ScreenBPP", enablePolymer ? "32" : "8");
        try (FileOutputStream output = new FileOutputStream(configFile)) {
            output.write(config.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private String setConfigValue(String config, String section, String key, String value) {
        String[] lines = config.split("\\r?\\n", -1);
        StringBuilder result = new StringBuilder();
        boolean foundSection = false;
        boolean inSection = false;
        boolean foundKey = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                if (inSection && !foundKey) {
                    result.append(key).append(" = ").append(value).append('\n');
                    foundKey = true;
                }
                inSection = trimmed.substring(1, trimmed.length() - 1).equalsIgnoreCase(section);
                foundSection |= inSection;
            }
            if (inSection && trimmed.regionMatches(true, 0, key, 0, key.length())
                    && trimmed.length() > key.length()
                    && (trimmed.charAt(key.length()) == '=' || Character.isWhitespace(trimmed.charAt(key.length())))) {
                result.append(key).append(" = ").append(value).append('\n');
                foundKey = true;
            } else {
                result.append(line).append('\n');
            }
        }
        if (!foundSection) result.append("\n[").append(section).append("]\n");
        if (!foundKey) result.append(key).append(" = ").append(value).append('\n');
        return result.toString();
    }
    private String detectGrpVersion(String selectedFolder) {
        File grp = null;
        if (selectedFolder != null) {
            File directory = new File(selectedFolder);
            File[] groups = directory.listFiles((parent, name) -> name.toLowerCase(Locale.ROOT).endsWith(".grp"));
            if (groups != null) {
                for (File candidate : groups) {
                    if (candidate.getName().equalsIgnoreCase("DUKE3D.GRP") && candidate.length() > 1024) {
                        grp = candidate;
                        break;
                    }
                }
            }
        }
        if (grp == null && gameData.isFile()) grp = gameData;
        if (grp == null || !grp.isFile()) return "GRP version unknown";

        long size = grp.length();
        CRC32 crc = new CRC32();
        try (InputStream input = new FileInputStream(grp)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) crc.update(buffer, 0, count);
        } catch (Exception ignored) {
            return "GRP version unknown";
        }

        long checksum = crc.getValue();
        if (isGrpVersion(checksum, size, 0x02F18900L, 9690241L)
                || isGrpVersion(checksum, size, 0xA28AA589L, 10429258L)
                || isGrpVersion(checksum, size, 0x912E1E8DL, 10442980L)
                || isGrpVersion(checksum, size, 0x983AD923L, 11035779L)
                || isGrpVersion(checksum, size, 0xC5F71561L, 10444391L)
                || isGrpVersion(checksum, size, 0x73A15EE7L, 10628573L))
            return "Shareware";
        if (isGrpVersion(checksum, size, 0xBBC9CE44L, 26524524L)) return "v1.3D";
        if (isGrpVersion(checksum, size, 0xAA4F6A40L, 26385383L)) return "v1.3D (Korean)";
        if (isGrpVersion(checksum, size, 0xFD3DCFF1L, 44356548L)) return "Atomic Edition";
        if (isGrpVersion(checksum, size, 0x982AFE4AL, 44356548L)) return "Atomic Edition (WT)";
        if (isGrpVersion(checksum, size, 0xF514A6ACL, 44348015L)) return "Plutonium Pak";
        return "GRP version unknown";
    }

    private boolean isGrpVersion(long actualCrc, long actualSize, long expectedCrc, long expectedSize) {
        return actualCrc == expectedCrc && actualSize == expectedSize;
    }

    private void addLauncherAction(LinearLayout parent, String icon, String titleText, String subtitleText,
                                  Runnable action, int surface, int textPrimary, int textSecondary, int blue, float density) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int horizontal = (int) (13 * density);
        card.setPadding(horizontal, (int) (10 * density), horizontal, (int) (10 * density));
        GradientDrawable cardShape = new GradientDrawable();
        cardShape.setColor(surface);
        cardShape.setCornerRadius(15 * density);
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), cardShape, null));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(view -> action.run());

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextColor(blue);
        iconView.setTextSize(13);
        iconView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        iconView.setGravity(Gravity.CENTER);
        GradientDrawable iconShape = new GradientDrawable();
        iconShape.setColor(0xFF102A48);
        iconShape.setShape(GradientDrawable.OVAL);
        iconView.setBackground(iconShape);
        card.addView(iconView, new LinearLayout.LayoutParams((int) (48 * density), (int) (48 * density)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding((int) (14 * density), 0, (int) (7 * density), 0);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(textPrimary);
        title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        labels.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(textSecondary);
        subtitle.setTextSize(12);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = (int) (3 * density);
        labels.addView(subtitle, subtitleParams);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(textSecondary);
        arrow.setTextSize(28);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams((int) (24 * density), -2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = (int) (5 * density);
        params.bottomMargin = (int) (5 * density);
        parent.addView(card, params);
    }

    private void styleLauncherButton(Button button, int color, int textColor, float density) {
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        button.setTextColor(textColor);
        button.setPadding((int) (18 * density), 0, (int) (18 * density), 0);
        button.setElevation(0f);
        button.setStateListAnimator(null);
        button.setAlpha(button.isEnabled() ? 1f : 0.45f);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(15 * density);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null));
    }
    private void confirmResetConfig() {
        new AlertDialog.Builder(this)
                .setTitle("Reset game config?")
                .setMessage("This removes settings and key bindings for the selected game preset. Your game files and touch-control layout stay intact.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, which) -> {
                    File configDir = presetConfigDir();
                    boolean removed = new File(configDir, preset.configName).delete();
                    removed |= new File(configDir, "settings.cfg").delete();
                    removed |= new File(configDir, "eduke32_settings.cfg").delete();
                    if (preset == GamePreset.BLOODCM) removed |= new File(configDir, "bloodcm-android_settings.cfg").delete();
                    Toast.makeText(this, removed ? "Game config reset" : "No saved game config found",
                            Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void shareLog() {
        File configLog = new File(presetConfigDir(), "eduke32.log");
        String gamePath = readGameDir();
        File gameLog = gamePath == null ? null : new File(gamePath, "eduke32.log");
        File log = null;
        if (configLog.isFile()) log = configLog;
        if (gameLog != null && gameLog.isFile()
                && (log == null || gameLog.lastModified() >= log.lastModified()))
            log = gameLog;
        try {
            DiagnosticApp.export(this,configLog,gameLog);
            Uri attachment = Uri.parse("content://com.codex.eduke32.logprovider/eduke32-runtime.txt");

            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "EDuke32 Android log");
            send.putExtra(Intent.EXTRA_TEXT, "EDuke32 Android runtime log attached as eduke32-runtime.txt");
            send.putExtra(Intent.EXTRA_STREAM, attachment);
            send.setClipData(ClipData.newUri(getContentResolver(), "EDuke32 runtime log", attachment));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Send EDuke32 log"));
        } catch (Exception error) {
            Toast.makeText(this, "Could not read log: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void chooseGameData() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_GAME_DATA);
    }

    private void chooseGameFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, PICK_GAME_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_GAME_DATA) {
            importGameData(uri);
        } else if (requestCode == PICK_GAME_FOLDER || requestCode == PICK_SAVE_FOLDER) {
            try {getContentResolver().takePersistableUriPermission(uri,data.getFlags()
                    &(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}
            catch(Exception error) {Toast.makeText(this,"Cannot retain folder access: "+error.getMessage(),Toast.LENGTH_LONG).show();return;}
            if(requestCode==PICK_GAME_FOLDER) importGameFolder(uri);
            else {
                final String selected=preset.id;
                new Thread(()->{
                    try {
                        SaveFolderSync.bind(this,selected,uri,"");SaveFolderSync.restore(this,selected);
                        SaveFolderSync.schedule(this,selected);
                        runOnUiThread(()->{showLauncher();Toast.makeText(this,"Save folder selected; existing backup restored if present",Toast.LENGTH_LONG).show();});
                    } catch(Exception error) {DiagnosticApp.stage("Save folder selection failed: "+error);
                        runOnUiThread(()->Toast.makeText(this,"Save folder error: "+error.getMessage(),Toast.LENGTH_LONG).show());}
                },"save-folder-select").start();
            }
        }
    }

    private void importGameData(Uri uri) {
        String displayName = getDisplayName(uri);
        if (displayName == null || !displayName.toLowerCase(Locale.ROOT).endsWith(".grp")) {
            Toast.makeText(this, "Select a .GRP game data file", Toast.LENGTH_LONG).show();
            return;
        }
        File configDir = gameData.getParentFile();
        if (!configDir.isDirectory() && !configDir.mkdirs()) {
            Toast.makeText(this, "Could not create game data folder", Toast.LENGTH_LONG).show();
            return;
        }
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(gameData)) {
            if (input == null) throw new IllegalStateException("Cannot open selected file");
            copy(input, output);
            gameDirMarker.delete();
            showLauncher();
        } catch (Exception error) {
            Toast.makeText(this, "Could not copy GRP: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importGameFolder(Uri treeUri) {
        File importRoot = new File(getFilesDir(), "game-folders/game-" + System.currentTimeMillis());
        try {
            if (!importRoot.mkdirs()) throw new IllegalStateException("Could not create local game folder");
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            String grp = copyTree(treeUri, rootId, importRoot, "");
            if (grp == null) {
                deleteTree(importRoot);
                Toast.makeText(this, "Selected folder must contain at least one .GRP file", Toast.LENGTH_LONG).show();
                return;
            }
            File gameRoot = findGameRoot(importRoot);
            if (gameRoot == null) {
                deleteTree(importRoot);
                Toast.makeText(this, preset == GamePreset.BLOODCM ? "BloodCM.grp and blcons/bloodcm.con were not found in the selected folder" : "Could not locate the game content folder", Toast.LENGTH_LONG).show();
                return;
            }
            File markerParent = gameDirMarker.getParentFile();
            if (!markerParent.isDirectory() && !markerParent.mkdirs())
                throw new IllegalStateException("Could not save game folder setting");
            try (FileOutputStream output = new FileOutputStream(gameDirMarker)) {
                output.write(gameRoot.getAbsolutePath().getBytes("UTF-8"));
            }
            String relative=importRoot.toURI().relativize(gameRoot.toURI()).getPath();
            SaveFolderSync.bind(this,preset.id,treeUri,relative);
            SaveFolderSync.restore(this,preset.id);
            SaveFolderSync.schedule(this,preset.id);
            showLauncher();
            Toast.makeText(this, "Game files copied. Using folder: " + gameRoot.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            deleteTree(importRoot);
            Toast.makeText(this, "Could not copy game folder: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String copyTree(Uri treeUri, String documentId, File destination, String relative) throws Exception {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] columns = { DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE };
        String mainGrp = null;
        String anyGrp = null;
        try (Cursor cursor = getContentResolver().query(childrenUri, columns, null, null, null)) {
            if (cursor == null) throw new IllegalStateException("Cannot read selected folder");
            while (cursor.moveToNext()) {
                String childId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if (name == null || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\")) continue;
                File child = new File(destination, name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if(name.startsWith("eduke32-android-"))continue;
                    if (!child.isDirectory() && !child.mkdir()) throw new IllegalStateException("Cannot create " + name);
                    String nestedGrp = copyTree(treeUri, childId, child, relative + name + "/");
                    if (nestedGrp != null && anyGrp == null) anyGrp = nestedGrp;
                } else {
                    Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                    try (InputStream input = getContentResolver().openInputStream(fileUri);
                         FileOutputStream output = new FileOutputStream(child)) {
                        if (input == null) throw new IllegalStateException("Cannot read " + name);
                        copy(input, output);
                    }
                    if (name.toLowerCase(Locale.ROOT).endsWith(".grp")) {
                        if (anyGrp == null) anyGrp = relative + name;
                        if (name.equalsIgnoreCase("DUKE3D.GRP")) mainGrp = relative + name;
                    }
                }
            }
        }
        return mainGrp != null ? mainGrp : anyGrp;
    }

    private File findGameRoot(File root) {
        File[] children = root.listFiles();
        if (children == null) return null;

        if (preset == GamePreset.BLOODCM) {
            if (isBloodCmFolder(root)) return root;
            for (File child : children) if (child.isDirectory()) {
                File nested = findGameRoot(child);
                if (nested != null) return nested;
            }
            return null;
        }
        boolean hasGameCon = false;
        boolean hasGroup = false;
        for (File child : children) {
            if (!child.isFile()) continue;
            String name = child.getName();
            if (name.equalsIgnoreCase("GAME.CON")) hasGameCon = true;
            if (name.toLowerCase(Locale.ROOT).endsWith(".grp")) hasGroup = true;
        }
        if (hasGameCon && hasGroup) return root;

        // Prefer a nested mod's own content root (GAME.CON plus its GRPs) over
        // a parent folder that only contains the base game's GRP.
        for (File child : children) {
            if (!child.isDirectory()) continue;
            File nested = findGameRoot(child);
            if (nested != null && containsGameConAndGroup(nested)) return nested;
        }

        if (hasGroup) return root;

        for (File child : children) {
            if (!child.isDirectory()) continue;
            File nested = findGameRoot(child);
            if (nested != null) return nested;
        }
        return null;
    }

    private boolean containsGameConAndGroup(File directory) {
        File[] children = directory.listFiles();
        if (children == null) return false;
        boolean hasGameCon = false;
        boolean hasGroup = false;
        for (File child : children) {
            if (!child.isFile()) continue;
            String name = child.getName();
            if (name.equalsIgnoreCase("GAME.CON")) hasGameCon = true;
            if (name.toLowerCase(Locale.ROOT).endsWith(".grp")) hasGroup = true;
        }
        return hasGameCon && hasGroup;
    }

    private void copy(InputStream input, FileOutputStream output) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }

    private void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private String readGameDir() {
        if (!gameDirMarker.isFile()) return null;
        try {
            byte[] bytes = new byte[(int) gameDirMarker.length()];
            try (InputStream input = new java.io.FileInputStream(gameDirMarker)) {
                int offset = 0;
                while (offset < bytes.length) {
                    int count = input.read(bytes, offset, bytes.length - offset);
                    if (count < 0) break;
                    offset += count;
                }
            }
            String path = new String(bytes, "UTF-8");
            return new File(path).isDirectory() ? path : null;
        } catch (Exception ignored) { return null; }
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) { }
        return null;
    }
}
