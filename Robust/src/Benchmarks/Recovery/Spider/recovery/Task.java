public class Task {
  //Current worker thread
  Worker w;
  public Task() {}
  public void execute();
  public void setWorker(Worker w) {
    this.w = w;
  }
  public void dequeueTask() {
    w.workingtask=null;
  }
  public void enqueueTask(Task t) {
    w.tasks.todo.push(t);
  }
  public native void execution();
}
