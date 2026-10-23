package cn.fanxing.starplay;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.view.accessibility.*;
import java.util.*;

/** Narrow optional adapter: only the official battery page's exact performance switch. */
public final class BatteryAutomation extends AccessibilityService {
    private static volatile BatteryAutomation bound;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long deadline;private boolean clicked;private String lastReason="等待原生页面";
    private static final String MODE="SmartModeStatus";
    public static boolean ready(){return bound!=null;}
    public static boolean owned(Context c){return new DeviceControl(c).prefs.getBoolean("battery_owned",false);}
    public static String current(Context c){return Settings.System.getString(c.getContentResolver(),MODE);}
    public static String currentLabel(Context c){String s=current(c);return "5".equals(s)?"系统性能模式":"1".equals(s)?"系统智能模式":s==null?"不可读":"其他电池策略（"+s+"）";}
    public static void enable(Context c,String returnGame)throws Exception{
        DeviceControl d=new DeviceControl(c);String current=current(c);
        if(!"1".equals(current)&&!"5".equals(current))throw new IllegalStateException("当前处于其他电池策略，请先在系统电池页退出省电模式。");
        if(!ready())throw new IllegalStateException("请先启用星游调度辅助功能，才能操作系统性能模式。");
        if(!owned(c)){
            if("5".equals(current)){returnTo(c,returnGame);return;}
            if(!d.prefs.edit().putBoolean("battery_owned",true).putString("battery_original",current).putString("battery_expected","5").commit())throw new IllegalStateException("无法保存电池策略恢复记录。");
        }else if(!current.equals(d.prefs.getString("battery_expected","5")))throw new IllegalStateException("电池策略已在助手之外改变，未覆盖。");
        request(c,true,returnGame,false);
    }
    public static boolean restore(Context c,String returnGame)throws Exception{
        DeviceControl d=new DeviceControl(c);if(!owned(c))return false;
        String original=d.prefs.getString("battery_original","1"),actual=current(c);
        if(original.equals(actual)){clear(c);return false;}
        if(!d.prefs.getString("battery_expected","5").equals(actual))throw new IllegalStateException("电池策略已在助手之外改变，未覆盖。恢复记录仍保留。");
        if(!ready())throw new IllegalStateException("游戏模式可单独恢复；电池策略恢复需要重新启用辅助功能，记录已保留。");
        request(c,"5".equals(original),returnGame,true);return true;
    }
    private static void request(Context c,boolean target,String game,boolean restoring)throws Exception{
        DeviceControl d=new DeviceControl(c);
        if(!d.prefs.edit().putBoolean("battery_op",true).putBoolean("battery_target",target).putBoolean("battery_restoring",restoring).putString("battery_return",game).putLong("battery_requested",System.currentTimeMillis()).remove("battery_error").commit())throw new IllegalStateException("无法保存电池操作记录。");
        BatteryAutomation b=bound;if(b==null)throw new IllegalStateException("辅助功能已断开，恢复记录保留。");
        b.handler.post(()->{b.deadline=SystemClock.elapsedRealtime()+18000;b.clicked=false;
            try{b.startActivity(new Intent("android.intent.action.POWER_USAGE_SUMMARY").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));b.handler.removeCallbacks(b.poll);b.handler.postDelayed(b.poll,400);}catch(Exception e){b.fail("无法打开系统电池页面");}
        });
    }
    public static void keepCurrent(Context c){clear(c);}
    private static void clear(Context c){new DeviceControl(c).prefs.edit().remove("battery_owned").remove("battery_original").remove("battery_expected").remove("battery_op").remove("battery_error").remove("battery_restoring").commit();}
    private static void returnTo(Context c,String game){
        Intent intent=game==null||game.isEmpty()?new Intent(c,MainActivity.class):c.getPackageManager().getLaunchIntentForPackage(game);
        if(intent==null)intent=new Intent(c,MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);c.startActivity(intent);
    }
    public static void openPermission(Context c){
        Intent i=new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME,new ComponentName(c,BatteryAutomation.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{c.startActivity(i);}catch(Exception e){c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    }
    public void onServiceConnected(){
        bound=this;DeviceControl d=new DeviceControl(this);
        if(d.prefs.getBoolean("battery_op",false)){
            long age=System.currentTimeMillis()-d.prefs.getLong("battery_requested",0);
            if(age>=0&&age<30000){deadline=SystemClock.elapsedRealtime()+18000;clicked=false;handler.removeCallbacks(poll);handler.post(poll);}
            else d.prefs.edit().remove("battery_op").putString("battery_error","辅助功能重新连接，请重新发起操作或恢复策略。").commit();
        }
    }
    public void onAccessibilityEvent(AccessibilityEvent event){ /* Timed poll is not postponed by frequent window events. */ }
    private AccessibilityNodeInfo batteryRoot(){
        for(AccessibilityWindowInfo w:getWindows()){
            AccessibilityNodeInfo root=w.getRoot();if(root!=null&&"com.hihonor.systemmanager".contentEquals(root.getPackageName()==null?"":root.getPackageName()))return root;
        }
        AccessibilityNodeInfo root=getRootInActiveWindow();
        return root!=null&&"com.hihonor.systemmanager".contentEquals(root.getPackageName()==null?"":root.getPackageName())?root:null;
    }
    private final Runnable poll=new Runnable(){public void run(){
        DeviceControl d=new DeviceControl(BatteryAutomation.this);if(!d.prefs.getBoolean("battery_op",false))return;
        if(SystemClock.elapsedRealtime()>deadline){fail("未能确认系统性能开关："+lastReason+"。恢复记录保留。");return;}
        AccessibilityNodeInfo root=batteryRoot();
        lastReason=root==null?"系统电池页不可读":"尚未找到性能开关";
        if(root!=null){
            List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId("com.hihonor.systemmanager:id/perform_mode_switch");
            if(!nodes.isEmpty()){
                AccessibilityNodeInfo n=nodes.get(0);android.util.Log.i("StarplayBattery","switch="+n.isChecked()+" enabled="+n.isEnabled()+" clicked="+clicked);lastReason=clicked?"已点击，等待系统确认":"性能开关等待操作";boolean target=d.prefs.getBoolean("battery_target",true);
                if(n.isCheckable()&&n.isChecked()==target&&((target&&"5".equals(current(BatteryAutomation.this)))||(!target&&"1".equals(current(BatteryAutomation.this))))){
                    String game=d.prefs.getString("battery_return","");boolean restoring=d.prefs.getBoolean("battery_restoring",false);
                    if(restoring)clear(BatteryAutomation.this);else d.prefs.edit().remove("battery_op").remove("battery_error").commit();
                    returnTo(BatteryAutomation.this,game);return;
                }
                if(!clicked&&n.isCheckable()&&n.isChecked()!=target&&n.isEnabled()){clicked=n.performAction(AccessibilityNodeInfo.ACTION_CLICK);}
            }
        }
        handler.postDelayed(this,400);
    }};
    private void fail(String message){android.util.Log.w("StarplayBattery",message);new DeviceControl(this).prefs.edit().remove("battery_op").putString("battery_error",message).commit();returnTo(this,"");}
    public void onInterrupt(){handler.removeCallbacks(poll);}
    public void onDestroy(){if(bound==this)bound=null;handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
