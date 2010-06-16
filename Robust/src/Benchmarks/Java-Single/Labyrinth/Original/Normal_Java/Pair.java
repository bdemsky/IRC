

public class Pair {
    public Object first;
    public Object second;

    public Pair() {
        first = null;
        second = null;
    }


/* =============================================================================
 * 
 * pair constructor
 * 
 * pair_t* pair_alloc(void* firstPtr, void* secondPtr);
 * =============================================================================
 */
    public static Pair alloc(Object first,Object second)
    {
        Pair ptr= new Pair();
        ptr.first = first;
        ptr.second = second;

        return ptr;
    }



/* =============================================================================
 * Ppair_alloc
 *
 * -- Returns NULL if failure
 * =============================================================================
 */
  public static Pair Ppair_alloc (Object firstPtr, Object secondPtr) {
    Pair pairPtr = new Pair();       
    pairPtr.first = firstPtr;
    pairPtr.second = secondPtr;
    return pairPtr;
  }


/* =============================================================================
 * pair_free
 * =============================================================================
 *
 *  void pair_free (pair_t* pairPtr);
 *
 */
    public static void free(Pair pairPtr)
    {
        pairPtr = null;
    }


/* =============================================================================
 * Ppair_free
 * =============================================================================
 *
void Ppair_free (pair_t* pairPtr);
*/

/* =============================================================================
 * pair_swap
 * -- Exchange 'firstPtr' and 'secondPtr'
 * =============================================================================
 * void pair_swap (pair_t* pairPtr);
*/
    public static void swap(Pair pairPtr)
    {
        Object tmpPtr = pairPtr.first;

        pairPtr.first = pairPtr.second;
        pairPtr.second = tmpPtr;
    }

}    

/* =============================================================================
 *
 * End of pair.java
 *
 * =============================================================================
 */
