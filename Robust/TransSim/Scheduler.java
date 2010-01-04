import java.util.HashSet;

public class Scheduler {
  Executor e;

  public Scheduler(Executor e, long time) {
    this.e=e;
    schedule=new int[e.numEvents()+1][e.numThreads()];
    turn=new int[e.numEvents()];
    //give last time an event can be scheduled
    lasttime=new long[e.maxEvents()][e.numThreads()];
    schedtime=new long[e.maxEvents()][e.numThreads()];
    lastrd=new long[e.numEvents()+1][e.numObjects()];
    lastwr=new long[e.numEvents()+1][e.numObjects()];
    shorttesttime=time;
    computeFinishing(time);
  }
  
  long currbest;
  long shorttesttime;
  int[][] schedule;
  int[] turn;
  long[][] lasttime;
  long[][] schedtime;
  long[][] lastrd;
  long[][] lastwr;

  public long getTime() {
    return currbest;
  }

  private void computeFinishing(long totaltime) {
    for(int threadnum=0;threadnum<e.numThreads();threadnum++) {
      ThreadClass thread=e.getThread(threadnum);
      long threadtime=totaltime;
      for(int transnum=thread.numTransactions()-1;transnum>=0;transnum--) {
	Transaction trans=thread.getTransaction(transnum);
	long ltime=trans.getTime(trans.numEvents()-1);
	threadtime-=ltime;
	lasttime[transnum][threadnum]=threadtime;
      }
    }
  }

  private boolean scheduleTask(int step, int iturn) {
    for(int i=0;i<e.numThreads();i++) {
      schedule[step+1][i]=schedule[step][i];
    }
    schedule[step+1][iturn]--;
    turn[step]=iturn;

    //compute start time
    ThreadClass thread=e.getThread(iturn);
    int transnum=schedule[0][iturn]-schedule[step][iturn];
    long starttime=0;
    if (transnum>0) {
      starttime=schedtime[transnum-1][iturn];
      Transaction prevtrans=thread.getTransaction(transnum-1);
      starttime+=prevtrans.getTime(prevtrans.numEvents()-1);
    }
    //Let's check for object conflicts that delay start time
    Transaction trans=thread.getTransaction(transnum);
    for(int ev=0;ev<trans.numEvents();ev++) {
      long evtime=trans.getTime(ev);
      int evobject=trans.getObject(ev);
      
      switch(trans.getEvent(ev)) {
      case Transaction.READ:
	{
	  //just need to check write time
	  long newstart=lastwr[step][evobject]-evtime;
	  if (newstart>starttime)
	    starttime=newstart;
	  break;
	}
      case Transaction.WRITE:
	{
	  //just need to check both write and read times
	  long newstart=lastwr[step][evobject]-evtime;
	  if (newstart>starttime)
	    starttime=newstart;

	  newstart=lastrd[step][evobject]-trans.getTime(trans.numEvents()-1);
	  if (newstart>starttime)
	    starttime=newstart;
	  break;
	}
      default:
      }
    } 
    //check to see if start time is okay
    if (starttime>lasttime[transnum][iturn])
      return false;

    //good to update schedule
    schedtime[transnum][iturn]=starttime;

    //copy read and write times forward
    for(int obj=0;obj<e.numObjects();obj++) {
      lastrd[step+1][obj]=lastrd[step][obj];
      lastwr[step+1][obj]=lastwr[step][obj];
    }
    
    long finishtime=starttime+trans.getTime(trans.numEvents()-1);
    
    //Update read and write times
    for(int ev=0;ev<trans.numEvents();ev++) {
      long evtime=trans.getTime(ev);
      int evobject=trans.getObject(ev);
      switch(trans.getEvent(ev)) {
      case Transaction.READ: {
	  //just need to check write time
	  if (finishtime>lastrd[step+1][evobject])
	    lastrd[step+1][evobject]=finishtime;
	  break;
	}
      case Transaction.WRITE: {
	  //just need to check both write and read times
	  if (finishtime>lastwr[step+1][evobject])
	    lastwr[step+1][evobject]=finishtime;
	  break;
	}
      default:
      }
    } 
    //    System.out.println("thread="+iturn+" trans="+transnum+" stime="+starttime+" ftime="+finishtime+" transtime="+trans.getTime(trans.numEvents()-1));
    return true;
  }


  public void dosim() {
    for(int i=0;i<e.numThreads();i++) {
      schedule[0][i]=e.getThread(i).numTransactions();
    }
    
    int lastEvent=e.numEvents()-1;

    //go forward
    for(int step=0;step<=lastEvent;step++) {
      int iturn=0;
      for(;iturn<e.numThreads();iturn++) {
	if (schedule[step][iturn]>0)
	  break;
      }

      //force blank transactions first
      for(int i=0;i<e.numThreads();i++) {
	if (schedule[step][i]>0) {
	  Transaction t=e.getThread(i).getTransaction(schedule[0][i]-schedule[step][i]);
	  if ((t.numEvents()==1)&&(t.getEvent(0)==Transaction.DELAY)) {
	    iturn=i;
	    break;
	  }
	}
      }

      boolean lgood=scheduleTask(step, iturn);
      
      if (step==lastEvent&&lgood) {
	long maxfinish=0;
	for(int i=0;i<e.numThreads();i++) {
	  int numTrans=e.getThread(i).numTransactions();
	  long startt=schedtime[numTrans-1][i];
	  Transaction lasttrans=e.getThread(i).getTransaction(numTrans-1);
	  long finisht=startt+lasttrans.getTime(lasttrans.numEvents()-1);
	  if (finisht>maxfinish)
	    maxfinish=finisht;
	}

	if (maxfinish<=shorttesttime) {
	  currbest=maxfinish;
	  shorttesttime=maxfinish;
	  computeFinishing(shorttesttime);
	}
      }
      
      if (!lgood||step==lastEvent) {
	//go backwards
	step--;
	for(;step>=0;step--) {
	  //check for delay transaction...just skip them
	  Transaction oldtrans=e.getThread(turn[step]).getTransaction(schedule[0][turn[step]]-schedule[step][turn[step]]);
	  if (oldtrans.numEvents()==1&&oldtrans.getEvent(0)==Transaction.DELAY)
	    continue;
	  
	  iturn=turn[step]+1;
	  for(;iturn<e.numThreads();iturn++) {
	    if (schedule[step][iturn]>0)
	      break;
	  }
	  if (iturn<e.numThreads()) {
	    //found something to iterate
	    if (scheduleTask(step, iturn))
	      break;
	  }
	}
	if (step<0)
	  return;
      }
    }
  }


  public static boolean checkConflicts(Transaction t1, Transaction t2) {
    HashSet writeset=new HashSet();
    HashSet readset=new HashSet();

    for(int i=0;i<t1.numEvents();i++) {
      int t1obj=t1.getObject(i);
      int t1evt=t1.getEvent(i);
      if (t1evt==Transaction.READ) {
	readset.add(new Integer(t1obj));
      } else if (t1evt==Transaction.WRITE) {
	writeset.add(new Integer(t1obj));      
      }
    }

    for(int i=0;i<t2.numEvents();i++) {
      int t2obj=t2.getObject(i);
      int t2evt=t2.getEvent(i);
      if (t2evt==Transaction.READ) {
	if (writeset.contains(new Integer(t2obj)))
	  return true;
      } else if (t2evt==Transaction.WRITE) {
	if (writeset.contains(new Integer(t2obj))||
	    readset.contains(new Integer(t2obj)))
	  return true;
      }
    }
    return false;
  }
}