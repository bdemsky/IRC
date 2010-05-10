public class Bar {
  public Bar() {
    System.out.println( "1. I'm the super" );
  }
}

public class Foo extends Bar {

  public Foo( int i ) {    
    this();
    System.out.println( "3. I see the number "+i );
  }

  public Foo() {
    super();
    System.out.println( "2. I see nothing" );
  }

  public Foo f;
}


public class Test {

  static public void main( String[] args ) {
    Foo f = new Foo( 7 );
  }   

}
