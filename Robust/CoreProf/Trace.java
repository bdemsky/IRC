import java.io.FileInputStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

public class Trace {
  int numThreads;
  int[] eventCounts;
  Event[][] eventstack;
  Hashtable<Integer, Counter> getcounter[];
  Hashtable<Integer, String> eventnames=new Hashtable<Integer, String>();

  public static final int STACKMAX=512;


  void initNames() {
    eventnames.put(CP_MAIN,     "MAIN     ");
    eventnames.put(CP_RUNMALLOC,"RUNMALLOC");
    eventnames.put(CP_RUNFREE,  "RUNFREE  ");
  }

  public void printStats() {
    for(int i=0;i<numThreads;i++) {
      System.out.println("Thread "+i);
      for(Iterator<Integer> evit=getcounter[i].keySet().iterator();evit.hasNext();) {
	Integer event=evit.next();
	Counter c=getcounter[i].get(event);
	String eventname=eventnames.containsKey(event)?eventnames.get(event):Integer.toString(event);
	System.out.println("Event: "+eventname+" self time="+c.selftime+"  total time="+c.totaltime+" count="+c.count);
      }
      System.out.println("-------------------------------------------------------------");
    }
  }

  public static int readInt(InputStream is) {
    try {
      int b1=is.read();
      int b2=is.read();
      int b3=is.read();
      int b4=is.read();
      int retval=(b4<<24)|(b3<<16)|(b2<<8)|b1;
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
    long retval=(b8<<56)|(b7<<48)|(b6<<40)|(b5<<32)|
	(b4<<24)|(b3<<16)|(b2<<8)|b1;
    if (retval<0)
      throw new Error();
    return retval;
    } catch (Exception e) {
      throw new Error();
    }
  }

  int readHeader(BufferedInputStream bir) {
    //First check version
    int offset=4;
    int version=readInt(bir);
    if (version!=0)
      throw new Error("Unsupported Version");

    //Second read number of threads
    offset+=4;
    numThreads=readInt(bir);

    //Third read event counts for each Thread
    eventCounts=new int[numThreads];
    eventstack=new Event[numThreads][STACKMAX];
    for(int i=0;i<numThreads;i++) {
      eventCounts[i]=readInt(bir);
      offset+=4;
    }
    return offset;
  }

  BufferedInputStream threads[];

  public Trace(String filename) {
    initNames();
    BufferedInputStream bir=null;
    int offset=0;
    try {
      bir=new BufferedInputStream(new FileInputStream(filename));
      offset=readHeader(bir);
      bir.close();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
    threads=new BufferedInputStream[numThreads];
    getcounter=new Hashtable[numThreads];

    for(int i=0;i<numThreads;i++) {
      getcounter[i]=new Hashtable<Integer,Counter>();
      try {
	threads[i]=new BufferedInputStream(new FileInputStream(filename));
	int skip=offset;
	while (skip>0) {
	  skip-=threads[i].skip(skip);
	}
	offset+=4*eventCounts[i];
      } catch (Exception e) {
	e.printStackTrace();
	System.exit(-1);
      }
    }
  }

  public void readThreads() {
    for(int i=0;i<numThreads;i++)
      readThread(i);
  }
  
  public void readThread(int t) {
    BufferedInputStream bis=threads[t];
    int numevents=eventCounts[t];
    Event[] stack=eventstack[t];
    Hashtable<Integer, Counter> countertab=getcounter[t];
    int i=0;
    int depth=0;
    long time=0;
    while(i<numevents) {
      int event=readInt(bis);
      time=readLong(bis);
      int baseevent=event>>CP_BASE_SHIFT;
      i+=3; //time and event

      switch(event&CP_MASK) {
      case CP_BEGIN:
	enqueue(stack, depth, baseevent, time, countertab);
	depth++;
	break;
      case CP_END: {
	depth--;
	Event e=stack[depth];
	long elapsedtime=time-e.time;
	Counter c=e.counter;
	c.totaltime+=elapsedtime;
	c.selftime+=elapsedtime;
	if(depth-1>0) {
	  Counter cn=stack[depth-1].counter;
	  cn.selftime-=elapsedtime;
	}
	break;
      }
      case CP_EVENT: {
	Counter counter=countertab.get(event);
	if (counter==null) {
	  Counter c=new Counter();
	  countertab.put(event, c);
	  counter=c;
	}
	counter.count++;
	break;
      }
      }
    }
    if (depth!=0) {
      //get rid of last item also
	depth--;
	Event e=stack[depth];
	long elapsedtime=time-e.time;
	Counter c=e.counter;
	c.totaltime+=elapsedtime;
	c.selftime+=elapsedtime;
	if(depth-1>0) {
	  Counter cn=stack[depth-1].counter;
	  cn.selftime-=elapsedtime;
	}
    }
  }

  public static void enqueue(Event[] stack, int depth, int event, long time, Hashtable<Integer, Counter> getcounter) {
    Counter counter=getcounter.get(event);
    if (counter==null) {
      Counter c=new Counter();
      getcounter.put(event, c);
      counter=c;
    }
    counter.count++;
    if (stack[depth]==null) {
      stack[depth]=new Event(time,event);
      stack[depth].counter=counter;
    } else {
      stack[depth].time=time;
      stack[depth].event=event;
      stack[depth].counter=counter;
    }
  }

  public static final int CP_BEGIN=0;
  public static final int CP_END=1;
  public static final int CP_EVENT=2;
  public static final int CP_MASK=3;
  public static final int CP_BASE_SHIFT=2;

  public static final int CP_MAIN=0;
  public static final int CP_RUNMALLOC=1;
  public static final int CP_RUNFREE=2;
  public static void main(String x[]) {
    Trace t=new Trace(x[0]);
    t.readThreads();
    t.printStats();
  }
}
