public class Graph {
  public Graph() {}
  public Vertex vertex;
}

public class Vertex {
  public Vertex() {}
  public Vertex f;
  public boolean marked;
  public void updateVertex();
}


public class Test {

  static public void main( String[] args ) {
    Test.graphLoop( 2 );
  }


  static public void graphLoop( int nGraphs ) {

    Graph[] a = new Graph[nGraphs];

    for( int i = 0; i < nGraphs; i++ ) {

      Graph g = disjoint graphs new Graph();
      Vertex v1 = new Vertex();
      g.vertex = v1;
      Vertex v2 = new Vertex();
      v2.f = v1; v1.f = v2 ;
      a[i] = g;
    }
    /*
    for( int i = 0; i < nGraphs; i++ ) {
      
      Graph  g = a[i];
      Vertex v = g.vertex;

      while( !v.marked ) {
        v.marked = true;
        v.updateVertex();
        v=v.f;
      } 
    }
    */
  }
}
