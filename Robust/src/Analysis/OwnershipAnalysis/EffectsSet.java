package Analysis.OwnershipAnalysis;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

public class EffectsSet {

  private Hashtable<Integer, HashSet<EffectsKey>> readTable;
  private Hashtable<Integer, HashSet<EffectsKey>> writeTable;
  private Hashtable<Integer, HashSet<EffectsKey>> strongUpdateTable;

  public EffectsSet() {
    readTable = new Hashtable<Integer, HashSet<EffectsKey>>();
    writeTable = new Hashtable<Integer, HashSet<EffectsKey>>();
    strongUpdateTable = new Hashtable<Integer, HashSet<EffectsKey>>();
  }

  public void addReadingVar(Integer idx, EffectsKey access) {
    HashSet<EffectsKey> aSet = readTable.get(idx);
    if (aSet == null) {
      aSet = new HashSet<EffectsKey>();
    }

    aSet.add(access);
    readTable.put(idx, aSet);
  }

  public void addReadingEffectsSet(Integer idx, HashSet<EffectsKey> newSet) {

    if (newSet != null) {
      HashSet<EffectsKey> aSet = readTable.get(idx);
      if (aSet == null) {
	aSet = new HashSet<EffectsKey>();
      }
      aSet.addAll(newSet);
      readTable.put(idx, aSet);
    }

  }

  public void addWritingEffectsSet(Integer idx, HashSet<EffectsKey> newSet) {

    if (newSet != null) {
      HashSet<EffectsKey> aSet = writeTable.get(idx);
      if (aSet == null) {
	aSet = new HashSet<EffectsKey>();
      }
      aSet.addAll(newSet);
      writeTable.put(idx, aSet);
    }

  }

  public void addStrongUpdateEffectsSet(Integer idx, HashSet<EffectsKey> newSet) {

    if (newSet != null) {
      HashSet<EffectsKey> aSet = strongUpdateTable.get(idx);
      if (aSet == null) {
	aSet = new HashSet<EffectsKey>();
      }
      aSet.addAll(newSet);
      strongUpdateTable.put(idx, aSet);
    }

  }


  public Hashtable<Integer, HashSet<EffectsKey>> getReadTable() {
    return readTable;
  }

  public Hashtable<Integer, HashSet<EffectsKey>> getWriteTable() {
    return writeTable;
  }

  public Hashtable<Integer, HashSet<EffectsKey>> getStrongUpdateTable() {
    return strongUpdateTable;
  }

  public void addWritingVar(Integer idx, EffectsKey access) {
    HashSet<EffectsKey> aSet = writeTable.get(idx);
    if (aSet == null) {
      aSet = new HashSet<EffectsKey>();
    }
    aSet.add(access);
    writeTable.put(idx, aSet);
  }

  public void addStrongUpdateVar(Integer idx, EffectsKey access) {
    HashSet<EffectsKey> aSet = strongUpdateTable.get(idx);
    if (aSet == null) {
      aSet = new HashSet<EffectsKey>();
    }
    aSet.add(access);
    strongUpdateTable.put(idx, aSet);
  }

  public Set<EffectsKey> getReadingSet(Integer idx) {
    return readTable.get(idx);
  }

  public Set<EffectsKey> getWritingSet(Integer idx) {
    return writeTable.get(idx);
  }

  public Set<EffectsKey> getStrongUpdateSet(Integer idx) {
    return strongUpdateTable.get(idx);
  }

  public void printSet() {
    System.out.println("writeTable=>" + writeTable.hashCode());

    Set<Integer> keySet = readTable.keySet();
    Iterator<Integer> iter = keySet.iterator();
    while (iter.hasNext()) {
      Integer idx = iter.next();
      Set<EffectsKey> effectSet = readTable.get(idx);
      String keyStr = "{";
      if (effectSet != null) {
	Iterator<EffectsKey> effectIter = effectSet.iterator();
	while (effectIter.hasNext()) {
	  EffectsKey key = effectIter.next();
	  keyStr += " " + key;
	}
      } else {
	keyStr = "null";
      }
      System.out.println("param" + idx + " R=" + keyStr);
    }

    keySet = writeTable.keySet();
    System.out.println("# R keyset=" + keySet.size());
    iter = keySet.iterator();
    while (iter.hasNext()) {
      Integer idx = iter.next();
      Set<EffectsKey> effectSet = writeTable.get(idx);
      String keyStr = "{";
      if (effectSet != null) {
	Iterator<EffectsKey> effectIter = effectSet.iterator();
	while (effectIter.hasNext()) {
	  EffectsKey key = effectIter.next();
	  keyStr += " " + key;
	}
      } else {
	keyStr = "null";
      }
      System.out.println("param" + idx + " W=" + keyStr);
    }

  }

  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }

    if (!(o instanceof EffectsSet)) {
      return false;
    }

    EffectsSet in = (EffectsSet) o;

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

    hash += getReadTable().hashCode() + getWriteTable().hashCode() * 31 + getStrongUpdateTable().hashCode();

    return hash;
  }

}
