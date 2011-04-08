// Bamboo version
/*import java.io.*;
import java.util.Stack;*/

/**
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
public class TestRunner //Voronoi 
{      
  /**
   * The number of points in the diagram
   **/
  private int points; // = 0;
  /**
   * Set to true to print informative messages
   **/
  //private static boolean printMsgs; // = false;
  /**
   * Set to true to print the voronoi diagram and its dual,
   * the delaunay diagram
   **/
 private boolean printResults; // = false;
  
  public TestRunner(int npoints) {
    this.points = npoints;
    //this.printMsgs = false;
    this.printResults = true;
  }
  
  public static void main(String[] args) {
    if(args.length < 2) {
      System.out.println("Usage: <num points> <parallel_threshold>");
      System.out.println("Recommended values:");
      System.out.println("  Num Points:          8000000");
      System.out.println("  Parallel_threshold:  3 for an 8 core, 6 for 24 core.");
      System.exit(-1);
    }
    int npoints           = Integer.parseInt(args[0]);
    int parallelThreshold = Integer.parseInt(args[1]);
    TestRunner tr         = new TestRunner(npoints);
    
    tr.run(parallelThreshold);
  }

  /**
   * The main routine which creates the points and then performs
   * the delaunay triagulation.
   * @param args the command line parameters
   **/
  public void run(int parallelThreshold) //main(String args[])
  {
//    parseCmdLine(args);
    
    /*if (printMsgs)
      System.out.println("Getting " + points +  " points");*/

    long start0 = System.currentTimeMillis();
    Vertex v = new Vertex();
    v.seed = 1023;
    Vertex extra = v.createPoints(1, new MyDouble(1.0f), points);
    Vertex point = v.createPoints(points-1, new MyDouble(extra.X()), points-1);
    long end0 = System.currentTimeMillis();

    /*if (printMsgs)*/ 
      System.out.println("Doing voronoi on " + points + " points with threshold " + parallelThreshold);

      long start1 = System.currentTimeMillis();
      Edge edge = point.buildDelaunayTriangulation(extra, parallelThreshold);
      long end1 = System.currentTimeMillis();
      System.out.println("Build time " + (end0-start0)/1000.0);
      System.out.println("Compute  time " + (end1-start1)/1000.0);
      System.out.println("Total time " + (end1-start0)/1000.0);
      System.out.println("Done!");
  
   if (printResults) 
      edge.outputVoronoiDiagram(); 

//    if (printMsgs) {

//    }
  }
  
  /**
   * Parse the command line options.
   * @param args the command line options.
   **/
  /*private static final void parseCmdLine(String args[])
  {
    int i = 0;
    String arg;
    
    while (i < args.length && args[i].startsWith("-")) {
      arg = args[i++];

      if (arg.equals("-n")) {
	if (i < args.length) {
	  points = new Integer(args[i++]).intValue();
	} else throw new RuntimeException("-n requires the number of points");
      } else if (arg.equals("-p")) {
	printResults = true;
      } else if (arg.equals("-m")) {
	printMsgs = true;
      } else if (arg.equals("-h")) {
	usage();
      }
    }
    if (points == 0) usage();
  }*/

  /**
   * The usage routine which describes the program options.
   **/
  private static final void usage()
  {
    /*System.err.println("usage: java Voronoi -n <points> [-p] [-m] [-h]");
    System.err.println("    -n the number of points in the diagram");
    System.err.println("    -p (print detailed results/messages - the voronoi diagram>)");
    System.err.println("    -v (print informative message)");
    System.err.println("    -h (this message)");
    System.exit(0);*/
  }

}
