/**
 * A Java implementation of the <tt>voronoi</tt> Olden benchmark. Voronoi
 * generates a random set of points and computes a Voronoi diagram for the
 * points.
 * <p>
 * <cite> L. Guibas and J. Stolfi. "General Subdivisions and Voronoi Diagrams"
 * ACM Trans. on Graphics 4(2):74-123, 1985. </cite>
 * <p>
 * The Java version of voronoi (slightly) differs from the C version in several
 * ways. The C version allocates an array of 4 edges and uses pointer addition
 * to implement quick rotate operations. The Java version does not use pointer
 * addition to implement these operations.
 **/
public class TestRunner // Voronoi
{
  /**
   * The number of points in the diagram
   **/
  private int points; // = 0;
  /**
   * Set to true to print informative messages
   **/
  // private static boolean printMsgs; // = false;
  /**
   * Set to true to print the voronoi diagram and its dual, the delaunay diagram
   **/
  private boolean printResults; // = false;

  public TestRunner(int npoints, boolean printResults) {
    this.points = npoints;
    this.printResults = printResults;
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("Usage: <num points> <parallel_threshold>");
      System.out.println("Recommended values:");
      System.out.println("  Num Points:          8000000");
      System.exit(-1);
    }
    int npoints = Integer.parseInt(args[0]);

    boolean printResults = false;
    if (args.length > 1) {
      if (args[1].equals("-p")) {
        printResults = true;
      }
    }

    TestRunner tr = new TestRunner(npoints, printResults);
    tr.run();
  }

  /**
   * The main routine which creates the points and then performs the delaunay
   * triagulation.
   * 
   * @param args
   *          the command line parameters
   **/
  public void run() // main(String args[])
  {

    long start0 = System.currentTimeMillis();
    Vertex v = new Vertex();
    v.seed = 1023;
    Vertex extra = Vertex.createPoints(1, new MyDouble(1.0), points);
    Vertex point = Vertex.createPoints(points - 1, new MyDouble(extra.X()), points - 1);
    long end0 = System.currentTimeMillis();

    System.out.println("Doing voronoi on " + points + " points.");

    long start1 = System.currentTimeMillis();
    Edge edge = point.buildDelaunayTriangulation(extra, 0);
    long end1 = System.currentTimeMillis();
    if (!printResults){
      System.out.println("Build time " + (end0 - start0) / 1000.0);
      System.out.println("Compute  time " + (end1 - start1) / 1000.0);
      System.out.println("Total time " + (end1 - start0) / 1000.0);
      System.out.println("Done!");
    }

    if (printResults)
      edge.outputVoronoiDiagram();

  }

}
