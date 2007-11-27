/*
 * PairMap.java
 * Author: Alokika Dash adash@uci.edu
 * Date: 11-24-2007
 */

package Analysis.Prefetch;
import IR.Flat.*;
import java.util.*;
import IR.*;

/**
 * Descriptor
 * This class is used to represent mappings between Prefetch sets of a parent and
 * child flatnode m(PSchildnode --> PSparentnode) Such analysis  is used to insert
 * prefetches during static analysis
 */

public class PairMap {
	public HashMap<PrefetchPair, PrefetchPair>  mappair;

	public PairMap() {
		mappair = new HashMap<PrefetchPair, PrefetchPair>();
	}

	public void addPair(PrefetchPair ppKey, PrefetchPair ppValue) {
		mappair.put(ppKey, ppValue);
	}

	public void removePair(PrefetchPair ppKey) {
		mappair.remove(ppKey);
	}

	public PrefetchPair getPair(PrefetchPair ppKey) {
		if(mappair != null) 
			return mappair.get(ppKey);
		return null;
	}

	public int hashCode() {
		int hashcode = mappair.hashCode();
		return hashcode;
	}

	public String pairMapToString() {
		String label = null;
		Set mapping = mappair.entrySet();
		Iterator it = mapping.iterator();
		label = "Mappings are:  ";
		for(;it.hasNext();) {
			Object o = it.next();
			label += o.toString() + "  ";
		}
		return label;
	}

	public boolean equals(Object o) {
		if(o instanceof PairMap) {
			PairMap pm = (PairMap) o;
			if(mappair == null && pm.mappair == null) {
				return true;
			} else if(mappair != null && pm.mappair != null) {
				if(mappair.equals((HashMap) pm.mappair)) {
					return true;
				}
			} else {
				return false;
			}
		}
		return false;
	}

	public boolean isEmpty() {
		if(mappair.isEmpty())
			return true;
		return false;
	}
}
