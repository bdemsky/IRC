package Analysis.MLP;

import java.util.HashSet;
import java.util.Iterator;

public class WaitingElement {

	private int waitingID;
	private int status;
	private HashSet<Integer> allocList;
	private String dynID;
	private HashSet<Integer> connectedSet;

	public WaitingElement() {
		this.allocList = new HashSet<Integer>();
		this.connectedSet = new HashSet<Integer>();
	}
	
	public void setWaitingID(int waitingID) {
		this.waitingID = waitingID;
	}
	
	public HashSet<Integer> getConnectedSet() {
		return connectedSet;
	}

	public void setConnectedSet(HashSet<Integer> connectedSet) {
		this.connectedSet.addAll(connectedSet);
	}

	public String getDynID(){
		return dynID;
	}
	
	public void setDynID(String dynID){
		this.dynID=dynID;
	}
	
	public int getWaitingID() {
		return waitingID;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public int getStatus() {
		return status;
	}

	public HashSet<Integer> getAllocList() {
		return allocList;
	}

	public void setAllocList(HashSet<Integer> allocList) {
		this.allocList.addAll(allocList);
	}

	public boolean equals(Object o) {

		if (o == null) {
			return false;
		}

		if (!(o instanceof WaitingElement)) {
			return false;
		}

		WaitingElement in = (WaitingElement) o;

		if (waitingID == in.getWaitingID() && status == in.getStatus()
				&& allocList.equals(in.getAllocList())) {
			return true;
		} else {
			return false;
		}

	}

	public String toString() {
		return "[waitingID=" + waitingID + " status=" + status + " allocList="
				+ allocList + "]";
	}

	public int hashCode() {

		int hash = 1;

		hash = hash * 31 + waitingID;

		hash += status;

		for (Iterator iterator = allocList.iterator(); iterator.hasNext();) {
			Integer type = (Integer) iterator.next();
			hash += type.intValue();
		}

		return hash;

	}

}