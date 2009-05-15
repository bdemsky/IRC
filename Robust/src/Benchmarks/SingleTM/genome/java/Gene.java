public class Gene {
  public long length;
  public String contents;
  public Bitmap startBitmapPtr; /* used for creating segments */
  
  Gene(long myLength) {
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

//    System.out.println("length: " + length);

    for (i = 0; i < length; i++) {
      int legitimateNumber = (int)randomObj.random_generate(randomObj); 
      if(legitimateNumber < 0) {
        legitimateNumber *= -1;
      }
//      System.out.println("legitNum: " + legitimateNumber + ":" + (legitimateNumber % 4));
      arrayContents[i] = nucleotides[legitimateNumber % 4];
//      System.out.println("arrayContents[" + i + "]: " + arrayContents[i]);
    }
    
    contents = new String(arrayContents);
//    System.out.println("contents: " + contents);
  }  
}
