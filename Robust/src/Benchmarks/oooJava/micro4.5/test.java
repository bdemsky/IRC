public class Foo {
  public Foo() {}
  int f;
}


public class test {
  public static void main( String argv[] ) {
    long count  = 800;
    int  numFoo = 10;
    
    Foo[] array = new Foo[numFoo];
    
    for( int i = 0; i < numFoo; i++ ) {
      array[i] = new Foo();
    }
    
    for( long j = 0; j < count; j++ ) {
      for( int i = 0; i < numFoo; i++ ) {
	rblock child1 {
	  int x = 2;
	}
	
	Foo foo = array[i];
	
	// a variable fro sib
	// AND memory dependence
	rblock child2 {
	  foo.f += x;
	}
      }
    }

    long total = 0;
    for( long j = 0; j < count; j++ ) {
      for( int i = 0; i < numFoo; i++ ) {
        total += array[i].f;
      }
    }
    System.out.println( "t="+total );
  }
}
