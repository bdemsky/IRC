//: interfaces/music4/Music4.java
// Abstract classes and methods.

abstract class Instrument {
  public Instrument(){}
  private int i; // Storage allocated for each
  public abstract void play(int n);
  public String what() { return "Instrument"; }
  public abstract void adjust();
}

class Wind extends Instrument {
  public Wind(){}
  public void play(int n) {
    System.out.println("Wind.play() " + n);
  }
  public String what() { return "Wind"; }
  public void adjust() {}
}

class Percussion extends Instrument {
  public Percussion(){}
  public void play(int n) {
    System.out.println("Percussion.play() " + n);
  }
  public String what() { return "Percussion"; }
  public void adjust() {}
}

class Stringed extends Instrument {
  public Stringed(){}
  public void play(int n) {
    System.out.println("Stringed.play() " + n);
  }
  public String what() { return "Stringed"; }
  public void adjust() {}
}

class Brass extends Wind {
  public Brass(){}
  public void play(int n) {
    System.out.println("Brass.play() " + n);
  }
  public void adjust() { System.out.println("Brass.adjust()"); }
}

class Woodwind extends Wind {
  public Woodwind(){}
  public void play(int n) {
    System.out.println("Woodwind.play() " + n);
  }
  public String what() { return "Woodwind"; }
}


public class AbstractTest {
  
  public AbstractTest() {}
  
  // Doesn’t care about type, so new types
  // added to the system still work right:
  static void tune(Instrument i) {
    // ...
    i.play(9);
  }
  static void tuneAll(Instrument[] e) {
    for(int k = 0; k < e.length; k++) {
      Instrument i = e[k];
      tune(i);
    }
  }
  public static void main(String[] args) {
    // Upcasting during addition to the array:
    Instrument[] orchestra = new Instrument[5];
    orchestra[0] = new Wind();
    orchestra[1] = new Percussion();
    orchestra[2] = new Stringed();
    orchestra[3] = new Brass();
    orchestra[4] = new Woodwind();
    tuneAll(orchestra);
  }
} /* Output:
Wind.play() MIDDLE_C
Percussion.play() MIDDLE_C
Stringed.play() MIDDLE_C
Brass.play() MIDDLE_C
Woodwind.play() MIDDLE_C
*///:~
