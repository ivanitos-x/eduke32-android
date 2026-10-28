package com.codex.eduke32;

import android.app.Application;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** App-owned diagnostics, available even when SDL/native initialization never starts. */
public final class DiagnosticApp extends Application {
    private static File directory;
    private static String process = "unknown";
    private static java.lang.Process collector;

    @Override public void onCreate() {
        super.onCreate();
        directory = new File(getFilesDir(), "diagnostics");
        directory.mkdirs();
        process = getPackageName();
        try {
            if (Build.VERSION.SDK_INT >= 28) process = Application.getProcessName();
            else {
                ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> list=am.getRunningAppProcesses();
                if(list!=null) for(ActivityManager.RunningAppProcessInfo p:list)
                    if(p.pid==Process.myPid()) process=p.processName;
            }
        } catch (Exception ignored) { }
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            stage("UNCAUGHT JAVA " + thread.getName() + "\n" + Log.getStackTraceString(error));
            if(previous!=null) previous.uncaughtException(thread,error);
            else { Process.killProcess(Process.myPid()); System.exit(10); }
        });
        stage("Application start; process="+process+" pid="+Process.myPid());
        if(!process.endsWith(":game")) {
            try {
                // Android exposes only accessible UID logs; no READ_LOGS/root permission.
                collector=new ProcessBuilder("logcat","-b","main","-b","system","-b","crash",
                        "--uid="+Process.myUid(),"-v","threadtime","-f",
                        new File(directory,"android-logcat.txt").getAbsolutePath(),"-r","512","-n","2")
                        .redirectErrorStream(true).start();
                final java.lang.Process child=collector;
                new Thread(()->{
                    try(BufferedReader reader=new BufferedReader(new InputStreamReader(child.getInputStream()))) {
                        String line;int lines=0;
                        while((line=reader.readLine())!=null && lines++<20) stage("logcat: "+line);
                        int exit=child.waitFor();stage("Logcat collector exited: "+exit);
                    } catch(Exception e) { stage("Logcat collector unavailable: "+e); }
                },"diagnostic-logcat").start();
            } catch(Exception e) { stage("Cannot start logcat: "+e); }
        }
    }

    static synchronized void stage(String text) {
        Log.i("EDuke32Diag",text);
        if(directory==null)return;
        File file=new File(directory,process.endsWith(":game")?"game-startup.txt":"launcher-startup.txt");
        try {
            if(file.length()>512*1024) {
                File old=new File(file.getPath()+".previous");
                if(old.exists())old.delete();file.renameTo(old);
            }
            try(FileOutputStream out=new FileOutputStream(file,true)) {
                out.write((System.currentTimeMillis()+" "+text+"\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch(Exception ignored) { }
    }

    static File export(Context context,File... runtimeLogs) throws IOException {
        File folder=new File(context.getFilesDir(),"shared-logs");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot create export folder");
        File result=new File(folder,"eduke32-runtime.txt");
        try(PrintWriter out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(result),StandardCharsets.UTF_8))) {
            out.println("EDuke32 extended diagnostics; local report, no automatic upload");
            try { out.println("App: "+context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName); }
            catch(Exception ignored) { }
            out.println("Device: "+Build.MANUFACTURER+" "+Build.MODEL+" / "+Build.DEVICE);
            out.println("Android: "+Build.VERSION.RELEASE+" SDK "+Build.VERSION.SDK_INT);
            out.println("ABIs: "+java.util.Arrays.toString(Build.SUPPORTED_ABIS));
            out.println("Fingerprint: "+Build.FINGERPRINT);
            try { out.println("Page size: "+android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)); }
            catch(Exception e) { out.println("Page size unavailable: "+e); }
            out.println("Native library dir: "+context.getApplicationInfo().nativeLibraryDir);
            for(File file:runtimeLogs) if(file!=null) append(out,file,512*1024);
            File[] diagnostics=new File(context.getFilesDir(),"diagnostics").listFiles();
            if(diagnostics!=null) {
                java.util.Arrays.sort(diagnostics,java.util.Comparator.comparing(File::getName));
                for(File file:diagnostics)if(file.isFile())append(out,file,512*1024);
            }
            out.println("\n=== Historical process exits ===");
            if(Build.VERSION.SDK_INT>=30) appendExitHistory(context,out);
            else out.println("Android < 11: ApplicationExitInfo unavailable; inspect Java/logcat sections.");
            if(out.checkError())throw new IOException("Report write failed");
        }
        return result;
    }

    private static void append(PrintWriter out,File file,int limit) {
        out.println("\n=== "+file.getPath()+" ===");
        if(!file.isFile()) { out.println("Not created (process may have failed before engine logging).");return; }
        try(RandomAccessFile input=new RandomAccessFile(file,"r")) {
            long offset=Math.max(0,input.length()-limit);input.seek(offset);
            byte[] data=new byte[(int)Math.min(limit,input.length()-offset)];input.readFully(data);
            if(offset>0)out.println("[tail truncated]");
            out.println(new String(data,StandardCharsets.UTF_8));
        } catch(Exception e) { out.println("Cannot read: "+e); }
    }

    @android.annotation.TargetApi(30)
    private static void appendExitHistory(Context context,PrintWriter out) {
        try {
            ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            for(ApplicationExitInfo info:am.getHistoricalProcessExitReasons(context.getPackageName(),0,6)) {
                out.println("time="+info.getTimestamp()+" process="+info.getProcessName()+" pid="+info.getPid()
                        +" reason="+info.getReason()+" status="+info.getStatus()+" description="+info.getDescription()
                        +" PSS="+info.getPss()+" RSS="+info.getRss());
                // Android 12+ can return a native tombstone protobuf, not UTF-8 text.
                // Preserve those bytes as Base64 in the TXT for later decoding/symbolization.
                try(InputStream trace=info.getTraceInputStream()) {
                    if(trace==null) {out.println("No trace supplied by Android.");continue;}
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                    while(bytes.size()<256*1024 && (n=trace.read(buffer,0,Math.min(buffer.length,256*1024-bytes.size())))>0)bytes.write(buffer,0,n);
                    out.println("TRACE_BASE64_BEGIN (up to 256 KiB; may be protobuf)");
                    out.println(android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));
                    out.println("TRACE_BASE64_END");
                } catch(Exception e) {out.println("Trace unavailable: "+e);}
            }
        } catch(Exception e) {out.println("Exit history unavailable: "+e);}
    }
}
