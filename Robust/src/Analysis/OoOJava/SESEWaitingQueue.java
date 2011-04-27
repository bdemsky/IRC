package Analysis.OoOJava;

import java.util.HashMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;
import IR.Flat.TempDescriptor;

public class SESEWaitingQueue {
  public static final int NORMAL= 0; // enqueue all stuff.
  public static final int EXCEPTION= 1; // dynamically decide whether a waiting element is enqueued or not.

  private HashMap<TempDescriptor, Set<WaitingElement>>tmp2WaitingElement;
  private HashMap<Integer, Set<WaitingElement>>mapWaitingElement;
  private HashMap<Integer, Integer>mapType;

  public SESEWaitingQueue() {
    mapWaitingElement=new HashMap<Integer, Set<WaitingElement>>();
    tmp2WaitingElement=new HashMap<TempDescriptor, Set<WaitingElement>>();
    mapType=new HashMap<Integer, Integer>();
  }

  public void setType(int queueID, int type) {
    mapType.put(new Integer(queueID), new Integer(type));
  }

  public int getType(int queueID) {
    Integer type=mapType.get(new Integer(queueID));
    if(type==null) {
      return SESEWaitingQueue.NORMAL;
    } else {
      return type.intValue();
    }
  }

  public void setWaitingElementSet(int queueID, Set<WaitingElement> set) {
    mapWaitingElement.put(new Integer(queueID), set);
    for(Iterator<WaitingElement> wit=set.iterator(); wit.hasNext(); ) {
      WaitingElement we=wit.next();
      TempDescriptor tmp=we.getTempDesc();
      if (!tmp2WaitingElement.containsKey(tmp))
	tmp2WaitingElement.put(tmp, new HashSet<WaitingElement>());
      tmp2WaitingElement.get(tmp).add(we);
    }
  }

  public Set<WaitingElement> getWaitingElementSet(TempDescriptor tmp) {
    return tmp2WaitingElement.get(tmp);
  }

  public Set<WaitingElement> getWaitingElementSet(int queueID) {
    return mapWaitingElement.get(new Integer(queueID));
  }

  public Set<Integer> getQueueIDSet() {
    return mapWaitingElement.keySet();
  }

  public int getWaitingElementSize() {
    int size=0;
    Set<Integer> keySet=mapWaitingElement.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext(); ) {
      Integer key = (Integer) iterator.next();
      size+=mapWaitingElement.get(key).size();
    }
    return size;
  }
}
