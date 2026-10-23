package cn.fanxing.starplay;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int BG=0xff0c1220,CARD=0xff151f30,TEXT=0xffedf3fa,MUTED=0xff9baac0,ACCENT=0xff65e4c0,AMBER=0xfff2c87e;
    private DeviceControl control;
    private LinearLayout body;
    private int tab=0;
    private String selected="2";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean foreground=false;
    public void onCreate(Bundle state){super.onCreate(state);control=new DeviceControl(this);selected=control.prefs.getString("selected","2");render();}
    public void onResume(){super.onResume();foreground=true;render();if(control.session.current()!=null)refreshService();}
    public void onPause(){foreground=false;super.onPause();}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setIncludeFontPadding(false);v.setLineSpacing(dp(4),1);return v;}
    private TextView title(String s,int sp){TextView v=text(s,sp,TEXT);v.setTypeface(null,Typeface.BOLD);return v;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void space(LinearLayout l,int n){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private LinearLayout card(){LinearLayout l=column();l.setPadding(dp(18),dp(18),dp(18),dp(18));l.setBackground(shape(CARD,20));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);body.addView(l,p);return l;}
    private TextView button(String s,boolean primary,Runnable action){TextView b=text(s,14,primary?BG:ACCENT);b.setGravity(Gravity.CENTER);b.setTypeface(null,Typeface.BOLD);
        b.setPadding(dp(12),dp(14),dp(12),dp(14));b.setMinHeight(dp(48));b.setBackground(shape(primary?ACCENT:0xff223444,14));
        b.setOnClickListener(v->action.run());b.setFocusable(true);return b;}
    private void fullButton(LinearLayout l,String s,boolean primary,Runnable action){space(l,12);l.addView(button(s,primary,action),new LinearLayout.LayoutParams(-1,-2));}
    private String gameName(String pkg){if(pkg==null||pkg.isEmpty())return "手动模式会话";try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception e){return pkg;}}
    private void render(){
        if(isFinishing())return;
        LinearLayout root=column();root.setBackgroundColor(BG);root.setFocusableInTouchMode(true);root.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        root.setOnApplyWindowInsetsListener((v,in)->{v.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in;});
        setContentView(root);root.requestApplyInsets();
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(22),dp(18),dp(22),dp(14));
        TextView logo=text("✦",30,ACCENT);head.addView(logo);LinearLayout word=column();word.setPadding(dp(12),0,0,0);word.addView(title("星游调度",21));word.addView(text("你的游戏，你的节奏",11,MUTED));
        head.addView(word,new LinearLayout.LayoutParams(0,-2,1));TextView ver=text("预览版 0.2",10,AMBER);head.addView(ver);root.addView(head);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);body=column();body.setPadding(dp(20),dp(8),dp(20),dp(16));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(tab==0)home();else if(tab==1)library();else settings();
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(16),dp(8),dp(16),dp(10));String[] names={"概览","游戏库","设置"};
        for(int i=0;i<3;i++){final int index=i;TextView b=button(names[i],tab==i,()->{tab=index;render();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(4),0,dp(4),0);nav.addView(b,p);}root.addView(nav);root.requestFocus();scroll.post(()->scroll.scrollTo(0,0));
    }
    private void home(){
        ModeSession.Entry running=control.session.current();
        if(running!=null){sessionCard(running);batteryStatusCard();return;}
        if(BatteryAutomation.owned(this))batteryStatusCard();
        LinearLayout hero=card();hero.setBackground(shape(0xff15332f,20));hero.addView(text("GAME SESSION",10,ACCENT));space(hero,12);hero.addView(title("准备好，进入游戏",25));space(hero,10);
        hero.addView(text("一次授权，拔线使用。\n从这里开始管理你的游戏模式。",14,0xffb3d1c7));space(hero,16);
        hero.addView(text(control.authorized()?"●  已获得系统设置授权":"○  等待首次授权",13,control.authorized()?ACCENT:AMBER));
        if(!control.authorized())fullButton(hero,"查看一次性授权步骤",true,()->{tab=2;render();showGrant();});
        LinearLayout state=card();state.addView(title("当前状态",16));space(state,12);
        state.addView(text("设备    "+Build.MANUFACTURER+" "+Build.MODEL,13,MUTED));space(state,8);
        state.addView(text("系统值    "+control.safeValue(),14,TEXT));space(state,8);state.addView(text("实际调度效果    待实机验证",13,AMBER));
        ModeSession.Entry active=control.session.current();
        if(active!=null){sessionCard(active);return;}
        LinearLayout mode=card();mode.addView(title("本次游戏策略",16));space(mode,8);mode.addView(text("只请求系统已有模式，不修改频率或温控。",12,MUTED));space(mode,14);modeSelector(mode);
        fullButton(mode,"应用模式并开始计时",true,()->startSession(""));
        LinearLayout games=card();games.addView(title("快速开始",16));space(games,8);
        Set<String> list=new TreeSet<>(control.prefs.getStringSet("games",Collections.emptySet()));
        if(list.isEmpty())games.addView(text("添加常玩的游戏，选好策略后直接启动。",13,MUTED));
        int count=0;for(String pkg:list){if(count++==3)break;fullButton(games,gameName(pkg)+"    启动 ›",false,()->launch(pkg));}
        fullButton(games,list.isEmpty()?"添加我的游戏":"打开游戏库",false,()->{tab=1;render();});
    }
    private void batteryStatusCard(){
        if(!BatteryAutomation.owned(this))return;
        LinearLayout c=card();c.addView(title("系统电池策略",17));space(c,8);
        c.addView(text("当前："+BatteryAutomation.currentLabel(this)+"\n进入前："+("5".equals(control.prefs.getString("battery_original","1"))?"系统性能模式":"系统智能模式"),13,MUTED));
        String err=control.prefs.getString("battery_error","");if(!err.isEmpty()){space(c,8);c.addView(text(err,12,AMBER));}
        if(control.prefs.getBoolean("battery_op",false))c.addView(text("正在等待原生电池开关确认…",12,AMBER));
        fullButton(c,"恢复进入前的电池策略",true,()->{try{if(!BatteryAutomation.restore(this,""))render();}catch(Exception e){error(e);}});
        fullButton(c,"保留当前电池策略并清除记录",false,()->new AlertDialog.Builder(this).setTitle("保留当前电池策略？").setMessage("清除电池策略恢复记录，保留系统当前值。").setPositiveButton("保留",(d,w)->{BatteryAutomation.keepCurrent(this);render();}).setNegativeButton("取消",null).show());
    }
    private void modeSelector(LinearLayout parent){
        LinearLayout row=new LinearLayout(this);String[] labels={"跟随系统","均衡","游戏模式"},values={"follow","1","2"};
        for(int i=0;i<3;i++){final String value=values[i];TextView b=button(labels[i],value.equals(selected),()->{selected=value;control.prefs.edit().putString("selected",value).apply();render();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i>0)p.leftMargin=dp(6);row.addView(b,p);}parent.addView(row);
    }
    private void sessionCard(ModeSession.Entry e){
        LinearLayout c=card();c.addView(text("会话记录已保存",11,ACCENT));space(c,10);c.addView(title(gameName(e.game),20));space(c,10);
        c.addView(text("已进行 "+Math.max(0,(System.currentTimeMillis()-e.started)/60000)+" 分钟",14,MUTED));space(c,8);
        c.addView(text("进入前："+DeviceControl.label(e.original)+"\n目标值："+DeviceControl.label(e.expected)+"\n当前值："+control.safeValue(),13,TEXT));
        space(c,10);c.addView(text("会话需手动结束。切到桌面不会自动恢复。",12,AMBER));
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){space(c,8);c.addView(text("通知未开启，请返回本页结束会话。",12,AMBER));}
        fullButton(c,"恢复原设置并结束",true,this::restore);
        fullButton(c,"切换本次策略",false,()->new AlertDialog.Builder(this).setTitle("切换会话策略").setItems(new String[]{"均衡","游戏模式"},(d,w)->{
            try{SessionActions.change(this,w==0?"1":"2","");toast("系统值已读回确认；效果待验证");render();}catch(Exception x){error(x);}
        }).setNegativeButton("取消",null).show());
        fullButton(c,"保留当前系统值并结束",false,()->new AlertDialog.Builder(this).setTitle("保留当前值？").setMessage("结束会话并保留当前游戏模式和电池策略，清除对应恢复记录。").setPositiveButton("保留并结束",(d,w)->{
            try{SessionActions.keep(this);render();}catch(Exception x){error(x);}
        }).setNegativeButton("取消",null).show());
    }
    private void library(){
        body.addView(title("我的游戏",26));space(body,8);body.addView(text("从已安装应用中选择，游戏列表仅保存在本机。",13,MUTED));space(body,18);
        LinearLayout policy=card();policy.addView(title("默认启动策略",15));space(policy,12);modeSelector(policy);
        fullButton(policy,"＋ 添加游戏",true,this::pickGames);
        Set<String> games=new TreeSet<>(control.prefs.getStringSet("games",Collections.emptySet()));
        if(games.isEmpty()){LinearLayout c=card();c.addView(title("留一片位置给喜欢的游戏",18));space(c,10);c.addView(text("还没有添加游戏，点击上方按钮开始。",13,MUTED));}
        for(String pkg:games){LinearLayout c=card();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            try{ImageView icon=new ImageView(this);icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(44),dp(44));ip.rightMargin=dp(12);row.addView(icon,ip);}catch(Exception ignored){}
            LinearLayout desc=column();desc.addView(title(gameName(pkg),17));space(desc,5);String mode=control.prefs.getString("policy."+pkg,"default");desc.addView(text("启动策略："+policyLabel(mode),12,MUTED));row.addView(desc,new LinearLayout.LayoutParams(0,-2,1));c.addView(row);
            fullButton(c,"启动游戏  ›",true,()->launch(pkg));
            LinearLayout tools=new LinearLayout(this);space(c,8);
            TextView config=button("单独设置策略",false,()->new AlertDialog.Builder(this).setTitle(gameName(pkg)).setSingleChoiceItems(new String[]{"使用默认策略","跟随系统","均衡","游戏模式"},policyIndex(control.prefs.getString("policy."+pkg,"default")),(d,w)->{control.prefs.edit().putString("policy."+pkg,new String[]{"default","follow","1","2"}[w]).apply();d.dismiss();render();}).setNegativeButton("取消",null).show());
            tools.addView(config,new LinearLayout.LayoutParams(0,-2,1));TextView del=button("移除",false,()->{Set<String> next=new HashSet<>(control.prefs.getStringSet("games",Collections.emptySet()));next.remove(pkg);control.prefs.edit().putStringSet("games",next).remove("policy."+pkg).apply();render();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(76),-2);p.leftMargin=dp(8);tools.addView(del,p);c.addView(tools);
        }
    }
    private String policyLabel(String s){return "default".equals(s)?"使用默认策略":("follow".equals(s)?"跟随系统":("1".equals(s)?"均衡":"游戏模式"));}
    private int policyIndex(String s){return "default".equals(s)?0:("follow".equals(s)?1:("1".equals(s)?2:3));}
    private void pickGames(){
        ProgressDialog progress=ProgressDialog.show(this,"添加游戏","正在读取可启动应用…",true,false);
        new Thread(()->{
            try{
                Intent q=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> resolved=getPackageManager().queryIntentActivities(q,0);
                Map<String,String> names=new HashMap<>();for(ResolveInfo r:resolved){String pkg=r.activityInfo.packageName;if(!pkg.equals(getPackageName()))names.put(pkg,r.loadLabel(getPackageManager()).toString());}
                List<String> packages=new ArrayList<>(names.keySet());packages.sort((a,b)->names.get(a).compareToIgnoreCase(names.get(b)));
                String[] labels=new String[packages.size()];boolean[] checked=new boolean[packages.size()];Set<String> selectedGames=new HashSet<>(control.prefs.getStringSet("games",Collections.emptySet()));
                for(int i=0;i<labels.length;i++){labels[i]=names.get(packages.get(i));checked[i]=selectedGames.contains(packages.get(i));}
                handler.post(()->{if(isFinishing()||!foreground){progress.dismiss();return;}progress.dismiss();new AlertDialog.Builder(this).setTitle("选择你的游戏")
                    .setMultiChoiceItems(labels,checked,(d,w,on)->{if(on)selectedGames.add(packages.get(w));else selectedGames.remove(packages.get(w));})
                    .setPositiveButton("保存",(d,w)->{control.prefs.edit().putStringSet("games",selectedGames).apply();render();}).setNegativeButton("取消",null).show();});
            }catch(Exception x){handler.post(()->{progress.dismiss();if(foreground)error(x);});}
        },"app-picker").start();
    }
    private void settings(){
        body.addView(title("让手机独立运行",25));space(body,8);body.addView(text("首次用电脑授权，之后无需保持 ADB 连接。",13,MUTED));space(body,18);
        LinearLayout auth=card();auth.addView(title(control.authorized()?"✓ 系统设置权限已就绪":"① 首次授权",18));space(auth,10);
        auth.addView(text("先安装星游调度，再连接电脑执行一次授权。重启后请在这里确认权限状态。",13,MUTED));fullButton(auth,"查看并复制授权命令",true,this::showGrant);
        LinearLayout overlay=card();overlay.addView(title("② 可选悬浮入口",18));space(overlay,10);overlay.addView(text("游戏中显示可拖动入口，点开展示 FPS、CPU、内存、网络与温度，并可直接切换游戏模式。",13,MUTED));
        Switch toggle=new Switch(this);toggle.setText("游戏会话中显示悬浮入口");toggle.setTextColor(TEXT);toggle.setTextSize(14);toggle.setMinHeight(dp(52));toggle.setChecked(control.prefs.getBoolean("overlay",false));
        toggle.setOnCheckedChangeListener((b,on)->{control.prefs.edit().putBoolean("overlay",on).apply();if(on&&!Settings.canDrawOverlays(this)){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}else refreshService();});space(overlay,10);overlay.addView(toggle);
        fullButton(overlay,"允许会话通知",false,()->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},9);return;}Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);});
        LinearLayout monitoring=card();monitoring.addView(title("③ CPU / 游戏帧率授权",18));space(monitoring,10);
        monitoring.addView(text(new DeviceMonitor(this).dumpGranted()?"DUMP 权限已就绪；可用数据仍取决于固件。":"需追加一次性 DUMP 授权，拔线后由 App 自行采集。",13,MUTED));
        fullButton(monitoring,"复制监测授权命令",true,()->copy(DeviceMonitor.GRANT));
        fullButton(monitoring,"开启 CPU 使用情况访问权",false,()->startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        monitoring.addView(text("FPS 只展示游戏图层的近期呈现帧；无数据时显示不可用。网络为全设备流量，不等同游戏延迟。清理由系统管家执行。",12,MUTED));
        LinearLayout battery=card();battery.addView(title("④ 联动系统电池性能模式",18));space(battery,10);
        battery.addView(text("当前："+BatteryAutomation.currentLabel(this)+"\n辅助功能："+(BatteryAutomation.ready()?"已连接":"未开启"),13,MUTED));
        Switch sync=new Switch(this);sync.setText("游戏模式同时启用系统性能模式");sync.setTextColor(TEXT);sync.setTextSize(13);sync.setMinHeight(dp(52));sync.setChecked(control.prefs.getBoolean("battery_sync",false));
        sync.setOnCheckedChangeListener((b,on)->{control.prefs.edit().putBoolean("battery_sync",on).apply();if(on&&!BatteryAutomation.ready())BatteryAutomation.openPermission(this);});battery.addView(sync);
        space(battery,8);battery.addView(text("可选辅助功能仅操作荣耀电池页的性能开关。切换会短暂打开原生页面；退出会话后恢复原策略。",12,MUTED));
        fullButton(battery,"开启系统性能模式辅助功能",false,()->BatteryAutomation.openPermission(this));
        fullButton(battery,"打开系统电池页面",false,()->startActivity(new Intent("android.intent.action.POWER_USAGE_SUMMARY")));
        LinearLayout info=card();info.addView(title("能力与边界",18));space(info,10);info.addView(text("• 仅适配荣耀设备，目标为 X50 / MagicOS 9。\n• 当前入口为原厂 APK 中发现的候选设置。\n• 设置读回成功不等于性能提升。\n• 不需要网络，不上传游戏列表。\n• 不自动结束游戏会话；异常退出后保留恢复记录。\n• 完成恢复后再卸载，避免丢失原设置记录。",13,MUTED));
        fullButton(info,"复制本机诊断信息",false,()->{copy("星游调度 0.2.0\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid: "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"\nHonor: "+control.honor()+"\nPermission: "+control.authorized()+"\nSystem mode: "+control.safeValue()+"\nPending session: "+(control.session.current()!=null)+"\n调度效果未验证");});
        fullButton(info,"刷新权限与状态",false,()->{render();toast("已刷新");});space(body,6);body.addView(text("番星制作 · 0.2.0 · 本地运行",12,MUTED));
    }
    private void showGrant(){new AlertDialog.Builder(this).setTitle("一次性 ADB 授权").setMessage("1. 在手机上开启 USB 调试并连接电脑。\n2. 在电脑执行下面的命令。\n3. 回到助手确认授权后，即可拔线。\n\n"+DeviceControl.GRANT+"\n"+DeviceMonitor.GRANT+"\n\n本 App 不保持 ADB 会话；授权是否保留需在你的系统上验证。").setPositiveButton("复制命令",(d,w)->copy(DeviceControl.GRANT+"\n"+DeviceMonitor.GRANT)).setNegativeButton("关闭",null).show();}
    private void launch(String pkg){
        Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);if(launch==null){toast("应用已卸载或没有启动入口");return;}
        if(control.session.current()!=null){toast("请先处理当前会话");tab=0;render();return;}
        String saved=control.prefs.getString("policy."+pkg,"default");String mode="default".equals(saved)?selected:saved;
        if("follow".equals(mode)){startActivity(launch);return;}
        confirmStart(pkg,mode,launch);
    }
    private void startSession(String pkg){if("follow".equals(selected)){toast("跟随系统不会修改设置；可从游戏库直接启动游戏。");return;}confirmStart(pkg,selected,null);}
    private void confirmStart(String pkg,String mode,Intent launch){
        if(!control.honor()){toast("当前版本仅适配荣耀设备");return;}
        if(!control.authorized()){showGrant();return;}
        if("2".equals(mode)&&control.prefs.getBoolean("battery_sync",false)&&!BatteryAutomation.ready()){toast("请先开启系统性能模式辅助功能，或关闭电池策略联动。");BatteryAutomation.openPermission(this);return;}
        Runnable run=()->{
            boolean began=false;
            try{
                control.session.begin(mode,pkg,System.currentTimeMillis());began=true;
                startForegroundService(new Intent(this,SessionService.class));
                toast("系统值已读回确认；实际调度效果待验证");tab=0;render();
                if("2".equals(mode)&&control.prefs.getBoolean("battery_sync",false)){BatteryAutomation.enable(this,pkg);}
                else if(launch!=null)startActivity(launch);
            }catch(Exception x){
                if(began){try{control.session.restore();stopService(new Intent(this,SessionService.class));}catch(Exception recovery){x.addSuppressed(recovery);}}
                render();error(x);
            }
        };
        if(!control.prefs.getBoolean("acknowledged",false))new AlertDialog.Builder(this).setTitle("首次试用系统游戏模式")
            .setMessage("这是从荣耀原厂管家代码找到的模式入口，尚未验证你手机上的调度效果。\n\n将先保存当前值，再写入所选模式。请保留原管家；结束游戏后在助手或通知中恢复设置。")
            .setPositiveButton("了解，开始试用",(d,w)->{control.prefs.edit().putBoolean("acknowledged",true).apply();run.run();}).setNegativeButton("取消",null).show();else run.run();
    }
    private void restore(){try{boolean pending=SessionActions.end(this);toast(pending?"游戏模式已恢复，正在恢复电池策略":"已恢复进入前的设置");render();}catch(Exception x){render();error(x);}}
    private void refreshService(){if(control.session.current()!=null)startForegroundService(new Intent(this,SessionService.class).setAction(SessionService.REFRESH));}
    private void copy(String s){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("星游调度",s));toast("已复制");}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void error(Exception x){new AlertDialog.Builder(this).setTitle("操作未完成").setMessage(x.getMessage()==null?x.getClass().getSimpleName():x.getMessage()).setPositiveButton("知道了",(d,w)->render()).show();}
}
