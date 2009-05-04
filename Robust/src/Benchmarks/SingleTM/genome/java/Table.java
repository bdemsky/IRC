public class Table {

    LinkedList buckets[];
    long numBucket;


    /* =============================================================================
     * table_alloc
     * -- Returns NULL on failure
     * =============================================================================
     */
    Table (long myNumBucket) {
    
      long i;

      buckets = new LinkedList[myNumBucket];

      numBucket = myNumBucket;
      
    }


    /* =============================================================================
     * table_insert
     * -- Returns TRUE if successful, else FALSE
     * =============================================================================
     */
    boolean table_insert (long hash, void* dataPtr) {
      long i = hash % numBucket;

      if(buckets[i].indexOf(dataPtr) != -1) {
        return FALSE;
      }

      buckets[i].add(dataPtr);

      return TRUE;
    }

    /* =============================================================================
     * table_remove
     * -- Returns TRUE if successful, else FALSE
     * =============================================================================
     */
    boolean table_remove (long hash, void* dataPtr) {
    
      long i = hash % numBucket;

      if (!buckets[i].remove(dataPtr) {
          return FALSE;
      }

      return TRUE;
    
    }

}