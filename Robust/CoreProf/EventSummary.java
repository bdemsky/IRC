import java.util.*;

// This class summarizes a group of events
// that are 1) the same eventID and
// 2) occurred under the same summarized parent
// event in the stack

public class EventSummary {

  // all events in this summary are the same
  // type of event
  public int eventID;  

  // log the beginning of the latest
  // instance of this event during parsing
  public long timeStampBeginLatestInstance;

  // the number of instances of the event
  // in this summary
  public long instanceCount;

  // track total time of all events in this summary,
  // and amount of time spent in the events of this
  // summary but OUTSIDE of children events (self time)
  public long totalTime_ticks;
  public long selfTime_ticks;

  // track parents/children to understand percentage
  // of a child in context of parent's total
  public EventSummary      parent;
  public Vector<EventSummary> children;


  public EventSummary( int eventID ) {
    this.eventID         = eventID;  
    this.instanceCount   = 0;
    this.totalTime_ticks = 0;
    this.selfTime_ticks  = 0;
    this.parent          = null;
    this.children        = new Vector<EventSummary>( 20, 100 );
  }
}
