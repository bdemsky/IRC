package Analysis.MLP;

import java.util.HashSet;
import java.util.Iterator;

import IR.Flat.TempDescriptor;

public class WaitingElement {

	private int waitingID;
	private int status;
	private String dynID="";
	private TempDescriptor tempDesc;
	
	public void setTempDesc(TempDescriptor tempDesc){
		this.tempDesc=tempDesc;
	}
	
	public TempDescriptor getTempDesc(){
		return tempDesc;
	}

	public void setWaitingID(int waitingID) {
		this.waitingID = waitingID;
	}
	
	public String getDynID(){
		return dynID;
	}
	
	public void setDynID(String dynID){
		this.dynID=dynID;
	}
	
	public int getQueueID() {
		return waitingID;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public int getStatus() {
		return status;
	}

	public boolean equals(Object o) {

		if (o == null) {
			return false;
		}

		if (!(o instanceof WaitingElement)) {
			return false;
		}

		WaitingElement in = (WaitingElement) o;

		if (waitingID == in.getQueueID() && status == in.getStatus() && dynID.equals(in.getDynID()) ) {
			return true;
		} else {
			return false;
		}

	}

	public String toString() {
		return "[waitingID=" + waitingID + " status=" + status + " dynID="
				+ dynID + "]";
	}

	public int hashCode() {

		int hash = 1;

		hash = hash * 31 + waitingID;

		hash += status;
		
		hash += dynID.hashCode();

		return hash;

	}

}