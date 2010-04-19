public class Worker extends Thread {
  int id;
  TaskSet tasks;
  Task workingtask;
  int numQueue;

  Worker(TaskSet tasks, int id, int numQueue) {
    this.tasks = tasks;
    this.id = id;
    this.numQueue = 3; // Correct this 3 should be hash defined
  }
  
  public void run() {
    long st = System.currentTimeMillis();
    long fi = 0;
    boolean notdone=true;
    while(notdone) {
      Task t=null;
      atomic {
        System.out.println("Transacion 1");
        int qindex = (id%numQueue);
        //System.out.println("id= " + id + " numQueue= " + numQueue);
        if (!tasks.todo[qindex].isEmpty()) {
          //grab segment from todo list
          t=workingtask=(Task) tasks.todo[qindex].pop();
          if(t!=null)
            t.setWorker(this, qindex);
        } else {
          int newqindex = qindex;
          boolean skipvisit = false;
          for(int queuecount=1;queuecount < numQueue;queuecount++) {
            newqindex = ((newqindex+1)%numQueue);
            if (!tasks.todo[newqindex].isEmpty()) {
              //grab segment from another todo list
              t=workingtask=(Task) tasks.todo[newqindex].pop();
              if(t!=null) {
                t.setWorker(this, qindex);
                skipvisit = true;
                break;
              }
            }
          }
          if(!skipvisit) {
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
                t.setWorker(this, qindex);
                break;
              }
            }
            if (shouldexit)
              notdone=false;
          }
        }
      }
      if (t!=null) {
        t.execution();
        continue;
      } else if (notdone) {
        //System.out.println("Not done");
        sleep(10000);
      }
    }
    fi = System.currentTimeMillis();
    System.out.println("\n\nDone - Time Elapse : " + (double)((fi-st)/1000) +"\n\n");
    RecoveryStat.printRecoveryStat();
    while(true) {
      sleep(100000);
    }
  }
  public static native void printRecoveryStat();
}
