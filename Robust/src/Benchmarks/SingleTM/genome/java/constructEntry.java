public class constructEntry {
    boolean isStart;
    String segment;
    long endHash;
    constructEntry startPtr;
    constructEntry nextPtr;
    constructEntry endPtr;
    long overlap;
    long length;
      
    constructEntry(String mySegment, boolean myStart, long myEndHash, constructEntry myStartPtr, constructEntry myNextPtr, constructEntry myEndPtr, long myOverlap, long myLength) {
      segment = mySegment;
      isStart = myStart;
      endHash = myEndHash;
      startPtr = this;
      nextPtr = myNextPtr;
      endPtr = this;
      overlap = myOverlap;
      length = myLength;
    }
    
    boolean equals(constructEntry copy) {
/*      int i = 0;
      for(i = 0; i < length; i++) {
        if(segment[i] != copy.segment[i]) {
          return false;
        }
      }*/
      return ((segment.compareTo(copy.segment) == 0) && (isStart == copy.isStart) && (endHash == copy.endHash) && (startPtr == copy.startPtr) && (nextPtr == copy.nextPtr) && (endPtr == copy.endPtr) && (overlap == copy.overlap) && (length == copy.length));
    }
}
