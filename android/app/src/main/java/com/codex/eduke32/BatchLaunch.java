package com.codex.eduke32;

import java.io.IOException;
import java.util.*;

/** Imports one EDuke32 invocation. Does not execute a Windows command shell. */
final class BatchLaunch {
    static String[] parse(String source) throws IOException {
        List<String> launch=null;
        int number=0;
        for(String raw:source.replace("\uFEFF", "").split("\\r?\\n")) {
            number++;
            String line=raw.trim();
            if(line.startsWith("@"))line=line.substring(1).trim();
            String lower=line.toLowerCase(Locale.ROOT);
            if(line.isEmpty() || lower.equals("cls") || lower.equals("pause")
                    || lower.equals("echo") || lower.startsWith("echo ") || lower.startsWith("echo.")
                    || lower.equals("rem") || lower.startsWith("rem ") || lower.startsWith("::"))continue;
            List<String> tokens=tokenize(line);
            String exe=tokens.get(0).replace('\\','/');
            exe=exe.substring(exe.lastIndexOf('/')+1);
            if(!exe.equalsIgnoreCase("eduke32.exe")&&!exe.equalsIgnoreCase("eduke32"))
                throw new IOException("Unsupported batch command on line "+number+": "+tokens.get(0));
            if(launch!=null)throw new IOException("Batch contains multiple engine launches; select a simpler script");
            launch=new ArrayList<>(tokens.subList(1,tokens.size()));
            for(int i=0;i<launch.size();i++)launch.set(i,launch.get(i).replace('\\','/'));
        }
        if(launch==null)throw new IOException("No EDuke32 launch command found in batch");
        return launch.toArray(new String[0]);
    }
    private static List<String> tokenize(String line) throws IOException {
        List<String> tokens=new ArrayList<>();StringBuilder token=new StringBuilder();
        boolean quoted=false,started=false;
        for(int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if(c=='"') {quoted=!quoted;started=true;}
            else if(c=='%' || c=='!' || (!quoted && "&|<>^".indexOf(c)>=0))
                throw new IOException("Batch variables and shell operators are not supported");
            else if(Character.isWhitespace(c)&&!quoted) {
                if(started) {tokens.add(token.toString());token.setLength(0);started=false;}
            } else {token.append(c);started=true;}
        }
        if(quoted)throw new IOException("Unclosed quote in batch launch command");
        if(started)tokens.add(token.toString());
        return tokens;
    }
}
