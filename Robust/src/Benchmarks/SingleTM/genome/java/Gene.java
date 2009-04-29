public class Gene {
  public long length;
  public String contents;
  public Bitmap startBitmapPtr; /* used for creating segments */
  
  Gene(long myLength) {
    length = myLength;
    contents = "";
    startBitmapPtr = new BitMap(length);
  }


/* =============================================================================
 * gene_create
 * -- Populate contents with random gene
 * =============================================================================
 */
  void create (Random randomObj) {
    long i;
    char nucleotides[] = {
        NUCLEOTIDE_ADENINE,
        NUCLEOTIDE_CYTOSINE,
        NUCLEOTIDE_GUANINE,
        NUCLEOTIDE_THYMINE,
    };

    for (i = 0; i < length; i++) {
      contents[i] = nucleotides[(random_generate(randomObj)% NUCLEOTIDE_NUM_TYPE)];
    }
  }  
}
