import java.util.*;

public class FlexScheduler extends Thread {
  Executor e;
  int abortThreshold;
  int abortRatio;
  int deadlockcount;
  int checkdepth;
  int barriercount;

  public FlexScheduler(Executor e, int policy, int abortThreshold, int abortRatio, int checkdepth, Plot p) {
    this(e, policy, p);
    this.abortThreshold=abortThreshold;
    this.abortRatio=abortRatio;
    this.checkdepth=checkdepth;
  }

  public void run() {
    dosim();
  }

  public FlexScheduler(Executor e, int policy, Plot p) {
    this.e=e;
    barriercount=e.numThreads();
    aborted=new boolean[e.numThreads()];
    currentevents=new Event[e.numThreads()];
    rdobjmap=new Hashtable();
    wrobjmap=new Hashtable();
    this.policy=policy;
    r=new Random(100);
    eq=new PriorityQueue();
    backoff=new int[e.numThreads()];
    retrycount=new int[e.numThreads()];
    transferred=new int[e.numThreads()];
    objtoinfo=new Hashtable();
    threadinfo=new ThreadInfo[e.numThreads()];
    blocked=new boolean[e.numThreads()];

    for(int i=0;i<e.numThreads();i++) {
      backoff[i]=BACKOFFSTART;
      threadinfo[i]=new ThreadInfo(this);
    }
    this.p=p;
    if (p!=null) {
      serCommit=p.getSeries("COMMIT");
      serStart=p.getSeries("START");
      serAbort=p.getSeries("ABORT");
      serStall=p.getSeries("STALL");
      serWake=p.getSeries("WAKE");
      serAvoid=p.getSeries("AVOIDDEADLOCK");
    }
  }

  Plot p;
  Series serCommit;
  Series serStart;
  Series serAbort;
  Series serStall;
  Series serAvoid;
  Series serWake;

  public int getDeadLockCount() {
    return deadlockcount;
  }

  //Where to start the backoff delay at
  public static final int BACKOFFSTART=1;

  //Commit options
  public static final int LAZY=0;
  public static final int COMMIT=1;
  public static final int ATTACK=2;
  public static final int SUICIDE=3;
  public static final int TIMESTAMP=4;
  public static final int LOCK=5;
  public static final int LOCKCOMMIT=6;
  public static final int RANDOM=7;
  public static final int KARMA=8;
  public static final int POLITE=9;
  public static final int ERUPTION=10;
  public static final int THREAD=11;
  public static final int ATTACKTIME=12;
  public static final int ATTACKTHREAD=13;

  public static String getName(int policy) {
    switch (policy) {
    case LAZY:
      return new String("LAZY");
    case COMMIT:
      return new String("COMMIT");
    case ATTACK:
      return new String("ATTACK");
    case SUICIDE:
      return new String("SUICIDE");
    case TIMESTAMP:
      return new String("TIMESTAMP");
    case LOCK:
      return new String("LOCK");
    case LOCKCOMMIT:
      return new String("LOCKCOMMIT");
    case RANDOM:
      return new String("RANDOM");
    case KARMA:
      return new String("KARMA");
    case POLITE:
      return new String("POLITE");
    case ERUPTION:
      return new String("ERUPTION");
    case THREAD:
      return new String("THREAD");
    case ATTACKTIME:
      return new String("ATTACKTIME");
    case ATTACKTHREAD:
      return new String("ATTACKTHREAD");
    }
    return null;
  }

  PriorityQueue eq;
  int policy;
  boolean[] aborted;
  long shorttesttime;
  long starttime=-1;
  Hashtable rdobjmap;
  Hashtable wrobjmap;
  int abortcount;
  int commitcount;
  long backoffcycles;
  long stallcycles;
  long abortedcycles;
  Event[] currentevents;
  Random r;
  int[] backoff;
  int[] retrycount;
  int[] transferred;
  Hashtable objtoinfo;
  ThreadInfo[] threadinfo;
  
  boolean[] blocked;

  public boolean isEager() {
    return policy==ATTACK||policy==SUICIDE||policy==TIMESTAMP||policy==RANDOM||policy==KARMA||policy==POLITE||policy==ERUPTION||policy==THREAD||policy==ATTACKTIME||policy==ATTACKTHREAD;
  }

  public boolean countObjects() {
    return policy==KARMA||policy==ERUPTION;
  }

  public boolean isLock() {
    return policy==LOCK||policy==LOCKCOMMIT;
  }

  public int getAborts() {
    return abortcount;
  }

  public int getCommits() {
    return commitcount;
  }

  public long getTime() {
    return shorttesttime-starttime;
  }

  public long getStallTime() {
    return stallcycles;
  }

  public long getBackoffTime() {
    return backoffcycles;
  }

  public long getAbortedTime() {
    return abortedcycles;
  }

  //Computes wasted time
  public void timewasted(int currthread, long currtime) {
    Event e=currentevents[currthread];
    Transaction trans=e.getTransaction();
    int eIndex=e.getEvent();
    long eTime=e.getTime();
    long timeleft=eTime-currtime;
    long totaltime=0;
    for(int i=0;i<=eIndex;i++)
      totaltime+=trans.getTime(i);
    totaltime-=timeleft;//subtract off time to the next event
    abortedcycles+=totaltime;
  }

  //Aborts another thread...
  public void reschedule(int currthread, long currtime, long backofftime) {
    long time=currtime+backofftime;
    backoffcycles+=backofftime;
    currentevents[currthread].makeInvalid();
    if (threadinfo[currthread].isStalled()) {
      //remove from waiter list
      threadinfo[currthread].setStall(false);
      getmapping(threadinfo[currthread].getObjIndex()).getWaiters().remove(currentevents[currthread]);
    }
    if (serAbort!=null) {
      serAbort.addPoint(time, currthread);
    }
    Transaction trans=currentevents[currthread].getTransaction();
    
    releaseObjects(trans, currthread, time);
    Event nev=new Event(time+trans.getTime(0), trans, 0, currthread, currentevents[currthread].getTransNum());
    currentevents[currthread]=nev;
    eq.add(nev);
  }

  //Aborts another thread...
  public void stall(Event ev, long time, long delay) {
    stallcycles+=delay;
    ev.setTime(time+delay);
    eq.add(ev);
  }

  private void releaseObjects(Transaction trans, int currthread, long time) {
    //remove all events
    for(int i=0;i<trans.numEvents();i++) {
      ObjIndex object=trans.getObjIndex(i);

      if (object!=null&&rdobjmap.containsKey(object)) {
	((Set)rdobjmap.get(object)).remove(new Integer(currthread));
      }
      if (object!=null&&wrobjmap.containsKey(object)) {
	((Set)wrobjmap.get(object)).remove(new Integer(currthread));
      }
      if (object!=null&&objtoinfo.containsKey(object)) {
	ObjectInfo oi=(ObjectInfo)objtoinfo.get(object);
	if (oi.getOwner()==currentevents[currthread].getThread()) {
	  oi.releaseOwner();
	  
	  //wake up one waiter
	  for(Iterator waitit=oi.getWaiters().iterator();waitit.hasNext();) {
	    //requeue everyone who was waiting on us and start them back up
	    Event waiter=(Event)waitit.next();
	    waitit.remove();
	    waiter.setTime(time);
	    threadinfo[waiter.getThread()].setStall(false);
	    if (serWake!=null)
	      serWake.addPoint(time,waiter.getThread());
	    oi.setOwner(waiter.getThread());
	    eq.add(waiter);
	    break;
	  }
	}
      }
    }
  }

  /* Initializes things and returns number of transactions */
  public int startinitial() {
    int tcount=0;
    for(int i=0;i<e.numThreads();i++) {
      Transaction trans=e.getThread(i).getTransaction(0);
      long time=trans.getTime(0);
      Event ev=new Event(time, trans, 0, i, 0);
      currentevents[i]=ev;
      eq.add(ev);
      tcount+=e.getThread(i).numTransactions();
    }
    return tcount;
  }

  public void dosim() {
    long lasttime=0;
    //start first transactions
    int numtrans=startinitial();
    System.out.println("Number of transactions="+numtrans);
    int tcount=0;
    while(!eq.isEmpty()) {
      Event ev=(Event)eq.poll();
      if (!ev.isValid()) {
	continue;
      }

      Transaction trans=ev.getTransaction();

      int event=ev.getEvent();
      long currtime=ev.getTime();
      lasttime=currtime;
      if (trans.started&&starttime==-1)
	starttime=currtime;

      if (trans.numEvents()==(event+1)) {
	tryCommit(ev, trans);
	tcount++;
	if ((tcount%100000)==0)
	  System.out.println("Attempted "+tcount+"transactions "+policy);
      } else {
	enqueueEvent(ev, trans);
      }
    }
    shorttesttime=lasttime;
    if (p!=null)
      p.close();
  }

  private ObjectInfo getmapping(ObjIndex obj) {
    if (!objtoinfo.containsKey(obj))
      objtoinfo.put(obj, new ObjectInfo(this));
    return (ObjectInfo)objtoinfo.get(obj);
  }

  public void tryCommit(Event ev, Transaction trans) {
    //ready to commit this one
    long currtime=ev.getTime();
    releaseObjects(trans, ev.getThread(), currtime);
    
    //See if we have been flagged as aborted for the lazy case
    boolean abort=aborted[ev.getThread()];
    aborted[ev.getThread()]=false;
    if (!abort) {
      //if it is a transaction, increment commit count
      if (trans.numEvents()>1||trans.getEvent(0)!=Transaction.DELAY) {
	commitcount++;
	if (serCommit!=null) {
	  serCommit.addPoint(ev.getTime(),ev.getThread());
	}
      }
      //Reset our backoff counter
      threadinfo[ev.getThread()].priority=0;
      threadinfo[ev.getThread()].aborted=false;
      backoff[ev.getThread()]=BACKOFFSTART;
      retrycount[ev.getThread()]=0;
      transferred[ev.getThread()]=0;

      //abort the other threads
      for(int i=0;i<trans.numEvents();i++) {
	ObjIndex object=trans.getObjIndex(i);
	int op=trans.getEvent(i);
	//Mark commits to objects
	if (isLock()&&(op==Transaction.WRITE||op==Transaction.READ)) {
	  if (object==null) {
	    System.out.println(op);
	  }
	  getmapping(object).recordCommit();
	}
	//Check for threads we might cause to abort
	if (op==Transaction.WRITE) {
	  HashSet abortset=new HashSet();
	  if (rdobjmap.containsKey(object)) {
	    for(Iterator it=((Set)rdobjmap.get(object)).iterator();it.hasNext();) {
	      Integer threadid=(Integer)it.next();
	      abortset.add(threadid);
	      if (isLock()) {
		ObjectInfo oi=getmapping(object);
		oi.recordAbort();
	      }
	    }
	  }
	  if (wrobjmap.containsKey(object)) {
	    for(Iterator it=((Set)wrobjmap.get(object)).iterator();it.hasNext();) {
	      Integer threadid=(Integer)it.next();
	      abortset.add(threadid);
	      if (isLock()&&(!rdobjmap.containsKey(object)||!((Set)rdobjmap.get(object)).contains(threadid))) {
		//if this object hasn't already cause this thread to
		//abort, then flag it as an abort cause
		ObjectInfo oi=getmapping(object);
		oi.recordAbort();
	      }
	    }
	  }
	  for(Iterator abit=abortset.iterator();abit.hasNext();) {
	    Integer threadid=(Integer)abit.next();
	    if (policy==LAZY||policy==LOCK) {
	      //just flag to abort when it trie to commit
	      aborted[threadid]=true;
	      if (serAbort!=null)
		serAbort.addPoint(currtime, threadid);
	    } else if (policy==COMMIT||policy==LOCKCOMMIT) {
	      //abort it immediately
	      timewasted(threadid, currtime);
	      reschedule(threadid, currtime, 0);
	      abortcount++;
	    }
	  }
	}
      }
    } else {
      abortcount++;
      timewasted(ev.getThread(), currtime);
    }
    
    //add next transaction event...could be us if we aborted
    int nexttransnum=abort?ev.getTransNum():ev.getTransNum()+1;
    if (nexttransnum<e.getThread(ev.getThread()).numTransactions()) {
      Transaction nexttrans=e.getThread(ev.getThread()).getTransaction(nexttransnum);
      if (serStart!=null) {
	if (nexttrans.numEvents()>1||nexttrans.getEvent(0)!=Transaction.DELAY) {
	  serStart.addPoint(ev.getTime(),ev.getThread());
	}
      }

      Event nev=new Event(currtime+nexttrans.getTime(0), nexttrans, 0, ev.getThread(), nexttransnum);
      currentevents[ev.getThread()]=nev;
      eq.add(nev);
    }
  }

  public Set rdConflictSet(int thread, ObjIndex obj) {
    if (!wrobjmap.containsKey(obj))
      return null;
    HashSet conflictset=new HashSet();
    for(Iterator it=((Set)wrobjmap.get(obj)).iterator();it.hasNext();) {
      Integer threadid=(Integer)it.next();
      if (threadid.intValue()!=thread)
	conflictset.add(threadid);
    }
    if (conflictset.isEmpty())
      return null;
    else
      return conflictset;
  }

  public Set wrConflictSet(int thread, ObjIndex obj) {
    HashSet conflictset=new HashSet();
    if (rdobjmap.containsKey(obj)) {
      for(Iterator it=((Set)rdobjmap.get(obj)).iterator();it.hasNext();) {
	Integer threadid=(Integer)it.next();
	if (threadid.intValue()!=thread)
	  conflictset.add(threadid);
      }
    }
    for(Iterator it=((Set)wrobjmap.get(obj)).iterator();it.hasNext();) {
      Integer threadid=(Integer)it.next();
      if (threadid.intValue()!=thread)
	conflictset.add(threadid);
    }
    if (conflictset.isEmpty())
      return null;
    else
      return conflictset;
  }

  //Takes as parameter -- current transaction read event ev, conflicting
  //set of threads, and the current time
  //Returning false causes current transaction not continue to be scheduled


  public boolean handleConflicts(Event ev, Set threadstokill, long time) {
    if (policy==RANDOM) {
      boolean b=r.nextBoolean();
      if (b) {
	//delay
	int thread=ev.getThread();
	int dback=backoff[thread]*2;
	if (dback>0)
	  backoff[thread]=dback;
	stall(ev, time, r.nextInt(backoff[thread]));
	return false;
      } else {
	//abort other transactions
	for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	  Integer thread=(Integer)thit.next();
	  timewasted(thread, time);
	  reschedule(thread, time, 0);
	  abortcount++;
	}
	return true;
      }
    } else if (policy==KARMA) {
      int maxpriority=0;
      for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	Integer thread=(Integer)thit.next();
	if (threadinfo[thread].priority>maxpriority)
	  maxpriority=threadinfo[thread].priority;
      }
      if (maxpriority>(threadinfo[ev.getThread()].priority+retrycount[ev.getThread()])) {
	//stall for a little while
	threadinfo[ev.getThread()].priority--;
	retrycount[ev.getThread()]++;
	int rtime=r.nextInt(3000);
	stall(ev, time, rtime);
	return false;
      } else {
	//we win
	for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	  Integer thread=(Integer)thit.next();
	  int dback=backoff[thread]*2;
	  if (dback>0)
	    backoff[thread]=dback;
	  int atime=r.nextInt(backoff[thread]);
	  timewasted(thread, time);
	  reschedule(thread, time, atime);
	  abortcount++;
	}
	return true;
      }
    } else if (policy==ERUPTION) {
      int maxpriority=0;
      //abort other transactions
      for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	Integer thread=(Integer)thit.next();
	if (threadinfo[thread].priority>maxpriority)
	  maxpriority=threadinfo[thread].priority;
      }
      if (maxpriority>(threadinfo[ev.getThread()].priority+retrycount[ev.getThread()])) {
	//we lose
	threadinfo[ev.getThread()].priority--;
	//stall for a little while
	int rtime=r.nextInt(3000);
	stall(ev, time, rtime);
	int ourpriority=threadinfo[ev.getThread()].priority;
	ourpriority-=transferred[ev.getThread()];
	for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	  Integer thread=(Integer)thit.next();
	  threadinfo[thread].priority+=ourpriority;
	}
	transferred[ev.getThread()]=threadinfo[ev.getThread()].priority;
	retrycount[ev.getThread()]++;

	return false;
      } else {
	//we win
	for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	  Integer thread=(Integer)thit.next();
	  int dback=backoff[thread]*2;
	  if (dback>0)
	    backoff[thread]=dback;
	  int atime=r.nextInt(backoff[thread]);
	  timewasted(thread, time);
	  reschedule(thread, time, atime);
	  abortcount++;
	}
	return true;
      }
    } else if (policy==POLITE) {
      int retry=(++retrycount[ev.getThread()]);
      if (retry>=22) {
	retrycount[ev.getThread()]=0;
	for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	  Integer thread=(Integer)thit.next();
	  timewasted(thread, time);
	  reschedule(thread, time, 0);
	  abortcount++;
	}
	return true;
      } else {
	//otherwise stall
	int stalltime=(1<<(retry-1))*12;
	if (stalltime<0)
	  stalltime=1<<30;
	stall(ev, time, r.nextInt(stalltime));
	return false;
      }
    } else if (policy==ATTACK) {
      for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	Integer thread=(Integer)thit.next();
	timewasted(thread, time);
	reschedule(thread, time, r.nextInt(backoff[thread.intValue()]));
	int dback=backoff[thread.intValue()]*2;
	if (dback>0)
	  backoff[thread.intValue()]=dback;
	abortcount++;
      }
      return true;
    } else if (policy==SUICIDE) {
      timewasted(ev.getThread(), time);
      reschedule(ev.getThread(), time, r.nextInt(backoff[ev.getThread()]));
      int dback=backoff[ev.getThread()]*2;
      if (dback>0)
	backoff[ev.getThread()]=dback;
      abortcount++;
      return false;
    } else if (policy==TIMESTAMP) {
      long opponenttime=0;

      for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	Integer thread=(Integer)thit.next();
	Event other=currentevents[thread.intValue()];
	int eventnum=other.getEvent();
	long otime=other.getTransaction().getTime(other.getEvent());
	if (otime>opponenttime)
	  opponenttime=otime;
      }
      if (opponenttime>ev.getTransaction().getTime(ev.getEvent())) {
	//kill ourself
	timewasted(ev.getThread(), time);
	reschedule(ev.getThread(), time, 0);
	abortcount++;
	return false;
      } else {
	//kill the opponents
	for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	  Integer thread=(Integer)thit.next();
	  timewasted(thread, time);
	  reschedule(thread, time, 0);
	  abortcount++;
	}
	return true;	
      }
    } else if (policy==THREAD) {
      long tid=1000;

      for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	Integer thread=(Integer)thit.next();
	Event other=currentevents[thread.intValue()];
	int eventnum=other.getEvent();
	long otid=thread.intValue();
	if (tid>otid)
	  tid=otid;
      }
      if (ev.getThread()>tid) {
	//kill ourself
	timewasted(ev.getThread(), time);
	reschedule(ev.getThread(), time, 0);
	abortcount++;
	return false;
      } else {
	//kill the opponents
	for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	  Integer thread=(Integer)thit.next();
	  timewasted(thread, time);
	  reschedule(thread, time, 0);
	  abortcount++;
	}
	return true;	
      }
    } else if (policy==ATTACKTIME) {
      boolean timebased=false;
      int tev=ev.getThread();
      timebased|=threadinfo[tev].aborted;
      for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	Integer thread=(Integer)thit.next();
	timebased|=threadinfo[thread.intValue()].aborted;
      }
      if (timebased) {
	long opponenttime=0;
	
	for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	  Integer thread=(Integer)thit.next();
	  Event other=currentevents[thread.intValue()];
	  int eventnum=other.getEvent();
	  long otime=other.getTransaction().getTime(other.getEvent());
	  if (otime>opponenttime)
	    opponenttime=otime;
	}
	if (opponenttime>ev.getTransaction().getTime(ev.getEvent())) {
	  //kill ourself
	  timewasted(ev.getThread(), time);
	  reschedule(ev.getThread(), time, 0);
	  threadinfo[ev.getThread()].aborted=true;
	  abortcount++;
	  return false;
	} else {
	  //kill the opponents
	  for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	    Integer thread=(Integer)thit.next();
	    timewasted(thread, time);
	    reschedule(thread, time, 0);
	    threadinfo[thread.intValue()].aborted=true;
	    abortcount++;
	  }
	  return true;	
	}
      } else {
	for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	  Integer thread=(Integer)thit.next();
	  timewasted(thread, time);
	  reschedule(thread, time, 0);
	  threadinfo[thread.intValue()].aborted=true;
	  abortcount++;
	}
	return true;
      }
    } else if (policy==ATTACKTHREAD) {
      boolean threadbased=false;
      int tev=ev.getThread();
      threadbased|=threadinfo[tev].aborted;
      for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	Integer thread=(Integer)thit.next();
	threadbased|=threadinfo[thread.intValue()].aborted;
      }
      if (threadbased) {
	long opponentthr=1000;
	
	for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	  Integer thread=(Integer)thit.next();
	  Event other=currentevents[thread.intValue()];
	  int eventnum=other.getEvent();
	  long othr=thread.intValue();
	  if (othr<opponentthr)
	    opponentthr=othr;
	}
	if (opponentthr<tev) {
	  //kill ourself
	  timewasted(ev.getThread(), time);
	  reschedule(ev.getThread(), time, 0);
	  threadinfo[ev.getThread()].aborted=true;
	  abortcount++;
	  return false;
	} else {
	  //kill the opponents
	  for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	    Integer thread=(Integer)thit.next();
	    timewasted(thread, time);
	    reschedule(thread, time, 0);
	    threadinfo[thread.intValue()].aborted=true;
	    abortcount++;
	  }
	  return true;	
	}
      } else {
	for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	  Integer thread=(Integer)thit.next();
	  timewasted(thread, time);
	  reschedule(thread, time, 0);
	  threadinfo[thread.intValue()].aborted=true;
	  abortcount++;
	}
	return true;
      }
    }

    //Not eager
    return true;
  }

  //Handle current event (read, write, delay) in a transaction and
  //enqueue the next one

  public void enqueueEvent(Event ev, Transaction trans) {
    //just enqueue next event
    int event=ev.getEvent();
    long currtime=ev.getTime();
    ObjIndex object=trans.getObjIndex(event);
    int operation=trans.getEvent(event);

    if ((operation==Transaction.READ||operation==Transaction.WRITE)&&isLock()) {
      ObjectInfo oi=getmapping(object);
      
      if (oi.isRisky()) {
	if (oi.isOwned()&&oi.getOwner()!=ev.getThread()) {
	  //we're going to wait
	  boolean deadlocked=true;
	  ObjectInfo toi=oi;
	  for(int i=0;i<checkdepth;i++) {
	    //check if stalling would close the loop
	    if (toi.getOwner()==ev.getThread())
	      break;
	    //see if cycle is broken
	    if (!threadinfo[toi.getOwner()].isStalled()) {
	      deadlocked=false;
	      break;
	    }
	    //follow one more in depth
	    toi=getmapping(threadinfo[toi.getOwner()].getObjIndex());
	  }
	  
	  if (!deadlocked) {
	    //don't wait on stalled threads, we could deadlock
	    threadinfo[ev.getThread()].setStall(true);
	    threadinfo[ev.getThread()].setObjIndex(object);
	    if (serStall!=null)
	      serStall.addPoint(ev.getTime(),ev.getThread());
	    oi.addWaiter(ev);
	    return;
	  } else {
	    if (serAvoid!=null)
	      serAvoid.addPoint(ev.getTime(),ev.getThread());
	    deadlockcount++;
	  }
	} else {
	  //we have object
	  oi.setOwner(ev.getThread());
	}
      }
    }
    
    //process the current event
    if (operation==Transaction.READ) {
      //record read event
      if (!rdobjmap.containsKey(object))
	rdobjmap.put(object,new HashSet());
      if (((Set)rdobjmap.get(object)).add(new Integer(ev.getThread()))) {
	//added new object
	if (countObjects()) {
	  threadinfo[ev.getThread()].priority++;
	}
      }
      if (isEager()) {
	//do eager contention management
	Set conflicts=rdConflictSet(ev.getThread(), object);
	if (conflicts!=null) {
	  if (!handleConflicts(ev, conflicts, currtime)) {
	    ((Set)rdobjmap.get(object)).remove(new Integer(ev.getThread()));
	    return;
	  }
	}
      }
    } else if (operation==Transaction.WRITE) {
      //record write event
      if (!wrobjmap.containsKey(object))
	wrobjmap.put(object,new HashSet());
      if (((Set)wrobjmap.get(object)).add(new Integer(ev.getThread()))) {
	if (countObjects()) {
	  threadinfo[ev.getThread()].priority++;
	}
      }
      if (isEager()) {
	Set conflicts=wrConflictSet(ev.getThread(), object);
	if (conflicts!=null) {
	  if (!handleConflicts(ev, conflicts, currtime)) {
	    ((Set)wrobjmap.get(object)).remove(new Integer(ev.getThread()));
	    return;
	  }
	}
      }
    } else if (operation==Transaction.BARRIER) {
      barriercount--;
      if (barriercount==0) {
	for(int i=0;i<e.numThreads();i++) {
	  //enqueue the next event
	  Event bev=currentevents[i];
	  int bevent=bev.getEvent();
	  long bcurrtime=bev.getTime();
	  Transaction btrans=bev.getTransaction();
	  long deltatime=btrans.getTime(bevent+1)-btrans.getTime(bevent);
	  Event nev=new Event(deltatime+currtime, btrans, bevent+1, bev.getThread(), bev.getTransNum());
	  currentevents[bev.getThread()]=nev;
	  eq.add(nev);
	}
	barriercount=e.numThreads();
      } else {
	//Do nothing
	//wait until all threads in barrier
      }
      return;
    }
    retrycount[ev.getThread()]=0;
    transferred[ev.getThread()]=0;
    //enqueue the next event
    long deltatime=trans.getTime(event+1)-trans.getTime(event);
    Event nev=new Event(deltatime+currtime, trans, event+1, ev.getThread(), ev.getTransNum());
    currentevents[ev.getThread()]=nev;
    eq.add(nev);
  }

  
  class Event implements Comparable {
    boolean valid;
    long time;
    int num;
    Transaction t;
    int threadid;
    int transnum;

    public void makeInvalid() {
      valid=false;
    }

    public boolean isValid() {
      return valid;
    }

    public int getTransNum() {
      return transnum;
    }

    public Transaction getTransaction() {
      return t;
    }

    public int getEvent() {
      return num;
    }

    public long getTime() {
      return time;
    }
    
    public void setTime(long time) {
      this.time=time;
    }

    public int getThread() {
      return threadid;
    }

    public Event(long time, Transaction t, int num, int threadid, int transnum) {
      this.time=time;
      this.t=t;
      this.num=num;
      this.threadid=threadid;
      this.transnum=transnum;
      valid=true;
    }

    //break ties to allow commits to occur earliest
    public int compareTo(Object o) {
      Event e=(Event)o;
      long delta=time-e.time;
      if (delta!=0) {
	if (delta>0)
	  return 1;
	else
	  return -1;
      }
      if (((getEvent()+1)==getTransaction().numEvents())&&
	  (e.getEvent()+1)!=e.getTransaction().numEvents())
	return -1;
      if (((getEvent()+1)!=getTransaction().numEvents())&&
	  (e.getEvent()+1)==e.getTransaction().numEvents())
	return 1;
      return 0;
    }
  }
}