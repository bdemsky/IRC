package Analysis.MLP;

import java.io.StringWriter;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import IR.Flat.TempDescriptor;

public class SESEEffectsSet {
	private Hashtable<TempDescriptor, HashSet<SESEEffectsKey>> readTable;
	private Hashtable<TempDescriptor, HashSet<SESEEffectsKey>> writeTable;
	private Hashtable<TempDescriptor, HashSet<SESEEffectsKey>> strongUpdateTable;
	private Hashtable<TempDescriptor, Integer> mapTempDescToInVarIdx;

	public SESEEffectsSet() {
		readTable = new Hashtable<TempDescriptor, HashSet<SESEEffectsKey>>();
		writeTable = new Hashtable<TempDescriptor, HashSet<SESEEffectsKey>>();
		strongUpdateTable =  new Hashtable<TempDescriptor, HashSet<SESEEffectsKey>>();
		mapTempDescToInVarIdx = new Hashtable<TempDescriptor, Integer>();
	}

	public void setInVarIdx(int idx, TempDescriptor td){
		mapTempDescToInVarIdx.put(td,new Integer(idx));
	}
	
	public int getInVarIdx(TempDescriptor td){
		Integer idx=mapTempDescToInVarIdx.get(td);
		if(idx==null){
			// if invar is from SESE placeholder, it is going to be ignored.
			return -1;
		}
		return idx.intValue();
	}
	
	public Hashtable<TempDescriptor, Integer> getMapTempDescToInVarIdx(){
		return mapTempDescToInVarIdx;
	}
	
	public void addReadingVar(TempDescriptor td, SESEEffectsKey access) {
		HashSet<SESEEffectsKey> aSet = readTable.get(td);
		if (aSet == null) {
			aSet = new HashSet<SESEEffectsKey>();
		}

		aSet.add(access);
		readTable.put(td, aSet);
	}

	public void addReadingEffectsSet(TempDescriptor td,
			HashSet<SESEEffectsKey> newSet) {

		if (newSet != null) {
			HashSet<SESEEffectsKey> aSet = readTable.get(td);
			if (aSet == null) {
				aSet = new HashSet<SESEEffectsKey>();
			}
			aSet.addAll(newSet);
			readTable.put(td, aSet);
		}

	}

	public void addWritingEffectsSet(TempDescriptor td,
			HashSet<SESEEffectsKey> newSet) {

		if (newSet != null) {
			HashSet<SESEEffectsKey> aSet = writeTable.get(td);
			if (aSet == null) {
				aSet = new HashSet<SESEEffectsKey>();
			}
			aSet.addAll(newSet);
			writeTable.put(td, aSet);
		}

	}

	public Hashtable<TempDescriptor, HashSet<SESEEffectsKey>> getReadTable() {
		return readTable;
	}

	public Hashtable<TempDescriptor, HashSet<SESEEffectsKey>> getWriteTable() {
		return writeTable;
	}
	
	public Hashtable<TempDescriptor, HashSet<SESEEffectsKey>> getStrongUpdateTable() {
		return strongUpdateTable;
	}

	public void addWritingVar(TempDescriptor td, SESEEffectsKey access) {
		HashSet<SESEEffectsKey> aSet = writeTable.get(td);
		if (aSet == null) {
			aSet = new HashSet<SESEEffectsKey>();
		}
		aSet.add(access);
		writeTable.put(td, aSet);
	}
	
	public void addStrongUpdateVar(TempDescriptor td, SESEEffectsKey access) {
		HashSet<SESEEffectsKey> aSet = strongUpdateTable.get(td);
		if (aSet == null) {
			aSet = new HashSet<SESEEffectsKey>();
		}
		aSet.add(access);
		strongUpdateTable.put(td, aSet);
	}

	public Set<SESEEffectsKey> getReadingSet(TempDescriptor td) {
		return readTable.get(td);
	}

	public Set<SESEEffectsKey> getWritingSet(TempDescriptor td) {
		return writeTable.get(td);
	}
	
	public Set<SESEEffectsKey> getStrongUpdateSet(TempDescriptor td){
		return strongUpdateTable.get(td);		
	}

	public String printSet() {
		
		StringWriter writer=new StringWriter();

		Set<TempDescriptor> keySet = readTable.keySet();
		Iterator<TempDescriptor> iter = keySet.iterator();
		while (iter.hasNext()) {
			TempDescriptor td = iter.next();
			Set<SESEEffectsKey> effectSet = readTable.get(td);
			String keyStr = "{";
			if (effectSet != null) {
				Iterator<SESEEffectsKey> effectIter = effectSet.iterator();
				while (effectIter.hasNext()) {
					SESEEffectsKey key = effectIter.next();
					keyStr += " " + key;
				}
			} 
			keyStr+=" }";
			writer.write("Live-in Var " + td + " Read=" + keyStr+"\n");
		}

		keySet = writeTable.keySet();
		iter = keySet.iterator();
		while (iter.hasNext()) {
			TempDescriptor td = iter.next();
			Set<SESEEffectsKey> effectSet = writeTable.get(td);
			String keyStr = "{";
			if (effectSet != null) {
				Iterator<SESEEffectsKey> effectIter = effectSet.iterator();
				while (effectIter.hasNext()) {
					SESEEffectsKey key = effectIter.next();
					keyStr += " " + key;
				}
			} 
			keyStr+=" }";
			writer.write("Live-in Var " + td + " Write=" + keyStr+"\n");
		}
		
		keySet = strongUpdateTable.keySet();
		iter = keySet.iterator();
		while (iter.hasNext()) {
			TempDescriptor td = iter.next();
			Set<SESEEffectsKey> effectSet = strongUpdateTable.get(td);
			String keyStr = "{";
			if (effectSet != null) {
				Iterator<SESEEffectsKey> effectIter = effectSet.iterator();
				while (effectIter.hasNext()) {
					SESEEffectsKey key = effectIter.next();
					keyStr += " " + key;
				}
			} 
			keyStr+=" }";
			writer.write("Live-in Var " + td + " StrongUpdate=" + keyStr+"\n");
		}
		
		return writer.toString();

	}

	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}

		if (!(o instanceof SESEEffectsSet)) {
			return false;
		}

		SESEEffectsSet in = (SESEEffectsSet) o;

		if (getReadTable().equals(in.getReadTable())
				&& getWriteTable().equals(in.getWriteTable())
				&& getStrongUpdateTable().equals(in.getStrongUpdateTable())) {
			return true;
		} else {
			return false;
		}

	}

	public int hashCode() {
		int hash = 1;

		hash += getReadTable().hashCode() + getWriteTable().hashCode() * 31 +getStrongUpdateTable().hashCode();

		return hash;
	}
}
