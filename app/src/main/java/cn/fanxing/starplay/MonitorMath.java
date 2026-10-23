package cn.fanxing.starplay;

import java.util.*;
import java.util.regex.*;

public final class MonitorMath {
    private MonitorMath() {}
    public static double cpuTotal(String dump) {
        Matcher m=Pattern.compile("(?m)^\\s*([0-9]+(?:\\.[0-9]+)?)% TOTAL:").matcher(dump);
        if(!m.find())return Double.NaN;
        double n=Double.parseDouble(m.group(1));return n>=0&&n<=100?n:Double.NaN;
    }
    /** Only actual presentation timestamps (column 2), never the first-line display period. */
    public static double fps(String dump,long now) {
        TreeSet<Long> stamps=new TreeSet<>();
        for(String line:dump.split("\\n")){
            String[] a=line.trim().split("\\s+");if(a.length!=3)continue;
            try{long n=Long.parseLong(a[1]);if(n>0&&n<Long.MAX_VALUE&&n<=now&&now-n<=2_000_000_000L)stamps.add(n);}catch(NumberFormatException ignored){}
        }
        if(stamps.size()<2||now-stamps.last()>1_500_000_000L)return Double.NaN;
        long span=stamps.last()-stamps.first();if(span<=0)return Double.NaN;
        double result=(stamps.size()-1)*1_000_000_000.0/span;
        return result>0&&result<=1000?result:Double.NaN;
    }
    public static double bytesPerSecond(long old,long current,long elapsedMs){
        return old<0||current<old||elapsedMs<=0?Double.NaN:(current-old)*1000.0/elapsedMs;
    }
    public static String rate(double n){
        if(Double.isNaN(n))return "—";
        if(n>=1024*1024)return String.format(Locale.ROOT,"%.1f MB/s",n/(1024*1024));
        return String.format(Locale.ROOT,"%.0f KB/s",n/1024);
    }
    public static List<String> gameLayers(String list,String pkg){
        List<String> layers=new ArrayList<>();if(pkg==null||pkg.isEmpty())return layers;
        for(String raw:list.split("\\n")){
            String s=raw.trim();
            if(s.contains(pkg)&&!s.contains("ActivityRecord{")&&!s.contains("Task="))layers.add(s);
        }
        layers.sort((a,b)->Integer.compare(score(b),score(a)));
        return layers.subList(0,Math.min(3,layers.size()));
    }
    private static int score(String s){return (s.contains("SurfaceView")?8:0)+(s.contains("BLAST")?4:0)+(s.contains("GameActivity")?2:0);}
}
