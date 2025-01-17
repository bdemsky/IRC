public class Bitmap {
  public long numBit;
  public long numWord;
  public long bits[];
  
  public int NUM_BIT_PER_BYTE;
  public int NUM_BIT_PER_WORD;

  
  /* =============================================================================
   * bitmap_alloc
   * -- Returns NULL on failure
   * =============================================================================
   */
  Bitmap(long myNumBit) {

    NUM_BIT_PER_BYTE = 8;
    NUM_BIT_PER_WORD = ((8) * NUM_BIT_PER_BYTE);

    numBit = myNumBit;
    numWord = DIVIDE_AND_ROUND_UP(numBit, NUM_BIT_PER_WORD);

    bits = new long[numWord];
    
    int i = 0;
    for(i = 0; i < numWord; i++) {
      bits[i] = 0;
    }
  }

  Bitmap(Bitmap myBitMap) {
    NUM_BIT_PER_BYTE = 8;
    NUM_BIT_PER_WORD = ((8) * NUM_BIT_PER_BYTE);


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
      return false;
    }

    bits[((int)i)/NUM_BIT_PER_WORD] |= (1 << (i % NUM_BIT_PER_WORD));

    return true;
  }


  /* =============================================================================
   * bitmap_clear
   * -- Clears ith bit to 0
   * -- Returns TRUE on success, else FALSE
   * =============================================================================
   */
  boolean clear (long i) {
      if ((i < 0) || (i >= numBit)) {
      return false;
    }

    bits[((int)i)/NUM_BIT_PER_WORD] &= ~(1 << (i % NUM_BIT_PER_WORD));

    return true;
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
  boolean isSet (int i) {
    int tempB = (int)bits[((int)i)/NUM_BIT_PER_WORD];
    int tempC = (1 << (((int)i) % NUM_BIT_PER_WORD));
    boolean tempbool = ((tempB & tempC) > 0) ? true:false;
    //tempB /*bits[((int)i)/NUM_BIT_PER_WORD]*/ & tempC /*(1 << (i % NUM_BIT_PER_WORD))*/ 
    if ((i >= 0) && (i < (int)numBit) && tempbool) {
        return true;
    }

    return false;
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
    boolean tempbool = ((bits[((int)i)/NUM_BIT_PER_WORD] & (1 << (i % NUM_BIT_PER_WORD))) > 0) ? true:false;
    for (i = MAX(startIndex, 0); i < numBit; i++) {
        if (!tempbool) {
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
      boolean tempbool = ((int)bits[((int)i)/NUM_BIT_PER_WORD] & (1 << ((int)i % NUM_BIT_PER_WORD)) > 0) ? true:false;
        if (tempbool) {
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
        boolean tempbool = ((int)bits[((int)i)/NUM_BIT_PER_WORD] & (1 << ((int)i % NUM_BIT_PER_WORD)) > 0) ? true:false;
        if (tempbool) {
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
        bits[(int)w] ^= -1L;
    }
  }
  
  long DIVIDE_AND_ROUND_UP(long a, long b) {
    return (a/b) + (((a % b) > 0) ? (1) : (0));
  }
  
  long MAX(long a, long b) {
    return (a > b) ? a : b; 
  }  
}
