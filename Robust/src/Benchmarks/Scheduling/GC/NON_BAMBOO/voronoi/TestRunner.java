package voronoi;

/**
 * 
 * A Java implementation of the <tt>voronoi</tt> Olden benchmark. Voronoi
 * generates a random set of points and computes a Voronoi diagram for
 * the points.
 * <p>
 * <cite>
 * L. Guibas and J. Stolfi.  "General Subdivisions and Voronoi Diagrams"
 * ACM Trans. on Graphics 4(2):74-123, 1985.
 * </cite>
 * <p>
 * The Java version of voronoi (slightly) differs from the C version
 * in several ways.  The C version allocates an array of 4 edges and
 * uses pointer addition to implement quick rotate operations.  The
 * Java version does not use pointer addition to implement these
 * operations.
 **/
public class TestRunner extends Thread
{

  /**
   * The number of points in the diagram
   **/
  private int points;

  public TestRunner(int npoints) {
    this.points = npoints;
  }

  /**
   * The main routine which creates the points and then performs
   * the delaunay triagulation.
   * @param args the command line parameters
   **/
  public void run()
  {
    Vertex v = new Vertex();
    v.seed = 1023;
    Vertex extra = v.createPoints(1, new MyDouble(1.0f), points);
    Vertex point = v.createPoints(points-1, new MyDouble(extra.X()), points-1);
    Edge edge = point.buildDelaunayTriangulation(extra);
  }

  public static void main(String[] args) {
    int threadnum = 62;
    int npoints = 32000;
    for(int i = 0; i < threadnum; ++i) {
      TestRunner tr = new TestRunner(npoints);
      tr.start();
    }
  }
}
