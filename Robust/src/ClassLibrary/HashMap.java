public class HashMap {
    HashEntry[] table;
    float loadFactor;
    int numItems;

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
	table=new HashEntry[initialCapacity];
	this.loadFactor=loadFactor;
	this.numItems=0;
    }

    private int hash(Object o) {
	if (o==null)
	    return 0;
	int value=o.hashCode()%table.length;
	if (value<0)
	    return -value;
	return value;
    }

    void resize() {
	int newCapacity=2*table.length+1;
	HashEntry[] oldtable=table;	
	this.table=new HashEntry[newCapacity];

	for(int i=0;i<oldtable.length;i++) {
	    HashEntry e=oldtable[i];
	    while(e!=null) {
		HashEntry next=e.next;
		int bin=hash(e.key);
		e.next=table[bin];
		table[bin]=e;
		e=next;
	    }
	}
    }

    public boolean isEmpty() {
	return numItems==0;
    }

    public int size() {
	return numItems;
    }

    /* 0=keys, 1=values */
    public HashMapIterator iterator(int type) {
	return new HashMapIterator(this, type);
    }

    Object remove(Object key) {
	int bin=hash(key);
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
	int bin=hash(key);
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
	int bin=hash(key);
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
	if (numItems>(loadFactor*table.length)) {
	    //Resize the table
	    resize();
	}
	int bin=hash(key);
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
}
