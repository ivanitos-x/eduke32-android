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
    private static final int PICK_GAME_FOLDER = 1002;
    private static final int PICK_SAVE_FOLDER = 1003;
    private File gameData;
    private File gameDirMarker;
    private GamePreset preset;
    private GamePreset pendingDirectPreset;
    private static volatile boolean progressImportRunning;

    private File baseConfigDir() { return new File(getFilesDir(), ".config/eduke32"); }
    private File presetConfigDir() {
        File direct=DirectGameStorage.selected(this,preset);
        if(direct!=null)return direct;
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
        org.json.JSONObject presets = customPresets();
        String activeId = activeCustomPreset(presets);
        for (GamePreset option : GamePreset.values()) {
            boolean selected = option == preset && activeId.isEmpty();
            String detail = option == GamePreset.BLOODCM ? "Blood Crossmatching" : "Original game & Duke mods";
            addLauncherAction(panel, selected ? "✓" : option == GamePreset.BLOODCM ? "BCM" : "D3D",
                    option.title, selected ? "Selected · " + detail : detail, () -> {
                        if (!selectBuiltInPreset(option)) return;
                        dialog.dismiss();
                        showLauncher();
                    }, selected ? 0xFF153049 : 0xFF1A222D,0xFFF4F6F8,0xFFADB5C0,0xFF70B3FF,density);
        }
        addLauncherAction(panel, "+", "Save as preset", "Save the current game folder and launch script",
                () -> { dialog.dismiss(); nameCustomPreset(null); },
                0xFF1A222D,0xFFF4F6F8,0xFFADB5C0,0xFF70B3FF,density);
        for (java.util.Iterator<String> ids = presets.keys(); ids.hasNext();) {
            String id = ids.next();
            org.json.JSONObject entry = presets.optJSONObject(id);
            if (entry == null) continue;
            boolean selected = id.equals(activeId);
            String detail = entry.optString("batch");
            if (selected) detail = detail.isEmpty() ? "Selected" : "Selected · " + detail;
            View card = addLauncherAction(panel, selected ? "✓" : "MOD", entry.optString("name"), detail,
                    () -> { if (selectCustomPreset(id, entry)) dialog.dismiss(); },
                    selected ? 0xFF153049 : 0xFF1A222D,0xFFF4F6F8,0xFFADB5C0,0xFF70B3FF,density);
            card.setOnLongClickListener(view -> { dialog.dismiss(); manageCustomPreset(id, entry); return true; });
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

    private org.json.JSONObject customPresets() {
        try {
            return new org.json.JSONObject(getSharedPreferences("launcher_settings", MODE_PRIVATE)
                    .getString("custom_presets", "{}"));
        } catch (org.json.JSONException error) {
            return new org.json.JSONObject();
        }
    }

    private void saveCustomPresets(org.json.JSONObject entries) {
        getSharedPreferences("launcher_settings", MODE_PRIVATE).edit()
                .putString("custom_presets", entries.toString()).apply();
    }

    private void nameCustomPreset(String existingId) {
        org.json.JSONObject entries = customPresets();
        org.json.JSONObject existing = existingId == null ? null : entries.optJSONObject(existingId);
        File folder = DirectGameStorage.selected(this, preset);
        if (existing == null && folder == null) {
            Toast.makeText(this, "Select a game folder first", Toast.LENGTH_LONG).show();
            return;
        }
        String gameId = preset.id;
        String batch = getSharedPreferences("launcher_settings", MODE_PRIVATE).getString(gameId + "_batch", "");
        android.widget.EditText name = new android.widget.EditText(this);
        name.setSingleLine(true);
        name.setText(existing == null ? folder.getName() : existing.optString("name"));
        LinearLayout panel = presetDialogContent(existing == null ? "Save as preset" : "Rename preset");
        name.setTextColor(0xFFF4F6F8);
        name.setBackgroundTintList(ColorStateList.valueOf(0xFF70B3FF));
        panel.addView(name, new LinearLayout.LayoutParams(-1, -2));
        android.app.Dialog prompt = showPresetDialog(panel);
        presetAction(panel, "✓", "Save", "", () -> {
            String title = name.getText().toString().trim();
            if (title.isEmpty()) {
                name.setError("Enter a preset name");
                return;
            }
            try {
                org.json.JSONObject entry = existing == null ? new org.json.JSONObject() : existing;
                if (existing == null) {
                    entry.put("game", gameId);
                    entry.put("folder", folder.getAbsolutePath());
                    entry.put("batch", batch);
                }
                entry.put("name", title);
                entries.put(existing == null ? java.util.UUID.randomUUID().toString() : existingId, entry);
                saveCustomPresets(entries);
                prompt.dismiss();
                choosePreset();
            } catch (org.json.JSONException error) {
                Toast.makeText(this, "Could not save preset", Toast.LENGTH_LONG).show();
            }
        });
        presetAction(panel, "‹", "Cancel", "", () -> { prompt.dismiss(); choosePreset(); });
    }

    private LinearLayout presetDialogContent(String title) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int inset = (int)(20 * density);
        panel.setPadding(inset, inset, inset, inset);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xFF101216);
        shape.setCornerRadius(24 * density);
        panel.setBackground(shape);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(0xFFF4F6F8);
        heading.setTextSize(23);
        panel.addView(heading);
        return panel;
    }

    private android.app.Dialog showPresetDialog(LinearLayout panel) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout((int)(getResources().getDisplayMetrics().widthPixels * .86f), -2);
        }
        return dialog;
    }

    private View presetAction(LinearLayout panel, String icon, String title, String description, Runnable action) {
        return addLauncherAction(panel, icon, title, description, action,
                0xFF1A222D, 0xFFF4F6F8, 0xFFADB5C0, 0xFF70B3FF,
                getResources().getDisplayMetrics().density);
    }

    private void manageCustomPreset(String id, org.json.JSONObject entry) {
        LinearLayout panel = presetDialogContent(entry.optString("name"));
        android.app.Dialog dialog = showPresetDialog(panel);
        presetAction(panel, "TXT", "Rename", "Change the preset name", () -> {
            dialog.dismiss();
            nameCustomPreset(id);
        });
        presetAction(panel, "×", "Delete", "Remove the preset from the launcher", () -> {
            dialog.dismiss();
            LinearLayout confirm = presetDialogContent("Delete " + entry.optString("name") + "?");
            android.app.Dialog confirmation = showPresetDialog(confirm);
            presetAction(confirm, "×", "Delete", "Game files stay on the phone", () -> {
                org.json.JSONObject entries = customPresets();
                entries.remove(id);
                saveCustomPresets(entries);
                confirmation.dismiss();
                choosePreset();
            });
            presetAction(confirm, "‹", "Cancel", "", () -> { confirmation.dismiss(); choosePreset(); });
        });
        presetAction(panel, "‹", "Close", "", () -> { dialog.dismiss(); choosePreset(); });
    }

    private String activeCustomPreset(org.json.JSONObject presets) {
        android.content.SharedPreferences settings = getSharedPreferences("launcher_settings", MODE_PRIVATE);
        String savedId = settings.getString("active_custom_preset", null);
        if ("".equals(savedId)) return "";
        File folder = DirectGameStorage.selected(this, preset);
        if (folder == null) return "";
        String batch = settings.getString(preset.id + "_batch", "");
        for (java.util.Iterator<String> ids = presets.keys(); ids.hasNext();) {
            String id = ids.next();
            if (savedId != null && !savedId.equals(id)) continue;
            org.json.JSONObject entry = presets.optJSONObject(id);
            if (entry != null && preset.id.equals(entry.optString("game"))
                    && folder.equals(new File(entry.optString("folder")))
                    && batch.equals(entry.optString("batch"))) return id;
        }
        return "";
    }

    private boolean selectCustomPreset(String id, org.json.JSONObject entry) {
        try {
            GamePreset base = GamePreset.fromId(entry.getString("game"));
            File folder = new File(entry.getString("folder"));
            String batch = entry.optString("batch");
            if (!batch.isEmpty() && (!batch.equals(new File(batch).getName()) || !new File(folder, batch).isFile()))
                throw new java.io.IOException("The saved launch script is missing");
            DirectGameStorage.checkWritable(folder);
            rememberBuiltInPreset(base);
            DirectGameStorage.bind(this, base, folder);
            getSharedPreferences("launcher_settings", MODE_PRIVATE).edit()
                    .putString("game_preset", base.id).putString(base.id + "_batch", batch)
                    .putString("active_custom_preset", id).apply();
            showLauncher();
            return true;
        } catch (java.io.IOException | org.json.JSONException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void rememberBuiltInPreset(GamePreset base) {
        android.content.SharedPreferences settings = getSharedPreferences("launcher_settings", MODE_PRIVATE);
        String key = base.id + "_builtin_folder";
        if (settings.contains(key)) return;
        File current = DirectGameStorage.selected(this, base);
        String batch = settings.getString(base.id + "_batch", "");
        org.json.JSONObject presets = customPresets();
        for (java.util.Iterator<String> ids = presets.keys(); current != null && ids.hasNext();) {
            org.json.JSONObject entry = presets.optJSONObject(ids.next());
            if (entry != null && base.id.equals(entry.optString("game"))
                    && current.equals(new File(entry.optString("folder")))
                    && batch.equals(entry.optString("batch"))) {
                current = null;
                batch = "";
            }
        }
        settings.edit().putString(key, current == null ? "" : current.getAbsolutePath())
                .putString(base.id + "_builtin_batch", current == null ? "" : batch).apply();
    }

    private boolean selectBuiltInPreset(GamePreset base) {
        try {
            rememberBuiltInPreset(base);
            android.content.SharedPreferences settings = getSharedPreferences("launcher_settings", MODE_PRIVATE);
            String folder = settings.getString(base.id + "_builtin_folder", "");
            File marker = DirectGameStorage.marker(this, base);
            if (!folder.isEmpty()) DirectGameStorage.bind(this, base, new File(folder));
            else if (marker.exists() && !marker.delete()) throw new java.io.IOException("Cannot clear the mod folder");
            settings.edit().putString("game_preset", base.id).putString("active_custom_preset", "")
                    .putString(base.id + "_batch", settings.getString(base.id + "_builtin_batch", "")).apply();
            return true;
        } catch (java.io.IOException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private String[] selectedBatchArguments() throws java.io.IOException {
        String name=getSharedPreferences("launcher_settings",MODE_PRIVATE).getString(preset.id+"_batch","");
        if(name.isEmpty())return new String[0];
        File root=DirectGameStorage.selected(this,preset);
        if(root==null)throw new java.io.IOException("Batch launch requires a direct working folder");
        if(name.contains("/")||name.contains("\\"))throw new java.io.IOException("Invalid batch filename");
        File file=new File(root,name);
        File[] available = listBatchScripts(root);
        if (available != null && available.length == 0) return new String[0];
        if(!file.isFile()||file.length()>65536)throw new java.io.IOException("Selected batch is missing or too large; select its file again");
        return BatchLaunch.parse(StorageText.read(file));
    }
    private static File[] listBatchScripts(File root) {
        if (root == null) return null;
        return root.listFiles(file -> {
            String name = file.getName().toLowerCase(Locale.ROOT);
            return file.isFile() && (name.endsWith(".bat") || name.endsWith(".cmd"));
        });
    }

    private void chooseBatch() {
        File root=DirectGameStorage.selected(this,preset);
        if(root==null) {Toast.makeText(this,"Choose a direct working folder first",Toast.LENGTH_LONG).show();return;}
        File[] files=listBatchScripts(root);
        if(files==null) {Toast.makeText(this,"Cannot read working folder",Toast.LENGTH_LONG).show();return;}
        java.util.Arrays.sort(files,(a,b)->a.getName().compareToIgnoreCase(b.getName()));
        String[] choices=new String[files.length+1];choices[0]="None - normal launch";
        for(int i=0;i<files.length;i++)choices[i+1]=files[i].getName();
        showLauncherChoices("PC launch script", choices, -1, which -> {
                    try {
                        String name=which==0?"":files[which-1].getName();
                        String[] args=which==0?new String[0]:BatchLaunch.parse(StorageText.read(files[which-1]));
                        android.content.SharedPreferences.Editor editor=getSharedPreferences("launcher_settings",MODE_PRIVATE).edit().putString(preset.id+"_batch",name);
                        android.content.SharedPreferences state = getSharedPreferences("launcher_settings", MODE_PRIVATE);
                        if ("".equals(state.getString("active_custom_preset", "")))
                            editor.putString(preset.id + "_builtin_batch", name);
                        for(String arg:args)if(arg.equalsIgnoreCase("-forcegl")&&selectedRenderer()==RendererMode.CLASSIC)
                            editor.putString(preset.rendererKey+"_mode",RendererMode.POLYMOST.id);
                        editor.apply();showLauncher();
                    } catch(Exception error) {Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}
                });
    }

    private RendererMode selectedRenderer() {
        android.content.SharedPreferences settings=getSharedPreferences("launcher_settings",MODE_PRIVATE);
        String key=preset.rendererKey+"_mode";
        return settings.contains(key)?RendererMode.fromId(settings.getString(key,"classic"))
                :(settings.getBoolean(preset.rendererKey,false)?RendererMode.POLYMER:RendererMode.CLASSIC);
    }
    private void launchPreset(RendererMode renderer) { launchPreset(renderer,false); }

    private void launchPreset(RendererMode renderer, boolean emergency) {
        if(progressImportRunning) {Toast.makeText(this,"Wait for progress import to finish",Toast.LENGTH_LONG).show();return;}
        final String[] batchArguments;
        try {batchArguments=emergency?new String[0]:selectedBatchArguments();}
        catch(Exception error) {Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();return;}
        DiagnosticApp.stage("Launch requested: preset="+preset.id+" renderer="+renderer.id+" emergency="+emergency);
        String signature = preset.id + ":" + renderer.id + ":" + readGameDir() + ":safe=" + emergency
                + ":batch=" + java.util.Arrays.toString(batchArguments)
                + ":direct=" + (DirectGameStorage.selected(this,preset)!=null);
        android.content.SharedPreferences settings = getSharedPreferences("launcher_settings", MODE_PRIVATE);
        android.app.ActivityManager manager = (android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
        int runningPid = 0;
        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes != null) for (android.app.ActivityManager.RunningAppProcessInfo process : processes)
            if ((getPackageName() + ":game").equals(process.processName)) runningPid = process.pid;
        final int gamePid = runningPid;
        Runnable start = () -> {
            // A resumed process owns its config. Prepare files only for a new
            // process, after any requested restart has stopped the old one.
            if (gamePid == 0 || !signature.equals(settings.getString("running_game_signature", ""))) {
                try {
                    File base=baseConfigDir();
                    if(!base.isDirectory()&&!base.mkdirs())throw new java.io.IOException("Cannot create config folder");
                    File direct=DirectGameStorage.selected(this,preset);
                    if (direct == null && readGameDir() != null) {
                        direct = new File(readGameDir());
                        DirectGameStorage.checkWritable(direct);
                        DirectGameStorage.bind(this, preset, direct);
                    } else if (direct != null) DirectGameStorage.checkWritable(direct);
                    if(!emergency&&!writeRendererConfig(renderer))throw new java.io.IOException("Could not save renderer settings");
                    try(FileOutputStream output=new FileOutputStream(new File(base,"android-game-preset.txt"))) {
                        output.write(preset.id.getBytes(StandardCharsets.UTF_8));
                    }
                } catch(Exception error) {
                    Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();return;
                }
            }
            settings.edit().putString("running_game_signature", signature).apply();
            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra("game_preset", preset.id);
            intent.putExtra("emergency_start",emergency);
            intent.putExtra("game_arguments",batchArguments);
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
        if(state!=null && state.containsKey("pending_direct_preset"))pendingDirectPreset=GamePreset.fromId(state.getString("pending_direct_preset"));
        File configDir = new File(getFilesDir(), ".config/eduke32");
        gameData = new File(configDir, "DUKE3D.GRP");
        gameDirMarker = new File(configDir, "android-game-dir.txt");
        showLauncher();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if(pendingDirectPreset!=null)state.putString("pending_direct_preset",pendingDirectPreset.id);
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
        File directFolder=DirectGameStorage.selected(this,preset);
        if(directFolder!=null)status.append("\nRead/write folder: "+directFolder.getAbsolutePath());
        status.setTextColor(gameReady ? 0xFFB7C4D3 : textSecondary);
        status.setPadding(0, (int) (5 * density), 0, (int) (9 * density));
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));

        addLauncherAction(content, "GAME", "Game preset", preset.title, () -> choosePreset(), surface, textPrimary, textSecondary, blue, density);
        addLauncherAction(content, "MOD", "Game folder & mods", (preset == GamePreset.BLOODCM ? "Select the folder containing BloodCM.grp and blcons" : "Manage game files and custom content"), () -> chooseGameFolder(), surface, textPrimary, textSecondary, blue, density);
        File[] batchScripts = listBatchScripts(directFolder);
        if (batchScripts != null && batchScripts.length > 0)
            addLauncherAction(content,"BAT","PC launch script",getSharedPreferences("launcher_settings",MODE_PRIVATE).getString(preset.id+"_batch", "None - normal launch"),()->chooseBatch(),surface,textPrimary,textSecondary,blue,density);
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
        rendererTitle.setText("Renderer");
        rendererTitle.setTextSize(15);
        rendererTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        rendererTitle.setTextColor(textPrimary);
        rendererCopy.addView(rendererTitle, new LinearLayout.LayoutParams(-1, -2));
        TextView rendererSubtitle = new TextView(this);
        rendererSubtitle.setTextSize(12);
        rendererSubtitle.setTextColor(textSecondary);
        rendererCopy.addView(rendererSubtitle, new LinearLayout.LayoutParams(-1, -2));
        rendererCard.addView(rendererCopy, new LinearLayout.LayoutParams(0, -2, 1f));

        RendererMode currentRenderer=selectedRenderer();
        rendererSubtitle.setText(currentRenderer.title+" - "+currentRenderer.description);
        TextView rendererArrow=new TextView(this);
        rendererArrow.setText("Select");rendererArrow.setTextColor(blue);rendererArrow.setTextSize(14);
        rendererCard.addView(rendererArrow,new LinearLayout.LayoutParams(-2,-2));
        rendererCard.setContentDescription("Select renderer");
        rendererCard.setOnClickListener(view->{
            RendererMode[] modes=RendererMode.values();
            String[] labels=new String[modes.length];
            for(int i=0;i<modes.length;i++)labels[i]=modes[i].title;
            showLauncherChoices("Renderer", labels, selectedRenderer().ordinal(), which -> {
                        RendererMode chosen=modes[which];
                        launcherSettings.edit().putString(preset.rendererKey+"_mode",chosen.id).apply();
                        rendererSubtitle.setText(chosen.title+" - "+chosen.description);
                    });
        });
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
        launch.setOnClickListener(view -> launchPreset(selectedRenderer()));
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

    private boolean writeRendererConfig(RendererMode renderer) {
        File configDir = presetConfigDir();
        if (!configDir.isDirectory() && !configDir.mkdirs()) return false;
        File configFile = new File(configDir, preset.configName);
        String config = "";
        try (FileInputStream input = new FileInputStream(configFile)) {
            if(configFile.length()>8*1024*1024)return false;
            byte[] data = new byte[(int) configFile.length()];
            new java.io.DataInputStream(input).readFully(data);
            config = new String(data, StandardCharsets.UTF_8);
        } catch (java.io.FileNotFoundException ignored) {
        } catch (Exception error) {
            return false;
        }
        if (preset == GamePreset.BLOODCM) {
            config = setConfigValue(config, "Setup", "ForceSetup", "0");
            if(DirectGameStorage.selected(this,preset)==null)config = setConfigValue(config, "Setup", "NoAutoLoad", "1");
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

        config = setConfigValue(config, "Screen Setup", "Polymer", renderer.polymer ? "1" : "0");
        config = setConfigValue(config, "Screen Setup", "ScreenBPP", Integer.toString(renderer.colorDepth));

        File stagedConfig=new File(configDir,preset.configName+".tmp");
        try (FileOutputStream output = new FileOutputStream(stagedConfig)) {
            output.write(config.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        } catch (Exception error) {
            return false;
        }
        return stagedConfig.renameTo(configFile);
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

    private void showLauncherChoices(String title, String[] choices, int selected,
                                     java.util.function.IntConsumer onSelect) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        float density = getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(18 * density);
        panel.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF121923);
        background.setCornerRadius(20 * density);
        panel.setBackground(background);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(0xFFF0F6FF);
        heading.setTextSize(22);
        panel.addView(heading);
        for (int i = 0; i < choices.length; i++) {
            final int index = i;
            addLauncherAction(panel, i == selected ? "*" : ">", choices[i],
                    i == selected ? "Selected" : "", () -> {
                        dialog.dismiss();
                        onSelect.accept(index);
                    }, 0xFF212B3C, 0xFFF0F6FF, 0xFFB2BDCD, 0xFF74AFFF, density);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - padding * 2,
                    Math.round(520 * density)), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private View addLauncherAction(LinearLayout parent, String icon, String titleText, String subtitleText,
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
        return card;
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

    private void chooseImportedFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, PICK_GAME_FOLDER);
    }

    private void chooseGameFolder() {
        pendingDirectPreset = preset;
        if (DirectGameStorage.hasAccess(this)) chooseDirectFolder();
        else DirectGameStorage.requestAccess(this);
    }
    private void chooseDirectFolder() {
        final GamePreset selected=pendingDirectPreset==null?preset:pendingDirectPreset;
        pendingDirectPreset=null;
        DirectGameStorage.choose(this,selected,root->{
            if(selected==GamePreset.BLOODCM && !isBloodCmFolder(root))
                throw new java.io.IOException("Select the folder containing BloodCM.grp and blcons/bloodcm.con");
            if(selected==GamePreset.DUKE3D) {
                File[] files=root.listFiles();boolean group=false;
                if(files!=null)for(File f:files)if(f.isFile()&&f.getName().toLowerCase(Locale.ROOT).endsWith(".grp"))group=true;
                if(!group)throw new java.io.IOException("Game folder must contain a GRP archive");
            }
            DirectGameStorage.bind(this,selected,root);
            getSharedPreferences("launcher_settings", MODE_PRIVATE).edit()
                    .putString("active_custom_preset", "")
                    .putString(selected.id + "_builtin_folder", root.getCanonicalPath())
                    .putString(selected.id + "_builtin_batch", "")
                    .remove(selected.id + "_batch").apply();
            showLauncher();
        });
    }
    private void importPreviousProgress() {
        if(progressImportRunning)return;
        final GamePreset selected=preset;
        final File target=DirectGameStorage.selected(this,selected);
        if(target==null)return;
        android.app.ActivityManager manager=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes=manager.getRunningAppProcesses();
        if(processes!=null)for(android.app.ActivityManager.RunningAppProcessInfo process:processes)
            if((getPackageName()+":game").equals(process.processName)) {
                Toast.makeText(this,"Exit the game before importing progress",Toast.LENGTH_LONG).show();return;
            }
        try {
            final java.util.List<File> files=SaveFolderSync.previousFiles(this,selected);
            final File source=SaveFolderSync.previousRoot(this,selected);
            if(files.isEmpty()) {Toast.makeText(this,"No previous Android saves or settings found",Toast.LENGTH_LONG).show();return;}
            new AlertDialog.Builder(this).setTitle("Import Android progress?")
                .setMessage("Copy "+files.size()+" files from "+source+" to "+target+". Existing destination files are backed up in android-before-import-… inside the game folder before replacement. Original Android files are retained.")
                .setNegativeButton("Cancel",null).setPositiveButton("Import",(d,w)->{
                    progressImportRunning=true;
                    new Thread(()->{
                        try {
                            String backup=ProgressTransfer.copy(source,target,files);
                            runOnUiThread(()->{progressImportRunning=false;Toast.makeText(this,"Progress imported. Backup: "+backup,Toast.LENGTH_LONG).show();});
                        } catch(Exception error) {runOnUiThread(()->{progressImportRunning=false;Toast.makeText(this,"Import failed: "+error.getMessage(),Toast.LENGTH_LONG).show();});}
                    },"progress-import").start();
                }).show();
        } catch(Exception error) {Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==DirectGameStorage.ACCESS) {
            if(DirectGameStorage.hasAccess(this))chooseDirectFolder();
            else Toast.makeText(this,"Folder access denied; game files were not changed",Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==DirectGameStorage.ACCESS) {
            if(DirectGameStorage.hasAccess(this))chooseDirectFolder();
            else Toast.makeText(this,"Folder access denied; game files were not changed",Toast.LENGTH_LONG).show();
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_GAME_FOLDER || requestCode == PICK_SAVE_FOLDER) {
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
            DirectGameStorage.marker(this,preset).delete();
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
        File direct=DirectGameStorage.selected(this,preset);
        if(direct!=null)return direct.getAbsolutePath();
        if ("".equals(getSharedPreferences("launcher_settings", MODE_PRIVATE)
                .getString(preset.id + "_builtin_folder", null))) return null;
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
