public class Event {
  public long time;
  public int event;
  public Counter counter;
  public Event(long t, int ev) {
    time=t;
    event=ev;
  }
}