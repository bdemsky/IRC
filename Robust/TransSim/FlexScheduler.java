import java.util.*;

public class FlexScheduler {
  Executor e;
  
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
    for(int i=0;i<e.numThreads();i++) {
      backoff[i]=1;
    }
  }

  public static final int LAZY=0;
  public static final int COMMIT=1;
  public static final int ATTACK=2;
  public static final int POLITE=3;
  public static final int KARMA=4;

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

  public boolean isEager() {
    return policy==ATTACK||policy==POLITE||policy==KARMA;
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

  public void reschedule(int currthread, int time) {
    currentevents[currthread].makeInvalid();
    Transaction trans=currentevents[currthread].getTransaction();

    //remove all events
    for(int i=0;i<trans.numEvents();i++) {
      int object=trans.getObject(i);
      if (object!=-1&&rdobjmap.containsKey(new Integer(object))) {
	((Set)rdobjmap.get(new Integer(object))).remove(new Integer(currthread));
      }
      if (object!=-1&&wrobjmap.containsKey(new Integer(object))) {
	((Set)wrobjmap.get(new Integer(object))).remove(new Integer(currthread));
      }
    }
    
    Event nev=new Event(time+trans.getTime(0), trans, 0, currthread, currentevents[currthread].getTransNum());
    currentevents[currthread]=nev;
    eq.add(nev);
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
      if (!ev.isValid())
	continue;

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


  public void tryCommit(Event ev, Transaction trans) {
    //ready to commit this one
    int currtime=ev.getTime();

    //Remove everything we put in object sets
    for(int i=0;i<trans.numEvents();i++) {
      int object=trans.getObject(i);
      if (object!=-1&&rdobjmap.containsKey(new Integer(object))) {
	((Set)rdobjmap.get(new Integer(object))).remove(new Integer(ev.getThread()));
      }
      if (object!=-1&&wrobjmap.containsKey(new Integer(object))) {
	((Set)wrobjmap.get(new Integer(object))).remove(new Integer(ev.getThread()));
      }
    }
    
    //See if we have been flagged as aborted
    boolean abort=aborted[ev.getThread()];
    aborted[ev.getThread()]=false;
    if (!abort) {
      if (trans.numEvents()>1||trans.getEvent(0)!=Transaction.DELAY) {
	commitcount++;
      }
      backoff[ev.getThread()]=1;
      //abort the other threads
      for(int i=0;i<trans.numEvents();i++) {
	int object=trans.getObject(i);
	int op=trans.getEvent(i);
	if (op==Transaction.WRITE) {
	  HashSet abortset=new HashSet();
	  if (rdobjmap.containsKey(new Integer(object))) {
	    for(Iterator it=((Set)rdobjmap.get(new Integer(object))).iterator();it.hasNext();) {
	      Integer threadid=(Integer)it.next();
	      abortset.add(threadid);
	    }
	  }
	  if (wrobjmap.containsKey(new Integer(object))) {
	    for(Iterator it=((Set)wrobjmap.get(new Integer(object))).iterator();it.hasNext();) {
	      Integer threadid=(Integer)it.next();
	      abortset.add(threadid);
	    }
	  }
	  for(Iterator abit=abortset.iterator();abit.hasNext();) {
	    Integer threadid=(Integer)abit.next();
	    if (policy==LAZY) {
	      aborted[threadid]=true;
	    } else if (policy==COMMIT) {
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
    if (!rdobjmap.containsKey(obj))
      return null;
    HashSet conflictset=new HashSet();
    for(Iterator it=((Set)rdobjmap.get(obj)).iterator();it.hasNext();) {
      Integer threadid=(Integer)it.next();
      if (threadid.intValue()!=thread)
	conflictset.add(threadid);
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
	backoff[ev.getThread()]*=2;
	abortcount++;
	return false;
      } else {
	//kill the opponents
	for(Iterator thit=threadstokill.iterator();thit.hasNext();) {
	  Integer thread=(Integer)thit.next();
	  reschedule(thread, time+r.nextInt(backoff[thread.intValue()]));
	  backoff[thread.intValue()]*=2;
	  abortcount++;
	}
	return true;	
      }
    }

    //Not eager
    return true;
  }

  public void enqueueEvent(Event ev, Transaction trans) {
    //just enqueue next event
    int event=ev.getEvent();
    int currtime=ev.getTime();
    int object=trans.getObject(event);
    int operation=trans.getEvent(event);
    //process the current event
    if (operation==Transaction.READ) {
      Integer obj=new Integer(object);
      if (!rdobjmap.containsKey(obj))
	rdobjmap.put(obj,new HashSet());
      ((Set)rdobjmap.get(obj)).add(new Integer(ev.getThread()));
      if (isEager()) {
	Set conflicts=rdConflictSet(ev.getThread(), object);
	if (conflicts!=null)
	  if (!handleConflicts(ev, conflicts, currtime))
	    return;
      }
    } else if (operation==Transaction.WRITE) {
      Integer obj=new Integer(object);
      if (!wrobjmap.containsKey(obj))
	wrobjmap.put(obj,new HashSet());
      ((Set)wrobjmap.get(obj)).add(new Integer(ev.getThread()));
      if (isEager()) {
	Set conflicts=wrConflictSet(ev.getThread(), object);
	if (conflicts!=null)
	  if (!handleConflicts(ev, conflicts, currtime))
	    return;
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