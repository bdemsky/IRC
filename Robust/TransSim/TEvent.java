public class TEvent {
  public int type;
  public int index;
  public int oid;
  public long time;

  public TEvent(int type, long time) {
    this.time=time;
    this.type=type;
    this.oid=-1;
    this.index=-1;
  }

  public TEvent(int type, long time, int oid) {
    this.time=time;
    this.type=type;
    this.oid=oid;
    this.index=-1;
  }

  public TEvent(int type, long time, int oid, int index) {
    this.time=time;
    this.type=type;
    this.oid=oid;
    this.index=index;
  }
}