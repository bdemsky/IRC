public class Transaction {
  int[] events;
  int[] objects;
  int[] indices;
  long[] times;

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
  public static final int BARRIER=2;
  public static final int DELAY=-1;

  public static Transaction getBarrier() {
    Transaction t=new Transaction(1);
    t.setEvent(0, BARRIER);
    return t;
  }

  public Transaction(int size) {
    events=new int[size];
    objects=new int[size];
    indices=new int[size];
    times=new long[size];
  }
  
  public int numEvents() {
    return events.length;
  }

  public int getEvent(int index) {
    return events[index];
  }

  public long getTime(int index) {
    return times[index];
  }

  public int getObject(int index) {
    return objects[index];
  }

  public int getIndex(int index) {
    return indices[index];
  }

  public ObjIndex getObjIndex(int index) {
    int obj=objects[index];
    if (obj==-1)
      return null;
    return new ObjIndex(obj, indices[index]);
  }

  public void setEvent(int index, int val) {
    events[index]=val;
  }

  public void setTime(int index, long val) {
    times[index]=val;
  }

  public void setObject(int index, int val) {
    objects[index]=val;
  }

  public void setIndex(int index, int val) {
    indices[index]=val;
  }
}