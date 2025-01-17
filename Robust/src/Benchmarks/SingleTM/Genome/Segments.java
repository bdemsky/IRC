public class Segments {
  public int length;
  public int minNum;
  Vector contentsPtr;
  /* private: */
  ByteString strings[];

  Segments (int myLength, int myMinNum) {
    minNum = myMinNum;
    length = myLength;

    strings = new ByteString[minNum];
    contentsPtr = new Vector(minNum);
  }


  /* =============================================================================
   * segments_create
   * -- Populates 'contentsPtr'
   * =============================================================================
   */
  void create (Gene genePtr, Random randomPtr) {
    ByteString geneString;
    int geneLength;
    Bitmap startBitmapPtr;
    int numStart;
    int i;
    int maxZeroRunLength;

    geneString = genePtr.contents;
    geneLength = genePtr.length;
    startBitmapPtr = genePtr.startBitmapPtr;
    numStart = geneLength - length + 1;

    /* Pick some random segments to start */
    for (i = 0; i < minNum; i++) {
      int j = (int)(randomPtr.random_generate() % numStart);
      boolean status = startBitmapPtr.set(j);
      strings[i] = geneString.substring(j, j+length);
      contentsPtr.addElement(strings[i]);
    }

    /* Make sure segment covers start */
    i = 0;
    if (!startBitmapPtr.isSet(i)) {
      ByteString string = geneString.subString(i, i+length);
      contentsPtr.addElement(string);
      startBitmapPtr.set(i);
    }

    /* Add extra segments to fill holes and ensure overlap */
    maxZeroRunLength = length - 1;
    for (i = 0; i < numStart; i++) {
      int i_stop = Math.imin((i+maxZeroRunLength), numStart);
      for ( /* continue */; i < i_stop; i++) {
        if (startBitmapPtr.isSet(i)) {
          break;
        }
      }
      if (i == i_stop) {
        /* Found big enough hole */
        i = i - 1;
        ByteString string = geneString.subString(i, i+length);
        contentsPtr.addElement(string);
        startBitmapPtr.set(i);
      }
    }
  }
}
