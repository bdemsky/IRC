public class Foo {
  public int z;

  public Foo( int z ) {
    this.z = z;
  }  
}

public class Test {

  public static void main( String args[] ) {        
    int x = Integer.parseInt( args[0] );
    Foo f = new Foo( x + 10000 );
    int s = doSomeWork( x, f );
    int t = moreWork( x, f );
    nullMethodBodyFinalNode();
    int r = s+t;
    System.out.println( "s = "+s+
                        ", t = "+t+
                        ", r = "+r );
  }

  public static int doSomeWork( int x, Foo f ) {
    float delta = 1.0f;
    int out = 0;
    for( int i = 0; i < x; ++i ) {
      sese calc {
	Foo g = new Foo( i );
	int sum = 0;
	for( int j = 0; j <= i % 10; ++j ) {
	  sum = calculateStuff( sum, 1, 0 );
	}        
      }
      sese forceVirtualReal {
	if( i % 7 == 0 ) {
	  sum = sum + (i % 20);
	}        
        for( int z = 0; z < x % 50; ++z ) {
          if( i % 2 == 0 ) {
            delta += 1.0f;
          }
        }
	g.z = sum + 1000;
      }
      sese arrayAlloc {
        int tempArray[] = new int[x];
        for( int k = 0; k < x/20; ++k ) {
          tempArray[k] = g.z / (i+1);          
        }        
      }
      sese gather {
        int inter = 1;
        for( int k = 0; k < x/20; ++k ) {
          inter = inter + tempArray[k];
        }             
        sum = sum + inter / 10;
      }
      sese modobj {
	g.z = g.z + f.z;
      }
      if( i % 11 == 0 ) {
	sese change {
	  for( int k = 0; k < i*2; ++k ) {
	    sum = calculateStuff( sum, k, 1 );
	  }
	  sum = sum + 1;
	}	
	
	for( int l = 0; l < 3; ++l ) {
	  sum = calculateStuff( sum, 2, 2 );
	}	
      } 
      sese prnt {
        if( i == x - 1 ) {
          out = x + i + sum + (int)delta + g.z;
        }
      }
    }
    return out;
  }

  public static int calculateStuff( int sum, int num, int mode ) {
    int answer = sum;    
    sese makePlaceholderStallAfter {
      sum = sum + 1;
    }
    sum = sum + 1;
    if( mode == 0 ) {
      sese mode1 {
	answer = sum + num;
      }
    } else if( mode == 1 ) {
      sese mode2 {
	answer = sum + (num/2);
      }
    } else {
      sese mode3 {
	answer = sum / num;
      }
    }    
    return answer;
  }

  public static int moreWork( int x, Foo f ) {

    int total = 0;

    for( int j = 0; j < x; ++j ) {
      sese doe {
        Foo g = new Foo( j );
        int prod = 1;
      }
      sese rae {
        if( j % 7 == 0 ) {
          prod = prod * j;
        }
        g.z = prod / x;
      }
      sese mi {
        g.z = g.z + f.z;
      }
      if( j % 3 == 0 ) {
        sese fa {
          prod = prod / 2;
        }
      }

      total = total + prod - g.z;
    }

    return total;
  }

  public static void nullMethodBodyFinalNode() {
    int y = 1;
    sese nothing {
      int x = 0;
    }
    y = x;
    if( x > y ) {
      return;
    } else {
      return;
    }
  }
}
