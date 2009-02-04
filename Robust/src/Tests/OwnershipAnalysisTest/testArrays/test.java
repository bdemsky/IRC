//
//  We should see b and c aliased
//
//  Also, a and f aliased, and e references a sub region from there
//

public class TestArrays {

  static public void main( String[] args ) {

    int[] b = new int[10];
    int[] c = b;

    int[][] a = new int[40][30];

    int     d = a[2][1];
    int[]   e = a[1];
    int[][] f = a;

    int [][] g = disjoint l1 new int[10][];
    int [][] h = disjoint l2 new int[10][];
    g[0] = h[0] = new int[5];
  }
}
