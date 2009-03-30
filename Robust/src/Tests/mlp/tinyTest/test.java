public class Test {

  public static void main( String args[] ) {

    int n = 10;

    sese {

      int[] a = new int[n];
      int[] b = new int[n];
      int[] c = new int[n];
      
      for( int i = 0; i < n; ++i ) {
	sese {
	  a[i] = i;
	  b[i] = n - i;
	}
      }

      for( int j = 0; j < n; ++j ) {
	sese {
	  c[j] = a[j] + b[j];
	}
      }

      int total = 0;
      for( int k = 0; k < n; ++k ) {
	sese {
	  total = total + c[k];
	}
      }

      System.out.println( "total is "+total );
    }

  }
}
