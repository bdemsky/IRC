import java.util.*;

public class FlexScheduler {
  Executor e;
  int abortThreshold;
  int abortRatio;
  int deadlockcount;
  int checkdepth;

  public FlexScheduler(Executor e, int policy, int abortThreshold, int abortRatio, int checkdepth) {
    this(e, policy);
    this.abortThreshold=abortThreshold;
    this.abortRatio=abortRatio;
    this.checkdepth=checkdepth;
  }
  
  public FlexScheduler(Executor e, int policy) {
    this.e=e;
    aborted=new boolean[e.numThreads()];
    currentevents=new Event[e.numThreads()];
    rdobjmap=new Hashtable();
    wrobjmap=new Hashtable();
    this.policy=policy;
    r=new Random(100);
    eq=new PriorityQueue();
    backoff=new int[e.numThreads()];
    objtoinfo=new Hashtable();
    threadinfo=new ThreadInfo[e.numThreads()];
    blocked=new boolean[e.numThreads()];

    for(int i=0;i<e.numThreads();i++) {
      backoff[i]=BACKOFFSTART;
      threadinfo[i]=new ThreadInfo(this);
    }
  }

  public int getDeadLockCount() {
    return deadlockcount;
  }

  //Where to start the backoff delay at
  public static final int BACKOFFSTART=1;

  //Commit options
  public static final int LAZY=0;
  public static final int COMMIT=1;
  public static final int ATTACK=2;
  public static final int POLITE=3;
  public static final int KARMA=4;
  public static final int LOCK=5;
  public static final int LOCKCOMMIT=6;

  PriorityQueue eq;
  int policy;
  boolean[] aborted;
  int shorttesttime;
  Hashtable rdobjmap;
  Hashtable wrobjmap;
  int abortcount;
  int commitcount;
  Event[] currentevents;
  Random r;
  int[] backoff;
  Hashtable objtoinfo;
  ThreadInfo[] threadinfo;
  
  boolean[] blocked;

  public boolean isEager() {
    return policy==ATTACK||policy==POLITE||policy==KARMA;
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

  public int getTime() {
    return shorttesttime;
  }

  //Aborts another thread...
  public void reschedule(int currthread, int time) {
    currentevents[currthread].makeInvalid();
    if (threadinfo[currthread].isStalled()) {
      //remove from waiter list
      threadinfo[currthread].setStall(false);
      getmapping(threadinfo[currthread].getObject()).getWaiters().remove(currentevents[currthread]);
    }
    
    Transaction trans=currentevents[currthread].getTransaction();
    
    releaseObjects(trans, currthread, time);
    Event nev=new Event(time+trans.getTime(0), trans, 0, currthread, currentevents[currthread].getTransNum());
    currentevents[currthread]=nev;
    eq.add(nev);
  }


  private void releaseObjects(Transaction trans, int currthread, int time) {
    //remove all events
    for(int i=0;i<trans.numEvents();i++) {
      int object=trans.getObject(i);
      Integer obj=new Integer(object);
      if (object!=-1&&rdobjmap.containsKey(obj)) {
	((Set)rdobjmap.get(obj)).remove(new Integer(currthread));
      }
      if (object!=-1&&wrobjmap.containsKey(obj)) {
	((Set)wrobjmap.get(obj)).remove(new Integer(currthread));
      }
      if (object!=-1&&objtoinfo.containsKey(obj)) {
	ObjectInfo oi=(ObjectInfo)objtoinfo.get(obj);
	if (oi.getOwner()==currentevents[currthread].getThread()) {
	  oi.releaseOwner();
	  
	  //wake up one waiter
	  for(Iterator waitit=oi.getWaiters().iterator();waitit.hasNext();) {
	    //requeue everyone who was waiting on us and start them back up
	    Event waiter=(Event)waitit.next();
	    waitit.remove();
	    waiter.setTime(time);
	    threadinfo[waiter.getThread()].setStall(false);
	    oi.setOwner(waiter.getThread());
	    eq.add(waiter);
	    break;
	  }
	}
      }
    }
  }

  public void startinitial() {
    for(int i=0;i<e.numThreads();i++) {
      Transaction trans=e.getThread(i).getTransaction(0);
      int time=trans.getTime(0);
      Event ev=new Event(time, trans, 0, i, 0);
      currentevents[i]=ev;
      eq.add(ev);
    }
  }

  public void dosim() {
    int lasttime=0;
    //start first transactions
    startinitial();

    while(!eq.isEmpty()) {
      Event ev=(Event)eq.poll();
      if (!ev.isValid()) {
	continue;
      }

      Transaction trans=ev.getTransaction();
      int event=ev.getEvent();
      int currtime=ev.getTime();
      lasttime=currtime;

      if (trans.numEvents()==(event+1)) {
	tryCommit(ev, trans);
      } else {
	enqueueEvent(ev, trans);
      }
    }
    shorttesttime=lasttime;
  }

  private ObjectInfo getmapping(int object) {
    Integer obj=new Integer(object);
    if (!objtoinfo.containsKey(obj))
      objtoinfo.put(obj, new ObjectInfo(this));
    return (ObjectInfo)objtoinfo.get(obj);
  }

  public void tryCommit(Event ev, Transaction trans) {
    //ready to commit this one
    int currtime=ev.getTime();
    releaseObjects(trans, ev.getThread(), currtime);
    
    //See if we have been flagged as aborted for the lazy case
    boolean abort=aborted[ev.getThread()];
    aborted[ev.getThread()]=false;
    if (!abort) {
      //if it is a transaction, increment comit count
      if (trans.numEvents()>1||trans.getEvent(0)!=Transaction.DELAY) {
	commitcount++;
      }
      //Reset our backoff counter
      backoff[ev.getThread()]=BACKOFFSTART;

      //abort the other threads
      for(int i=0;i<trans.numEvents();i++) {
	int object=trans.getObject(i);
	int op=trans.getEvent(i);
	//Mark commits to objects
	if (isLock()&&(op==Transaction.WRITE||op==Transaction.READ)) {
	  getmapping(object).recordCommit();
	}
	//Check for threads we might cause to abort
	if (op==Transaction.WRITE) {
	  HashSet abortset=new HashSet();
	  Integer obj=new Integer(object);
	  if (rdobjmap.containsKey(obj)) {
	    for(Iterator it=((Set)rdobjmap.get(obj)).iterator();it.hasNext();) {
	      Integer threadid=(Integer)it.next();
	      abortset.add(threadid);
	      if (isLock()) {
		ObjectInfo oi=getmapping(object);
		oi.recordAbort();
	      }
	    }
	  }
	  if (wrobjmap.containsKey(obj)) {
	    for(Iterator it=((Set)wrobjmap.get(obj)).iterator();it.hasNext();) {
	      Integer threadid=(Integer)it.next();
	      abortset.add(threadid);
	      if (isLock()&&(!rdobjmap.containsKey(obj)||!((Set)rdobjmap.get(obj)).contains(threadid))) {
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
	    } else if (policy==COMMIT||policy==LOCKCOMMIT) {
	      //abort it immediately
	      reschedule(threadid, currtime);
	      abortcount++;
	    }
	  }
	}
      }
    } else {
      abortcount++;
    }
    
    //add next transaction event...could be us if we aborted
    int nexttransnum=abort?ev.getTransNum():ev.getTransNum()+1;
    if (nexttransnum<e.getThread(ev.getThread()).numTransactions()) {
      Transaction nexttrans=e.getThread(ev.getThread()).getTransaction(nexttransnum);
      Event nev=new Event(currtime+nexttrans.getTime(0), nexttrans, 0, ev.getThread(), nexttransnum);
      currentevents[ev.getThread()]=nev;
      eq.add(nev);
    }
  }

  public Set rdConflictSet(int thread, int object) {
    Integer obj=new Integer(object);
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

  public Set wrConflictSet(int thread, int object) {
    Integer obj=new Integer(object);

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

  public boolean handleConflicts(Event ev, Set threadstokill, int time) {
    if (policy==ATTACK) {
      for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	Integer thread=(Integer)thit.next();
	reschedule(thread, time+r.nextInt(backoff[thread.intValue()]));
	backoff[thread.intValue()]*=2;
	abortcount++;
      }
      return true;
    } else if (policy==POLITE) {
      reschedule(ev.getThread(), time+r.nextInt(backoff[ev.getThread()]));
      backoff[ev.getThread()]*=2;
      abortcount++;
      return false;
    } else if (policy==KARMA) {
      int opponenttime=0;

      for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	Integer thread=(Integer)thit.next();
	Event other=currentevents[thread.intValue()];
	int eventnum=other.getEvent();
	int otime=other.getTransaction().getTime(other.getEvent());
	if (otime>opponenttime)
	  opponenttime=otime;
      }
      if (opponenttime>ev.getTransaction().getTime(ev.getEvent())) {
	//kill ourself
	reschedule(ev.getThread(), time+r.nextInt(backoff[ev.getThread()]));
	abortcount++;
	return false;
      } else {
	//kill the opponents
	for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	  Integer thread=(Integer)thit.next();
	  reschedule(thread, time+r.nextInt(backoff[thread.intValue()]));
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
    int currtime=ev.getTime();
    int object=trans.getObject(event);
    int operation=trans.getEvent(event);
    Integer obj=new Integer(object);

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
	    toi=getmapping(threadinfo[toi.getOwner()].getObject());
	  }
	  
	  if (!deadlocked) {
	    //don't wait on stalled threads, we could deadlock
	    threadinfo[ev.getThread()].setStall(true);
	    threadinfo[ev.getThread()].setObject(object);
	    oi.addWaiter(ev);
	    return;
	  } else
	    deadlockcount++;
	} else {
	  //we have object
	  oi.setOwner(ev.getThread());
	}
      }
    }
    
    //process the current event
    if (operation==Transaction.READ) {
      //record read event
      if (!rdobjmap.containsKey(obj))
	rdobjmap.put(obj,new HashSet());
      ((Set)rdobjmap.get(obj)).add(new Integer(ev.getThread()));
      if (isEager()) {
	//do eager contention management
	Set conflicts=rdConflictSet(ev.getThread(), object);
	if (conflicts!=null) {
	  if (!handleConflicts(ev, conflicts, currtime))
	    return;
	}
      }
    } else if (operation==Transaction.WRITE) {
      //record write event
      if (!wrobjmap.containsKey(obj))
	wrobjmap.put(obj,new HashSet());
      ((Set)wrobjmap.get(obj)).add(new Integer(ev.getThread()));
      if (isEager()) {
	Set conflicts=wrConflictSet(ev.getThread(), object);
	if (conflicts!=null) {
	  if (!handleConflicts(ev, conflicts, currtime))
	    return;
	}
      }
    }
    
    //enqueue the next event
    int deltatime=trans.getTime(event+1)-trans.getTime(event);
    Event nev=new Event(deltatime+currtime, trans, event+1, ev.getThread(), ev.getTransNum());
    currentevents[ev.getThread()]=nev;
    eq.add(nev);
  }

  
  class Event implements Comparable {
    boolean valid;
    int time;
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

    public int getTime() {
      return time;
    }
    
    public void setTime(int time) {
      this.time=time;
    }

    public int getThread() {
      return threadid;
    }

    public Event(int time, Transaction t, int num, int threadid, int transnum) {
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
      int delta=time-e.time;
      if (delta!=0)
	return delta;
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