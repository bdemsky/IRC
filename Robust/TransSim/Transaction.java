public class Transaction {
  int[] events;
  int[] objects;
  int[] times;

  public String toString() {
    String s="";
    for(int i=0;i<numEvents();i++) {
      if (events[i]==READ)
	s+="Read";
      else if(events[i]==WRITE)
	s+="Write";
      else 
	s+="Delay";
      s+=" on "+objects[i]+" at "+times[i]+"\n";
    }
    return s;
  }

  public static final int READ=0;
  public static final int WRITE=1;
  public static final int DELAY=-1;

  public Transaction(int size) {
    events=new int[size];
    objects=new int[size];
    times=new int[size];
  }
  
  public int numEvents() {
    return events.length;
  }

  public int getEvent(int index) {
    return events[index];
  }

  public int getTime(int index) {
    return times[index];
  }

  public int getObject(int index) {
    return objects[index];
  }

  public void setEvent(int index, int val) {
    events[index]=val;
  }

  public void setTime(int index, int val) {
    times[index]=val;
  }

  public void setObject(int index, int val) {
    objects[index]=val;
  }
}