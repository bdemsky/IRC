package Analysis.Scheduling;

import java.util.Queue;
import java.util.Vector;

public class FIFORSchedule extends RuntimeSchedule {
  static FIFORSchedule rschedule;

  public static FIFORSchedule getFIFORSchedule() {
    if(rschedule == null) {
      rschedule = new FIFORSchedule();
    }
    return rschedule;
  }

  public FIFORSchedule() {
    super("FIFO Algorithm");
  }

  public TaskSimulator schedule(Vector<TaskSimulator> tasks) {
    if(tasks == null) {
      return null;
    }
    TaskSimulator next = null;
    int i = 0;
    for(; i < tasks.size(); i++) {
      next = tasks.elementAt(i);
      int paraNum = next.getTd().numParameters();
      Vector<Queue<ObjectSimulator>> pqueues = next.getParaQueues();
      if((pqueues == null) || (pqueues.size() < paraNum)) {
	continue;
      }
      int j = 0;
      for(; j < pqueues.size(); j++) {
	Queue<ObjectSimulator> objs = pqueues.elementAt(j);
	if((objs == null) || (objs.size() == 0)) {
	  break;
	}
      }
      if(j == pqueues.size()) {
	return next;
      }
    }
    if(i == tasks.size()) {
      return null;
    }
    return next;
  }
}