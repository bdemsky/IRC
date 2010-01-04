import java.util.Random;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.util.Vector;

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

  public static int readInt(InputStream is) {
    try {
    int b1=is.read();
    int b2=is.read();
    int b3=is.read();
    int b4=is.read();
    int retval=(b1<<24)|(b2<<16)|(b3<<8)|b4;
    if (retval<0)
      throw new Error();
    return retval;
    } catch (Exception e) {
      throw new Error();
    }
  }

  public static long readLong(InputStream is) {
    try {
    long b1=is.read();
    long b2=is.read();
    long b3=is.read();
    long b4=is.read();
    long b5=is.read();
    long b6=is.read();
    long b7=is.read();
    long b8=is.read();
    long retval=(b1<<56)|(b2<<48)|(b3<<40)|(b4<<32)|
      (b5<<24)|(b6<<16)|(b7<<8)|b8;
    if (retval<0)
      throw new Error();
    return retval;
    } catch (Exception e) {
      throw new Error();
    }
  }

  public Executor(String filename) {
    BufferedInputStream bir;
    try {
      bir=new BufferedInputStream(new FileInputStream(filename));
    } catch (Exception e) {
      throw new Error();
    }
    numThreads=readInt(bir);
    threads=new ThreadClass[numThreads];
    long earliest=-1;
    for(int i=0;i<numThreads;i++) {
      threads[i]=readThread(bir);
      long inittime=threads[i].trans[0].getTime(0);
      if (earliest==-1||earliest>inittime) {
	earliest=inittime;
      }
    }
    for(int i=0;i<numThreads;i++) {
      long inittime=threads[i].trans[0].getTime(0);
      assert(threads[i].trans[0].numEvents()==1);
      threads[i].trans[0].setTime(0, inittime-earliest);
    }
  }

  public static final int EV_THREAD=0;
  public static final int EV_ENTERBARRIER=1;
  public static final int EV_READ=2;
  public static final int EV_WRITE=3;
  public static final int EV_START=4;
  public static final int EV_COMMIT=5;
  public static final int EV_ABORT=6;
  public static final int EV_ARRAYREAD=7;
  public static final int EV_ARRAYWRITE=8;
  public static final int EV_EXITBARRIER=9;

  private Transaction createTransaction(Vector<TEvent> v) {
    Transaction t=new Transaction(v.size());
    for(int i=0;i<v.size();i++) {
      TEvent e=v.get(i);
      t.setTime(i,e.time);
      t.setEvent(i,e.type);
      t.setObject(i,e.oid);
      t.setIndex(i,e.index);
    }
    return t;
  }
  
  private ThreadClass readThread(InputStream is) {
    int numEvents=readInt(is);
    Vector<Transaction> transactions=new Vector<Transaction>();
    Vector<TEvent> currtrans=null;
    long starttime=-1;
    long lasttime=0;
    long firsttime=-1;
    for(int i=0;i<numEvents;i++) {
      int eventType=readInt(is);
      int object=-1;
      int index=-1;
      switch(eventType) {
      case EV_READ:
      case EV_WRITE:
	object=readInt(is);
	break;
      case EV_ARRAYREAD:
      case EV_ARRAYWRITE:
	object=readInt(is);
	index=readInt(is);
	break;
      default:
	break;
      }
      long time=readLong(is);
      //create dummy first transaction
      if (firsttime==-1) {
	Transaction t=new Transaction(1);
	t.setTime(0, time);
	t.setObject(0, -1);
	t.setIndex(0, -1);
	transactions.add(t);
	firsttime=time;
      }
      //have read all data in
      switch(eventType) {
      case EV_START:
	starttime=time;
	currtrans=new Vector<TEvent>();
	if (lasttime!=-1) {
	  Transaction t=new Transaction(1);
	  t.setEvent(0, Transaction.DELAY);
	  t.setTime(0, time-lasttime);
	  t.setObject(0, -1);
	  t.setIndex(0, -1);
	  transactions.add(t);
	}
	break;
      case EV_READ:
      case EV_ARRAYREAD: {
	long delta=time-starttime;
	TEvent ev=new TEvent(Transaction.READ, delta, object, index);
	currtrans.add(ev);
      }
	break;
      case EV_WRITE:
      case EV_ARRAYWRITE: {
	long delta=time-starttime;
	TEvent ev=new TEvent(Transaction.WRITE, delta, object, index);
	currtrans.add(ev);
      }
	break;
      case EV_COMMIT: {
	long delta=time-starttime;
	TEvent ev=new TEvent(Transaction.DELAY, delta);
	currtrans.add(ev);
	lasttime=time;
	transactions.add(createTransaction(currtrans));
      }
	break;
      case EV_ABORT:
	//No need to generate new delay transaction
	lasttime=-1;
	break;
      case EV_THREAD:
	//No need to do anything
	break;
      case EV_ENTERBARRIER: {
	//Barrier
	if (lasttime!=-1) {
	  Transaction t=new Transaction(1);
	  t.setEvent(0, Transaction.DELAY);
	  t.setTime(0, time-lasttime);
	  t.setObject(0, -1);
	  t.setIndex(0, -1);
	  transactions.add(t);
	}
	transactions.add(Transaction.getBarrier());
      }
	break;
      case EV_EXITBARRIER: {
	//Barrier
	lasttime=time;
      }
	break;
      }
    }
    ThreadClass tc=new ThreadClass(transactions.size());
    for(int i=0;i<transactions.size();i++) {
      tc.setTransaction(i,transactions.get(i));
    }
    return tc;
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
