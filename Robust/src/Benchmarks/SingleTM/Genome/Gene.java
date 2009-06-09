public class Gene {
  public int length;
  public String contents;
  public Bitmap startBitmapPtr; /* used for creating segments */
  
  Gene(int myLength) {
    length = myLength;
    contents = "";
    startBitmapPtr = new Bitmap(length);
  }


/* =============================================================================
 * gene_create
 * -- Populate contents with random gene
 * =============================================================================
 */
  void create (Random randomObj) {
    int i;
    char[] nucleotides = new char[4];
    char[] arrayContents = new char[length];
    nucleotides[0] = 'a';
    nucleotides[1] = 'c';
    nucleotides[2] = 'g';
    nucleotides[3] = 't';

    for (i = 0; i < length; i++) {
      int legitimateNumber = (int)randomObj.random_generate(); 
      if(legitimateNumber < 0) {
        legitimateNumber *= -1;
      }
      arrayContents[i] = nucleotides[legitimateNumber % 4];
    }
    
    contents = new String(arrayContents);
  }  
}
