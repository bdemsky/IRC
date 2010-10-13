import java.io.*;
import java.util.*;

// This class holds all the necessary structures for
// processing one worker thread's event data within
// the coreprof master output file.

public class ThreadData {

  public BufferedInputStream   dataStream;
  public int                   numDataWords;
  public Vector<EventSummary>  rootEvents;
  public Vector<EventSummary>  eventStack;
  public int                   stackDepth;

  public ThreadData() {
    dataStream   = null;
    numDataWords = 0;
    rootEvents   = new Vector<EventSummary>( 20, 100 );
    eventStack   = new Vector<EventSummary>( 20, 100 );
  }
}
