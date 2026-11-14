package com.codex.eduke32;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.EditText;
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
    static void choose(Activity a, GamePreset p, Selection selection) {
        EditText path = new EditText(a);
        path.setSingleLine(true);
        File old = selected(a,p);
        path.setText(old == null ? new File(Environment.getExternalStorageDirectory(),"Games").getAbsolutePath() : old.getPath());
        AlertDialog dialog = new AlertDialog.Builder(a).setTitle("Game working folder")
                .setMessage("Enter the full folder path, for example /storage/emulated/0/Games/Duke3D. The game reads and writes here: saves, settings, logs, cache and campaign data. Files are not imported. Existing Android progress remains in its previous folder.")
                .setView(path).setNegativeButton("Cancel",null)
                .setNeutralButton("Browse",(d,w)->{
                    File start=new File(path.getText().toString().trim());
                    while(!start.isDirectory() && start.getParentFile()!=null)start=start.getParentFile();
                    browse(a,start,selection);
                }).setPositiveButton("Use folder",null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
            try {
                File root = new File(path.getText().toString().trim()).getCanonicalFile();
                checkWritable(root);
                selection.accept(root);
                dialog.dismiss();
            } catch (Exception e) { Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show(); }
        }));
        dialog.show();
    }
    private static void browse(Activity a,File directory,Selection selection) {
        File[] entries=directory.listFiles(File::isDirectory);
        if(entries==null) {Toast.makeText(a,"Cannot list "+directory,Toast.LENGTH_LONG).show();return;}
        java.util.Arrays.sort(entries,(x,y)->x.getName().compareToIgnoreCase(y.getName()));
        boolean parent=directory.getParentFile()!=null;
        String[] names=new String[entries.length+(parent?1:0)];
        if(parent)names[0]=".. (parent folder)";
        for(int i=0;i<entries.length;i++)names[i+(parent?1:0)]=entries[i].getName()+"/";
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(directory.getAbsolutePath())
                .setItems(names,(d,w)->browse(a,parent&&w==0?directory.getParentFile():entries[w-(parent?1:0)],selection))
                .setNegativeButton("Cancel",null).setPositiveButton("Use this folder",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            try {File root=directory.getCanonicalFile();checkWritable(root);selection.accept(root);dialog.dismiss();}
            catch(Exception e) {Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();}
        }));
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
