public class DistributedHashMap {
  DistributedHashEntry[] table;
  float loadFactor;
  int secondcapacity;
  //int size;
  int DEFAULT_INITIALCAPACITY;
  int DEFAULT_SECONDCAPACITY;
  float DEFAULT_LOADFACTOR;

  public DistributedHashMap() 
  {
    DEFAULT_INITIALCAPACITY = 500;
    DEFAULT_SECONDCAPACITY = 500;
    DEFAULT_LOADFACTOR = 0.75f;

    init(DEFAULT_INITIALCAPACITY,DEFAULT_SECONDCAPACITY,DEFAULT_LOADFACTOR);
    //size = 0;
  }

  public DistributedHashMap(int initialCapacity, int secondcapacity, float loadFactor) {
    init(initialCapacity, secondcapacity, loadFactor);
		//size = 0;
  }

  private void init(int initialCapacity, int secondcapacity, float loadFactor) {
    table=global new DistributedHashEntry[initialCapacity];
    this.loadFactor=loadFactor;
    this.secondcapacity=secondcapacity;
  }

  private static int hash1(int hashcode, int length) {
    //if(length==0)
    //  System.out.println("length is null");
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
    //System.out.println("Calling resize size= "+size);
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
				//size--;
				return ptr.value;
      }
      while(ptr.next!=null) {
				if (ptr.hashval==hashcode&&ptr.next.key.equals(key)) {
					Object oldvalue=ptr.value;
					ptr.next=ptr.next.next;
					dhe.count--;
					//size--;
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

    int index2=hash2(hashcode, table.length, dhe.array.length);
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
    //System.out.println("dhe.array.length= " + dhe.array.length);
    int index2=hash2(hashcode, table.length, dhe.array.length);
    DHashEntry ptr=dhe.array[index2];
    //System.out.println("index1= " + index1 + " index2= " + index2);

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
    //size++;
    return null;
  }
	
	public int size() {
	//	return size;
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
