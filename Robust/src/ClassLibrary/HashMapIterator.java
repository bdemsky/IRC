class HashMapIterator {
    HashMap map;
    int type;
    int bin;
    HashEntry he;

    public HashMapIterator(HashMap map, int type) {
	this.map=map;
	this.type=type;
	this.bin=0;
	this.he=null;
    }

    public boolean hasNext() {
	if (he.next!=null)
	    return true;
	int i=bin;
	while(map.table[i]==null&&(i<map.table.length))
	    i++;
	return (i<map.table.length);
    }

    public Object next() {
	if (he.next!=null) {
	    he=he.next;
	    Object o;
	    if (type==0)
		o=he.key;
	    else
		o=he.value;
	    return o;
	}
	while((map.table[bin]==null)&&(bin<map.table.length))
	    bin++;
	if (bin<map.table.length) {
	    he=map.table[bin];
	    Object o;
	    if (type==0)
		o=he.key;
	    else
		o=he.value;
	    return o;
	} else System.error();
    }

}
