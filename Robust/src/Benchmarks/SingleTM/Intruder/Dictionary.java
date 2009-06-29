public class Dictionary {
  String global_defaultSignatures[];
  long global_numDefaultSignature = 72;

  public Dictionary() {
    global_defaultSignatures = new String[72];

    global_defaultSignatures[0] =    "about";
    global_defaultSignatures[1] =    "after";
    global_defaultSignatures[2] =    "all";
    global_defaultSignatures[3] =    "also";
    global_defaultSignatures[4] =    "and";
    global_defaultSignatures[5] =    "any";
    global_defaultSignatures[6] =    "back";
    global_defaultSignatures[7] =    "because";
    global_defaultSignatures[8] =    "but";
    global_defaultSignatures[9] =    "can";
    global_defaultSignatures[10] =    "come";
    global_defaultSignatures[11] =    "could";
    global_defaultSignatures[12] =    "day";
    global_defaultSignatures[13] =    "even";
    global_defaultSignatures[14] =    "first";
    global_defaultSignatures[15] =    "for";
    global_defaultSignatures[16] =    "from";
    global_defaultSignatures[17] =    "get";
    global_defaultSignatures[18] =    "give";
    global_defaultSignatures[20] =    "good";
    global_defaultSignatures[21] =    "have";
    global_defaultSignatures[22] =    "him";
    global_defaultSignatures[23] =    "how";
    global_defaultSignatures[24] =    "into";
    global_defaultSignatures[25] =    "its";
    global_defaultSignatures[26] =    "just";
    global_defaultSignatures[27] =    "know";
    global_defaultSignatures[28] =    "like";
    global_defaultSignatures[29] =    "look";
    global_defaultSignatures[30] =    "make";
    global_defaultSignatures[31] =    "most";
    global_defaultSignatures[32] =    "new";
    global_defaultSignatures[33] =    "not";
    global_defaultSignatures[34] =    "now";
    global_defaultSignatures[35] =    "one";
    global_defaultSignatures[36] =    "only";
    global_defaultSignatures[37] =    "other";
    global_defaultSignatures[38] =    "out";
    global_defaultSignatures[39] =    "over";
    global_defaultSignatures[40] =    "people";
    global_defaultSignatures[41] =    "say";
    global_defaultSignatures[42] =    "see";
    global_defaultSignatures[43] =    "she";
    global_defaultSignatures[44] =    "some";
    global_defaultSignatures[45] =    "take";
    global_defaultSignatures[46] =    "than";
    global_defaultSignatures[47] =    "that";
    global_defaultSignatures[48] =    "their";
    global_defaultSignatures[49] =    "them";
    global_defaultSignatures[50] =    "then";
    global_defaultSignatures[51] =    "there";
    global_defaultSignatures[52] =    "these";
    global_defaultSignatures[53] =    "they";
    global_defaultSignatures[54] =    "think";
    global_defaultSignatures[55] =    "this";
    global_defaultSignatures[56] =    "time";
    global_defaultSignatures[57] =    "two";
    global_defaultSignatures[58] =    "use";
    global_defaultSignatures[59] =    "want";
    global_defaultSignatures[60] =    "way";
    global_defaultSignatures[61] =    "well";
    global_defaultSignatures[62] =    "what";
    global_defaultSignatures[63] =    "when";
    global_defaultSignatures[64] =    "which";
    global_defaultSignatures[65] =    "who";
    global_defaultSignatures[66] =    "will";
    global_defaultSignatures[67] =    "with";
    global_defaultSignatures[68] =    "work";
    global_defaultSignatures[69] =    "would";
    global_defaultSignatures[70] =    "year";
    global_defaultSignatures[71] =    "your"
  }

  String dictionary_get(long i) {
    if (i < 0 || i >= global_numDefaultSignature) {
      System.out.print("dictionary_get: Index out of bounds");
    }
    return global_defaultSignatures[i];
  }

  String dictionary_match(String str) {
    long i;

    for (i = 0; i < global_defaultSignatures; i++) {
      if(global_defaultSignatures[i].equals(str)) {
        return global_defaultSignatures[i];
      } 
    }
    return null;
  }
}
