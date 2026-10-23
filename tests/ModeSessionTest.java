import cn.fanxing.starplay.ModeSession;

public final class ModeSessionTest {
    static int count;
    static class Fixture implements ModeSession.Backend, ModeSession.Journal {
        String value;ModeSession.Entry entry;boolean failWrite,failSave,ignoreWrite,failClear;int writes;
        ModeSession s=new ModeSession(this,this);
        Fixture(String value){this.value=value;}
        public String read(){return value;}
        public void write(String v)throws Exception{writes++;if(entry==null)throw new AssertionError("write without journal");if(failWrite)throw new Exception("write refused");if(!ignoreWrite)value=v;}
        public ModeSession.Entry load(){return entry;}
        public void save(ModeSession.Entry e)throws Exception{if(failSave)throw new Exception("disk full");entry=e;}
        public void clear()throws Exception{if(failClear)throw new Exception("disk full");entry=null;}
    }
    interface Run{void run()throws Exception;}
    static void check(boolean ok){if(!ok)throw new AssertionError();count++;}
    static void fails(Run r)throws Exception{try{r.run();throw new AssertionError("expected failure");}catch(AssertionError e){throw e;}catch(Exception expected){count++;}}
    public static void main(String[] args)throws Exception{
        Fixture f=new Fixture("1");f.s.begin("2","game",123);check(f.value.equals("2")&&f.entry.original.equals("1")&&f.entry.started==123);f.s.restore();check(f.value.equals("1")&&f.entry==null);
        f=new Fixture(null);f.s.begin("2","g",1);f.s.restore();check(f.value==null&&f.entry==null);
        f=new Fixture("custom");f.s.begin("1","g",1);f.s.restore();check(f.value.equals("custom"));
        Fixture disk=new Fixture("1");disk.failSave=true;fails(()->disk.s.begin("2","g",1));check(disk.writes==0&&disk.value.equals("1"));
        Fixture refused=new Fixture("1");refused.failWrite=true;fails(()->refused.s.begin("2","g",1));check(refused.entry.original.equals("1"));refused.failWrite=false;refused.s.restore();check(refused.entry==null);
        Fixture ignored=new Fixture("1");ignored.ignoreWrite=true;fails(()->ignored.s.begin("2","g",1));check(ignored.entry!=null);ignored.s.restore();check(ignored.entry==null);
        Fixture active=new Fixture("1");active.s.begin("2","g",1);fails(()->active.s.begin("1","other",2));check(active.entry.game.equals("g"));
        active.value="external";fails(active.s::restore);check(active.value.equals("external")&&active.entry!=null);fails(()->active.s.change("1"));active.s.keepCurrentAndEnd();check(active.entry==null&&active.value.equals("external"));
        Fixture changed=new Fixture("original");changed.s.begin("2","g",7);changed.s.change("1");check(changed.entry.original.equals("original")&&changed.entry.started==7);changed.s.restore();check(changed.value.equals("original"));
        Fixture crash=new Fixture("1");crash.s.begin("2","g",8);ModeSession recreated=new ModeSession(crash,crash);recreated.restore();check(crash.value.equals("1")&&crash.entry==null);
        Fixture rollback=new Fixture("1");rollback.s.begin("2","g",0);rollback.ignoreWrite=true;fails(rollback.s::restore);check(rollback.entry!=null);rollback.ignoreWrite=false;rollback.s.restore();check(rollback.entry==null);
        Fixture invalid=new Fixture("1");fails(()->invalid.s.begin("0","g",0));check(invalid.entry==null&&invalid.writes==0);
        Fixture clear=new Fixture("1");clear.s.begin("2","g",0);clear.failClear=true;fails(clear.s::restore);check(clear.value.equals("1")&&clear.entry!=null);clear.failClear=false;clear.s.restore();check(clear.entry==null);
        Fixture noop=new Fixture("2");noop.s.begin("2","g",0);noop.s.restore();check(noop.value.equals("2")&&noop.entry==null);
        Fixture failedChange=new Fixture("original");failedChange.s.begin("2","g",9);failedChange.failWrite=true;fails(()->failedChange.s.change("1"));check(failedChange.value.equals("2"));failedChange.failWrite=false;failedChange.s.restore();check(failedChange.value.equals("original")&&failedChange.entry==null);
        Fixture ignoredChange=new Fixture("original");ignoredChange.s.begin("2","g",9);ignoredChange.ignoreWrite=true;fails(()->ignoredChange.s.change("1"));ignoredChange.ignoreWrite=false;ignoredChange.s.restore();check(ignoredChange.value.equals("original")&&ignoredChange.entry==null);
        System.out.println("PASS: "+count+" transaction assertions (null values, crash recovery, conflicts, failed writes and persistence)");
    }
}
