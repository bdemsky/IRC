import java.util.*;

public class ThreadInfo {
  FlexScheduler fs;
  public ThreadInfo(FlexScheduler fs) {
    this.fs=fs;
  }
  boolean stalled;
  int oid;

  public void setObject(int oid) {
    this.oid=oid;
  }

  public int getObject() {
    return oid;
  }

  public boolean isStalled() {
    return stalled;
  }
  public void setStall(boolean stall) {
    stalled=stall;
  }
}