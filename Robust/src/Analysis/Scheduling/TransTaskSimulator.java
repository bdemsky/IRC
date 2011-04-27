package Analysis.Scheduling;

import java.util.Queue;

public class TransTaskSimulator extends TaskSimulator {
  private int targetCoreNum;
  private Queue<ObjectInfo> newObjs;

  public TransTaskSimulator(CoreSimulator cs,
                            int targetCoreNum,
                            Queue<ObjectInfo> nobjs) {
    super(null, cs);
    this.targetCoreNum = targetCoreNum;
    this.newObjs = nobjs;
  }

  public void process() {
    if(this.currentRun == null) {
      this.currentRun = new ExeResult();
    }

    this.currentRun.finishTime = 1 * sizeof(this.newObjs.peek().obj.getCd());
  }

  public ObjectInfo refreshTask() {
    return this.newObjs.poll();
  }

  private int sizeof(Object obj) {
    return 1;
  }

  public boolean isFinished() {
    return this.newObjs.isEmpty();
  }

  public int getTargetCoreNum() {
    return targetCoreNum;
  }

  public Queue<ObjectInfo> getNewObjs() {
    return newObjs;
  }

}