import java.util.*;

public class ObjectInfo {
  FlexScheduler fs;
  Set waiters;
  int aborts;
  int commits;
  boolean riskyflag;

  public ObjectInfo(FlexScheduler fs) {
    this.fs=fs;
    threadowner=-1;
    this.waiters=new HashSet();
    if (fs.isLock()&&fs.abortThreshold==0)
      riskyflag=true;
  }

  public boolean isRisky() {
    return riskyflag;
  }

  public void setRisky(boolean risky) {
    this.riskyflag=risky;
  }

  public void recordAbort() {
    aborts++;
    if (fs.isLock()&&(aborts>fs.abortThreshold)&&
	aborts>(commits*fs.abortRatio/100))
      setRisky(true);
  }

  public void recordCommit() {
    commits++;
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