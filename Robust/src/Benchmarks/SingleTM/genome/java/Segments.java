public class Segments {
    public long length;
    public long minNum;
    Vector contentsPtr;
/* private: */
    String strings[];
    
    Segments (long myLength, long myMinNum) {
      minNum = myMinNum;
      length = myLength;

      contentsPtr = new Vector(minNum);

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
        long i;
        long maxZeroRunLength;

        geneString = genePtr.contents;
        geneLength = genePtr.length;
        startBitmapPtr = genePtr.startBitmapPtr;
        numStart = geneLength - segmentLength + 1;

        /* Pick some random segments to start */
        for (i = 0; i < minNumSegment; i++) {
            long j = (long)(random_generate(randomPtr) % numStart);
            boolean status = startBitmapPtr.set(j);
            strings[i] = geneString[j];
            segmentsContentsPtr.add(strings[i]);
        }

        /* Make sure segment covers start */
        i = 0;
        if (!startBitmapPtr.isSet(i)) {
            String string;
            string = geneString[i];
            segmentsContentsPtr.add(string);
            startBitmapPtr.set(i);
        }

        /* Add extra segments to fill holes and ensure overlap */
        maxZeroRunLength = length - 1;
        for (i = 0; i < numStart; i++) {
            long i_stop = MIN((i+maxZeroRunLength), numStart);
            for ( /* continue */; i < i_stop; i++) {
                if (startBitmapPtr.isSet(i)) {
                    break;
                }
            }
            if (i == i_stop) {
                /* Found big enough hole */
                i = i - 1;
                String string = geneString[i];
                segmentsContentsPtr.add(string);
                startBitmapPtr.set(i);
            }
        }
    }
}