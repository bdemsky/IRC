public class Bitmap {
  public long numBit;
  public long numWord;
  public long bits[];
  
  private static NUM_BIT_PER_BYTE = 8;
  private static NUM_BIT_PER_WORD = (8) * NUM_BIT_PER_BYTE)

  
  /* =============================================================================
   * bitmap_alloc
   * -- Returns NULL on failure
   * =============================================================================
   */
  Bitmap(long myNumBit) {

    numBit = myNumBit;
    numWord = DIVIDE_AND_ROUND_UP(numBit, NUM_BIT_PER_WORD);

    bits = new long[numWord];
    
    int i = 0;
    for(i = 0; i < numWord; i++) {
      bits[i] = 0;
    }
  }

  Bitmap(Bitmap myBitMap) {
    numBit = myBitMap.numBit;
    numWord = myBitMap.numWord;
    bits = new long[numWord];
    int i = 0;
    for(i = 0; i < numWord; i++) {
      bits[i] = myBitMap.bits[i];
    }
  }

  /* =============================================================================
   * Pbitmap_alloc
   * -- Returns NULL on failure
   * =============================================================================
   */
  //bitmap_t* Pbitmap_alloc (long numBit) { }


  /* =============================================================================
   * bitmap_free
   * =============================================================================
   */
  //void bitmap_free (bitmap_t* bitmapPtr);


  /* =============================================================================
   * Pbitmap_free
   * =============================================================================
   */
  //void Pbitmap_free (bitmap_t* bitmapPtr);


  /* =============================================================================
   * bitmap_set
   * -- Sets ith bit to 1
   * -- Returns TRUE on success, else FALSE
   * =============================================================================
   */
  boolean set (long i) {
    if ((i < 0) || (i >= numBit)) {
      return FALSE;
    }

    bits[i/NUM_BIT_PER_WORD] |= (1 << (i % NUM_BIT_PER_WORD));

    return TRUE;
  }


  /* =============================================================================
   * bitmap_clear
   * -- Clears ith bit to 0
   * -- Returns TRUE on success, else FALSE
   * =============================================================================
   */
  boolean clear (long i) {
      if ((i < 0) || (i >= numBit)) {
      return FALSE;
    }

    bits[i/NUM_BIT_PER_WORD] &= ~(1 << (i % NUM_BIT_PER_WORD));

    return TRUE;
  }


  /* =============================================================================
   * bitmap_clearAll
   * -- Clears all bit to 0
   * =============================================================================
   */
  void clearAll () {
    int i = 0;
    for(i = 0; i < numWord; i++) {
      bits[i] = 0;
    }
  }


  /* =============================================================================
   * bitmap_isSet
   * -- Returns TRUE if ith bit is set, else FALSE
   * =============================================================================
   */
  boolean isSet (long i) {
    if ((i >= 0) && (i < numBit) &&
        (bits[i/NUM_BIT_PER_WORD] & (1 << (i % NUM_BIT_PER_WORD)))) {
        return TRUE;
    }

    return FALSE;
  }


  /* =============================================================================
   * bitmap_findClear
   * -- Returns index of first clear bit
   * -- If start index is negative, will start from beginning
   * -- If all bits are set, returns -1
   * =============================================================================
   */
  long findClear (long startIndex) {
    long i;

    for (i = MAX(startIndex, 0); i < numBit; i++) {
        if (!(bits[i/NUM_BIT_PER_WORD] & (1 << (i % NUM_BIT_PER_WORD)))) {
            return i;
        }
    }

    return -1;
  }


  /* =============================================================================
   * bitmap_findSet
   * -- Returns index of first set bit
   * -- If all bits are clear, returns -1
   * =============================================================================
   */
  long findSet (long startIndex) {
    long i;

    for (i = MAX(startIndex, 0); i < numBit; i++) {
        if (bits[i/NUM_BIT_PER_WORD] & (1 << (i % NUM_BIT_PER_WORD))) {
            return i;
        }
    }

    return -1;
  }


  /* =============================================================================
   * bitmap_getNumClear
   * =============================================================================
   */
  long getNumClear () {
    return (numBit - getNumSet());
  }


  /* =============================================================================
   * bitmap_getNumSet
   * =============================================================================
   */
  long getNumSet () {
    long i;
    long count = 0;

    for (i = 0; i < numBit; i++) {
        if (bits[i/NUM_BIT_PER_WORD] & (1 << (i % NUM_BIT_PER_WORD))) {
            count++;
        }
    }

    return count;
  }

  /* =============================================================================
   * bitmap_copy
   * =============================================================================
   */
  //void copy(bitmap_t* dstPtr, bitmap_t* srcPtr);
  // SEE COPY CONSTRUCTOR

  /* =============================================================================
   * bitmap_toggleAll
   * =============================================================================
   */
  void toggleAll () {
    long w;
    for (w = 0; w < numWord; w++) {
        bits[w] ^= -1L;
    }
  }
  
  long DIVIDE_AND_ROUND_UP(long a, long b) {
    return (a/b) + (((a % b) > 0) ? (1) : (0));
  }
  
  long MAX(long a, long b) {
    return (a > b) ? a : b; 
  }  
}
