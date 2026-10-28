package com.codex.eduke32;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.FileObserver;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Versioned, app-owned snapshots in the user-selected game folder (SAF). */
final class SaveFolderSync {
    private static final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private static FileObserver observer;
    private static ScheduledFuture<?> pending;
    private static final String PREFIX="eduke32-state-";
    private static final long LIMIT=256L*1024*1024;

    private static File profile(Context c,String preset) {
        return new File(c.getFilesDir(),".config/eduke32"+("bloodcm".equals(preset)?"/presets/bloodcm":""));
    }
    private static boolean allowed(String n) {
        return n.matches("save[0-9A-Za-z_-]*\\.(esv|sav)") || n.equals("settings.cfg")
                || n.equals("eduke32.cfg") || n.equals("bloodcm-android.cfg")
                || n.equals("bloodcm-android_settings.cfg") || n.equals("eduke32_settings.cfg");
    }
    private static File location(Context c,String preset) {
        return new File(c.getFilesDir(),"save-folder-"+preset+".properties");
    }
    private static Properties readLocation(Context c,String preset) {
        Properties values=new Properties();File f=location(c,preset);
        if(f.isFile())try(InputStream in=new FileInputStream(f)) {values.load(in);}
        catch(Exception e) {DiagnosticApp.stage("Cannot read save folder mapping: "+e);}
        return values;
    }
    static boolean configured(Context c,String preset) {return readLocation(c,preset).getProperty("tree")!=null;}
    private static Uri tree(Context c,String preset) {
        String value=readLocation(c,preset).getProperty("tree");
        return value==null?null:Uri.parse(value);
    }
    private static List<Uri> children(Context c,Uri tree,String parent) throws IOException {
        List<Uri> list=new ArrayList<>();
        try(Cursor cursor=c.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent),
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},null,null,null)) {
            if(cursor==null)throw new IOException("Folder provider unavailable");
            while(cursor.moveToNext())list.add(DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0)));
        }
        return list;
    }
    private static String name(Context c,Uri doc) throws IOException {
        try(Cursor cursor=c.getContentResolver().query(doc,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)) {
            if(cursor!=null && cursor.moveToFirst())return cursor.getString(0);
        }
        throw new IOException("Cannot read document name");
    }
    private static Uri find(Context c,Uri tree,String parent,String target) throws IOException {
        for(Uri child:children(c,tree,parent))if(target.equals(name(c,child)))return child;
        return null;
    }
    static void bind(Context c,String preset,Uri selected,String relative) throws Exception {
        String id=DocumentsContract.getTreeDocumentId(selected);
        for(String part:relative.replace('\\','/').split("/"))if(!part.isEmpty()) {
            Uri child=find(c,selected,id,part);
            if(child==null)throw new IOException("Cannot locate game subfolder");
            id=DocumentsContract.getDocumentId(child);
        }
        Properties values=new Properties();values.setProperty("tree",selected.toString());values.setProperty("root",id);
        File f=location(c,preset),tmp=new File(f.getPath()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)) {values.store(out,"Selected game/save folder");out.getFD().sync();}
        if(!tmp.renameTo(f))throw new IOException("Cannot retain save folder mapping");
    }
    private static Uri storage(Context c,String preset,Uri tree,boolean create) throws Exception {
        String root=readLocation(c,preset).getProperty("root",DocumentsContract.getTreeDocumentId(tree));
        String folder="eduke32-android-"+preset;
        Uri dir=find(c,tree,root,folder);
        if(dir==null && create)dir=DocumentsContract.createDocument(c.getContentResolver(),
                DocumentsContract.buildDocumentUriUsingTree(tree,root),DocumentsContract.Document.MIME_TYPE_DIR,folder);
        if(dir==null && create)throw new IOException("Game folder is not writable");
        return dir;
    }
    static synchronized void start(Context c,String preset) {
        if(observer!=null)observer.stopWatching();
        if(!configured(c,preset))return;
        Context app=c.getApplicationContext();File dir=profile(app,preset);dir.mkdirs();
        observer=new FileObserver(dir.getAbsolutePath(),FileObserver.CLOSE_WRITE|FileObserver.MOVED_TO) {
            @Override public void onEvent(int event,String path) {
                if(path!=null && allowed(path))schedule(app,preset);
            }
        };
        observer.startWatching();
    }
    static synchronized void schedule(Context c,String preset) {
        if(!configured(c,preset))return;
        if(pending!=null)pending.cancel(false);
        Context app=c.getApplicationContext();
        pending=worker.schedule(()->{
            try { snapshot(app,preset); }
            catch(Exception e) {
                DiagnosticApp.stage("Save folder sync failed: "+e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(()->android.widget.Toast.makeText(app,
                        "Save backup failed. Re-select a writable game folder; details in Send log.",android.widget.Toast.LENGTH_LONG).show());
            }
        },800,TimeUnit.MILLISECONDS);
    }
    private static void snapshot(Context c,String preset) throws Exception {
        Uri tree=tree(c,preset);if(tree==null)return;
        File[] files=profile(c,preset).listFiles((dir,n)->allowed(n));
        if(files==null || files.length==0)return;
        File zip=File.createTempFile("save-snapshot-",".zip",c.getCacheDir());
        try {
            long total=0;
            try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))) {
                byte[] buffer=new byte[65536];
                for(File file:files)if(file.isFile()) {
                    long size=file.length(),modified=file.lastModified();total+=size;
                    if(total>LIMIT)throw new IOException("Save snapshot exceeds 256 MiB");
                    out.putNextEntry(new ZipEntry(file.getName()));
                    try(InputStream in=new FileInputStream(file)) { int n;while((n=in.read(buffer))>0)out.write(buffer,0,n); }
                    out.closeEntry();
                    if(file.length()!=size || file.lastModified()!=modified)throw new IOException("File changed during snapshot; retry on next save");
                }
            }
            Uri folder=storage(c,preset,tree,true);
            String fileName=PREFIX+System.currentTimeMillis()+"-"+UUID.randomUUID()+".zip";
            Uri target=DocumentsContract.createDocument(c.getContentResolver(),folder,"application/zip",fileName);
            if(target==null)throw new IOException("Cannot create save archive");
            try(InputStream in=new FileInputStream(zip);OutputStream out=c.getContentResolver().openOutputStream(target,"wt")) {
                if(out==null)throw new IOException("Cannot open save archive");
                byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))>0)out.write(buffer,0,n);
            }
            // A separate completion marker prevents partial archives being restored after a crash.
            Uri complete=DocumentsContract.createDocument(c.getContentResolver(),folder,"text/plain",fileName+".complete");
            if(complete==null)throw new IOException("Cannot commit save archive");
            DiagnosticApp.stage("Saves/settings synchronized to selected game folder: "+fileName);
            // Keep three completed generations; operate only on our uniquely named archives.
            List<Uri> all=children(c,tree,DocumentsContract.getDocumentId(folder));
            List<String> good=new ArrayList<>();
            for(Uri item:all) { String n=name(c,item);if(n.startsWith(PREFIX)&&n.endsWith(".zip.complete"))good.add(n.substring(0,n.length()-9)); }
            Collections.sort(good,Collections.reverseOrder());
            for(int i=3;i<good.size();i++)for(Uri item:all) {
                String n=name(c,item);if(n.equals(good.get(i))||n.equals(good.get(i)+".complete"))DocumentsContract.deleteDocument(c.getContentResolver(),item);
            }
        } finally {zip.delete();}
    }
    static void restore(Context c,String preset) throws Exception {
        Uri tree=tree(c,preset);if(tree==null)return;
        Uri folder=storage(c,preset,tree,false);if(folder==null)return;
        List<Uri> all=children(c,tree,DocumentsContract.getDocumentId(folder));
        Map<String,Uri> byName=new TreeMap<>(Collections.reverseOrder());
        for(Uri item:all)byName.put(name(c,item),item);
        Uri latest=null;
        for(String n:byName.keySet())if(n.startsWith(PREFIX)&&n.endsWith(".zip")&&byName.containsKey(n+".complete")) {latest=byName.get(n);break;}
        if(latest==null)return;
        File staging=new File(c.getCacheDir(),"save-restore-"+UUID.randomUUID());
        if(!staging.mkdirs())throw new IOException("Cannot stage save restore");
        List<File> staged=new ArrayList<>();
        try {
            long total=0;int entries=0;
            try(InputStream raw=c.getContentResolver().openInputStream(latest);ZipInputStream in=new ZipInputStream(raw)) {
                ZipEntry entry;byte[] buffer=new byte[65536];
                while((entry=in.getNextEntry())!=null) {
                    if(++entries>128 || !allowed(entry.getName()))throw new IOException("Invalid save archive entry");
                    File file=new File(staging,entry.getName());
                    try(OutputStream out=new FileOutputStream(file)) {int n;while((n=in.read(buffer))>0) {total+=n;if(total>LIMIT)throw new IOException("Save archive too large");out.write(buffer,0,n);}}
                    staged.add(file);in.closeEntry();
                }
            }
            File dest=profile(c,preset);dest.mkdirs();
            for(File file:staged)try(InputStream in=new FileInputStream(file);OutputStream out=new FileOutputStream(new File(dest,file.getName()))) {
                byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))>0)out.write(buffer,0,n);
            }
            DiagnosticApp.stage("Restored saves/settings from selected game folder");
        } finally {for(File f:staged)f.delete();File[] remaining=staging.listFiles();if(remaining!=null)for(File f:remaining)f.delete();staging.delete();}
    }
}
