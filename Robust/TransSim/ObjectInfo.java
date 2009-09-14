import java.util.*;

public class ObjectInfo {
  FlexScheduler fs;
  Set waiters;

  public ObjectInfo(FlexScheduler fs) {
    this.fs=fs;
    threadowner=-1;
    this.waiters=new HashSet();
  }

  public void addWaiter(FlexScheduler.Event ev) {
    waiters.add(ev);
  }

  public Set getWaiters() {
    return waiters;
  }

  int threadowner;
  public void setOwner(int thread) {
    threadowner=thread;
  }

  public boolean isOwned() {
    return threadowner!=-1;
  }

  public void releaseOwner() {
    threadowner=-1;
  }

  public int getOwner() {
    return threadowner;
  }
}