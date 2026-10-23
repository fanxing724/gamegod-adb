package cn.fanxing.starplay;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.os.*;
import android.view.WindowManager;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Passive metrics. All shell diagnostics run as this app's UID, never via adb/su. */
public final class DeviceMonitor {
    public static final String DUMP="android.permission.DUMP";
    public static final String GRANT="adb shell pm grant cn.fanxing.starplay android.permission.DUMP\nadb shell pm grant cn.fanxing.starplay android.permission.PACKAGE_USAGE_STATS\nadb shell appops set cn.fanxing.starplay GET_USAGE_STATS allow";
    public static final class Snapshot {
        public String memory="—",cpu="—",fps="—",fpsDetail="等待采样",network="—",traffic="—",battery="—",frequency="—",cpuDetail="系统历史采样";
        public double cpuValue=Double.NaN,fpsValue=Double.NaN;
    }
    private final Context context;
    private long previousAt,rx=-1,tx=-1,lastCpu,lastLayerSearch;
    private String cpu="—",cpuDetail="系统历史采样",layer="",lastPackage="";
    private double cpuValue=Double.NaN;
    public DeviceMonitor(Context c){context=c.getApplicationContext();}
    public boolean dumpGranted(){return context.checkSelfPermission(DUMP)==PackageManager.PERMISSION_GRANTED;}
    public boolean usageGranted(){return context.getSystemService(AppOpsManager.class).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),context.getPackageName())==AppOpsManager.MODE_ALLOWED;}
    public Snapshot sample(String pkg){
        Snapshot s=new Snapshot();long now=SystemClock.elapsedRealtime();
        ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();context.getSystemService(ActivityManager.class).getMemoryInfo(mem);
        s.memory=String.format(Locale.ROOT,"可用 %.2f / %.2f GB",mem.availMem/1073741824.0,mem.totalMem/1073741824.0);
        try{
            ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);Network n=cm.getActiveNetwork();NetworkCapabilities c=cm.getNetworkCapabilities(n);
            if(c==null)s.network="未连接";else{
                String type=c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)?"VPN":c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"移动网络":c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)?"有线网络":"其他网络";
                s.network=type+" · "+(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?"互联网已验证":"互联网未验证");
            }
        }catch(Exception e){s.network="网络状态不可读";}
        long currentRx=TrafficStats.getTotalRxBytes(),currentTx=TrafficStats.getTotalTxBytes();
        s.traffic="↓ "+MonitorMath.rate(MonitorMath.bytesPerSecond(rx,currentRx,now-previousAt))+"   ↑ "+MonitorMath.rate(MonitorMath.bytesPerSecond(tx,currentTx,now-previousAt));
        rx=currentRx;tx=currentTx;previousAt=now;
        Intent b=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(b!=null){int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,100);s.battery=(level>=0&&scale>0?level*100/scale+"%":"—")+" · 电池 "+String.format(Locale.ROOT,"%.1f°C",b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10.0);}
        long min=Long.MAX_VALUE,max=0;for(int i=0;i<16;i++)try{
            long v=Long.parseLong(readFile("/sys/devices/system/cpu/cpu"+i+"/cpufreq/scaling_cur_freq").trim());if(v>0){min=Math.min(min,v);max=Math.max(max,v);}
        }catch(Exception ignored){}
        if(max>0)s.frequency=String.format(Locale.ROOT,"%.2f–%.2f GHz",min/1e6,max/1e6);
        if(dumpGranted()){
            if(now-lastCpu>4000){lastCpu=now;String raw=command("cpuinfo");cpuValue=MonitorMath.cpuTotal(raw);cpu=Double.isNaN(cpuValue)?"不可用":String.format(Locale.ROOT,"%.1f%%",cpuValue);cpuDetail=Double.isNaN(cpuValue)?(raw.contains("Permission")?"系统诊断权限不足":"诊断未返回统计，可能受固件限制"):"设备总体 · 系统采样窗口";}
            s.cpu=usageGranted()?cpu:"待授权";s.cpuValue=usageGranted()?cpuValue:Double.NaN;s.cpuDetail=usageGranted()?cpuDetail:"需使用情况访问权（系统 CPU 接口要求）";
            if(pkg==null||pkg.isEmpty()){s.fpsDetail="从游戏库启动后采集该游戏";}
            else{
                if(!pkg.equals(lastPackage)){layer="";lastPackage=pkg;lastLayerSearch=0;}
                if(!layer.isEmpty())s.fpsValue=MonitorMath.fps(command("SurfaceFlinger","--latency",layer),System.nanoTime());
                if(Double.isNaN(s.fpsValue)&&now-lastLayerSearch>10000){
                    lastLayerSearch=now;List<String> candidates=MonitorMath.gameLayers(command("SurfaceFlinger","--list"),pkg);
                    for(String candidate:candidates){double value=MonitorMath.fps(command("SurfaceFlinger","--latency",candidate),System.nanoTime());if(!Double.isNaN(value)){layer=candidate;s.fpsValue=value;break;}}
                }
                if(Double.isNaN(s.fpsValue)){s.fps="不可用";s.fpsDetail="无近期游戏呈现帧或固件未开放";}else{s.fps=String.format(Locale.ROOT,"%.0f FPS",s.fpsValue);s.fpsDetail="游戏图层呈现帧率 · 近 2 秒";}
            }
        }else{s.cpu="待授权";s.cpuDetail="需一次性 DUMP 授权";s.fps="待授权";s.fpsDetail="需一次性 DUMP 授权";}
        return s;
    }
    private static String readFile(String path)throws IOException{
        try(BufferedReader r=new BufferedReader(new FileReader(path))){return r.readLine();}
    }
    private static String command(String... args){
        List<String> cmd=new ArrayList<>();cmd.add("/system/bin/dumpsys");cmd.add("-t");cmd.add("1");Collections.addAll(cmd,args);
        java.lang.Process process=null;
        try{
            process=new ProcessBuilder(cmd).redirectErrorStream(true).start();final InputStream stream=process.getInputStream();
            FutureTask<String> task=new FutureTask<>(()->{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=stream.read(buf))!=-1){if(out.size()+n>262144)throw new IOException("Diagnostic output exceeded bound");out.write(buf,0,n);}return out.toString("UTF-8");});
            Thread reader=new Thread(task,"bounded-diagnostic-reader");reader.setDaemon(true);reader.start();
            if(!process.waitFor(1500,TimeUnit.MILLISECONDS)){process.destroyForcibly();return "";}
            return task.get(300,TimeUnit.MILLISECONDS);
        }catch(Exception e){return "";}finally{if(process!=null){process.destroy();try{process.getInputStream().close();}catch(Exception ignored){}}}
    }
}
