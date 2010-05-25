public class Record {
  int key;
  char[] data;
  public Record( int k ) {
    key = k;
    data = new char[250];
  }
}

public class MergeSort {

  static public void main( String[] args ) {
    
    int problemSize = 20000;
    int numProblems = 100;
    
    for( int p = 0; p < numProblems; ++p ) {

      rblock doAProblem {      

        Record[] records = new Record[problemSize];    
        
        Random random = new Random( p );
        
        for( int i = 0; i < problemSize; ++i ) {
          int k = random.nextInt();
          if( k < 0 ) { k *= -1; }
          k = k % 1000;
          records[i] = new Record( k );
        }

        mergeSort( records );                
      }
    }
  }


  static public void mergeSort( Record[] a ) {
    if( a.length <= 1 ) {
      //insertionSort( a );, but for len < 10, 14 ish
      return;
    }

    int middle = a.length / 2;

    rblock sortLeft {
      Record[] l = new Record[middle];
      for( int i = 0; i < middle; ++i ) {
        l[i] = a[i];
      }
      mergeSort( l );
    }

    rblock sortRight {
      Record[] r = new Record[a.length - middle];
      for( int i = middle; i < a.length; ++i ) {
        r[i - middle] = a[i];
      }
      mergeSort( r );
    }
    
    int lpos = 0;
    int rpos = 0;
    for( int i = 0; i < a.length; ++i ) {

      if( rpos == r.length ) {
        a[i] = l[lpos]; ++lpos;

      } else if( lpos == l.length ) {
        a[i] = r[rpos]; ++rpos;

      } else if( l[lpos].key < r[rpos].key ) {
        a[i] = l[lpos]; ++lpos;
        
      } else {
        a[i] = r[rpos]; ++rpos;
      }

    }    
  }

}
