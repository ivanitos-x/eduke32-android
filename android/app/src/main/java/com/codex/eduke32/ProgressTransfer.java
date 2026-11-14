package com.codex.eduke32;

import java.io.*;
import java.util.*;

/** Bounded streaming transfer; originals and conflicting destination data survive. */
final class ProgressTransfer {
    static String copy(File source,File destination,List<File> files) throws IOException {
        File from=source.getCanonicalFile(), to=destination.getCanonicalFile();
        if(from.equals(to))throw new IOException("Source and destination are the same folder");
        File work=new File(to,"android-before-import-"+UUID.randomUUID());
        File staged=new File(work,"incoming"), backup=new File(work,"previous");
        if(!staged.mkdirs() || !backup.mkdir())throw new IOException("Cannot create import staging folder");
        List<String> paths=new ArrayList<>(), replaced=new ArrayList<>(), installed=new ArrayList<>();
        try {
            for(File f:files) {
                String prefix=from.getPath()+File.separator;
                String sourcePath=f.getAbsolutePath();
                if(!sourcePath.startsWith(prefix))throw new IOException("Source outside progress folder");
                String relative=sourcePath.substring(prefix.length());
                if(paths.contains(relative))throw new IOException("Duplicate progress path: "+relative);
                File src=inside(from,relative), dst=inside(to,relative);
                if(!src.isFile())throw new IOException("Missing source: "+relative);
                if(dst.exists()&&!dst.isFile())throw new IOException("Destination is not a file: "+relative);
                stream(src,inside(staged,relative));
                paths.add(relative);
            }
            for(String relative:paths) {
                File dst=inside(to,relative), old=inside(backup,relative), next=inside(staged,relative);
                makeParent(dst);
                if(dst.exists()) {
                    makeParent(old);
                    if(!dst.renameTo(old))throw new IOException("Cannot back up "+relative);
                    replaced.add(relative);
                }
                if(!next.renameTo(dst))throw new IOException("Cannot install "+relative);
                installed.add(relative);
            }
            return work.getAbsolutePath();
        } catch(IOException error) {
            // Restore the previous destination if a commit step failed. Never delete backups.
            Collections.reverse(installed);
            for(String relative:installed) {
                File dst=inside(to,relative);
                if(dst.exists()&&!dst.delete())error.addSuppressed(new IOException("Cannot remove partial import: "+dst));
            }
            Collections.reverse(replaced);
            for(String relative:replaced) {
                File old=inside(backup,relative), dst=inside(to,relative);
                if(dst.exists() || !old.renameTo(dst))error.addSuppressed(new IOException("Restore manually from "+old));
            }
            throw new IOException(error.getMessage()+"; retained import folder: "+work,error);
        }
    }
    private static File inside(File root,String relative) throws IOException {
        File file=new File(root,relative).getAbsoluteFile();
        if(relative.isEmpty() || new File(relative).isAbsolute() || !file.getCanonicalFile().equals(file)
                || !file.getPath().startsWith(root.getPath()+File.separator) || file.equals(root))
            throw new IOException("Unsafe progress path: "+relative);
        return file;
    }
    private static void makeParent(File file) throws IOException {
        File parent=file.getParentFile();
        if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create "+parent);
    }
    private static void stream(File source,File target) throws IOException {
        long size=source.length(), modified=source.lastModified();
        makeParent(target);
        try(InputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(target)) {
            byte[] buffer=new byte[65536];int count;
            while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
            out.getFD().sync();
        }
        if(source.length()!=size || source.lastModified()!=modified || target.length()!=size)
            throw new IOException("Source changed during import: "+source);
    }
}
