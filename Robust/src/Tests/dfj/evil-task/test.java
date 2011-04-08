
public class Foo {
  public int z;
  public Foo x;

  public Foo() {
    z = 0;
    x = null;
  }
}

public class Test {

  static public void main( String args[] ) {
    innerMain( Integer.parseInt( args[0] ) );
  }

  static public void innerMain( int n ) {

    int total = 0;

    // attach a big chain of things that we'll
    // never use in the actual computation, but
    // write code in such a way that traverser
    // must conservatively walk the whole chain,
    // because we want the traversers to get
    // behind and do something EVIL!!
    Foo bigChain = createAFoo();
    Foo newLink  = bigChain;
    for( int i = 0; i < 900900; ++i ) {
      newLink.x = createAFoo();
      newLink = newLink.x;
    }
    
    for( int i = 0; i < n; ++i ) { 

      Foo tmp = createAFoo();
      Foo val = createAFoo();
      val.z = i*3;
      val.x = bigChain;
      tmp.x = val;

      sese evil {
        Foo foo = tmp;

        // actual computation just gets to val,
        // traverser will go down the big chain
        for( int j = 0; j < 1; ++j ) {
          foo = foo.x;
        }
        
        // now mutate heap so traverser, if it
        // ran, would be broken
        tmp.x = null;

        // then do some tiny bit of work
        total += compute( foo );
      }
    }

    System.out.println( total );
  }

  static public int compute( Foo foo ) {

    // create a write effect that
    // does not actually mutate heap
    Foo bar = foo;
    for( int j = 0; j < 2; ++j ) {
      bar = bar.x;
    }
    foo.x.x = bar;

    return foo.z;
  }

  static Foo createAFoo() {
    return new Foo();
  }
}
