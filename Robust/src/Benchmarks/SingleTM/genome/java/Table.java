public class Table {

    LinkedList buckets[];
    long numBucket;


    /* =============================================================================
     * table_alloc
     * -- Returns NULL on failure
     * =============================================================================
     */
    Table (long myNumBucket) {
    
      int i;

      buckets = new LinkedList[myNumBucket];
      for(i = 0; i < myNumBucket; i++) {
        buckets[i] = new LinkedList();      
      }

      numBucket = myNumBucket;
      
    }


    /* =============================================================================
     * table_insert
     * -- Returns TRUE if successful, else FALSE
     * =============================================================================
     */
    boolean table_insert (long hash, Object dataPtr) {
      int i = (int)(hash % numBucket);
      if(i < 0) i *= -1;
      if(buckets[i].contains(dataPtr)) {
        return false;
      }
      buckets[i].add(dataPtr);
      return true;
    }

    /* =============================================================================
     * table_remove
     * -- Returns TRUE if successful, else FALSE
     * =============================================================================
     */
    boolean table_remove (long hash, Object dataPtr) {
    
      int i = (int)(hash % numBucket);
      boolean tempbool = buckets[i].contains(dataPtr);
      if (tempbool) {
          buckets[i].remove(dataPtr);
          return true;
      }

      return false;
    
    }

}
