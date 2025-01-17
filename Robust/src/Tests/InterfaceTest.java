public interface Instrument {
  // Compile-time constant:
  int VALUE = 5; // static & final
  // Cannot have method definitions:
  void play(int n); // Automatically public
  void adjust();
}

public interface Instrument2 {
  // Cannot have method definitions:
  void play(int n); // Automatically public
}

class Wind implements Instrument,Instrument2 {
  public Wind(){}
  public void play(int n) {
    System.out.println("Wind.play() " + n);
  }
  public String what() { return "Wind"; }
  public void adjust() { System.out.println("Wind.adjust()"); }
}

class Percussion implements Instrument,Instrument2 {
  public Percussion(){}
  public void play(int n) {
    System.out.println("Percussion.play() " + n);
  }
  public String what() { return "Percussion"; }
  public void adjust() { System.out.println("Percussion.adjust()"); }
}

class Stringed implements Instrument,Instrument2 {
  public Stringed(){}
  public void play(int n) {
    System.out.println("Stringed.play() " + n);
  }
  public String what() { return "Stringed"; }
  public void adjust() { System.out.println("Stringed.adjust()"); }
}

class Brass extends Wind {
  public Brass(){}
  public String what() { return "Brass"; }
}

class Woodwind extends Wind {
  public Woodwind(){}
  public String what() { return "Woodwind"; }
}

public class InterfaceTest {
  public InterfaceTest(){}
  
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
  static void tune2(Instrument2 i) {
    // ...
    i.play(9);
  }
  static void tuneAll2(Instrument2[] e) {
    for(int k = 0; k < e.length; k++) {
      Instrument2 i = e[k];
      tune2(i);
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
    Instrument2[] orchestra2 = new Instrument2[5];
    orchestra2[0] = new Wind();
    orchestra2[1] = new Percussion();
    orchestra2[2] = new Stringed();
    orchestra2[3] = new Brass();
    orchestra2[4] = new Woodwind();
    tuneAll2(orchestra2);
  }
} /* Output:
Wind.play() MIDDLE_C
Percussion.play() MIDDLE_C
Stringed.play() MIDDLE_C
Wind.play() MIDDLE_C  //Brass.play() MIDDLE_C
Wind.play() MIDDLE_C  //Woodwind.play() MIDDLE_C
*///:~
