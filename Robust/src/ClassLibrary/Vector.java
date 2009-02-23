public class Vector {
  Object[] array;
  int size;
  int capacityIncrement;

  public Vector() {
    capacityIncrement=0;
    size=0;
    array=new Object[10];
  }

  public Vector( int size ) {
    capacityIncrement=0;
    this.size=0;
    array=new Object[size];
  }

  public void clear() {
    size=0;
    array=new Object[10];
  }

  public int indexOf(Object elem) {
    return indexOf(elem, 0);
  }

  public int indexOf(Object elem, int index) {
    for(int i=index; i<size; i++) {
      if (elem.equals(array[i]))
	return i;
    }
    return -1;
  }

  public Object elementAt(int index) {
    if (index<0 || index >=size) {
      System.printString("Illegal Vector.elementAt\n");
      System.exit(-1);
      return null;
    }
    return array[index];
  }

  public void setElementAt(Object obj, int index) {
    if (index>=size)
      ensureCapacity(index+1);
    if (index>=0 && index <size)
      array[index]=obj;
    else {
      System.printString("Illegal Vector.setElementAt\n");
      System.exit(-1);
    }
  }

  private ensureCapacity(int minCapacity) {
    if (minCapacity>array.length) {
      int newsize;
      if (capacityIncrement<=0)
	newsize=array.length*2;
      else
	newsize=array.length+capacityIncrement;
      if (newsize<minCapacity)
	newsize=minCapacity;
      Object [] newarray=new Object[newsize];
      for(int i=0; i<size; i++)
	newarray[i]=array[i];
      array=newarray;
    }
  }

  public int size() {
    return size;
  }

  public Enumeration elements() {
    System.printString("Vector.elements not implemented\n");
    System.exit(-1);
  }

  public void addElement(Object obj) {
    if (size==array.length) {
      ensureCapacity(size+1);
    }
    array[size++]=obj;
  }

  public void insertElementAt(Object obj, int index) {
    if (index<0||index>size) {
      System.printString("Illegal Vector.insertElementAt\n");
      System.exit(-1);
    }

    if (size==array.length) {
      ensureCapacity(size+1);
    }
    size++;
    for(int i=size-1; i>index; --i) {
      array[i] = array[i-1];
    }
    array[index] = obj;
  }

  public void removeElementAt(int index) {
    if (index<0||index>=size) {
      System.printString("Illegal Vector.removeElementAt\n");
      System.exit(-1);
    }
    for(int i=index; i<(size-1); i++) {
      array[i]=array[i+1];
    }
    size--;
  }

  public void removeAllElements() {
    int s = size;
    for(int i = 0; i<s; ++i ) {
      removeElementAt(0);
    }
  }
}
