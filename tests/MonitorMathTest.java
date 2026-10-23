import cn.fanxing.starplay.MonitorMath;
public final class MonitorMathTest {
 static int n;static void ok(boolean b){if(!b)throw new AssertionError();n++;}
 public static void main(String[] args){
  ok(MonitorMath.cpuTotal("CPU usage\n  17.5% TOTAL: 9% user + 8% kernel")==17.5);
  ok(Double.isNaN(MonitorMath.cpuTotal("Permission Denial")));
  ok(Double.isNaN(MonitorMath.cpuTotal("150% TOTAL: invalid")));
  ok(MonitorMath.cpuTotal("0% TOTAL: idle")==0);
  long now=10_000_000_000L;StringBuilder b=new StringBuilder("16666666\n");
  for(int i=0;i<61;i++){long t=9_000_000_000L+i*16_666_666L;b.append(t).append(' ').append(t).append(' ').append(t).append('\n');}
  ok(Math.abs(MonitorMath.fps(b.toString(),now)-60)<.01);
  ok(Double.isNaN(MonitorMath.fps("16666666\n0 0 0\n1 9223372036854775807 2\n",now)));
  ok(Double.isNaN(MonitorMath.fps(b.toString(),now+3_000_000_000L)));
  ok(Double.isNaN(MonitorMath.fps("16666666\n",now)));
  ok(Double.isNaN(MonitorMath.fps("1 9000000000 1\n2 9000000000 2",now)));
  ok(Double.isNaN(MonitorMath.fps("1 11000000000 2\n2 12000000000 3",now)));
  ok(MonitorMath.bytesPerSecond(0,2048,2000)==1024);
  ok(Double.isNaN(MonitorMath.bytesPerSecond(-1,20,1000)));
  ok(Double.isNaN(MonitorMath.bytesPerSecond(30,20,1000)));
  ok(Double.isNaN(MonitorMath.bytesPerSecond(20,30,0)));
  ok(MonitorMath.gameLayers("SurfaceView[game.pkg/GameActivity](BLAST)#1\nSurfaceView[other.pkg]", "game.pkg").size()==1);
  ok(MonitorMath.gameLayers("SurfaceView[game.pkg]", "").isEmpty());
  ok(MonitorMath.gameLayers("SurfaceView[game.pkg]\nActivityRecord{game.pkg}","game.pkg").size()==1);
  System.out.println("PASS: "+n+" monitor parsing assertions (real frames, stale/sentinel data, CPU, traffic counters)");
 }
}
