public class Foo {
  public Foo next;
}

public class Test {

  

  static public void main( String args[] ) {
    
    Foo f = new Foo();
    Foo g = f;

    while( false ) {
      f.next = new Foo();
      f = f.next;
    }

    f = yodel( f, g );

    gendefreach yo;

    System.out.println( f );
  }

  static public Foo yodel( Foo a, Foo b ) {
    return a.next;
  }
}
