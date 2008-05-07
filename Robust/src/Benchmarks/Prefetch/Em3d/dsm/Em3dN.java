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

  Random r;

  // yipes! static members are not supported so
  // using the following constants:
  // runMode == 1 is RUNMODE_ALLOC
  // runMode == 2 is RUNMODE_NEIGHBORS
  // runMode == 3 is RUNMODE_MAKEFROM
  // runMode == 4 is RUNMODE_FROMLINKS
  // runMode == 5 is RUNMODE_WORK
  int runMode;


  public Em3d() {
    numNodes = 0;
    numDegree = 0;
    numIter = 1;
    printResult = false;
    printMsgs = false;
    runMode = 0;
  }

  public Em3d(BiGraph bg, int lowerlimit, int upperlimit, int numIter, Barrier mybarr, int numDegree, Random r, int runMode) {
    this.bg = bg;
    this.lowerlimit = lowerlimit;
    this.upperlimit = upperlimit;
    this.numIter = numIter;
    this.mybarr = mybarr;
    this.runMode = runMode;
    this.numDegree = numDegree;
    this.r = r;
  }

  public void run() {
    int iteration;
    Barrier barr;
    int degree;
    Random random;
    int mode;

    atomic {
      iteration = numIter;
      barr=mybarr;
      degree = numDegree;
      random = r;
      mode = runMode;
    }

    if( mode == 1 ) {
	atomic {
	    bg.allocate( lowerlimit, upperlimit, degree, r );
	}
	Barrier.enterBarrier(barr);
	System.clearPrefetchCache();

    } else if( mode == 2 ) {
	atomic {
	    bg.makeNeighbors( lowerlimit, upperlimit, r );
	}
	Barrier.enterBarrier(barr);
	System.clearPrefetchCache();

    } else if( mode == 3 ) {
	atomic {
	    bg.makeFromNodes( lowerlimit, upperlimit );
	}
	Barrier.enterBarrier(barr);
	System.clearPrefetchCache();

    } else if( mode == 4 ) {
	atomic {
	    bg.makeFromLinks( lowerlimit, upperlimit, r );
	}
	Barrier.enterBarrier(barr);
	System.clearPrefetchCache();

    } else if( mode == 5 ) {

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
	    System.clearPrefetchCache();
	    
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
	    System.clearPrefetchCache();
	}
	
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
    mid[0] = (128<<24)|(195<<16)|(175<<8)|69;//dw-1
    mid[1] = (128<<24)|(195<<16)|(175<<8)|70;//dw-2
    mid[2] = (128<<24)|(195<<16)|(175<<8)|73;
    mid[3] = (128<<24)|(195<<16)|(175<<8)|78;
    System.printString("DEBUG -> numThreads = " + numThreads+"\n");
    Barrier mybarr;
    BiGraph graph;
    Random rand;

    
    // initialization step 1: allocate BiGraph
    System.printString( "Allocating BiGraph.\n" );

    atomic {
      mybarr = global new Barrier(numThreads);
      rand = global new Random(783);
      graph =  BiGraph.create(em.numNodes, em.numDegree, em.printResult, rand);
    }

    Em3d[] em3d;    
    Em3d tmp;
    int base;
    int increment;


	increment = em.numNodes/numThreads;


    // initialization step 2: divide work of allocating nodes
    System.printString( "Launching distributed allocation of nodes.\n" );

    atomic {
      em3d = global new Em3d[numThreads];
      base=0;
      for(int i=0;i<numThreads;i++) {
	  if ((i+1)==numThreads)
	      em3d[i] = global new Em3d(graph, base, em.numNodes, em.numIter, mybarr, em.numDegree, rand, 1);
	  else
	      em3d[i] = global new Em3d(graph, base, base+increment, em.numIter, mybarr, em.numDegree, rand, 1);
	  base+=increment;
      }
    }

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

    // initialization step 3: link together the ends of segments
    // that were allocated and internally linked in step 2
    System.printString( "Linking together allocated segments.\n" );

    base = 0;
    for(int i = 0; i < numThreads - 1; i++) {
	atomic {
	    graph.linkSegments( base + increment );
	    base += increment;   	    
	}
    }    

    // initialization step 4: divide work of making links
    System.printString( "Launching distributed neighbor initialization.\n" );

    atomic {
      em3d = global new Em3d[numThreads];
      base=0;
      for(int i=0;i<numThreads;i++) {
	  if ((i+1)==numThreads)
	      em3d[i] = global new Em3d(graph, base, em.numNodes, em.numIter, mybarr, em.numDegree, rand, 2);
	  else
	      em3d[i] = global new Em3d(graph, base, base+increment, em.numIter, mybarr, em.numDegree, rand, 2);
	  base+=increment;
      }
    }

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

    // initialization step 5: divide work of making from links
    System.printString( "Launching distributed makeFromNodes initialization.\n" );

    atomic {
      em3d = global new Em3d[numThreads];
      base=0;
      for(int i=0;i<numThreads;i++) {
	  if ((i+1)==numThreads)
	      em3d[i] = global new Em3d(graph, base, em.numNodes, em.numIter, mybarr, em.numDegree, rand, 3);
	  else
	      em3d[i] = global new Em3d(graph, base, base+increment, em.numIter, mybarr, em.numDegree, rand, 3);
	  base+=increment;
      }
    }

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

    // initialization step 6: divide work of making from links
    System.printString( "Launching distributed fromLink initialization.\n" );

    atomic {
      em3d = global new Em3d[numThreads];
      base=0;
      for(int i=0;i<numThreads;i++) {
	  if ((i+1)==numThreads)
	      em3d[i] = global new Em3d(graph, base, em.numNodes, em.numIter, mybarr, em.numDegree, rand, 4);
	  else
	      em3d[i] = global new Em3d(graph, base, base+increment, em.numIter, mybarr, em.numDegree, rand, 4);
	  base+=increment;
      }
    }

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

    // initialization complete
    System.printString( "Initialization complete.\n" );

    long end0 = System.currentTimeMillis();


    // compute a single iteration of electro-magnetic propagation
    if (em.printMsgs) 
      System.printString("Propagating field values for " + em.numIter + 
          " iteration(s)...\n");
    long start1 = System.currentTimeMillis();

    atomic {
      em3d = global new Em3d[numThreads];
      base=0;
      for(int i=0;i<numThreads;i++) {
	  if ((i+1)==numThreads)
	      em3d[i] = global new Em3d(graph, base, em.numNodes, em.numIter, mybarr, em.numDegree, rand, 5);
	  else
	      em3d[i] = global new Em3d(graph, base, base+increment, em.numIter, mybarr, em.numDegree, rand, 5);
	  base+=increment;
      }
    }

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
