package com.codex.eduke32;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** A real working directory: native VFS owns all game reads and writes. */
final class DirectGameStorage {
    static final int ACCESS = 1010;
    static File marker(Context c, GamePreset p) {
        return new File(c.getFilesDir(), ".config/eduke32/" + p.directoryMarker + ".direct");
    }
    static File selected(Context c, GamePreset p) {
        File m = marker(c,p);
        if (!m.isFile()) return null;
        try { return new File(StorageText.read(m).trim()); }
        catch (IOException e) { throw new IllegalStateException("Cannot read game folder setting",e); }
    }
    static boolean hasAccess(Activity a) {
        return Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager()
                : a.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
    static void requestAccess(Activity a) {
        if (Build.VERSION.SDK_INT >= 30) {
            try { a.startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:"+a.getPackageName())), ACCESS); }
            catch (android.content.ActivityNotFoundException e) {
                a.startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),ACCESS);
            }
        } else a.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},ACCESS);
    }
    interface Selection { void accept(File root) throws Exception; }
    static void choose(Activity activity, GamePreset preset, Selection selection) {
        File directory = selected(activity, preset);
        if (directory == null || !directory.isDirectory())
            directory = Environment.getExternalStorageDirectory();
        browse(activity, directory, selection);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static TextView folderRow(Activity activity, String label, Runnable action) {
        TextView row = new TextView(activity);
        row.setText(label);
        row.setTextColor(0xffeff4fa);
        row.setTextSize(16);
        row.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff253142);
        background.setCornerRadius(dp(activity, 10));
        row.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(activity, 6);
        row.setLayoutParams(params);
        row.setOnClickListener(view -> action.run());
        return row;
    }

    private static void browse(Activity activity, File directory, Selection selection) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xff141b25);
        int padding = dp(activity, 16);
        panel.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(activity);
        title.setText("Game folder");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        panel.addView(title);

        TextView path = new TextView(activity);
        path.setText(directory.getAbsolutePath());
        path.setTextColor(0xffb7c4d3);
        path.setPadding(0, dp(activity, 6), 0, dp(activity, 12));
        panel.addView(path);

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(panel).create();
        File parent = directory.getParentFile();
        if (parent != null) {
            panel.addView(folderRow(activity, "↑  Parent folder", () -> {
                dialog.dismiss();
                browse(activity, parent, selection);
            }));
        }
        ScrollView scroll = new ScrollView(activity);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1,
                Math.min(dp(activity, 280), activity.getResources().getDisplayMetrics().heightPixels / 3)));
        File[] entries = directory.listFiles(File::isDirectory);
        if (entries != null && entries.length > 0) {
            java.util.Arrays.sort(entries, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File entry : entries)
                rows.addView(folderRow(activity, entry.getName() + "  ›", () -> {
                    dialog.dismiss();
                    browse(activity, entry, selection);
                }));
        } else {
            TextView empty = new TextView(activity);
            empty.setText(entries == null ? "Cannot read this folder." : "No subfolders.");
            empty.setTextColor(0xffb7c4d3);
            rows.addView(empty);
        }

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(android.view.Gravity.END);
        Button cancel = new Button(activity);
        cancel.setText("Cancel");
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel);
        Button use = new Button(activity);
        use.setText("Use this folder");
        use.setEnabled(entries != null);
        use.setOnClickListener(view -> {
            try {
                File root = directory.getCanonicalFile();
                checkWritable(root);
                selection.accept(root);
                dialog.dismiss();
            } catch (Exception error) {
                Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        actions.addView(use);
        panel.addView(actions);
        dialog.show();
    }

    static void checkWritable(File root) throws IOException {
        if (!root.isDirectory() || !root.canRead() || root.list() == null)
            throw new IOException("Cannot read game folder: "+root);
        File probe = File.createTempFile(".eduke-access-", ".tmp",root);
        File renamed = new File(probe.getPath()+".rename");
        try {
            try (FileOutputStream out = new FileOutputStream(probe)) { out.write(1); out.getFD().sync(); }
            if (!probe.renameTo(renamed)) throw new IOException("Cannot rename files in game folder");
        } finally {
            if (probe.exists() && !probe.delete()) throw new IOException("Cannot remove access probe");
            if (renamed.exists() && !renamed.delete()) throw new IOException("Cannot remove access probe");
        }
    }
    static void bind(Context c,GamePreset p,File root) throws IOException {
        File m=marker(c,p), parent=m.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot store game folder setting");
        File staged=File.createTempFile("direct-", ".tmp",parent);
        try {
            StorageText.write(staged,root.getCanonicalPath()+"\n");
            if (!staged.renameTo(m)) throw new IOException("Cannot commit game folder setting");
        } finally { staged.delete(); }
    }
}
