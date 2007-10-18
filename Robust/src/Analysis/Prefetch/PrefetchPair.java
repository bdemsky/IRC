package Analysis.Prefetch;
import IR.Flat.*;
import java.util.*;
import IR.*;

public class PrefetchPair {
	TempDescriptor td;
	FieldDescriptor[] fd;
	int arryindex;

	public PrefetchPair() {
	}

	public PrefetchPair(TempDescriptor td) {
		this.td = td;
	}

	public PrefetchPair(TempDescriptor td, int index) {
		this.td = td;
		fd = new FieldDescriptor[index];
		arryindex = index;
	}

	public TempDescriptor getTemp() {
		return td;
	}

	public FieldDescriptor getField(int index) {
		return fd[index];
	}

	public int  getIndex() {
		return arryindex;
	}

	public int hashCode() {
		int hashcode = td.hashCode(); 
		for(int i=0; i<arryindex; i++) {
			hashcode = hashcode ^ fd[i].hashCode();
		}
		return hashcode;
	}

	public String toString() {
		//if(getTemp()!=null)
		return"<"+getTemp()+">";
	}

	public boolean equals(Object o) {
		if(o instanceof PrefetchPair) {
			PrefetchPair pp = (PrefetchPair) o;
			if(td != pp.td)
				return false;
			for(int i=0; i< arryindex; i++) {
				if(!fd[i].equals(pp.fd[i]))
					return false;
			}
		}
		return false;
	}
}
