public class Hashtable {

    List buckets[];
    long numBucket;
    long size;
    // MOVE HASH FUNCTION
    // MOVE COMPARISON FUNCTION
    long resizeRatio;
    long growthFactor;
    
    
    public Hashtable (long initNumBucket, long resizeRatio, long growthFactor) {

      allocBuckets(initNumBucket);
      numBucket = initNumBucket;
      size = 0;
      resizeRatio = ((resizeRatio < 0) ? 3 : resizeRatio);
      growthFactor = ((growthFactor < 0) ? 3 : growthFactor);
    }
    
    boolean TMhashtable_insert (String keyPtr, String dataPtr) {
      long i = hashSegment(keyPtr) % numBucket;

      Pair findPair = new Pair();
      findPair.firstPtr = keyPtr;
      Pair pairPtr = buckets[(int)i].find(findPair);
      if (pairPtr != null) {
          return false;
      }

      Pair insertPtr = new Pair(keyPtr, dataPtr);

      /* Add new entry  */
      if (buckets[(int)i].insert(insertPtr) == false) {
          return false;
      }

      size++;

      return true;
    }
    
    void allocBuckets (long numBucket) {
      long i;
      /* Allocate bucket: extra bucket is dummy for easier iterator code */
      buckets = new List[numBucket+1];
      
      for (i = 0; i < (numBucket + 1); i++) {
          List chainPtr = new List();
          buckets[(int)i] = chainPtr;
      }
    }
    
    long hashSegment (String str) {
      long hash = 0;

      int index = 0;
      /* Note: Do not change this hashing scheme */
      for(index = 0; index < str.length(); index++) {
        char c = str.charAt(index);
        hash = c + (hash << 6) + (hash << 16) - hash;
      }
  
      if(hash < 0) hash *= -1;

      return hash;
    }
}
