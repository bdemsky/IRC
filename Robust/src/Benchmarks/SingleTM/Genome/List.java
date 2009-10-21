public class List {
  ListNode head;
  int size;

  public List () {
    head = new ListNode();
    head.dataPtr = null;
    head.nextPtr = null;
    size = 0;
  }

  Pair find (Pair dataPtr) {
    ListNode nodePtr;
    ListNode prevPtr = findPrevious(dataPtr);

    nodePtr = prevPtr.nextPtr;

    if ((nodePtr == null) || (compareSegment(nodePtr.dataPtr, dataPtr) != 0)) {
      return null;
    }

    return (nodePtr.dataPtr);
  }

  ListNode findPrevious (Pair dataPtr) {
    ListNode prevPtr = head;
    ListNode nodePtr;
    nodePtr = prevPtr.nextPtr;

    for (; nodePtr != null; nodePtr = nodePtr.nextPtr) {
      if (compareSegment(nodePtr.dataPtr, dataPtr) >= 0) {
        return prevPtr;
      }
      prevPtr = nodePtr;
    }

    return prevPtr;
  }

  boolean insert (Pair dataPtr) {
    ListNode prevPtr;
    ListNode nodePtr;
    ListNode currPtr;

    prevPtr = findPrevious(dataPtr);
    currPtr = prevPtr.nextPtr;

    if ((currPtr != null) && (compareSegment((Pair)currPtr.dataPtr, (Pair)dataPtr) == 0)) {
      return false;
    }

    nodePtr = new ListNode(dataPtr);

    nodePtr.nextPtr = currPtr;
    prevPtr.nextPtr = nodePtr;
    size++;

    return true;
  }

  int compareSegment (Pair a, Pair b) { 
    ByteString aString = a.firstPtr;
    ByteString bString = b.firstPtr;
    return aString.compareTo(bString);
  }
}
