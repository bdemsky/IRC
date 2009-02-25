public class LinkedListElement {
  public LinkedListElement next;
  public LinkedListElement prev;
  public Object element;

  public LinkedListElement( Object e,
			    LinkedListElement n,
			    LinkedListElement p ) {
    element = e;
    next = n;
    prev = p;
  }
}

public class LinkedList {
  LinkedListElement head;
  LinkedListElement tail;
  int size;

  public LinkedList() {
    clear();
  }

  public add( Object o ) {
    if( tail == null ) {
      head = new LinkedListElement( o, null, null );
      tail = head;

    } else {
      tail.next = new LinkedListElement( o, null, tail );
      tail = tail.next;
    }
    size++;
  }

  public addFirst( Object o ) {
    if( head == null ) {
      head = new LinkedListElement( o, null, null );
      tail = head;

    } else {
      head.prev = new LinkedListElement( o, head, null );
      head = head.prev;
    }
    size++;
  }

  public addLast( Object o ) {
    add( o );
  }

  public clear() {
    head = null;
    tail = null;
    size = 0;
  }

  public int size() {
    return size;
  }

  public Object clone() {
    System.out.println( "LinkedList.clone() not implemented." );
    System.exit(-1);
  }

  public boolean contains( Object o ) {
    LinkedListElement e = head;
    while( e != null ) {
      if( e.element == o ) {
	return true;
      }
      e = e.next;
    }
    return false;
  }

  public Object getFirst() {
    if( head == null ) {
      return null;
    }
    return head.element;
  }

  public Object getLast() {
    if( tail == null ) {
      return null;
    }
    return tail.element;    
  }

  public Object element() {
    getFirst();
  }

  public Object peek() {
    getFirst();
  }

  public Object peekFirst() {
    getFirst();
  }

  public Object peekLast() {
    getLast();
  }

  public void removeFirst() {
    if( head == null ) {
      System.out.println( "LinkedList: illegal removeFirst()" );
      System.exit(-1);
    }
    head = head.next;
    if( head != null ) {
      head.prev = null;
    } else {
      tail = null;
    }
    size--;
  }

  public void removeLast() {
    if( tail == null ) {
      System.out.println( "LinkedList: illegal removeLast()" );
      System.exit(-1);
    }
    tail = tail.prev;
    if( tail != null ) {
      tail.next = null;
    } else {
      head = null;
    }
    size--;
  }

  public void remove( Object o ) {
    if( head == null ) {
      System.out.println( "LinkedList: illegal remove( Object o )" );
      System.exit(-1);
    }
    LinkedListElement e = head;
    while( e != null ) {
      if( e.element == o ) {
	if( e.prev != null ) {
	  e.prev.next = e.next;
	}
	if( e.next != null ) {
	  e.next.prev = e.prev;
	}
	size--;
	return;
      }
      e = e.next;
    }
    System.out.println( "LinkedList: illegal remove( Object o ), "+o+" not found" );
    System.exit(-1);    
  }

  public Object pop() {
    Object o = getFirst();
    removeFirst();
    return o;
  }

  public void push( Object o ) {
    addFirst( o );
  }

  public Iterator iterator() {
    return new LinkedListIterator( this );
  }
}

public class LinkedListIterator extends Iterator {
  LinkedList ll;
  LinkedListElement itr;
  Object removeable;
  
  public LinkedListIterator( LinkedList ll ) {
    this.ll = ll;
    itr = ll.head;
    removeable = null;
  }

  public boolean hasNext() {
    return itr != null;
  }

  public Object next() {
    if( itr == null ) {
      System.out.println( "LinkedListIterator: illegal next()" );
      System.exit(-1);
    }
    removeable = itr.element;
    itr = itr.next;
    return removeable;
  }

  public void remove() {
    if( removeable == null ) {
      System.out.println( "LinkedListIterator: illegal remove()" );
      System.exit(-1);
    }
    ll.remove( removeable );
    removeable = null;
  }
}
