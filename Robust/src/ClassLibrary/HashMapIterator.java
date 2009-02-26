class HashMapIterator extends Iterator {
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
    if (he!=null&&he.next!=null)
      return true;
    int i=bin;
    while((i<map.table.length)&&map.table[i]==null)
      i++;
    return (i<map.table.length);
  }

  public Object next() {
    if (he!=null&&he.next!=null) {
      he=he.next;
      Object o;
      if (type==0)
	o=he.key;
      else
	o=he.value;
      return o;
    }
    while((bin<map.table.length)&&
          (map.table[bin]==null))
      bin++;
    if (bin<map.table.length) {
      he=map.table[bin++];
      Object o;
      if (type==0)
	o=he.key;
      else
	o=he.value;
      return o;
    } else System.error();
  }

  public void remove() {
    System.out.println( "HashMapIterator.remove() not implemented." );
    System.exit( -1 );
  }
}
