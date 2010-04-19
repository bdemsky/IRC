public class Task {
  //Current worker thread
  Worker w;
  int queueid;
  public Task() {}
  public void execute();
  public void setWorker(Worker w, int queueid) {
    this.w = w;
    this.queueid = queueid;
  }
  public void dequeueTask() {
    w.workingtask=null;
  }
  public void enqueueTask(Task t) {
    //System.out.println("queueid= " + queueid);
    w.tasks.todo[queueid].push(t);
  }
  public native void execution();
}
