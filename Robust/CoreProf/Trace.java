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
  public static final int CP_EVENTID_POOLALLOC        = 0x14;
  public static final int CP_EVENTID_COUNT_POOLALLOC  = 0x15;
  public static final int CP_EVENTID_COUNT_POOLREUSE  = 0x16;
  public static final int CP_EVENTID_WORKSCHEDGRAB    = 0x20;
  public static final int CP_EVENTID_WORKSCHEDSUBMIT  = 0x21;
  public static final int CP_EVENTID_TASKDISPATCH     = 0x30;
  public static final int CP_EVENTID_PREPAREMEMQ      = 0x31;
  public static final int CP_EVENTID_TASKEXECUTE      = 0x40;
  public static final int CP_EVENTID_TASKRETIRE       = 0x50;
  public static final int CP_EVENTID_TASKSTALLVAR     = 0x60;
  public static final int CP_EVENTID_TASKSTALLMEM     = 0x61;
  public static final int CP_EVENTID_RCR_TRAVERSE     = 0x80;
  public static final int CP_EVENTID_DEBUG_A          = 0x180;
  public static final int CP_EVENTID_DEBUG_B          = 0x181;
  public static final int CP_EVENTID_DEBUG_C          = 0x182;
  public static final int CP_EVENTID_DEBUG_D          = 0x183;
  public static final int CP_EVENTID_DEBUG_E          = 0x184;
  public static final int CP_EVENTID_DEBUG_F          = 0x185;
  public static final int CP_EVENTID_DEBUG_G          = 0x186;
  public static final int CP_EVENTID_DEBUG_H          = 0x187;
  public static final int CP_EVENTID_DEBUG_I          = 0x188;
  public static final int CP_EVENTID_DEBUG_J          = 0x189;


  void initNames() {
    eid2name = new Hashtable<Integer, String>();
    eid2name.put( CP_EVENTID_MAIN,              "MAIN           " );
    eid2name.put( CP_EVENTID_RUNMALLOC,         "RUNMALLOC      " );
    eid2name.put( CP_EVENTID_RUNFREE,           "RUNFREE        " );
    eid2name.put( CP_EVENTID_POOLALLOC,         "POOLALLOC      " );
    eid2name.put( CP_EVENTID_COUNT_POOLALLOC,   "POOLALLOC CNT  " );
    eid2name.put( CP_EVENTID_COUNT_POOLREUSE,   "POOLREUSE CNT  " );
    eid2name.put( CP_EVENTID_WORKSCHEDGRAB,     "WORKSCHEDGRAB  " );
    eid2name.put( CP_EVENTID_WORKSCHEDSUBMIT,   "WORKSCHEDSUBMIT" );
    eid2name.put( CP_EVENTID_TASKDISPATCH,      "TASKDISPATCH   " );
    eid2name.put( CP_EVENTID_PREPAREMEMQ,       "PREPAREMEMQ    " );
    eid2name.put( CP_EVENTID_TASKEXECUTE,       "TASKEXECUTE    " );
    eid2name.put( CP_EVENTID_TASKRETIRE,        "TASKRETIRE     " );
    eid2name.put( CP_EVENTID_TASKSTALLVAR,      "TASKSTALLVAR   " );
    eid2name.put( CP_EVENTID_TASKSTALLMEM,      "TASKSTALLMEM   " );
    eid2name.put( CP_EVENTID_RCR_TRAVERSE,      "RCR TRAVERSE   " );
    eid2name.put( CP_EVENTID_DEBUG_A,           "DEBUG A        " );
    eid2name.put( CP_EVENTID_DEBUG_B,           "DEBUG B        " );
    eid2name.put( CP_EVENTID_DEBUG_C,           "DEBUG C        " );
    eid2name.put( CP_EVENTID_DEBUG_D,           "DEBUG D        " );
    eid2name.put( CP_EVENTID_DEBUG_E,           "DEBUG E        " );
    eid2name.put( CP_EVENTID_DEBUG_F,           "DEBUG F        " );
    eid2name.put( CP_EVENTID_DEBUG_G,           "DEBUG G        " );
    eid2name.put( CP_EVENTID_DEBUG_H,           "DEBUG H        " );
    eid2name.put( CP_EVENTID_DEBUG_I,           "DEBUG I        " );
    eid2name.put( CP_EVENTID_DEBUG_J,           "DEBUG J        " );
  }

  Hashtable<Integer, String> eid2name;
  


  public static void main( String args[] ) {
    if( args.length < 2 ) {
      System.out.println( "usage: <coreprof.dat file> <trace out file> [-2txt]\n"+
                          "\nThe -2txt option will take the raw binary events and spit\n"+
                          "out every event as text in events.txt, useful for debugging\n"+
                          "event mis-matches." );
      System.exit( 0 );
    }
    String inputfile=null;
    String outputfile=null;
    boolean events=false;
    HashSet<Integer> eventset=new HashSet<Integer>();
    boolean hasinput=false;
    long maxtime=-1;
    long mintime=0;
    long scale=1;

    for(int i=0; i<args.length; i++) {
      if (args[i].equals("-2txt")) {
	events=true;
      } else if (args[i].equals("-event")) {
	eventset.add(Integer.parseInt(args[i+1]));
	i++;
      } else if (args[i].equals("-maxtime")) {
	maxtime=Long.parseLong(args[i+1]);
	i++;
      } else if (args[i].equals("-mintime")) {
	mintime=Long.parseLong(args[i+1]);
	i++;
      } else if (args[i].equals("-scale")) {
	scale=Long.parseLong(args[i+1]);
	i++;
      } else {
	if (hasinput) {
	  outputfile=args[i];
	} else {
	  hasinput=true;
	  inputfile=args[i];
	}
      }
    }

    Trace t = new Trace(events, inputfile, outputfile, eventset, mintime, maxtime,scale);
  }



  // event IDs are a word, timestamps are long ints
  public static final int WORD_SIZE      = 4;
  public static final int EVENT_SIZE     = WORD_SIZE;
  public static final int TIMESTAMP_SIZE = WORD_SIZE*2;

  int          numThreads;
  ThreadData[] threadData;
  HashSet<Integer> eventset;

  boolean        convert2txt;
  BufferedWriter txtStream;

  boolean             convert2plot;
  BufferedWriter      bwPlot;
  long                tSysZero;
  long maxtime;
  long mintime;
  long scale;

  public Trace( boolean c2txt, String inFile, String outFile,  HashSet<Integer> eventset, long mintime, long maxtime, long scale) {
    this.eventset=eventset;
    this.maxtime=maxtime;
    this.mintime=mintime;
    this.scale=scale;
    convert2txt  = c2txt;
    convert2plot = true;

    openInputStreams( inFile );

    initNames();

    if (convert2plot) {
      printPlotCmd();
    }

    for( int i = 0; i < numThreads; i++ ) {
      readThread( i );
    }

    printStats( outFile );

    if( convert2plot ) {
      try {
	bwPlot.write("plot [0:"+(globendtime/scale)+"] [-1:"+(numThreads+1)+"] -3\n");
	bwPlot.close();
      } catch (IOException e) {
	e.printStackTrace();
      }
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
	threadData[i].dataStream.mark(20);
	int eventRaw = readInt ( threadData[i].dataStream );
	long Stamp    = readLong( threadData[i].dataStream );
	threadData[i].dataStream.reset();
	
	if (globalstarttime==-1||globalstarttime>Stamp)
	  globalstarttime=Stamp;


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

    if( convert2txt ) {
      try {
        txtStream = new BufferedWriter( new FileWriter( "events.txt" ) );

        txtStream.write( "\n\n\n\n" );
        txtStream.write( "*************************************************\n" );
        txtStream.write( "**  Thread "+tNum+"\n" );
        txtStream.write( "*************************************************\n" );
      } catch( IOException e ) {
        e.printStackTrace();
        System.exit( -1 );
      }
    }


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
    
    long lasttime=-1;
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

      if (eventset.isEmpty()||eventset.contains(eventID))
	switch( eventType ) {

        case CP_EVENTTYPE_BEGIN: {
          if( eventID == CP_EVENTID_MAIN ) {
            tSysZero = timeStamp;
          }
	  if( tdata.stackDepth>0 && convert2plot ) {
	    EventSummary esParent = tdata.eventStack.get( tdata.stackDepth - 1 );
	    long starttime=lasttime>esParent.timeStampBeginLatestInstance?lasttime:esParent.timeStampBeginLatestInstance;
	    addPointToPlot( tNum, esParent.eventID, starttime, timeStamp);
	  }

          pushEvent( tdata, eventID, timeStamp );          
        } break;

        case CP_EVENTTYPE_END: {
	  if( convert2plot ) {
	    EventSummary esParent = tdata.eventStack.get( tdata.stackDepth - 1 );
	    long starttime=lasttime>esParent.timeStampBeginLatestInstance?lasttime:esParent.timeStampBeginLatestInstance;

	    addPointToPlot( tNum, esParent.eventID, starttime, timeStamp);
	    lasttime=timeStamp;
	  }
          popEvent( tdata, eventID, timeStamp );
        } break;    
	}
    }

    System.out.println( "" );

    while( tdata.stackDepth > 0 ) {
      // worker threads currently do not exit gracefully, and therefore
      // may not register END events, so supply them with whatever the
      // latest known timestamp is
      EventSummary eventSummary = tdata.eventStack.get( tdata.stackDepth);

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


    // print to the event text file (if needed) before
    // updating the stack depth to get tabs right
    if( convert2txt ) {
      try {
        for( int tabs = 0; tabs < tdata.stackDepth; ++tabs ) {
          txtStream.write( "  " );
        }
        txtStream.write( "begin "+getEventName( eventID )+"@"+timeStamp+"\n" );
      } catch( IOException e ) {
        e.printStackTrace();
        System.exit( -1 );
      }
    }


    tdata.stackDepth++;
  }


  protected void popEvent( ThreadData tdata,
                           int        eventID,
                           long       timeStamp ) {
    tdata.stackDepth--;


    // print to the event text file (if needed) after
    // updating the stack depth to get tabs right
    if( convert2txt ) {
      try {
        for( int tabs = 0; tabs < tdata.stackDepth; ++tabs ) {
          txtStream.write( "  " );
        }
        txtStream.write( "end   "+getEventName( eventID )+"@"+timeStamp+"\n" );
      } catch( IOException e ) {
        e.printStackTrace();
        System.exit( -1 );
      }
    }


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

  long globalstarttime=-1;
  long globendtime=-1;

  public void addPointToPlot( int thread, int eventID, long starttime, long endtime) {
    try {
      long nstart=starttime-globalstarttime-mintime;
      long nend=endtime-globalstarttime-mintime;
      if ((maxtime==-1 || (endtime-globalstarttime)<maxtime)&&(nend>0)) {
	if (nend>globendtime)
	  globendtime=nend;
	if (nstart<0)
	  nstart=0;
	bwPlot.write( "set arrow from "+(nstart/scale)+","+thread+
                      " to "+(nend/scale)+","+thread+" lt palette cb "+eventID+" nohead\n");
      }
    } catch( IOException e ) {
      e.printStackTrace();
      System.exit( -1 );
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
    } catch( IOException e ) {
      e.printStackTrace();
      System.exit( -1 );
    }
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
      String.format( " total(ticks)=%12dK, %%self=%5.1f, count=%d, avgTicks=%d",
                     es.totalTime_ticks/1000,
                     tSelf_perc,
                     es.instanceCount,
                     (int)((float)es.totalTime_ticks/(float)es.instanceCount) );

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


  public void printPlotCmd() {
    try {
      bwPlot = 
        new BufferedWriter( new FileWriter( "plot.cmd" ) );
    } catch( IOException e ) {
      e.printStackTrace();
      System.exit( -1 );
    }    
  }
}
