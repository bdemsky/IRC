package Analysis.Prefetch;
import IR.Flat.*;
import java.util.*;
import IR.*;

public class PrefetchPair {
	TempDescriptor base;
	ArrayList<Descriptor> desc;
	ArrayList<Boolean> isTemp;

	public PrefetchPair() {
	}

	public PrefetchPair(TempDescriptor t, Descriptor f, Boolean type) {
		base = t;
		if (desc == null) 
			desc = new ArrayList<Descriptor>();
		if (isTemp == null)
			isTemp = new ArrayList<Boolean>();
		desc.add(f);
		isTemp.add(type);
	}

	public PrefetchPair(TempDescriptor t, ArrayList<Descriptor> descriptor, ArrayList<Boolean> bool) {
		base = t;
		if(desc == null){
			desc = new ArrayList<Descriptor>();
		}
		if(isTemp == null)
			isTemp = new ArrayList<Boolean>();
		desc.addAll(descriptor);
		isTemp.addAll(bool);
	}

	public TempDescriptor getBase() {
		return base;
	}

	public boolean isTempDesc(int index) {
		return isTemp.get(index).booleanValue();
	}

	public Descriptor getDescAt(int index) {
		return desc.get(index);
	}

	public List<Descriptor> getDesc() {
		return desc;
	}

	public FieldDescriptor getFieldDesc(int index) {
		return (FieldDescriptor) desc.get(index);
	}

	public TempDescriptor getTempDesc(int index) {
		return (TempDescriptor) desc.get(index);
	}

	public int hashCode() {
		int hashcode = base.hashCode(); 
		ListIterator li = desc.listIterator();
		while(li.hasNext()) {
			hashcode = hashcode ^ li.next().hashCode();
		}
		return hashcode;
	}

	public String toString() {
		return"<"+getBase().toString() +">";
	}

	public boolean equals(Object o) {
		if(o instanceof PrefetchPair) {
			PrefetchPair pp = (PrefetchPair) o;
			if(base != pp.base)
				return false;
			if (desc.equals((List<Descriptor>)pp.desc) && isTemp.equals((List<Boolean>)pp.isTemp))
				return true;
			else
				return false;
		}
		return false;
	}
}
