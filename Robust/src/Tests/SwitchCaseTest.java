public class SwitchCaseTest {
  public SwitchCaseTest(){}
  
  public static void main(String[] args) {
    Random rand = new Random(47);
    for(int i = 0; i < 30; i++) {
      int c = rand.nextInt(26) + 'a';
      System.out.printString((char)c + ", " + c + ": ");
      switch(c) {
        case 'a':
        case 'e':
        case 'i':
        case 'o':
        case 'u': System.out.printString("vowel\n");
                  //break;
        case 'y':
        case 'w': System.out.printString("Sometimes a vowel\n");
                  break;
        default: System.out.printString("consonant\n");
      }
    }
  }
} /* Output:
y, 121: Sometimes a vowel
n, 110: consonant
z, 122: consonant
b, 98: consonant
r, 114: consonant
n, 110: consonant
y, 121: Sometimes a vowel
g, 103: consonant
c, 99: consonant
f, 102: consonant
o, 111: vowel
w, 119: Sometimes a vowel
z, 122: consonant
...
*///:~
