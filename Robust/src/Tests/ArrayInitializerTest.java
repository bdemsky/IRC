public class ArrayInitializerTest {
  static public void main( String[] args ) {
    
    int[] a = { 1, 2, 3 };

    System.out.println( "a[2] should be 3: "+a[2] );
    
    int ia[][] = { {1, 2}, null };
    for (int i = 0; i < 2; i++) {
      if(ia[i] != null) {
        for (int j = 0; j < 2; j++) {
            System.out.println(ia[i][j]);
        }
      } else {
        System.out.println("ia[" + i + "] is null");
      }
    }
  }
}