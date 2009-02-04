public class DistributedHashMap {
  DistributedHashEntry[] table;
  float loadFactor;
  int secondcapacity;

  public HashMap(int initialCapacity, int secondcapacity, float loadFactor) {
    init(initialCapacity, secondcapacity, loadFactor);
  }

  private void init(int initialCapacity, int secondcapacity, float loadFactor) {
    table=global new DistributedHashEntry[initialCapacity];
    this.loadFactor=loadFactor;
    this.secondcapacity=secondcapacity;
  }

  private static unsigned int hash1(unsigned int hashcode, int length) {
    int value=hashcode%length;
    return value;
  }

  private static unsigned int hash2(unsigned int hashcode, int length1, int length2) {
    int value=(hashcode*31)%length2;
    return value;
  }

  void resize(int index) {
    DHashEntry[] oldtable=table[index].array;
    int newCapacity=oldtable.length*2+1;
    table[index].array=global new DHashEntry[newCapacity];

    for(int i=0; i<oldtable.length; i++) {
      DHashEntry e=oldtable[i];
      while(e!=null) {
	HashEntry next=e.next;
	int bin=hash2(e.hashval, newCapacity);
	e.next=table[bin];
	table[bin]=e;
	e=next;
      }
    }
  }

  Object remove(Object key) {
    int hashcode=key.hashCode();
    int index1=hash1(key, table.length);
    DistributedHashEntry dhe=table[index1];
    if (dhe=null)
      return null;
    int index2=hash2(key, table.length, dhe.array.length);
    DHashEntry ptr=dhe.array[index2];

    if (ptr!=null) {
      if (ptr.hashval==hashcode&&ptr.key.equals(key)) {
	dhe.array[index2]=ptr.next;
	dhe.count--;
	return ptr.value;
      }
      while(ptr.next!=null) {
	if (ptr.hashval==hashcode&&ptr.next.key.equals(key)) {
	  Object oldvalue=ptr.value;
	  ptr.next=ptr.next.next;
	  dhe.count--;
	  return oldvalue;
	}
	ptr=ptr.next;
      }
    }
    return null;
  }

  Object get(Object key) {
    int hashcode=key.hashCode();
    int bin=hash(key, table.length);
    int index1=hash1(key, table.length);
    DistributedHashEntry dhe=table[index1];
    if (dhe==null)
      return null;

    int index2=hash2(key, table.length, dhe.array.length);
    DHashEntry ptr=dhe.array[index2];

    while(ptr!=null) {
      if (ptr.hashval==hashcode
	  &&ptr.key.equals(key)) {
	return ptr.value;
      }
      ptr=ptr.next;
    }
    return null;
  }

  boolean containsKey(Object key) {
    int hashcode=key.hashCode();
    int bin=hash(key, table.length);
    int index1=hash1(key, table.length);
    DistributedHashEntry dhe=table[index1];
    if (dhe==null)
      return false;
    int index2=hash2(key, table.length, dhe.array.length);
    DHashEntry ptr=dhe.array[index2];

    while(ptr!=null) {
      if (ptr.hashval==hashcode
	  &&ptr.key.equals(key)) {
	return true;
      }
      ptr=ptr.next;
    }
    return false;
  }

  Object put(Object key, Object value) {
    int hashcode=key.hashCode();
    int bin=hash(key, table.length);
    int index1=hash1(key, table.length);
    DistributedHashEntry dhe=table[index1];
    if (dhe==null) {
      dhe=global new DistributedHashEntry(secondlevelcapacity);
      table[index1]=dhe;
    }

    dhe.count++;
    if (dhe.count>(loadFactor*dhe.array.length)) {
      //Resize the table
      resize(index1);
    }

    int index2=hash2(key, table.length, dhe.array.length);
    DHashEntry ptr=dhe.array[index2];

    while(ptr!=null) {
      if (ptr.hashval!=hashcode&&ptr.key.equals(key)) {
	Object oldvalue=ptr.value;
	ptr.value=value;
	dhe.count--;
	return oldvalue;
      }
      ptr=ptr.next;
    }
    DHashEntry he=global new DHashEntry();
    he.value=value;
    he.key=key;
    he.hashval=hashcode;
    he.next=table[index2];
    table[index2]=he;
    return null;
  }
}


class DistributedHashEntry {
  public DistributedHashEntry(int capacity) {
    array=global new DHashEntry[capacity];
  }
  int count;
  DHashEntry[] array;
}


class DHashEntry {
  public DHashEntry() {
  }
  int hashval;
  Object key;
  Object value;
  DHashEntry next;
}
