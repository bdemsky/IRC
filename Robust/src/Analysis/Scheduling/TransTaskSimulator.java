package Analysis.Scheduling;

import java.util.Queue;

public class TransTaskSimulator extends TaskSimulator {
    private int targetCoreNum;
    private Queue<ObjectSimulator> newObjs;

    public TransTaskSimulator(CoreSimulator cs, int targetCoreNum, Queue<ObjectSimulator> nobjs) {
	super(null, cs);
	this.targetCoreNum = targetCoreNum;
	this.newObjs = nobjs;
    }
    
    public void process() {
	if(this.currentRun == null) {
	    this.currentRun = new ExeResult();
	}

	this.currentRun.finishTime = 1 * sizeof(this.newObjs.peek().getCd());
    }
    
    public ObjectSimulator refreshTask() {
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
}