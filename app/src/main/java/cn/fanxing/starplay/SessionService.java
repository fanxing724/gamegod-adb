package cn.fanxing.starplay;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** User initiated overlay + a bounded metrics worker, both stop with the session. */
public final class SessionService extends Service {
    public static final String END="cn.fanxing.starplay.END",REFRESH="cn.fanxing.starplay.REFRESH";
    private static final int TEXT=0xffedf3fa,MUTED=0xffa0b3c6,ACCENT=0xff65e4c0;
    private WindowManager window;private TextView bubble,metrics,status;private LinearLayout panel;
    private DeviceControl control;private DeviceMonitor monitor;
    private volatile boolean expanded=false,destroyed=false;private boolean samplingStarted=false;private long confirmEnd;
    private volatile DeviceMonitor.Snapshot snapshot=new DeviceMonitor.Snapshot();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private HandlerThread workerThread;private Handler worker;
    private final Runnable sample=new Runnable(){public void run(){
        if(destroyed)return;ModeSession.Entry e=control.session.current();if(e==null){handler.post(()->stopSelf());return;}
        try{snapshot=monitor.sample(e.game);}catch(Exception ignored){}
        handler.post(()->{if(!destroyed)paintMetrics();});
        if(!destroyed)worker.postDelayed(this,expanded?2000:5000);
    }};
    public void onCreate(){
        super.onCreate();control=new DeviceControl(this);monitor=new DeviceMonitor(this);window=getSystemService(WindowManager.class);
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("session","游戏会话",NotificationManager.IMPORTANCE_LOW));
        workerThread=new HandlerThread("starplay-monitor",android.os.Process.THREAD_PRIORITY_BACKGROUND);workerThread.start();worker=new Handler(workerThread.getLooper());
    }
    public int onStartCommand(Intent intent,int flags,int startId){
        startForeground(7,notification("游戏会话与悬浮工具 · 可恢复原设置"));
        if(intent!=null&&END.equals(intent.getAction()))finishSession();
        if(control.session.current()==null){stopSelf();return START_NOT_STICKY;}
        refreshOverlay();if(!samplingStarted){samplingStarted=true;worker.post(sample);}return START_NOT_STICKY;
    }
    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent end=PendingIntent.getService(this,1,new Intent(this,SessionService.class).setAction(END),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"session").setSmallIcon(R.drawable.ic_status).setContentTitle("星游调度").setContentText(text).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"恢复并结束",end).build()).build();
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),0xff35504e);return d;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);t.setLineSpacing(dp(4),1);return t;}
    private TextView button(String label,Runnable action){TextView b=text(label,13,ACCENT);b.setGravity(Gravity.CENTER);b.setPadding(dp(10),dp(12),dp(10),dp(12));b.setMinHeight(dp(44));b.setBackground(bg(0xff213746,12));b.setOnClickListener(v->action.run());return b;}
    private void addButton(LinearLayout root,String label,Runnable action){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);root.addView(button(label,action),p);}
    private String elapsed(){ModeSession.Entry e=control.session.current();return Math.max(0,e==null?0:(System.currentTimeMillis()-e.started)/60000)+" 分钟";}
    private WindowManager.LayoutParams params(int width,int height,int y){WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.END;p.x=dp(12);p.y=y;return p;}
    private void refreshOverlay(){
        removeWindows();expanded=false;
        if(!control.prefs.getBoolean("overlay",false)||!Settings.canDrawOverlays(this))return;
        try{
            bubble=text("✦  星游工具",12,ACCENT);bubble.setTypeface(null,Typeface.BOLD);bubble.setPadding(dp(12),dp(10),dp(12),dp(10));bubble.setBackground(bg(0xf2132330,22));bubble.setContentDescription("展开游戏内工具面板");
            WindowManager.LayoutParams lp=params(-2,-2,dp(72));
            bubble.setOnTouchListener(new View.OnTouchListener(){float downX,downY;int x,y;boolean moved;
                public boolean onTouch(View v,MotionEvent ev){
                    if(ev.getAction()==MotionEvent.ACTION_DOWN){downX=ev.getRawX();downY=ev.getRawY();x=lp.x;y=lp.y;moved=false;return true;}
                    if(ev.getAction()==MotionEvent.ACTION_MOVE){float dx=ev.getRawX()-downX,dy=ev.getRawY()-downY;if(Math.abs(dx)+Math.abs(dy)>dp(6))moved=true;
                        lp.x=Math.max(0,x-(int)dx);lp.y=Math.max(0,y+(int)dy);try{window.updateViewLayout(v,lp);}catch(Exception ignored){}return true;}
                    if(ev.getAction()==MotionEvent.ACTION_UP){if(!moved)v.performClick();return true;}return false;
                }});
            bubble.setOnClickListener(v->{if(expanded)closePanel();else openPanel();});window.addView(bubble,lp);paintMetrics();
        }catch(Exception e){removeWindows();}
    }
    private void openPanel(){
        if(panel!=null)return;
        try{
            panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(14),dp(16),dp(14));panel.setBackground(bg(0xfa111e2c,20));
            LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView name=text("✦  游戏工具",17,ACCENT);name.setTypeface(null,Typeface.BOLD);head.addView(name,new LinearLayout.LayoutParams(0,-2,1));head.addView(button("收起",this::closePanel));panel.addView(head);
            ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            metrics=text("正在采样…",13,TEXT);metrics.setPadding(0,dp(12),0,dp(8));content.addView(metrics);
            TextView note=text("内存为系统可用量；网速为全设备计数。\nFPS 来自游戏图层，不是屏幕刷新率。",10,MUTED);content.addView(note);
            LinearLayout modes=new LinearLayout(this);LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,-2);row.topMargin=dp(12);content.addView(modes,row);
            modes.addView(button("均衡",()->changeMode("1")),new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(8);modes.addView(button("游戏模式",()->changeMode("2")),p);
            status=text("",11,0xfff2c87e);status.setPadding(0,dp(10),0,0);content.addView(status);
            addButton(content,"清理运行内存 · 系统入口",this::systemCleaner);
            addButton(content,"系统电池性能模式",this::openBattery);
            addButton(content,"恢复原设置并结束",()->{
                if(SystemClock.elapsedRealtime()-confirmEnd<4000){finishSession();return;}
                confirmEnd=SystemClock.elapsedRealtime();status.setText("再点一次“恢复原设置并结束”确认结束会话。");
            });
            addButton(content,"打开主界面",()->{closePanel();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));});
            int w=Math.min(dp(330),getResources().getDisplayMetrics().widthPixels-dp(24));int h=Math.min(dp(520),getResources().getDisplayMetrics().heightPixels-dp(72));
            window.addView(panel,params(w,h,dp(24)));expanded=true;if(bubble!=null)bubble.setVisibility(View.GONE);paintMetrics();
        }catch(Exception e){closePanel();Toast.makeText(this,"无法打开悬浮面板："+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();}
    }
    private void paintMetrics(){
        DeviceMonitor.Snapshot s=snapshot;
        if(bubble!=null)bubble.setText("✦ "+(Double.isNaN(s.fpsValue)?"工具":s.fps)+" · "+elapsed());
        if(metrics!=null)metrics.setText("游戏 FPS     "+s.fps+"\n"+s.fpsDetail+"\n\n设备 CPU     "+s.cpu+"\n"+s.cpuDetail+"\nCPU 频率     "+s.frequency+"\n\n运行内存     "+s.memory+"\n\n网络     "+s.network+"\n"+s.traffic+"\n\n电量 / 温度     "+s.battery+"\n游戏模式     "+control.safeValue()+"\n电池策略     "+BatteryAutomation.currentLabel(this));
    }
    private void changeMode(String mode){try{ModeSession.Entry e=control.session.current();boolean pending=SessionActions.change(this,mode,e==null?"":e.game);if(status!=null)status.setText("系统模式值已读回确认；调度效果待验证。");paintMetrics();if(pending)closePanel();}catch(Exception e){if(status!=null)status.setText(e.getMessage());}}
    private void finishSession(){
        try{SessionActions.end(this);stopSelf();}
        catch(Exception e){if(status!=null)status.setText(e.getMessage());getSystemService(NotificationManager.class).notify(7,notification("恢复需要处理 · 点击打开助手"));Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void systemCleaner(){
        Intent launch=getPackageManager().getLaunchIntentForPackage("com.hihonor.systemmanager");
        if(launch==null){status.setText("未找到可启动的荣耀系统管家。Android 15 不允许普通 App 清理其他应用进程。");return;}
        status.setText("由荣耀系统管家执行清理，本助手不宣称已释放内存。");closePanel();launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(launch);
    }
    private void openBattery(){try{closePanel();startActivity(new Intent("android.intent.action.POWER_USAGE_SUMMARY").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Exception e){Toast.makeText(this,"系统电池页面不可用",Toast.LENGTH_LONG).show();}}
    private void closePanel(){if(panel!=null)try{window.removeView(panel);}catch(Exception ignored){}panel=null;metrics=null;status=null;expanded=false;if(bubble!=null)bubble.setVisibility(View.VISIBLE);}
    private void removeWindows(){closePanel();if(bubble!=null)try{window.removeView(bubble);}catch(Exception ignored){}bubble=null;}
    public void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);worker.removeCallbacksAndMessages(null);workerThread.quitSafely();removeWindows();super.onDestroy();}
    public IBinder onBind(Intent intent){return null;}
}
