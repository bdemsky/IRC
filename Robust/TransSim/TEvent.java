public class TEvent {
  public byte type;
  public int index;
  public int oid;
  public long time;

  public TEvent(byte type, long time) {
    this.time=time;
    this.type=type;
    this.oid=-1;
    this.index=-1;
  }

  public TEvent(byte type, long time, int oid) {
    this.time=time;
    this.type=type;
    this.oid=oid;
    this.index=-1;
  }

  public TEvent(byte type, long time, int oid, int index) {
    this.time=time;
    this.type=type;
    this.oid=oid;
    this.index=index;
  }
}