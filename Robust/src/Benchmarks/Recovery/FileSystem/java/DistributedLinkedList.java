public class DistributedLinkedListElement {
  public DistributedLinkedListElement next;
  public DistributedLinkedListElement prev;
  public Object element;

  public DistributedLinkedListElement(Object e,
                           DistributedLinkedListElement n,
                           DistributedLinkedListElement p) {
    element = e;
    next = n;
    prev = p;
  }
}

public class DistributedLinkedList {
  DistributedLinkedListElement head;
  DistributedLinkedListElement tail;
  int size;

  public DistributedLinkedList() {
    clear();
  }

  public add(Object o) {
    if( tail == null ) {
      head =  new DistributedLinkedListElement(o, null, null);
      tail = head;

    } else {
      tail.next =  new DistributedLinkedListElement(o, null, tail);
      tail = tail.next;
    }
    size++;
  }

  public addFirst(Object o) {
    if( head == null ) {
      head =  new DistributedLinkedListElement(o, null, null);
      tail = head;

    } else {
      head.prev =  new DistributedLinkedListElement(o, head, null);
      head = head.prev;
    }
    size++;
  }

  public addLast(Object o) {
    add(o);
  }

  public clear() {
    head = null;
    tail = null;
    size = 0;
  }

  public int size() {
    return size;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  public Object clone() {
    System.out.println("LinkedList.clone() not implemented.");
    System.exit(-1);
  }

  public boolean contains(Object o) {
    DistributedLinkedListElement e = head;
		if (o==null) {
      while(e!=null) {
				if (e.element==null) {
					return true;
				}
				e=e.next;
			}
			return false;
		} else {
			while( e != null ) {
				if (o.equals(e.element)) {
					return true;
				}
				e = e.next;
			}
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

  public Object removeFirst() {
    if( head == null ) {
      System.out.println("LinkedList: illegal removeFirst()");
      System.exit(-1);
    }
    Object o = head.element;
    head = head.next;
    if( head != null ) {
      head.prev = null;
    } else {
      tail = null;
    }
    size--;
    return o;
  }

  public Object removeLast() {
    if( tail == null ) {
      System.out.println("LinkedList: illegal removeLast()");
      System.exit(-1);
    }
    Object o = tail.element;
    tail = tail.prev;
    if( tail != null ) {
      tail.next = null;
    } else {
      head = null;
    }
    size--;
    return o;
  }

  public void remove(Object o) {
    if( head == null ) {
      System.out.println("LinkedList: illegal remove( Object o )");
      System.exit(-1);
    }
    DistributedLinkedListElement e = head;
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
    System.out.println("LinkedList: illegal remove( Object o ), "+o+" not found");
    System.exit(-1);
  }

  public Object pop() {
    Object o = getFirst();
    removeFirst();
    return o;
  }

  public void push(Object o) {
    addFirst(o);
  }

  public Iterator iterator() {
    return  new DistributedLinkedListIterator(this);
  }
}

public class DistributedLinkedListIterator extends Iterator {
  DistributedLinkedList ll;
  DistributedLinkedListElement itr;
  Object removeable;

  public DistributedLinkedListIterator(DistributedLinkedList ll) {
    this.ll = ll;
    itr = ll.head;
    removeable = null;
  }

  public boolean hasNext() {
    return (null != itr);
  }

  public Object next() {
    if( itr == null ) {
      System.out.println("LinkedListIterator: illegal next()");
      System.exit(-1);
    }
    removeable = itr.element;
    itr = itr.next;
    return removeable;
  }

  public void remove() {
    if( removeable == null ) {
      System.out.println("LinkedListIterator: illegal remove()");
      System.exit(-1);
    }
    ll.remove(removeable);
    removeable = null;
  }
}

