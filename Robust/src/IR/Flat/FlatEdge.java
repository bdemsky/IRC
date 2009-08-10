package IR.Flat;

public class FlatEdge {

  protected FlatNode tail;
  protected FlatNode head;

  public FlatEdge( FlatNode t, FlatNode h ) {
    assert t != null;
    assert h != null;
    tail = t;
    head = h;
  }

  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }
    
    if( !(o instanceof FlatEdge) ) {
      return false;
    }

    FlatEdge fe = (FlatEdge) o;

    return tail.equals( fe.tail ) && head.equals( fe.head );
  }

  public int hashCode() {
    int tailHC = tail.hashCode();
    int headHC = head.hashCode();

    int hash = 7;
    hash = 31*hash + tailHC;
    hash = 31*hash + headHC;
    return hash;
  }

  public String toString() {
    return "FlatEdge("+tail+"->"+head+")";
  }
}
