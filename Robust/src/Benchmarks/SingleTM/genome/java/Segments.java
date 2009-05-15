public class Segments {
    public long length;
    public long minNum;
    Vector contentsPtr;
/* private: */
    String strings[];
    
    Segments (long myLength, long myMinNum) {
      minNum = myMinNum;
      length = myLength;
      
      strings = new String[(int)minNum];
      contentsPtr = new Vector((int)minNum);
    }


    /* =============================================================================
     * segments_create
     * -- Populates 'contentsPtr'
     * =============================================================================
     */
    void create (Gene genePtr, Random randomPtr) {
        String geneString;
        long geneLength;
        Bitmap startBitmapPtr;
        long numStart;
        int i;
        long maxZeroRunLength;

        geneString = genePtr.contents;
        geneLength = genePtr.length;
        startBitmapPtr = genePtr.startBitmapPtr;
        numStart = geneLength - length + 1;
        
        System.out.println("minNum: " + minNum);
        /* Pick some random segments to start */
        for (i = 0; i < minNum; i++) {
            int j = (int)(randomPtr.random_generate(randomPtr) % numStart);
            boolean status = startBitmapPtr.set(j);
            strings[i] = geneString.substring((int)j, (int)(j+length));
            contentsPtr.addElement(strings[i]);
        }
        

        
        /* Make sure segment covers start */
        i = 0;
        if (!startBitmapPtr.isSet(i)) {
            String string;
            string = geneString.subString((int)i, (int)(i+length));
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
                String string = geneString.subString((int)i, (int)(i+length));
                contentsPtr.addElement(string);
                startBitmapPtr.set(i);
            }
        }
        
        System.out.println("gene: " + geneString);
        for(i = 0; i < contentsPtr.size(); i++) {
          System.out.print(" " + contentsPtr.array[i]);
        }
        System.out.println("");
        
    }
}
