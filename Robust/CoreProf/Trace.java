import java.io.*;
import java.util.*;

public class Trace {

  // everything defined here should match coreprof.h
  // definitions exactly
  public static final int CP_EVENT_MASK           = 3;
  public static final int CP_EVENT_BASESHIFT      = 8;
                                                  
  public static final int CP_EVENTTYPE_BEGIN      = 1;
  public static final int CP_EVENTTYPE_END        = 2;
  public static final int CP_EVENTTYPE_ONEOFF     = 3;

  public static final int CP_EVENTID_MAIN             = 0x04;
  public static final int CP_EVENTID_RUNMALLOC        = 0x10;
  public static final int CP_EVENTID_RUNFREE          = 0x11;
  public static final int CP_EVENTID_COUNT_POOLALLOC  = 0x15;
  public static final int CP_EVENTID_COUNT_POOLREUSE  = 0x16;
  public static final int CP_EVENTID_WORKSCHEDGRAB    = 0x20;
  public static final int CP_EVENTID_TASKDISPATCH     = 0x30;
  public static final int CP_EVENTID_TASKEXECUTE      = 0x31;
  public static final int CP_EVENTID_TASKRETIRE       = 0x32;
  public static final int CP_EVENTID_TASKSTALLVAR     = 0x40;
  public static final int CP_EVENTID_TASKSTALLMEM     = 0x41;


  void initNames() {
    eid2name = new Hashtable<Integer, String>();
    eid2name.put( CP_EVENTID_MAIN,          "MAIN         " );
    eid2name.put( CP_EVENTID_RUNMALLOC,     "RUNMALLOC    " );
    eid2name.put( CP_EVENTID_RUNFREE,       "RUNFREE      " );
    eid2name.put( CP_EVENTID_WORKSCHEDGRAB, "WORKSCHEDGRAB" );
    eid2name.put( CP_EVENTID_TASKDISPATCH,  "TASKDISPATCH " );
    eid2name.put( CP_EVENTID_TASKEXECUTE,   "TASKEXECUTE  " );
    eid2name.put( CP_EVENTID_TASKRETIRE,    "TASKRETIRE   " );
    eid2name.put( CP_EVENTID_TASKSTALLVAR,  "TASKSTALLVAR " );
    eid2name.put( CP_EVENTID_TASKSTALLMEM,  "TASKSTALLMEM " );
  }

  Hashtable<Integer, String> eid2name;
  


  public static void main( String args[] ) {
    if( args.length != 2 ) {
      System.out.println( "usage: <coreprof.dat file> <trace out file>" );
      System.exit( 0 );
    }
    Trace t = new Trace( args[0], args[1] );
  }



  // event IDs are a word, timestamps are long ints
  public static final int WORD_SIZE      = 4;
  public static final int EVENT_SIZE     = WORD_SIZE;
  public static final int TIMESTAMP_SIZE = WORD_SIZE*2;

  int          numThreads;
  ThreadData[] threadData;



  public Trace( String inFile, String outFile ) {

    openInputStreams( inFile );

    initNames();

    for( int i = 0; i < numThreads; i++ ) {
      readThread( i );
    }

    printStats( outFile );
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

    for( int i = 0; i < numThreads; ++i ) {
      try {
        // point a thread's event stream to the
        // beginning of its data within the input file
	threadData[i].dataStream = 
          new BufferedInputStream( new FileInputStream( filename ) );

	int skip = offset;
	while( skip > 0 ) {
	  skip -= threadData[i].dataStream.skip( skip );
	}

	offset += WORD_SIZE*threadData[i].numDataWords;

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

    threadData = new ThreadData[numThreads];

    // read number of words used for all events, per thread
    for( int i = 0; i < numThreads; ++i ) {
      threadData[i] = new ThreadData();
      threadData[i].numDataWords = readInt( bis );
      offset += WORD_SIZE;
    }
    return offset;
  }

  
  public void readThread( int tNum ) {

    System.out.print( "Reading thread "+tNum );

    ThreadData tdata = threadData[tNum];
    tdata.stackDepth = 0;
    long timeStamp   = 0;
    int  i           = 0;
    int numProgress  = 10;

    int progressChunk = tdata.numDataWords / numProgress;
    int j;
    boolean[] progress = new boolean[numProgress];
    for( j = 0; j < numProgress; ++j ) {
      progress[j] = false;
    }
    j = 0;
    
    while( i < tdata.numDataWords ) {
      
      if( !progress[j] && i > j*progressChunk ) {
        System.out.print( "." );
        progress[j] = true;
        if( j < numProgress - 1 ) {
          ++j;
        }
      }

      int eventRaw = readInt ( tdata.dataStream );
      timeStamp    = readLong( tdata.dataStream );
      i += 3;

      int eventType = eventRaw &  CP_EVENT_MASK;
      int eventID   = eventRaw >> CP_EVENT_BASESHIFT;

      switch( eventType ) {

        case CP_EVENTTYPE_BEGIN: {
          pushEvent( tdata, eventID, timeStamp );
        } break;

        case CP_EVENTTYPE_END: {
          popEvent( tdata, eventID, timeStamp );
        } break;    
    
      }
    }

    System.out.println( "" );

    while( tdata.stackDepth > 0 ) {
      // worker threads currently do not exit gracefully, and therefore
      // may not register END events, so supply them with whatever the
      // latest known timestamp is
      EventSummary eventSummary = tdata.eventStack.get( tdata.stackDepth );

      if( eventSummary == null ) {
        // if there is no previous event it means there are no children
        // events with a timestamp for the workaround, so just punt
        break;
      }

      popEvent( tdata, eventSummary.eventID, timeStamp );

      --tdata.stackDepth;
    }
  }


  protected void pushEvent( ThreadData tdata,
                            int        eventID,
                            long       timeStamp ) {

    EventSummary eventSummary = null;

    if( tdata.stackDepth == 0 ) {
      // there are no parents, so look in the rootEvents
      // for an existing EventSummary of this type
      for( Iterator<EventSummary> itr = tdata.rootEvents.iterator();
           itr.hasNext();
           ) {
        EventSummary es = itr.next();
        if( es.eventID == eventID ) {
          eventSummary = es;
          break;
        }
      }
      if( eventSummary == null ) {
        // there is no summary for this event type yet,
        // so add it
        eventSummary = new EventSummary( eventID );
        tdata.rootEvents.add( eventSummary );
      }

    } else {
      // look through the parent's children for an existing
      // EventSummary of this type
      EventSummary esParent = tdata.eventStack.get( tdata.stackDepth - 1 );
      for( Iterator<EventSummary> itr = esParent.children.iterator();
           itr.hasNext();
           ) {
        EventSummary es = itr.next();
        if( es.eventID == eventID ) {
          eventSummary = es;
          break;
        }
      }
      if( eventSummary == null ) {
        // there is no summary for this event type yet,
        // under this parent, so add it
        eventSummary = new EventSummary( eventID );
        esParent.children.add( eventSummary );
        eventSummary.parent = esParent;
      }
    }

    eventSummary.timeStampBeginLatestInstance = timeStamp;
    
    eventSummary.instanceCount++;

    if( tdata.eventStack.size() <= tdata.stackDepth ) {
      tdata.eventStack.setSize( 2*tdata.stackDepth + 20 );
    }

    tdata.eventStack.set( tdata.stackDepth, eventSummary );

    tdata.stackDepth++;
  }


  protected void popEvent( ThreadData tdata,
                           int        eventID,
                           long       timeStamp ) {
    tdata.stackDepth--;
    if( tdata.stackDepth < 0 ) {
      throw new Error( "Event stack underflow\n" );
    }

    EventSummary eventSummary = tdata.eventStack.get( tdata.stackDepth );
    assert eventSummary != null;

    if( eventSummary.eventID != eventID ) {
      System.out.println( "Warning: event begin("+
                          getEventName( eventSummary.eventID )+
                          ") end("+
                          getEventName( eventID )+
                          ") mismatch!\n" );
    }

    long elapsedTime = 
      timeStamp - eventSummary.timeStampBeginLatestInstance;

    eventSummary.totalTime_ticks += elapsedTime;
    eventSummary.selfTime_ticks  += elapsedTime;
    
    if( tdata.stackDepth - 1 >= 0 ) {
      EventSummary esParent = tdata.eventStack.get( tdata.stackDepth-1 );
      esParent.selfTime_ticks -= elapsedTime;
    }
  }



  public void printStats( String filename ) {

    System.out.println( "Printing..." );

    try {
      BufferedWriter bw = 
        new BufferedWriter( new FileWriter( filename ) );
      
      for( int i = 0; i < numThreads; ++i ) {

        ThreadData tdata = threadData[i];

        bw.write( "----------------------------------\n" );
        bw.write( "Thread "+i+"\n" );
      
        for( Iterator<EventSummary> itr = tdata.rootEvents.iterator();
             itr.hasNext();
             ) {
          EventSummary es = itr.next();
          printEventSummary( bw, es, 0 );
        }

        bw.write( "\n" );
      }

      bw.close();

    } catch( IOException e ) {}
  }


  public String getEventName( int eventID ) {
    return
      eid2name.containsKey( eventID ) ?
      eid2name.get        ( eventID ) :
      Integer.toString    ( eventID );
  }
  

  public void printEventSummary( BufferedWriter bw,
                                 EventSummary   es, 
                                 int            depth ) 
    throws IOException {

    String strIndent = "";
    for( int i = 0; i < depth; ++i ) {
      strIndent += "--";
    }

    String strEventName = getEventName( es.eventID );
        
    float tOfParent_perc;
    String strPercParent = "";
    if( es.parent != null ) {
      float divisor = new Long( es.parent.totalTime_ticks ).floatValue();
      if( divisor <= 0.00001f ) {
        divisor = 0.00001f;
      }

      tOfParent_perc =
        100.0f *
        new Long( es.totalTime_ticks        ).floatValue() /
        divisor;
      
      strPercParent = String.format( " %%ofParent=%5.1f",
                                     tOfParent_perc );
    }
    
    float tSelf_perc = 
      100.0f *
      new Long( es.selfTime_ticks  ).floatValue() /
      new Long( es.totalTime_ticks ).floatValue();      

    String strSelfStats =
      String.format( " total(ticks)=%12dK, %%self=%5.1f, count=%d",
                     es.totalTime_ticks/1000,
                     tSelf_perc,
                     es.instanceCount );

    bw.write( strIndent+
              strEventName+
              strPercParent+
              strSelfStats+
              "\n" );

    for( Iterator<EventSummary> itr = es.children.iterator();
         itr.hasNext();
         ) {
      EventSummary esChild = itr.next();
      printEventSummary( bw, esChild, depth + 1 );
    }    
  }
}
