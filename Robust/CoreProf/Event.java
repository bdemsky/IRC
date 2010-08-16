public class Event {
  public long    timeStamp;
  public int     eventID;  
  public Counter counter;  

  public Event( long    timeStamp, 
                int     eventId,
                Counter counter ) {

    this.timeStamp = timeStamp;
    this.eventID   = eventID;  
    this.counter   = counter;  
  }
}
