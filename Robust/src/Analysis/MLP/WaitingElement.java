package Analysis.MLP;

import java.util.HashSet;

public class WaitingElement {

	private int waitingID;
	private int status;
	private HashSet<Integer> allocList;

	public WaitingElement() {
		this.allocList = new HashSet<Integer>();
	}

	public void setWaitingID(int waitingID) {
		this.waitingID = waitingID;
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

}