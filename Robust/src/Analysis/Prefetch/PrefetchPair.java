package Analysis.Prefetch;
import IR.Flat.*;
import java.util.*;
import IR.*;

public class PrefetchPair {
  public TempDescriptor base;
  public ArrayList<Descriptor> desc;

  public PrefetchPair() {
    base = new TempDescriptor("");
    desc = new ArrayList<Descriptor>();
  }

  public PrefetchPair(TempDescriptor t, Descriptor f) {
    base = t;
    desc = new ArrayList<Descriptor>();
    desc.add(f);
  }

  public PrefetchPair(TempDescriptor t, ArrayList<Descriptor> descriptor) {
    base = t;
    desc = new ArrayList<Descriptor>();
    desc.addAll(descriptor);
  }

  public TempDescriptor getBase() {
    return base;
  }

  public Descriptor getDescAt(int index) {
    return desc.get(index);
  }

  public ArrayList<Descriptor> getDesc() {
    return desc;
  }

  public FieldDescriptor getFieldDesc(int index) {
    return (FieldDescriptor) desc.get(index);
  }

  public IndexDescriptor getIndexDesc(int index) {
    return (IndexDescriptor) desc.get(index);
  }

  public int hashCode() {
    int hashcode = base.toString().hashCode();
    if(desc != null) {
      hashcode^=desc.hashCode();
    }
    return hashcode;
  }

  public String toString() {
    String label= getBase().toString();
    if(getDesc() == null)
      return label;
    ListIterator it=getDesc().listIterator();
    for(; it.hasNext();) {
      Object o = it.next();
      if(o instanceof FieldDescriptor) {
	FieldDescriptor fd = (FieldDescriptor) o;
	label+="."+ fd.toString();
      } else {
	IndexDescriptor id = (IndexDescriptor) o;
	label+= id.toString();
      }
    }
    return label;
  }

  public boolean equals(Object o) {
    if(o instanceof PrefetchPair) {
      PrefetchPair pp = (PrefetchPair) o;
      return base == pp.base && desc.equals(pp.desc);
    }
    return false;
  }

  public Object clone() {
    PrefetchPair newpp = new PrefetchPair();
    newpp.base = this.base;
    for(int i = 0; i < this.desc.size(); i++) {
      Object o = desc.get(i);
      if(o instanceof FieldDescriptor) {
	newpp.desc.add((FieldDescriptor) o);
      } else {
	ArrayList<TempDescriptor> td = new ArrayList<TempDescriptor>();
	for(int j = 0; j < ((IndexDescriptor)o).tddesc.size(); j++) {
	  td.add(((IndexDescriptor)o).getTempDescAt(j));
	}
	IndexDescriptor idesc = new IndexDescriptor();
	idesc.tddesc = td;
	idesc.offset = ((IndexDescriptor)o).offset;
	newpp.desc.add(idesc);
      }
    }
    return newpp;
  }

  /** This function returns true if a tempdescriptor object is found in the array of descriptors
   *  for a given prefetch pair else returns false*/
  public boolean containsTemp(TempDescriptor td) {
    ArrayList<Descriptor> desc = (ArrayList<Descriptor>)getDesc();
    for(ListIterator it = desc.listIterator(); it.hasNext();) {
      Object o = it.next();
      if(o instanceof IndexDescriptor) {
	ArrayList<TempDescriptor> tdarray = (ArrayList<TempDescriptor>)((IndexDescriptor)o).tddesc;
	if(tdarray.contains(td)) {
	  return true;
	}
      }
    }
    return false;
  }

  /** This function creates a new Arraylist of Descriptors by replacing old tempdescriptors with new
   * tempdescriptors when there is a match */
  public PrefetchPair replaceTemp(TempDescriptor td, TempDescriptor[] newtd) {
    PrefetchPair npp=(PrefetchPair)clone();
    ArrayList<Descriptor> desc = (ArrayList<Descriptor>)npp.getDesc();
    for(ListIterator it = desc.listIterator(); it.hasNext();) {
      Object currdesc = it.next();
      if(currdesc instanceof IndexDescriptor) {
	ArrayList<TempDescriptor> tdarray = (ArrayList<TempDescriptor>)((IndexDescriptor)currdesc).tddesc;
	if (tdarray.contains(td)) {
	  int index = tdarray.indexOf(td);
	  tdarray.set(index, newtd[0]);
	  for(int i=1; i<newtd.length; i++) {
	    tdarray.add(newtd[i]);
	  }
	}
      }
    }
    return npp;
  }

}
