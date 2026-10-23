package cn.fanxing.starplay;

/** Durable transaction journal. No Android dependencies so recovery can be tested on the JVM. */
public final class ModeSession {
    public interface Backend { String read() throws Exception; void write(String value) throws Exception; }
    public interface Journal { Entry load(); void save(Entry e) throws Exception; void clear() throws Exception; }
    public static final class Entry {
        public final String original, expected, game, previous;
        public final long started;
        public Entry(String original, String expected, String game, long started) {
            this(original,expected,game,started,null);
        }
        public Entry(String original, String expected, String game, long started, String previous) {
            this.original=original; this.expected=expected; this.game=game; this.started=started; this.previous=previous;
        }
    }
    public static final class Conflict extends Exception {
        public Conflict() { super("系统模式已在助手之外改变，未覆盖当前设置。可保留当前值并结束会话。"); }
    }
    private final Backend backend;
    private final Journal journal;
    public ModeSession(Backend backend, Journal journal) { this.backend=backend; this.journal=journal; }
    public Entry current() { return journal.load(); }
    public static boolean same(String a, String b) { return a==null ? b==null : a.equals(b); }
    public void begin(String mode, String game, long now) throws Exception {
        if (journal.load()!=null) throw new IllegalStateException("请先结束或恢复已有会话。");
        validate(mode);
        String before=backend.read();
        // Flush original value BEFORE touching the system. A crash leaves a recoverable record.
        journal.save(new Entry(before, mode, game, now));
        backend.write(mode);
        if (!same(mode, backend.read())) throw new IllegalStateException("设置未通过读回确认，恢复记录已保留。");
    }
    public void change(String mode) throws Exception {
        validate(mode);
        Entry e=journal.load();
        if(e==null) throw new IllegalStateException("没有正在进行的会话。");
        if(!same(backend.read(),e.expected)) throw new Conflict();
        journal.save(new Entry(e.original,mode,e.game,e.started,e.expected));
        backend.write(mode);
        if(!same(mode,backend.read())) throw new IllegalStateException("设置未通过读回确认，恢复记录已保留。");
        journal.save(new Entry(e.original,mode,e.game,e.started));
    }
    public void restore() throws Exception {
        Entry e=journal.load();
        if(e==null) return;
        String actual=backend.read();
        if(same(actual,e.original)) { journal.clear(); return; }
        if(!same(actual,e.expected) && !(e.previous!=null && same(actual,e.previous))) throw new Conflict();
        backend.write(e.original);
        if(!same(e.original,backend.read())) throw new IllegalStateException("恢复未通过读回确认，请保留记录稍后重试。");
        journal.clear();
    }
    public void keepCurrentAndEnd() throws Exception { journal.clear(); }
    private static void validate(String mode) {
        if(!"1".equals(mode) && !"2".equals(mode)) throw new IllegalArgumentException("不支持的模式");
    }
}
