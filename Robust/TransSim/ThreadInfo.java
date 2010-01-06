import java.util.*;

public class ThreadInfo {
  FlexScheduler fs;
  public ThreadInfo(FlexScheduler fs) {
    this.fs=fs;
  }
  boolean stalled;
  int oid;
  int index;
  int priority;

  public void setObject(int oid) {
    this.oid=oid;
  }

  public void setIndex(int index) {
    this.index=index;
  }

  public int getObject() {
    return oid;
  }

  public int getIndex() {
    return index;
  }

  public void setObjIndex(ObjIndex oi) {
    oid=oi.object;
    index=oi.index;
  }

  public ObjIndex getObjIndex() {
    return new ObjIndex(oid, index);
  }

  public boolean isStalled() {
    return stalled;
  }
  public void setStall(boolean stall) {
    stalled=stall;
  }
}