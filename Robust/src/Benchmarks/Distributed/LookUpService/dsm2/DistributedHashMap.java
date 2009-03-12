public class DistributedHashMap {
  DistributedHashEntry[] table;
  float loadFactor;

  public DistributedHashMap(int initialCapacity, float loadFactor) {
    init(initialCapacity, loadFactor);
  }

  private void init(int initialCapacity, float loadFactor) {
    table=global new DistributedHashEntry[initialCapacity];
    this.loadFactor=loadFactor;
  }

  private static int hash1(int hashcode, int length) {
    int value=hashcode%length;
    if (value<0)
      return -value;
    else
      return value;
  }

  Object remove(Object key) {
    int hashcode=key.hashCode();
    int index1=hash1(hashcode, table.length);
    DistributedHashEntry dhe=table[index1];
    if (dhe==null)
      return null;
    DHashEntry ptr=dhe.array;

    if (ptr!=null) {
      if (ptr.hashval==hashcode&&ptr.key.equals(key)) {
	dhe.array=ptr.next;
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
    
    DistributedHashEntry dhe=table[index1];
    if (dhe==null)
      return null;

    /****** Add Manual Prefetch *****/
    //dhe.array.next(5).key
    Object obj1 = dhe.array;
    short[] offsets1 = new short[4];
    offsets1[0] = getoffset {DHashEntry, next};
    offsets1[1] = (short) 5;
    offsets1[2] = getoffset {DHashEntry, key};
    offsets1[3] = (short) 0;
    System.rangePrefetch(obj1, offsets1);
    /********************************/

    DHashEntry ptr=dhe.array;

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

    DHashEntry ptr=dhe.array;

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
	dhe=global new DistributedHashEntry();
	table[index1]=dhe;
    }
    DHashEntry ptr=dhe.array;

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
    he.next=dhe.array;
    dhe.array=he;

    dhe.count++;
    return null;
  }
}


class DistributedHashEntry {
  public DistributedHashEntry() {
  }
  int count;
  DHashEntry array;
}


class DHashEntry {
  public DHashEntry() {
  }
  int hashval;
  Object key;
  Object value;
  DHashEntry next;
}
