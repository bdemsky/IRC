public class List {
    ListNode head;
    long size;
    
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
    
    ListNode findPrevious (Object dataPtr) {
      ListNode prevPtr = head;
      ListNode nodePtr;

      for (nodePtr = prevPtr.nextPtr; nodePtr != null; nodePtr = nodePtr.nextPtr) {
        if (compareSegment((Pair)nodePtr.dataPtr, (Pair)dataPtr) >= 0) {
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
      
      
/*    long compareSegment (Pair a, Pair b) { // RE WRITE THIS FOR BYTE ARRAYS
      byte aString[] = (byte[])a.firstPtr;
      byte bString[] = (byte[])b.firstPtr;
      int i = 0;
      while(aString[i] != null) {
        if(bString[i] == null) {
          return 1;
        } else if(aString[i] < bString[i]) {
          return -1;
        } else {
          return 1;
        }
      }
        
      return 0;
    }
*/
    long compareSegment (Pair a, Pair b) { // RE WRITE THIS FOR BYTE ARRAYS
      ByteArray aString = (ByteArray)a.firstPtr;
      ByteArray bString = (ByteArray)b.firstPtr;
      return aString.compareTo(bString);
    }
 
}
