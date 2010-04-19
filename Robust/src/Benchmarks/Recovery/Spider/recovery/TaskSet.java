public class TaskSet {
  public TaskSet(int nt) {
    numthreads=nt;
    threads=global new Worker[nt];
    todo=global new GlobalQueue[(nt/2)];
  }

  //Tasks to be executed
  GlobalQueue[] todo;
  //Vector of worker threads
  Worker threads[];
  int numthreads;
}
