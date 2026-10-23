package cn.fanxing.starplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

public final class DeviceControl {
    public static final String KEY="game_power_save_mode";
    public static final String PERMISSION="android.permission.WRITE_SECURE_SETTINGS";
    public static final String GRANT="adb shell pm grant cn.fanxing.starplay android.permission.WRITE_SECURE_SETTINGS";
    public final SharedPreferences prefs;
    public final ModeSession session;
    private final Context context;
    public DeviceControl(Context c) {
        context=c.getApplicationContext();
        prefs=context.getSharedPreferences("starplay",Context.MODE_PRIVATE);
        session=new ModeSession(new ModeSession.Backend() {
            public String read() { return value(); }
            public void write(String v) {
                if(!authorized()) throw new SecurityException("尚未获得一次性 ADB 授权。");
                if(!honor()) throw new SecurityException("当前版本仅允许在荣耀设备上试验。");
                if(!Settings.Secure.putString(context.getContentResolver(),KEY,v))
                    throw new IllegalStateException("系统拒绝写入游戏模式。");
            }
        },new ModeSession.Journal() {
            public ModeSession.Entry load() {
                if(!prefs.getBoolean("pending",false)) return null;
                return new ModeSession.Entry(prefs.getString("original",null),prefs.getString("expected",null),
                    prefs.getString("game",""),prefs.getLong("started",0),prefs.getString("previous",null));
            }
            public void save(ModeSession.Entry e) {
                SharedPreferences.Editor w=prefs.edit().putBoolean("pending",true)
                    .putString("expected",e.expected).putString("previous",e.previous).putString("game",e.game).putLong("started",e.started);
                if(e.original==null)w.remove("original");else w.putString("original",e.original);
                if(!w.commit())throw new IllegalStateException("无法保存恢复记录，未继续操作。");
            }
            public void clear() {
                if(!prefs.edit().remove("pending").remove("original").remove("expected").remove("previous").remove("game").remove("started").commit())
                    throw new IllegalStateException("无法清除会话记录，请重试。");
            }
        });
    }
    public boolean authorized() { return context.checkSelfPermission(PERMISSION)==PackageManager.PERMISSION_GRANTED; }
    public boolean honor() { return "HONOR".equalsIgnoreCase(Build.BRAND)||"HONOR".equalsIgnoreCase(Build.MANUFACTURER); }
    public String value() { return Settings.Secure.getString(context.getContentResolver(),KEY); }
    public String safeValue() { try{return label(value());}catch(Exception e){return "读取受限";} }
    public static String label(String v) { return v==null?"未设置":("1".equals(v)?"均衡（1）":("2".equals(v)?"游戏模式（2）":"其他值（"+v+"）")); }
}
