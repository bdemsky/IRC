class StaticTester {
  static int i; // = 47;
  
  static {
    i = 47;
  }
  
  public StaticTester() {}
}

class Incrementable {
  static void increment() { StaticTester.i++; }
  
  public Incrementable() {}
}

public class StaticTest{
  public StaticTest() {
  }

  public static void main(String[] st) {
    //StaticTester.i = 47;
    StaticTester st1 = new StaticTester();
    StaticTester st2 = new StaticTester();
    System.printString("static i: "+StaticTester.i+"\n");
    System.printString("st1 i: "+st1.i+"\n");
    System.printString("st2 i: "+st2.i+"\n");
    
    Incrementable incr = new Incrementable();
    incr.increment();
    System.printString("static i: "+StaticTester.i+"\n");
    System.printString("st1 i: "+st1.i+"\n");
    System.printString("st2 i: "+st2.i+"\n");
    
    Incrementable.increment();
    System.printString("static i: "+StaticTester.i+"\n");
    System.printString("st1 i: "+st1.i+"\n");
    System.printString("st2 i: "+st2.i+"\n");
  }
}