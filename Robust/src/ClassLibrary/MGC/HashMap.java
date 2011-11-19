public class HashMap implements Map {
  HashEntry[] table;
  float loadFactor;
  int numItems;
  int threshold;
  Collection values;
  Set keys;

  public HashMap() {
    init(16, 0.75f);
  }

  public HashMap(int initialCapacity) {
    init(initialCapacity, 0.75f);
  }

  public HashMap(int initialCapacity, float loadFactor) {
    init(initialCapacity, loadFactor);
  }

  private void init(int initialCapacity, float loadFactor) {
    table=new HashEntry[computeCapacity(initialCapacity)];
    this.loadFactor=loadFactor;
    this.numItems=0;
    this.threshold=(int)(loadFactor*table.length);
  }
  
  private static int computeCapacity(int capacity) {
    int x=16;
    while(x<capacity)
      x=x<<1;
    return x;
  }

  private static int hash(Object o, int length) {
    int orig=o.hashCode();
    orig=orig^(orig>>>22)^(orig>>>10);
    orig=orig^(orig>>>8)^(orig>>4);
    return orig&(length-1);
  }

  void resize() {
    int newCapacity=table.length<<1;
    HashEntry[] oldtable=table;
    this.table=new HashEntry[newCapacity];
    this.threshold=(int) (newCapacity*loadFactor);

    for(int i=0; i<oldtable.length; i++) {
      HashEntry e=oldtable[i];
      while(e!=null) {
    HashEntry next=e.next;
    int bin=hash(e.key, newCapacity);
    e.next=table[bin];
    table[bin]=e;
    e=next;
      }
    }
  }
  
  public void clear() {
    for(int i=0;i<table.length;i++)
      table[i]=null;
    numItems=0;
  }

  public boolean isEmpty() {
    return numItems==0;
  }

  public int size() {
    return numItems;
  }

  /* 0=keys, 1=values */
  public Iterator iterator(int type) {
    return (Iterator)(new HashMapIterator(this, type));
  }

  Object remove(Object key) {
    int bin=hash(key, table.length);
    HashEntry ptr=table[bin];
    if (ptr!=null) {
      if (ptr.key.equals(key)) {
	table[bin]=ptr.next;
	numItems--;
	return ptr.value;
      }
      while(ptr.next!=null) {
	if (ptr.next.key.equals(key)) {
	  Object oldvalue=ptr.value;
	  ptr.next=ptr.next.next;
	  numItems--;
	  return oldvalue;
	}
	ptr=ptr.next;
      }
    }
    return null;
  }

  Object get(Object key) {
    int bin=hash(key, table.length);
    HashEntry ptr=table[bin];
    while(ptr!=null) {
      if (ptr.key.equals(key)) {
	return ptr.value;
      }
      ptr=ptr.next;
    }
    return null;
  }

  boolean containsKey(Object key) {
    int bin=hash(key, table.length);
    HashEntry ptr=table[bin];
    while(ptr!=null) {
      if (ptr.key.equals(key)) {
	return true;
      }
      ptr=ptr.next;
    }
    return false;
  }

  Object put(Object key, Object value) {
    numItems++;
    if (numItems>threshold) {
      //Resize the table
      resize();
    }
    int bin=hash(key, table.length);
    HashEntry ptr=table[bin];
    while(ptr!=null) {
      if (ptr.key.equals(key)) {
	Object oldvalue=ptr.value;
	ptr.value=value;
	return oldvalue;
      }
      ptr=ptr.next;
    }
    HashEntry he=new HashEntry();
    he.value=value;
    he.key=key;
    he.next=table[bin];
    table[bin]=he;
    return null;
  }
  
  public Collection values()
  {
    if (values == null)
      // We don't bother overriding many of the optional methods, as doing so
      // wouldn't provide any significant performance advantage.
      values = new AbstractCollection()
      {
        
        public int size()
        {
          return numItems;
        }

        public Iterator iterator()
        {
          // Cannot create the iterator directly, because of LinkedHashMap.
          return new HashMapIterator(HashMap.this, 1);
        }

        public void clear()
        {
          HashMap.this.clear();
        }
      };
    return values;
  }
  
  public Set keySet()
  {
    if (keys == null)
      // Create an AbstractSet with custom implementations of those methods
      // that can be overridden easily and efficiently.
      keys = new AbstractSet()
      {
        public int size()
        {
          return numItems;
        }

        public Iterator iterator()
        {
          // Cannot create the iterator directly, because of LinkedHashMap.
          //return HashMap.this.iterator(KEYS);
          return new HashMapIterator(HashMap.this, 0);
        }

        public void clear()
        {
          HashMap.this.clear();
        }

        public boolean contains(Object o)
        {
          return containsKey(o);
        }

        public boolean remove(Object o)
        {
          // Test against the size of the HashMap to determine if anything
          // really got removed. This is necessary because the return value
          // of HashMap.remove() is ambiguous in the null case.
          int oldsize = numItems;
          HashMap.this.remove(o);
          return oldsize != numItems;
        }
      };
    return keys;
  }
}
