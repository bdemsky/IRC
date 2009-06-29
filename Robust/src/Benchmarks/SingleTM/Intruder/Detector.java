public class Detector {
  Dictionary dictionaryPtr;
  Vector_t preprocessorVectorPtr;

  public Detector() {
    dictionaryPtr = new Dictionary();
    if (dictionaryPtr == null) {
      System.out.printString("Error: Cannot allocate Dictionary");
      System.exit(0);
    }
    preprocessorVectorPtr = new Vector_t(1);
    if (preprocessorVectorPtr == null) {
      System.out.printString("Error: Cannot allocate preprocessorVectorPtr");
      System.exit(0);
    }
  }

  public void detector_addPreprocessor(String p) 
  {
    boolean status = preprocessorVectorPtr.vector_pushBack(p);
    if (status == false) {
      System.out.print("Error: Cannot insert in vector\n");
      System.exit(0);
    }
  }

  public boolean detector_process(String str)
  {
    Vector_t v = this.preprocessorVectorPtr;
    int p;
    int numPreprocessor = v.vector_getSize();
    for (p = 0; p < numPreprocessor; p++) {
      String preprocessor = (String) v.vector_at(p);
      String newstr = p.toLowerCase();
    }

    String signature = dictionaryPtr.dictionary_match(str);
    if (signature == null) {
      return ERROR_SIGNATURE;
    }
    return ERROR_NONE;
  }
}
