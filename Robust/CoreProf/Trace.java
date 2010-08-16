import java.io.FileInputStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

public class Trace {

  // everything defined here should match coreprof.h
  // definitions exactly
  public static final int CP_EVENT_MASK           = 3;
  public static final int CP_EVENT_BASESHIFT      = 2;
                                                  
  public static final int CP_EVENTTYPE_BEGIN      = 0;
  public static final int CP_EVENTTYPE_END        = 1;
  public static final int CP_EVENTTYPE_ONEOFF     = 2;

  public static final int CP_EVENTID_MAIN         = 0;
  public static final int CP_EVENTID_RUNMALLOC    = 1;
  public static final int CP_EVENTID_RUNFREE      = 2;
  public static final int CP_EVENTID_TASKDISPATCH = 3;
  public static final int CP_EVENTID_TASKRETIRE   = 4;
  public static final int CP_EVENTID_TASKSTALLVAR = 5;
  public static final int CP_EVENTID_TASKSTALLMEM = 6;


  void initNames() {
    eid2name = new Hashtable<Integer, String>();
    eid2name.put( CP_EVENTID_MAIN,         "MAIN        " );
    eid2name.put( CP_EVENTID_RUNMALLOC,    "RUNMALLOC   " );
    eid2name.put( CP_EVENTID_RUNFREE,      "RUNFREE     " );
    eid2name.put( CP_EVENTID_TASKDISPATCH, "TASKDISPATCH" );
    eid2name.put( CP_EVENTID_TASKRETIRE,   "TASKRETIRE  " );
    eid2name.put( CP_EVENTID_TASKSTALLVAR, "TASKSTALLVAR" );
    eid2name.put( CP_EVENTID_TASKSTALLMEM, "TASKSTALLMEM" );
  }
  

  public static void main( String args[] ) {
    if( args.length != 1 ) {
      System.out.println( "usage: <coreprof.dat file>" );
      System.exit( 0 );
    }
    Trace t = new Trace( args[0] );    
    t.printStats();
  }


  // event IDs are a word, timestamps are long ints
  public static final int WORD_SIZE      = 4;
  public static final int EVENT_SIZE     = WORD_SIZE;
  public static final int TIMESTAMP_SIZE = WORD_SIZE*2;
  public static final int STACKMAX       = 512;

  int                           numThreads;
  BufferedInputStream[]         threadNum2stream;
  int[]                         threadNum2numWords;
  Event[][]                     threadNum2eventStack;
  Hashtable<Integer, Counter>[] threadNum2eid2c;
  Hashtable<Integer, String>    eid2name;


  public Trace( String filename ) {  
    openInputStreams( filename );
    initNames();
    readThreads();
  }


  protected void openInputStreams( String filename ) {

    BufferedInputStream bis    = null;
    int                 offset = 0;

    try {
      bis    = new BufferedInputStream( new FileInputStream( filename ) );
      offset = readHeader( bis );
      bis.close();
    } catch( Exception e ) {
      e.printStackTrace();
      System.exit( -1 );
    }

    threadNum2stream = new BufferedInputStream[numThreads];
    
    for( int i = 0; i < numThreads; ++i ) {
      try {

        // point a thread's event stream to the
        // beginning of its data within the input file
	threadNum2stream[i] = 
          new BufferedInputStream( new FileInputStream( filename ) );

	int skip = offset;
	while( skip > 0 ) {
	  skip -= threadNum2stream[i].skip( skip );
	}

	offset += WORD_SIZE*threadNum2numWords[i];

      } catch( Exception e ) {
	e.printStackTrace();
	System.exit( -1 );
      }
    }
  }


  int readHeader( BufferedInputStream bis ) {

    // check version
    int version = readInt( bis );
    if( version != 0 ) {
      throw new Error( "Unsupported Version" );
    }
    int offset = WORD_SIZE;
    
    // read number of threads
    numThreads = readInt( bis );
    offset += WORD_SIZE;

    // read number of words used for event, per thread
    threadNum2numWords   = new int[numThreads];
    threadNum2eventStack = new Event[numThreads][STACKMAX];
    for( int i = 0; i < numThreads; ++i ) {
      threadNum2numWords[i] = readInt( bis );
      offset += WORD_SIZE;
    }
    return offset;
  }


  public void readThreads() {
    // cannot have array of generics, so this line generates
    // a compiler warning, just grimace and move on  :oP
    threadNum2eid2c = new Hashtable[numThreads];

    for( int i = 0; i < numThreads; i++ ) {
      threadNum2eid2c[i] = new Hashtable<Integer, Counter>();
      readThread( i );
    }
  }

  
  public void readThread( int tNum ) {

    BufferedInputStream         stream   = threadNum2stream    [tNum];
    int                         numWords = threadNum2numWords  [tNum];
    Event[]                     stack    = threadNum2eventStack[tNum];
    Hashtable<Integer, Counter> eid2c    = threadNum2eid2c     [tNum];

    int  depth     = 0;
    long timeStamp = 0;
    int  i         = 0;

    while( i < numWords ) {
      
      int event = readInt ( stream );
      timeStamp = readLong( stream );
      i += 3;

      int eventType = event &  CP_EVENT_MASK;
      int eventID   = event >> CP_EVENT_BASESHIFT;

      switch( eventType ) {

        case CP_EVENTTYPE_BEGIN: {
          Counter counter = eid2c.get( eventID );
          if( counter == null ) {
            counter = new Counter();
            eid2c.put( eventID, counter );
          }
          counter.count++;
          if( stack[depth] == null ) {
            stack[depth] = new Event( timeStamp, eventID, counter );
          } else {
            stack[depth].timeStamp = timeStamp;
            stack[depth].eventID   = eventID;
            stack[depth].counter   = counter;
          }
          depth++;
          if( depth == STACKMAX ) {
            throw new Error( "Event stack overflow\n" );
          }
        } break;

        case CP_EVENTTYPE_END: {
          depth--;
          if( depth < 0 ) {
            throw new Error( "Event stack underflow\n" );
          }
          Event   e           = stack[depth];
          long    elapsedTime = timeStamp - e.timeStamp;
          Counter c           = e.counter;
          c.totalTime += elapsedTime;
          c.selfTime  += elapsedTime;
          if( depth - 1 >= 0 ) {
            Counter cParent = stack[depth-1].counter;
            cParent.selfTime -= elapsedTime;
          }
        } break;        
      }
    }

    
    if( depth != 0 ) {
      System.out.println( "Warning: unmatched event begin/end\n" );
      /*
      //get rid of last item also
	depth--;
	Event e=stack[depth];
	long elapsedtime=time-e.time;
	Counter c=e.counter;
	c.totaltime+=elapsedtime;
	c.selftime+=elapsedtime;
	if(depth-1>=0) {
	  Counter cn=stack[depth-1].counter;
	  cn.selftime-=elapsedtime;
          }
      */
    }
  }


  public static int readInt( InputStream is ) {
    try {
      int b1 = is.read();
      int b2 = is.read();
      int b3 = is.read();
      int b4 = is.read();

      int retval = (b4<<24)|(b3<<16)|(b2<<8)|b1;

      if( retval < 0 ) {
	throw new Error();
      }
      return retval;

    } catch( Exception e ) {
      throw new Error();
    }
  }


  public static long readLong( InputStream is ) {
    try {
      long b1 = is.read();
      long b2 = is.read();
      long b3 = is.read();
      long b4 = is.read();
      long b5 = is.read();
      long b6 = is.read();
      long b7 = is.read();
      long b8 = is.read();

      long retval = 
        (b8<<56)|(b7<<48)|(b6<<40)|(b5<<32)|
	(b4<<24)|(b3<<16)|(b2<< 8)|b1;
      
      if( retval < 0 ) {
        throw new Error();
      }
      return retval;
      
    } catch( Exception e ) {
      throw new Error();
    }
  }


  public void printStats() {
    
    for( int i = 0; i < numThreads; ++i ) {

      System.out.println( "Thread "+i );
      
      for( Iterator<Integer> evit = threadNum2eid2c[i].keySet().iterator();
           evit.hasNext();
           ) {
	Integer event     = evit.next();
	Counter c         = threadNum2eid2c[i].get( event );
	String  eventname = eid2name.containsKey( event ) ?
                            eid2name.get( event )         :
                            Integer.toString( event );

        float   tSelf_s   = new Long( c.selfTime  ).floatValue() / 1000000.0f;
        float   tTotal_s  = new Long( c.totalTime ).floatValue() / 1000000.0f;

	System.out.println( "Event: "+eventname+
                            " self time=" +tSelf_s+
                            " total time="+tTotal_s+
                            " count="+c.count
                            );
      }
      System.out.println("----------------------------------------------------");
    }
  }

}
