package cn.fanxing.starplay;
import android.content.*;

public final class SessionActions {
    private SessionActions(){}
    public static boolean change(Context c,String mode,String returnGame)throws Exception{
        DeviceControl d=new DeviceControl(c);
        if(d.prefs.getBoolean("battery_op",false))throw new IllegalStateException("系统电池策略正在处理，请稍候。");
        d.session.change(mode);
        if("2".equals(mode)&&d.prefs.getBoolean("battery_sync",false)){BatteryAutomation.enable(c,returnGame);return true;}
        if("1".equals(mode))return BatteryAutomation.restore(c,returnGame);
        return false;
    }
    public static boolean end(Context c)throws Exception{
        DeviceControl d=new DeviceControl(c);
        if(d.prefs.getBoolean("battery_op",false))throw new IllegalStateException("系统电池策略正在处理，请稍候；失败时可在主界面恢复。");
        try{d.session.restore();return BatteryAutomation.restore(c,"");}
        finally{if(d.session.current()==null)c.stopService(new Intent(c,SessionService.class));}
    }
    public static void keep(Context c)throws Exception{
        new DeviceControl(c).session.keepCurrentAndEnd();BatteryAutomation.keepCurrent(c);c.stopService(new Intent(c,SessionService.class));
    }
}
