package Util;

public class JoinOpInteger implements JoinOp<Integer> {
  public Integer join( Integer a, Integer b ) {
    int aVal = 0;
    if( a != null ) {
      aVal = a;
    }
    int bVal = 0;
    if( b != null ) {
      bVal = b;
    }
    return aVal + bVal;
  }
}
