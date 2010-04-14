public class Worker extends Thread {
  Object[] currentWorkList;
  int mid;
  TaskSet tasks;
  Task workingtask;

  Worker(TaskSet tasks, int mid) {
    this.tasks = tasks;
    this.currentWorkList = currentWorkList;
    mid = mid;
  }
  
  public void run() {
    long st = System.currentTimeMillis();
    long fi = 0;
    boolean notdone=true;
    while(notdone) {
      Task t=null;
      atomic {
        if (!tasks.todo.isEmpty()) {
          //grab segment from todo list
          t=workingtask=(Task) tasks.todo.pop();
          if(t!=null)
            t.setWorker(this);
        } else {
          //steal work from dead threads
          Worker[] threads=tasks.threads;
	  boolean shouldexit=true;
          for(int i=0;i<threads.length;i++) {
            Worker w=(Worker)threads[i];
	    if (w.workingtask!=null)
	      shouldexit=false;
            if (w.getStatus(i)==-1&&w.workingtask!=null) {
              //steal work from this thread
              t=workingtask=w.workingtask;
              w.workingtask=null;
	      t.setWorker(this);
              break;
            }
          }
	  if (shouldexit)
	    notdone=false;
        }
      }
      if (t!=null) {
        t.execution();
        continue;
      } else if (notdone) {
        //System.out.println("Not done");
      	sleep(500000);
      }
    }
    fi = System.currentTimeMillis();
    System.out.println("\n\nDone - Time Elapse : " + (double)((fi-st)/1000) +"\n\n");
    while(true) {
      sleep(100000);
    }
  }
  public static native void printRecoveryStat();
}
