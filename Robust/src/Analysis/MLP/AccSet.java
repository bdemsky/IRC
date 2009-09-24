package Analysis.MLP;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;

import IR.Flat.TempDescriptor;

public class AccSet {

	private Hashtable<TempDescriptor, HashSet<AccKey>> readTable;
	private Hashtable<TempDescriptor, HashSet<AccKey>> writeTable;

	public AccSet() {

		readTable = new Hashtable<TempDescriptor, HashSet<AccKey>>();
		writeTable = new Hashtable<TempDescriptor, HashSet<AccKey>>();

	}

	public void addReadingVar(TempDescriptor td, AccKey access) {
		HashSet<AccKey> aSet = readTable.get(td);
		if (aSet == null) {
			aSet = new HashSet<AccKey>();
		}

		aSet.add(access);
		readTable.put(td, aSet);
	}

	public void addWritingVar(TempDescriptor td, AccKey access) {

		HashSet<AccKey> aSet = writeTable.get(td);
		if (aSet == null) {
			aSet = new HashSet<AccKey>();
		}

		aSet.add(access);
		writeTable.put(td, aSet);

	}

	public boolean containsParam(TempDescriptor td) {

		Iterator<TempDescriptor> iter = readTable.keySet().iterator();

		while (iter.hasNext()) {

			TempDescriptor key = iter.next();
			if (key.equals(td)) {
				return true;
			}

		}

		return false;

	}

	public void addParam(TempDescriptor paramTD) {

		if (!readTable.containsKey(paramTD)) {
			HashSet<AccKey> readSet = new HashSet<AccKey>();
			readTable.put(paramTD, readSet);
		}

		if (!writeTable.containsKey(paramTD)) {
			HashSet<AccKey> writeSet = new HashSet<AccKey>();
			writeTable.put(paramTD, writeSet);
		}

	}

	public void addAll(AccSet newAccSet) {

		Hashtable<TempDescriptor, HashSet<AccKey>> newReadTable = newAccSet
				.getReadTable();
		Hashtable<TempDescriptor, HashSet<AccKey>> newWriteTable = newAccSet
				.getWriteTable();

		Iterator<TempDescriptor> iter = newReadTable.keySet().iterator();
		while (iter.hasNext()) { // for each variables
			TempDescriptor td = iter.next();

			HashSet<AccKey> currentSet;
			if (!readTable.containsKey(td)) {
				currentSet = new HashSet<AccKey>();
			} else {
				currentSet = readTable.get(td);
			}

			HashSet<AccKey> newSet = newReadTable.get(td);
			currentSet.addAll(newSet);
			readTable.put(td, currentSet);
		}

		iter = newWriteTable.keySet().iterator();
		while (iter.hasNext()) { // for each variables
			TempDescriptor td = iter.next();

			HashSet<AccKey> currentSet;
			if (!writeTable.containsKey(td)) {
				currentSet = new HashSet<AccKey>();
			} else {
				currentSet = writeTable.get(td);
			}

			HashSet<AccKey> newSet = newWriteTable.get(td);
			currentSet.addAll(newSet);
			writeTable.put(td, currentSet);
		}

	}

	public Hashtable<TempDescriptor, HashSet<AccKey>> getReadTable() {
		return readTable;
	}

	public Hashtable<TempDescriptor, HashSet<AccKey>> getWriteTable() {
		return writeTable;
	}

	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}

		if (!(o instanceof AccSet)) {
			return false;
		}

		AccSet in = (AccSet) o;

		if (getReadTable().equals(in.getReadTable())
				&& getWriteTable().equals(in.getWriteTable())) {
			return true;
		} else {
			return false;
		}

	}

	public void printSet() {

		String readStr = "";
		String writeStr = "";

		readStr = "#Reading#\r\n";
		Iterator<TempDescriptor> iter = getReadTable().keySet().iterator();
		while (iter.hasNext()) { // for each variables

			TempDescriptor td = iter.next();
			HashSet<AccKey> aSet = readTable.get(td);

			readStr += td.getSymbol() + ":" + "{";

			Iterator<AccKey> setIter = aSet.iterator();
			while (setIter.hasNext()) {
				AccKey element = setIter.next();
				readStr += " " + element;
			}
			readStr += "}\r\n";
		}

		writeStr = "#Writing#\r\n";
		iter = getWriteTable().keySet().iterator();
		while (iter.hasNext()) { // for each variables

			TempDescriptor td = iter.next();
			HashSet<AccKey> aSet = writeTable.get(td);

			writeStr += td.getSymbol() + ":" + "{";

			Iterator<AccKey> setIter = aSet.iterator();
			while (setIter.hasNext()) {
				AccKey element = setIter.next();
				writeStr += " " + element;
			}
			writeStr += "}\r\n";
		}

		System.out.println(readStr);
		System.out.println(writeStr);

	}

	public String toString() {

		String str = "\n";

		Iterator<TempDescriptor> iter = getReadTable().keySet().iterator();
		while (iter.hasNext()) { // for each variables

			TempDescriptor td = iter.next();

			str += "\tParameter " + td.getSymbol() + " reading={";

			HashSet<AccKey> aSet = readTable.get(td);
			Iterator<AccKey> setIter = aSet.iterator();
			boolean first = true;
			while (setIter.hasNext()) {
				AccKey element = setIter.next();
				if (first) {
					str += " " + element;
					first = false;
				} else {
					str += " , " + element;
				}

			}
			str += " }\n";

			str += "\tParameter " + td.getSymbol() + " writing={";

			aSet = writeTable.get(td);
			setIter = aSet.iterator();
			first = true;
			while (setIter.hasNext()) {
				AccKey element = setIter.next();
				if (first) {
					str += " " + element;
					first = false;
				} else {
					str += " , " + element;
				}

			}
			str += " }\n";

		}

		return str;
	}

}
