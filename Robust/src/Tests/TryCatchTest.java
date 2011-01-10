public class TryCatchTest {
  
  public static void main(String[] args) {
    try{
      System.out.println("try block");
    }catch(Exception e) {
      System.out.println("catch block");
    }finally{
      System.out.println("finally block");
    }
  }
}