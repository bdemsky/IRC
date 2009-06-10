public class Gene {
  public long length;
  public ByteArray contents;
  public Bitmap startBitmapPtr; /* used for creating segments */
  
  Gene(long myLength) {
    length = myLength;
    byte array[] = new byte[length];
    contents = new ByteArray(array);
    startBitmapPtr = new Bitmap(length);
  }


/* =============================================================================
 * gene_create
 * -- Populate contents with random gene
 * =============================================================================
 */
  void create (Random randomObj) {
    int i;
    byte[] nucleotides = new byte[4];
    byte[] arrayContents = new byte[length];
    nucleotides[0] = (byte)'a';
    nucleotides[1] = (byte)'c';
    nucleotides[2] = (byte)'g';
    nucleotides[3] = (byte)'t';

//    System.out.println("length: " + length);

    for (i = 0; i < length; i++) {
      int legitimateNumber = (int)randomObj.random_generate(randomObj); 
      if(legitimateNumber < 0) {
        legitimateNumber *= -1;
      }
//      System.out.println("legitNum: " + legitimateNumber + ":" + (legitimateNumber % 4));
      arrayContents[i] = nucleotides[legitimateNumber % 4];
//      System.out.println("arrayContents[" + i + "]: " + (char)arrayContents[i]);
    }
    
    contents = new ByteArray(arrayContents);
  }  
}
