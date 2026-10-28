package com.codex.eduke32;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Decode the original menu tile from the user's GRP; no game art is bundled. */
final class BloodCmArtwork {
    private static final int MENU_TILE = 7166;
    private static final class Entry {
        String name; long offset, size;
        Entry(String name, long offset, long size) { this.name=name; this.offset=offset; this.size=size; }
    }
    private static int u16(RandomAccessFile input) throws Exception {
        return input.readUnsignedByte() | input.readUnsignedByte() << 8;
    }
    private static long u32(RandomAccessFile input) throws Exception {
        return (long)u16(input) | (long)u16(input) << 16;
    }
    private static String readMarker(File marker) throws Exception {
        if (marker.length()>16384) return "";
        byte[] bytes=new byte[(int)marker.length()];
        try (DataInputStream input=new DataInputStream(new FileInputStream(marker))) { input.readFully(bytes); }
        return new String(bytes,StandardCharsets.UTF_8);
    }
    static Bitmap load(File group, File cacheDir) {
        if (group == null || !group.isFile()) return null;
        try {
            cacheDir.mkdirs();
            File image = new File(cacheDir, "header.png");
            File marker = new File(cacheDir, "source.txt");
            String stamp = group.getCanonicalPath()+":"+group.length()+":"+group.lastModified();
            if (image.isFile() && marker.isFile()
                    && stamp.equals(readMarker(marker)))
                return BitmapFactory.decodeFile(image.getAbsolutePath());
            try (RandomAccessFile input = new RandomAccessFile(group, "r")) {
                byte[] signature = new byte[12]; input.readFully(signature);
                if (!"KenSilverman".equals(new String(signature, StandardCharsets.US_ASCII))) return null;
                long count = u32(input);
                if (count < 1 || count > 65536 || 16+16*count > input.length()) return null;
                List<Entry> entries = new ArrayList<>();
                long offset = 16+16*count;
                for (int i=0; i<count; ++i) {
                    byte[] nameBytes = new byte[12]; input.readFully(nameBytes);
                    int length=0; while (length<12 && nameBytes[length]!=0) ++length;
                    String name = new String(nameBytes,0,length,StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT);
                    long size = u32(input);
                    if (size > input.length()-offset) return null;
                    entries.add(new Entry(name,offset,size)); offset+=size;
                }
                int[] palette = null;
                for (Entry entry: entries) if (entry.name.equals("PALETTE.DAT") && entry.size>=768) {
                    input.seek(entry.offset); palette = new int[256];
                    for (int i=0;i<256;++i) {
                        int r=Math.min(255,input.readUnsignedByte()*4);
                        int g=Math.min(255,input.readUnsignedByte()*4);
                        int b=Math.min(255,input.readUnsignedByte()*4);
                        palette[i]=0xff000000 | r<<16 | g<<8 | b;
                    }
                    break;
                }
                if (palette==null) return null;
                for (Entry entry: entries) {
                    if (!entry.name.endsWith(".ART") || entry.size<16) continue;
                    input.seek(entry.offset);
                    if (u32(input)!=1) continue;
                    u32(input); long start=u32(input), end=u32(input);
                    if (start>MENU_TILE || end<MENU_TILE || end-start>65535) continue;
                    int n=(int)(end-start+1);
                    if (16+8L*n>entry.size) continue;
                    int[] widths=new int[n],heights=new int[n];
                    for (int i=0;i<n;++i) widths[i]=u16(input);
                    for (int i=0;i<n;++i) heights[i]=u16(input);
                    long pixelOffset=entry.offset+16+8L*n;
                    int index=MENU_TILE-(int)start;
                    for (int i=0;i<index;++i) pixelOffset+=(long)widths[i]*heights[i];
                    int w=widths[index],h=heights[index];
                    if (w<1 || h<1 || w>2048 || h>2048 || pixelOffset+(long)w*h>entry.offset+entry.size) return null;
                    input.seek(pixelOffset);
                    byte[] pixels=new byte[w*h]; input.readFully(pixels);
                    int[] colors=new int[pixels.length];
                    for (int x=0;x<w;++x) for (int y=0;y<h;++y) colors[y*w+x]=palette[pixels[x*h+y]&255];
                    Bitmap bitmap=Bitmap.createBitmap(colors,w,h,Bitmap.Config.ARGB_8888);
                    try (FileOutputStream output=new FileOutputStream(image)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,output); }
                    try (FileOutputStream output=new FileOutputStream(marker)) { output.write(stamp.getBytes(StandardCharsets.UTF_8)); }
                    return bitmap;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }
}
