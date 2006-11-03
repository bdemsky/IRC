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

    void resize() {
	int newCapacity=2*table.length+1;
	HashEntry[] newtable=new HashEntry[newCapacity];
	for(int i=0;i<table.length;i++) {
	    HashEntry e=table[i];
	    while(e!=null) {
		HashEntry next=e.next;
		int bin=e.key.hashCode()%newCapacity;
		e.next=newtable[bin];
		newtable[bin]=e;
		e=next;
	    }
	}
	this.table=newtable;
    }

    public boolean isEmpty() {
	return numItems==0;
    }

    public int size() {
	return numItems;
    }

    Object remove(Object key) {
	int bin=key.hashCode()%table.length;
	HashEntry ptr=table[bin];
	if (ptr.key==key) {
	    table[bin]=ptr.next;
	    numItems--;
	    return ptr.value;
	}
	while(ptr.next!=null) {
	    if (ptr.next.key==key) {
		Object oldvalue=ptr.value;
		ptr.next=ptr.next.next;
		numItems--;
		return oldvalue;
	    }
	}
	return null;
    }

    Object get(Object key) {
	int bin=key.hashCode()%table.length;
	HashEntry ptr=table[bin];
	while(ptr!=null) {
	    if (ptr.key==key) {
		return ptr.value;
	    }
	}
	return null;
    }

    boolean containsKey(Object key) {
	int bin=key.hashCode()%table.length;
	HashEntry ptr=table[bin];
	while(ptr!=null) {
	    if (ptr.key==key) {
		return true;
	    }
	}
	return false;
    }

    Object put(Object key, Object value) {
	numItems++;
	if (numItems>(loadFactor*table.length)) {
	    //Resize the table
	    resize();
	}
	int bin=key.hashCode()%table.length;
	HashEntry ptr=table[bin];
	while(ptr!=null) {
	    if (ptr.key==key) {
		Object oldvalue=ptr.value;
		ptr.value=value;
		return oldvalue;
	    }
	}
	HashEntry he=new HashEntry();
	he.value=value;
	he.key=key;
	he.next=table[bin];
	table[bin]=he;
	return null;
    }

}
