package Analysis.Prefetch;
import IR.Flat.*;
import java.util.*;
import IR.*;

public class PrefetchPair {
	TempDescriptor td;
	FieldDescriptor fd;
	public float num;

	public PrefetchPair() {
	}

	public PrefetchPair(TempDescriptor td, float prob) {
		this.td = td;
		num = prob;
	}

	public TempDescriptor getTemp() {
		return td;
	}

	public String toString() {
		//if(getTemp()!=null)
		return"<"+getTemp()+">";
	}
}
