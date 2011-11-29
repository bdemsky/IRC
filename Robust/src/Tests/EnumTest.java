public enum Spiciness {
  NOT, MILD, MEDIUM, HOT, FLAMING
} ///:~

public class EnumTest {
  
  public Spiciness1 howHot;
  
  public static enum Spiciness1 {
    NOT, FLAMING, MILD, MEDIUM, HOT
  } ///:~
  
  //public EnumTest(){}
  
  public static void main(String[] args) {
    Spiciness howHot = Spiciness.MEDIUM;
    System.out.println(howHot);
    
    EnumTest et = new EnumTest();
    et.howHot = EnumTest.Spiciness1.MEDIUM;
    System.out.println(et.howHot);
  }
} /* Output:
MEDIUM
*///:~
