public class Segments {
    public long length;
    public long minNum;
    Vector contentsPtr;
/* private: */
    ByteArray strings[];
    
    Segments (long myLength, long myMinNum) {
      minNum = myMinNum;
      length = myLength;
      strings = new ByteArray[minNum];
      contentsPtr = new Vector((int)minNum);
    }


    /* =============================================================================
     * segments_create
     * -- Populates 'contentsPtr'
     * =============================================================================
     */
    void create (Gene genePtr, Random randomPtr) {
        ByteArray geneString;
        long geneLength;
        Bitmap startBitmapPtr;
        long numStart;
        int i;
        long maxZeroRunLength;

        geneString = genePtr.contents;
        geneLength = genePtr.length;
        startBitmapPtr = genePtr.startBitmapPtr;
        numStart = geneLength - length + 1;
//        String str = new String(geneString.array);
//        System.out.println("str: " + str);
//        System.out.println("minNum: " + minNum);
        /* Pick some random segments to start */
//        System.out.println("minNum: " + minNum);
        for (i = 0; i < minNum; i++) {
            int j = (int)(randomPtr.random_generate(randomPtr) % numStart);
            boolean status = startBitmapPtr.set(j);
            strings[i] = geneString.substring((int)j, (int)(j+length)); // WRITE SUBSTRING FUNCTION
//            System.out.print("seg: ");
//            strings[i].print();
            contentsPtr.addElement(strings[i]);
        }
        
///        System.out.println("post random segments selection");
        
        /* Make sure segment covers start */
        i = 0;
        if (!startBitmapPtr.isSet(i)) {
            ByteArray string;
            string = geneString.substring((int)i, (int)(i+length)); // USE BYTE SUBSTRING FUNCTION
            contentsPtr.addElement(string);
            startBitmapPtr.set(i);
        }

        /* Add extra segments to fill holes and ensure overlap */
        maxZeroRunLength = length - 1;
        for (i = 0; i < numStart; i++) {
            long i_stop = (long)Math.imin((int)(i+maxZeroRunLength), (int)numStart);
            for ( /* continue */; i < i_stop; i++) {
                if (startBitmapPtr.isSet(i)) {
                    break;
                }
            }
            if (i == i_stop) {
                /* Found big enough hole */
                i = i - 1;
                ByteArray string = geneString.substring((int)i, (int)(i+length)); // USE BYTE SUBSTRING FUNCTION
                contentsPtr.addElement(string);
                startBitmapPtr.set(i);
            }
        }
        
//        System.out.println("gene: " + geneString);
//        for(i = 0; i < contentsPtr.size(); i++) {
//          System.out.print(" " + contentsPtr.array[i]);
//        }
//        System.out.println("");
        
    }
    
/*    static byte[] byteSubstring(byte a[], int start, int length) {
      byte substring[] = new byte[length];
      int i = start;
      for(i = start; i < start+length; i++) {
        substring[i] = a[start+i];
      }
      return substring;
    }    
    */
}
