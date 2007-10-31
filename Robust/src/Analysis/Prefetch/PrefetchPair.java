package Analysis.Prefetch;
import IR.Flat.*;
import java.util.*;
import IR.*;

public class PrefetchPair {
	TempDescriptor base;
	ArrayList<Descriptor> desc;
	ArrayList<Boolean> isTempDesc; //Keeps track if the desc is a FieldDescriptor or TempDescriptor. Has same size() as desc
				

	public PrefetchPair() {
	}

	public PrefetchPair(TempDescriptor t) {
		base = t;
		desc = null;
		isTempDesc = null;
	}

	public PrefetchPair(TempDescriptor t, Descriptor f, Boolean type) {
		base = t;
		if (desc == null) 
			desc = new ArrayList<Descriptor>();
		if (isTempDesc == null)
			isTempDesc = new ArrayList<Boolean>();
		desc.add(f);
		isTempDesc.add(type);
	}

	public PrefetchPair(TempDescriptor t, ArrayList<Descriptor> descriptor, ArrayList<Boolean> bool) {
		base = t;
		if(desc == null){
			desc = new ArrayList<Descriptor>();
		}
		if(isTempDesc == null)
			isTempDesc = new ArrayList<Boolean>();
		desc.addAll(descriptor);
		isTempDesc.addAll(bool);
	}

	public TempDescriptor getBase() {
		return base;
	}

	public boolean isTempDescDesc(int index) {
		return isTempDesc.get(index).booleanValue();
	}

	public Descriptor getDescAt(int index) {
		return desc.get(index);
	}

	public ArrayList<Descriptor> getDesc() {
		return desc;
	}

	public ArrayList<Boolean> getisTempDesc() {
		return isTempDesc;
	}

	public FieldDescriptor getFieldDesc(int index) {
		return (FieldDescriptor) desc.get(index);
	}

	public TempDescriptor getTempDesc(int index) {
		return (TempDescriptor) desc.get(index);
	}

	public int hashCode() {
		int hashcode = base.toString().hashCode();
		if(desc != null) {
			ListIterator li = desc.listIterator();
			while(li.hasNext()) {
				hashcode = hashcode ^ li.next().toString().hashCode();
			}
		}
		return hashcode;
	}

	public String toString() {
	        String label= getBase().toString();
		if(getDesc() == null || getisTempDesc() == null)
			return label;
		ListIterator it=getDesc().listIterator();
		ListIterator istemp=getisTempDesc().listIterator();
		for(;it.hasNext() && istemp.hasNext();) {
			Boolean isFd = (Boolean) istemp.next();
			if(isFd.booleanValue() == false) {
				FieldDescriptor fd = (FieldDescriptor) it.next();
				label+="."+ fd.toString();
			} else { 
				TempDescriptor td = (TempDescriptor) it.next();
				label+="."+ td.toString();
			}
		}
		return label;
	}

	public boolean equals(Object o) {
		if(o instanceof PrefetchPair) {
			PrefetchPair pp = (PrefetchPair) o;
			if(base != pp.base) {
				return false;
			}
			if (desc == null && pp.desc == null) {
				return true;
			} else if (desc != null && pp.desc != null) {
				if (desc.equals((List<Descriptor>)pp.desc) && 
						isTempDesc.equals((List<Boolean>)pp.isTempDesc)) {
					return true;
				} 
			}
		}
		return false;
	}
}
