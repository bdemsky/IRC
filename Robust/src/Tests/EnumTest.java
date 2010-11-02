public enum Spiciness {
  NOT, MILD, MEDIUM, HOT, FLAMING
} ///:~

public class EnumTest {
  
  public EnumTest(){}
  
  public static void main(String[] args) {
    Spiciness howHot = Spiciness.MEDIUM;
    System.out.println(howHot);
  }
} /* Output:
MEDIUM
*///:~
