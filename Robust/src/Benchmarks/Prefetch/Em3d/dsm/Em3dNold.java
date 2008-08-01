/** 
 *
 *
 * Java implementation of the <tt>em3d</tt> Olden benchmark.  This Olden
 * benchmark models the propagation of electromagnetic waves through
 * objects in 3 dimensions. It is a simple computation on an irregular
 * bipartite graph containing nodes representing electric and magnetic
 * field values.
 *
 * <p><cite>
 * D. Culler, A. Dusseau, S. Goldstein, A. Krishnamurthy, S. Lumetta, T. von 
 * Eicken and K. Yelick. "Parallel Programming in Split-C".  Supercomputing
 * 1993, pages 262-273.
 * </cite>
 **/
public class Em3d extends Thread
{

  /**
   * The number of nodes (E and H) 
   **/
  private int numNodes;
  /**
   * The out-degree of each node.
   **/
  private int numDegree;
  /**
   * The number of compute iterations 
   **/
  private int numIter;
  /**
   * Should we print the results and other runtime messages
   **/
  private boolean printResult;
  /**
   * Print information messages?
   **/
  private boolean printMsgs;

  int numThreads;

  BiGraph bg;
  int upperlimit;
  int lowerlimit;
  Barrier mybarr;

  public Em3d() {
    numNodes = 0;
    numDegree = 0;
    numIter = 1;
    printResult = false;
    printMsgs = false;
  }

  public Em3d(BiGraph bg, int lowerlimit, int upperlimit, int numIter, Barrier mybarr) {
    this.bg = bg;
    this.lowerlimit = lowerlimit;
    this.upperlimit = upperlimit;
    this.numIter = numIter;
    this.mybarr = mybarr;
  }

  public void run() {
    int iteration;
    Barrier barr;

    atomic {
      iteration = numIter;
      barr=mybarr;
    }

    for (int i = 0; i < iteration; i++) {
	/* for  eNodes */
	atomic {
	    for(int j = lowerlimit; j<upperlimit; j++) {
		Node n = bg.eNodes[j];
		
		for (int k = 0; k < n.fromCount; k++) {
		    n.value -= n.coeffs[k] * n.fromNodes[k].value;
		}
	    }
	}
	
	Barrier.enterBarrier(barr);

	/* for  hNodes */
	atomic {
	    for(int j = lowerlimit; j<upperlimit; j++) {
		Node n = bg.hNodes[j];
		for (int k = 0; k < n.fromCount; k++) {
		    n.value -= n.coeffs[k] * n.fromNodes[k].value;
		}
	    }
	}
	Barrier.enterBarrier(barr);
    }
  }

  /**
   * The main roitine that creates the irregular, linked data structure
   * that represents the electric and magnetic fields and propagates the
   * waves through the graph.
   * @param args the command line arguments
   **/
  public static void main(String args[]) {
    Em3d em = new Em3d();
    Em3d.parseCmdLine(args, em);
    if (em.printMsgs) 
      System.printString("Initializing em3d random graph...\n");
    long start0 = System.currentTimeMillis();
    int numThreads = em.numThreads;
    int[] mid = new int[4];
    mid[0] = (128<<24)|(195<<16)|(175<<8)|79;
    mid[1] = (128<<24)|(195<<16)|(175<<8)|73;
    mid[2] = (128<<24)|(195<<16)|(175<<8)|78;
    mid[3] = (128<<24)|(195<<16)|(175<<8)|69;
    //System.printString("DEBUG -> numThreads = " + numThreads+"\n");
    Barrier mybarr;
    BiGraph graph;
    Random rand = new Random(783);

    atomic {
      mybarr = global new Barrier(numThreads);
      graph =  BiGraph.create(em.numNodes, em.numDegree, em.printResult, rand);
    }

    long end0 = System.currentTimeMillis();

    // compute a single iteration of electro-magnetic propagation
    if (em.printMsgs) 
      System.printString("Propagating field values for " + em.numIter + 
          " iteration(s)...\n");
    long start1 = System.currentTimeMillis();
    Em3d[] em3d;

    atomic {
      em3d = global new Em3d[numThreads];
      int increment=em.numNodes/numThreads;
      int base=0;
      for(int i=0;i<numThreads;i++) {
	  if ((i+1)==numThreads)
	      em3d[i] = global new Em3d(graph, base, em.numNodes, em.numIter, mybarr);
	  else
	      em3d[i] = global new Em3d(graph, base, base+increment, em.numIter, mybarr);
	  base+=increment;
      }
    }

    Em3d tmp;
    for(int i = 0; i<numThreads; i++) {
      atomic {
        tmp = em3d[i];
      }
      tmp.start(mid[i]);
    }

    for(int i = 0; i<numThreads; i++) {
      atomic { 
        tmp = em3d[i];
      }
      tmp.join();
    }
    long end1 = System.currentTimeMillis();

    // print current field values
    if (em.printResult) {
      StringBuffer retval = new StringBuffer();
      double dvalue;
      atomic {
        dvalue = graph.hNodes[0].value;
      }
      int intvalue = (int)dvalue;
    }

    if (em.printMsgs) {
      System.printString("EM3D build time "+ (long)((end0 - start0)/1000.0) + "\n");
      System.printString("EM3D compute time " + (long)((end1 - start1)/1000.0) + "\n");
      System.printString("EM3D total time " + (long)((end1 - start0)/1000.0) + "\n");
    }
    System.printString("Done!"+ "\n");
  }


  /**
   * Parse the command line options.
   * @param args the command line options.
   **/

  public static void parseCmdLine(String args[], Em3d em)
  {
    int i = 0;
    String arg;

    while (i < args.length && args[i].startsWith("-")) {
      arg = args[i++];

      // check for options that require arguments
      if (arg.equals("-N")) {
        if (i < args.length) {
		em.numNodes = new Integer(args[i++]).intValue();
        }
      } else if (arg.equals("-T")) {
        if (i < args.length) {
		em.numThreads = new Integer(args[i++]).intValue();
        }
      } else if (arg.equals("-d")) {
        if (i < args.length) {
		em.numDegree = new Integer(args[i++]).intValue();
        }
      } else if (arg.equals("-i")) {
        if (i < args.length) {
            em.numIter = new Integer(args[i++]).intValue();
        }
      } else if (arg.equals("-p")) {
	      em.printResult = true;
      } else if (arg.equals("-m")) {
	      em.printMsgs = true;
      } else if (arg.equals("-h")) {
        em.usage();
      }
    }

    if (em.numNodes == 0 || em.numDegree == 0) 
      em.usage();
  }

  /**
   * The usage routine which describes the program options.
   **/
  public void usage()
  {
    System.printString("usage: java Em3d -T <threads> -N <nodes> -d <degree> [-p] [-m] [-h]\n");
    System.printString("    -N the number of nodes\n");
    System.printString("    -T the number of threads\n");
    System.printString("    -d the out-degree of each node\n");
    System.printString("    -i the number of iterations\n");
    System.printString("    -p (print detailed results\n)");
    System.printString("    -m (print informative messages)\n");
    System.printString("    -h (this message)\n");
  }

}
