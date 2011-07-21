package tsp;

//import java.io.*;

/**
 * A Java implementation of the <tt>tsp</tt> Olden benchmark, the traveling
 * salesman problem.
 * <p>
 * <cite>
 * R. Karp, "Probabilistic analysis of partitioning algorithms for the 
 * traveling-salesman problem in the plane."  Mathematics of Operations Research 
 * 2(3):209-224, August 1977
 * </cite>
 **/
public class TestRunner extends Thread
{

  /**
   * Number of cities in the problem.
   **/
  public int cities;
  /**
   * Set to true if the result should be printed
   **/
  //private static boolean printResult = false;
  /**
   * Set to true to print informative messages
   **/
  //private static boolean printMsgs = false;

  public TestRunner(int cities) {
    this.cities = cities;
  }

  /**
   * The main routine which creates a tree and traverses it.
   * @param args the arguments to the program
   **/
  public void run()
  {
    /*parseCmdLine(args);

    if (printMsgs)
      System.out.println("Building tree of size " + cities);

    long start0 = System.currentTimeMillis();*/
    Tree_tsp  t = Tree_tsp.buildTree(this.cities, false, 0.0f, 1.0f, 0.0f, 1.0f);
    /*long end0 = System.currentTimeMillis();

    long start1 = System.currentTimeMillis();*/
    t.tsp(150);
    /*long end1 = System.currentTimeMillis();

    if (printResult) {
      // if the user specifies, print the final result
      t.printVisitOrder();
    }

    if (printMsgs) {
      System.out.println("Tsp build time  " + (end0 - start0)/1000.0);
      System.out.println("Tsp time " + (end1 - start1)/1000.0);
      System.out.println("Tsp total time " + (end1 - start0)/1000.0);
    }
    System.out.println("Done!");*/
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

      if (arg.equals("-c")) {
	if (i < args.length)
	  cities = new Integer(args[i++]).intValue();
	else throw new Error("-c requires the size of tree");
      } else if (arg.equals("-p")) {
	printResult = true;
      } else if (arg.equals("-m")) {
	printMsgs = true;
      } else if (arg.equals("-h")) {
	usage();
      }
    }
    if (cities == 0) usage();
  }*/

  /**
   * The usage routine which describes the program options.
   **/
  /*private static final void usage()
  {
    System.err.println("usage: java TSP -c <num> [-p] [-m] [-h]");
    System.err.println("    -c number of cities (rounds up to the next power of 2 minus 1)");
    System.err.println("    -p (print the final result)");
    System.err.println("    -m (print informative messages)");
    System.err.println("    -h (print this message)");
    System.exit(0);
  }*/
  public static void main(String[] args) {
    int threadnum = THREADNUM;
    int ncities = 4080*4;
    System.setgcprofileflag();
    TestRunner trarray[]=new TestRunner[threadnum];
    for(int i = 1; i < threadnum; ++i) {
      TestRunner tr = new TestRunner(ncities);
      tr.start();
      trarray[i]=tr;
    }
    TestRunner tr0 = new TestRunner(ncities);
    tr0.run();
    for(int i = 1; i < threadnum; ++i) {
      trarray[i].join();
    }
  }
}

