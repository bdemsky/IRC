public class Node {

  public Node( int v, Node l, Node r ) {
    left   = l;
    right  = r;
    this.v = v;
  }

  public Node left;
  public Node right;

  public int v;
  public int t;

  public int computeTotal() {
    t = v;
    if( left != null ) {
      sese left {
        t += left.computeTotal();
      }
    }
    if( right != null ) {
      sese right {
        t += right.computeTotal();
      }
    }
    return t;
  }
}

public class Test {

  static public void main( String[] args ) {

    // total = 1+2+4+2+6+3+7+1+3+1+2 = 32
    Node root = 
      new Node( 1,
                new Node( 2,
                          new Node( 4,
                                    new Node( 2,
                                              new Node( 6, null, null ),
                                              null
                                              ),
                                    new Node( 3, null, null )
                                    ),
                          new Node( 7, 
                                    null,
                                    new Node( 1, null, null )
                                    )
                          ),
                new Node( 3,
                          new Node( 1, null, null ),
                          new Node( 2, null, null )
                          )
                );

    System.out.println( "total="+root.computeTotal() );    
  }
}
