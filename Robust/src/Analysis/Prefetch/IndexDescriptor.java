/*
 * IndexDescriptor.java
 * Author: Alokika Dash adash@uci.edu
 * Date: 11-02-2007
 */

package Analysis.Prefetch;
import IR.Flat.*;
import java.util.*;
import IR.*;

/**
 * Descriptor
 *
 * This class is used to represent the index and index offset of Arrays in
 * a prefetch pair 
 * for eg: for a prefetch pair a[i+z], an instance of this class stores var i and var z 
 */

public class IndexDescriptor extends Descriptor {
	public ArrayList<TempDescriptor> tddesc;
	public Integer offset;

	public IndexDescriptor(Integer offset) {
		super(offset.toString()); 
		this.offset = offset;
	}

	public IndexDescriptor(TempDescriptor tdesc, Integer offset) {
		super(tdesc.toString()); 
		if(tddesc == null)
			tddesc = new ArrayList<TempDescriptor>();
		tddesc.add(tdesc);
		this.offset = offset;
	}

	public IndexDescriptor() {
		super("Empty");
		tddesc = new ArrayList<TempDescriptor>();
		offset = 0;
	}

	public IndexDescriptor(ArrayList<TempDescriptor> tdesc, Integer offset) {
		super(tdesc.toString()); 
		if(tddesc == null)
			tddesc = new ArrayList<TempDescriptor>();
		tddesc.addAll(tdesc);
		this.offset = offset;
	}

	public ArrayList<TempDescriptor> getTempDesc() {
		return tddesc;
	}

	public TempDescriptor getTempDescAt(int index) {
		return ((TempDescriptor) (tddesc.get(index)));
	}

	public int getOffset() {
		return offset.intValue();
	}

	public String toString() {
		String label="[";
		if(getTempDesc() == null) {
			label += offset.toString();
			return label;
		} else {
			ListIterator lit = getTempDesc().listIterator();
			for(;lit.hasNext();) {
				TempDescriptor td = (TempDescriptor) lit.next();
				label += td.toString()+"+";
			}
			label +=offset.toString();
		}
		label += "]";
		return label;
	}

	public int hashCode() {
		int hashcode = (Integer) offset.hashCode();
		if(tddesc != null) {
			ListIterator lit = tddesc.listIterator();
			while(lit.hasNext()) {
				hashcode = hashcode ^ lit.next().hashCode();
			}
		}
		return hashcode;
	}

	public boolean equals(Object o) {
		if(o instanceof IndexDescriptor) {
			IndexDescriptor idesc = (IndexDescriptor) o;
			if(!(offset.equals(idesc.offset))) {
				return false;
			}
			if(tddesc == null && idesc.tddesc == null) {
				return true;
			} else if(tddesc!=null && idesc.tddesc!=null) {
				if(tddesc.equals((ArrayList<TempDescriptor>)idesc.tddesc)){
					return true;
				}
			}else {
				return false;
			}
		}
		return false;
	}
}
