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
      return ((segment == copy.segment) && (isStart == copy.isStart) && (endHash == copy.endHash) && (startPtr == copy.startPtr) && (nextPtr == copy.nextPtr) && (endPtr == copy.endPtr) && (overlap == copy.overlap) && (length == copy.length));
    }
}
