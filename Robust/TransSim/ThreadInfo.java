import java.util.*;

public class ThreadInfo {
  FlexScheduler fs;
  public ThreadInfo(FlexScheduler fs) {
    this.fs=fs;
  }
  boolean stalled;

  public boolean isStalled() {
    return stalled;
  }
  public void setStall(boolean stall) {
    stalled=stall;
  }
}