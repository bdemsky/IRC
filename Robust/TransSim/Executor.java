import java.util.Random;

public class Executor {
  int numThreads;
  int numTrans;
  int deltaTrans;
  int numObjects;
  int numAccesses;
  int deltaAccesses;
  int delay;
  int deltaDelay;
  int nonTrans;
  int deltaNonTrans;
  int readPercent;
  Random r;
  ThreadClass[] threads;
  int splitobjects;
  int splitaccesses;
  int readPercentSecond;

  public String toString() {
    String s="";
    for(int i=0;i<numThreads;i++)
      s+=threads[i].toString();

    return s;
  }

  public Executor(int numThreads, int numTrans, int deltaTrans, int numObjects, int numAccesses, int deltaAccesses, int readPercent, int delay, int deltaDelay, int nonTrans, int deltaNonTrans) {
    this(numThreads, numTrans, deltaTrans, numObjects, numAccesses, deltaAccesses, readPercent, delay, deltaDelay, nonTrans, deltaNonTrans, 100, 100, 0);
  }

  public Executor(int numThreads, int numTrans, int deltaTrans, int numObjects, int numAccesses, int deltaAccesses, int readPercent, int delay, int deltaDelay, int nonTrans, int deltaNonTrans, int splitobjects, int splitaccesses, int readPercentSecond) {
    this.numThreads=numThreads;
    this.numTrans=numTrans;
    this.deltaTrans=deltaTrans;
    this.numObjects=numObjects;
    this.numAccesses=numAccesses;
    this.deltaAccesses=deltaAccesses;
    this.readPercent=readPercent;
    this.delay=delay;
    this.deltaDelay=deltaDelay;
    this.nonTrans=nonTrans;
    this.deltaNonTrans=deltaNonTrans;
    this.splitobjects=splitobjects;
    this.splitaccesses=splitaccesses;
    this.readPercentSecond=readPercentSecond;
    r=new Random();
    threads=new ThreadClass[numThreads];
    generateThreads();
  }

  public int maxTime() {
    int maxtime=0;
    for(int i=0;i<numThreads;i++) {
      int time=0;
      for(int j=0;j<getThread(i).numTransactions();j++) {
	Transaction trans=getThread(i).getTransaction(j);
	time+=trans.getTime(trans.numEvents()-1);
      }
      if (time>maxtime)
	maxtime=time;
    }
    return maxtime;
  }

  public int numObjects() {
    return numObjects;
  }

  public int numThreads() {
    return numThreads;
  }

  public int numEvents() {
    int events=0;
    for(int i=0;i<numThreads();i++) {
      events+=getThread(i).numTransactions();
    }
    return events;
  }

  public int maxEvents() {
    int events=0;
    for(int i=0;i<numThreads();i++) {
      if (events<getThread(i).numTransactions())
	events=getThread(i).numTransactions();
    }
    return events;
  }

  public ThreadClass getThread(int i) {
    return threads[i];
  }

  private int getRandom(int base, int delta) {
    return base+delta-r.nextInt(2*delta+1);
  }
  
  public void generateThreads() {
    for(int i=0;i<numThreads;i++) {
      threads[i]=generateThread();
    }
  }

  private Transaction generateTransaction() {
    int accesses=getRandom(numAccesses, deltaAccesses);
    Transaction t=new Transaction(accesses);
    int time=0;
    int splitpoint=(numObjects*splitobjects)/100;
    for(int i=0;i<(accesses-1); i++) {
      if (r.nextInt(100)<splitaccesses) {
	boolean isRead=r.nextInt(100)<readPercent;
	time+=getRandom(delay, deltaDelay);
	int object=r.nextInt(splitpoint);
	t.setObject(i, object);
	t.setTime(i, time);
	if (isRead)
	  t.setEvent(i, Transaction.READ);
	else
	  t.setEvent(i, Transaction.WRITE);
      } else {
	boolean isRead=r.nextInt(100)<readPercentSecond;
	time+=getRandom(delay, deltaDelay);
	int object=r.nextInt(numObjects-splitpoint)+splitpoint;
	t.setObject(i, object);
	t.setTime(i, time);
	if (isRead)
	  t.setEvent(i, Transaction.READ);
	else
	  t.setEvent(i, Transaction.WRITE);
      }
    }
    t.setEvent(accesses-1, Transaction.DELAY);
    t.setObject(accesses-1, Transaction.DELAY);
    time+=getRandom(delay, deltaDelay);
    t.setTime(accesses-1, time);

    return t;
  }

  private ThreadClass generateThread() {
    int numTransactions=getRandom(numTrans, deltaTrans);
    ThreadClass t=new ThreadClass(numTransactions*2);
    for(int i=0;i<numTransactions;i++) {
      Transaction trans=generateTransaction();
      t.setTransaction(i*2, trans);
      Transaction transdelay=new Transaction(1);
      transdelay.setObject(0,Transaction.DELAY);
      transdelay.setEvent(0,Transaction.DELAY);
      transdelay.setTime(0, getRandom(nonTrans, deltaNonTrans));
      t.setTransaction(i*2+1, transdelay);
    }
    return t;
  }
}
