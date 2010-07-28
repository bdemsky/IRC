
/*import java.util.Enumeration;
import java.lang.Math;*/

/**
 * A Java implementation of the <tt>bh</tt> Olden benchmark.
 * The Olden benchmark implements the Barnes-Hut benchmark
 * that is decribed in :
 * <p><cite>
 * J. Barnes and P. Hut, "A hierarchical o(NlogN) force-calculation algorithm",
 * Nature, 324:446-449, Dec. 1986
 * </cite>
 * <p>
 * The original code in the Olden benchmark suite is derived from the
 * <a href="ftp://hubble.ifa.hawaii.edu/pub/barnes/treecode">
 * source distributed by Barnes</a>.
 **/
public class TestRunner 
{
  flag run;
  
  /**
   * The user specified number of bodies to create.
   **/
  public  int nbody; // = 0;

  /**
   * The maximum number of time steps to take in the simulation
   **/
  public  int nsteps; // = 10;

  /**
   * Should we print information messsages
   **/
  //private static boolean printMsgs = false;
  /**
   * Should we print detailed results
   **/
  //private static boolean printResults = false;

  public  double DTIME; // = 0.0125;
  public  double TSTOP; // = 2.0;
  
  public TestRunner(int nbody) {
    this.nbody = nbody;
    this.nsteps = 10;
    this.DTIME = 0.0125;
    this.TSTOP = 2.0;
  }

  public void run()
  {
    //parseCmdLine(args);

    /*if (printMsgs)
      System.out.println("nbody = " + nbody);*/

    //long start0 = System.currentTimeMillis();
    Tree root = new Tree(this.DTIME);
    root.createTestData(nbody);
    /*long end0 = System.currentTimeMillis();
    if (printMsgs)
      System.out.println("Bodies created");

    long start1 = System.currentTimeMillis();*/
    double tnow = 0.0;
    int i = 0;
    while ((tnow < (TSTOP + 0.1f*DTIME)) && (i < nsteps)) {
      root.stepSystem(i++);
      tnow += DTIME;
    }
    /*long end1 = System.currentTimeMillis();

    if (printResults) {
      int j = 0;
      for (Enumeration e = root.bodies(); e.hasMoreElements(); ) {
	Body b = (Body)e.nextElement();
	System.out.println("body " + j++ + " -- " + b.pos);
      }
    }

    if (printMsgs) {
      System.out.println("Build Time " + (end0 - start0)/1000.0);
      System.out.println("Compute Time " + (end1 - start1)/1000.0);
      System.out.println("Total Time " + (end1 - start0)/1000.0);
    }
    System.out.println("Done!");*/
  }

  /**
   * Random number generator used by the orignal BH benchmark.
   * @param seed the seed to the generator
   * @return a random number
   **/
  public  double myRand(double seed)
  {
    double t = 16807.0*seed + 1;
    
    seed = t - 2147483647.0 * Math.floor(t / 2147483647.0f);
    return seed;
  }

  /**
   * Generate a doubleing point random number.  Used by
   * the original BH benchmark.
   *
   * @param xl lower bound
   * @param xh upper bound
   * @param r seed
   * @return a doubleing point randon number
   **/
  public  double xRand(double xl, double xh, double r)
  {
    double res = xl + (xh-xl)*r/2147483647.0;
    return res;
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

      // check for options that require arguments
      if (arg.equals("-b")) {
	if (i < args.length) {
	  nbody = new Integer(args[i++]).intValue();
	} else {
	  throw new Error("-l requires the number of levels");
	}
      } else if (arg.equals("-s")) {
	if (i < args.length) {
	  nsteps = new Integer(args[i++]).intValue();
	} else {
	  throw new Error("-l requires the number of levels");
	}
      } else if (arg.equals("-m")) {
	printMsgs  = true;
      } else if (arg.equals("-p")) {
	printResults = true;
      } else if (arg.equals("-h")) {
	usage();
      }
    }
    if (nbody == 0) usage();
  }*/

  /**
   * The usage routine which describes the program options.
   **/
  /*private static final void usage()
  {
    System.err.println("usage: java BH -b <size> [-s <steps>] [-p] [-m] [-h]");
    System.err.println("    -b the number of bodies");
    System.err.println("    -s the max. number of time steps (default=10)");
    System.err.println("    -p (print detailed results)");
    System.err.println("    -m (print information messages");
    System.err.println("    -h (this message)");
    System.exit(0);
  }*/

}
