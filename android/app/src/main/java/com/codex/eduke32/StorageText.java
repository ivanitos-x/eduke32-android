package com.codex.eduke32;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Small, bounded storage metadata; game resources use native file I/O. */
final class StorageText {
    static String read(File file) throws IOException {
        if(file.length()>8*1024*1024)throw new IOException("Storage metadata is too large");
        byte[] data=new byte[(int)file.length()];
        try(DataInputStream in=new DataInputStream(new FileInputStream(file))){in.readFully(data);}
        return new String(data,StandardCharsets.UTF_8);
    }
    static void write(File file,String text) throws IOException {
        try(FileOutputStream out=new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();
        }
    }
}
