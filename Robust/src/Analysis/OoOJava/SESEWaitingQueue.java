package Analysis.OoOJava;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class SESEWaitingQueue {
	public static final int NORMAL= 0; // enqueue all stuff.
	public static final int EXCEPTION= 1; // dynamically decide whether a waiting element is enqueued or not.
	
	private HashMap<Integer, Set<WaitingElement>>mapWaitingElement;
	private HashMap<Integer, Integer>mapType;
	
	public SESEWaitingQueue(){
		mapWaitingElement=new HashMap<Integer, Set<WaitingElement>>();
		mapType=new HashMap<Integer, Integer>();
	}
	
	public void setType(int queueID, int type){
		mapType.put(new Integer(queueID), new Integer(type));
	}
	
	public int getType(int queueID){
		Integer type=mapType.get(new Integer(queueID));
		if(type==null){
			return SESEWaitingQueue.NORMAL;
		}else{
			return type.intValue();
		}
	}
	
	public void setWaitingElementSet(int queueID, Set<WaitingElement> set){
		mapWaitingElement.put(new Integer(queueID), set);
	}
	
	public Set<WaitingElement> getWaitingElementSet(int queueID){
		return mapWaitingElement.get(new Integer(queueID));
	}
	
	public Set<Integer> getQueueIDSet(){
		return mapWaitingElement.keySet();
	}
	
	public int getWaitingElementSize(){
		int size=0;
		Set<Integer> keySet=mapWaitingElement.keySet();
		for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
			Integer key = (Integer) iterator.next();
			size+=mapWaitingElement.get(key).size();
		}
		return size;
	}
}
