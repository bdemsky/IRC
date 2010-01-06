public class Transaction {
  byte[] events;
  int[] objects;
  int[] indices;
  int[] times;
  long[] alttimes;
  boolean started;

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

  public static final byte READ=0;
  public static final byte WRITE=1;
  public static final byte BARRIER=2;
  public static final byte DELAY=-1;

  public Transaction(int size,boolean started) {
    events=new byte[size];
    objects=new int[size];
    indices=new int[size];
    times=new int[size];
    this.started=started;
  }
  
  public int numEvents() {
    return events.length;
  }

  public byte getEvent(int index) {
    return events[index];
  }

  public long getTime(int index) {
    if (times!=null)
      return times[index];
    else
      return alttimes[index];
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

  public void setEvent(int index, byte val) {
    events[index]=val;
  }

  public void setTime(int index, long val) {
    if (times==null) {
      alttimes[index]=val;
    } else {
      int v=(int)val;
      if (v==val) {
	times[index]=v;
      } else {
	alttimes=new long[times.length];
	for(int i=0;i<times.length;i++)
	  alttimes[i]=times[i];
	times=null;
	alttimes[index]=val;
      }
    }
  }

  public void setObject(int index, int val) {
    objects[index]=val;
  }

  public void setIndex(int index, int val) {
    indices[index]=val;
  }
}