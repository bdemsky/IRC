public class DistributedHashMap {
  DistributedHashEntry[] table;
  float loadFactor;
  int secondcapacity;

  public DistributedHashMap(int initialCapacity, int secondcapacity, float loadFactor) {
    init(initialCapacity, secondcapacity, loadFactor);
  }

  private void init(int initialCapacity, int secondcapacity, float loadFactor) {
    table=global new DistributedHashEntry[initialCapacity];
    this.loadFactor=loadFactor;
    this.secondcapacity=secondcapacity;
  }

  private static int hash1(int hashcode, int length) {
    int value=hashcode%length;
    if (value<0)
      return -value;
    else
      return value;
  }

  private static int hash2(int hashcode, int length1, int length2) {
    int value=(hashcode*31)%length2;
    if (value<0)
      return -value;
    else
      return value;
  }

  void resize(int index) {
    DHashEntry[] oldtable=table[index].array;
    int newCapacity=oldtable.length*2+1;
    DHashEntry [] newtable=global new DHashEntry[newCapacity];
    table[index].array=newtable;

    for(int i=0; i<oldtable.length; i++) {
      DHashEntry e=oldtable[i];
      while(e!=null) {
	DHashEntry next=e.next;
	int bin=hash2(e.hashval, table.length, newCapacity);
	e.next=newtable[bin];
	newtable[bin]=e;
	e=next;
      }
    }
  }

  Object remove(Object key) {
    int hashcode=key.hashCode();
    int index1=hash1(hashcode, table.length);
    DistributedHashEntry dhe=table[index1];
    if (dhe==null)
      return null;
    int index2=hash2(hashcode, table.length, dhe.array.length);
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
    int index1=hash1(hashcode, table.length);
    /****** Add Manual Prefetch *****/
    //table[index1].array
    Object obj = table;
    short[] offsets = new short[4];
    offsets[0] = (short) index1;
    offsets[1] = (short) 0;
    offsets[2] = getoffset {DistributedHashEntry, array};
    offsets[3] = (short) 0;
    System.rangePrefetch(obj,offsets);
    /********************************/

    DistributedHashEntry dhe=table[index1];
    if (dhe==null)
      return null;

    int index2=hash2(hashcode, table.length, dhe.array.length);
    /****** Add Manual Prefetch *****/
    //dhe.array[index2].next(5).key
    Object obj1 = dhe;
    short[] offsets1 = new short[8];
    offsets1[0] = getoffset {DistributedHashEntry, array};
    offsets1[1] = (short) 0;
    offsets1[2] = (short) index2;
    offsets1[3] = (short) 0;
    offsets1[4] = getoffset {DHashEntry, next};
    offsets1[5] = (short) 5;
    offsets1[6] = getoffset {DHashEntry, key};
    offsets1[7] = (short) 0;
    System.rangePrefetch(obj1, offsets1);
    /********************************/

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
    int index1=hash1(hashcode, table.length);
    DistributedHashEntry dhe=table[index1];
    if (dhe==null)
      return false;
    int index2=hash2(hashcode, table.length, dhe.array.length);
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
    int index1=hash1(hashcode, table.length);
    DistributedHashEntry dhe=table[index1];
    if (dhe==null) {
      dhe=global new DistributedHashEntry(secondcapacity);
      table[index1]=dhe;
    }
    int index2=hash2(hashcode, table.length, dhe.array.length);
    DHashEntry ptr=dhe.array[index2];

    while(ptr!=null) {
      if (ptr.hashval==hashcode&&ptr.key.equals(key)) {
	Object oldvalue=ptr.value;
	ptr.value=value;
	return oldvalue;
      }
      ptr=ptr.next;
    }

    DHashEntry he=global new DHashEntry();
    he.value=value;
    he.key=key;
    he.hashval=hashcode;
    he.next=dhe.array[index2];
    dhe.array[index2]=he;

    dhe.count++;
    if (dhe.count>(loadFactor*dhe.array.length)) {
      //Resize the table
      resize(index1);
    }
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
