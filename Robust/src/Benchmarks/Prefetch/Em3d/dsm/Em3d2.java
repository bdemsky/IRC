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
public class Em3d extends Thread {

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

    int threadindex;
    int numThreads;

  BiGraph bg;
  int upperlimit;
  int lowerlimit;
    public Em3d() {
    }

    public Em3d(BiGraph bg, int lowerlimit, int upperlimit, int numIter, int numDegree, int threadindex) {
    this.bg = bg;
    this.lowerlimit = lowerlimit;
    this.upperlimit = upperlimit;
    this.numIter = numIter;
    this.numDegree = numDegree;
    this.threadindex=threadindex;
  }

  public void run() {
    int iteration;
    Barrier barr;
    int degree;
    Random random;
    String hname;

    barr = new Barrier("128.195.175.78");
    //System.printString("Inside run\n");
    atomic {
	iteration = numIter;
	degree = numDegree;
	random = new Random(lowerlimit);
    //barr =  mybarr;
    }

    //System.printString("Here 1\n");
    atomic {
	//This is going to conflict badly...Minimize work here
	bg.allocateNodes ( lowerlimit, upperlimit, threadindex);
    }
    Barrier.enterBarrier(barr);
    System.clearPrefetchCache();
    //System.printString("Here 2\n");

    atomic {
	//initialize the eNodes
	bg.initializeNodes(bg.eNodes, bg.hNodes, lowerlimit, upperlimit, degree, random, threadindex);
    }
    Barrier.enterBarrier(barr);
    //System.printString("Here 3\n");

    atomic {
	//initialize the hNodes
	bg.initializeNodes(bg.hNodes, bg.eNodes, lowerlimit, upperlimit, degree, random, threadindex);
    }
    Barrier.enterBarrier(barr);
    //System.printString("Here 4\n");

    atomic {
	bg.makeFromNodes(bg.hNodes, lowerlimit, upperlimit, random);
    }
    Barrier.enterBarrier(barr);
    //System.printString("Here 5\n");

    atomic {
	bg.makeFromNodes(bg.eNodes, lowerlimit, upperlimit, random);
    }
    Barrier.enterBarrier(barr);
    //System.printString("Here 6\n");

    //Do the computation
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
	
    //System.printString("Here 7\n");
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
    //System.printString("Here 8\n");
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
    //String[] hostname;
    //atomic {
    /*
      hostname = global new String[4];
      hostname[0] = global new String("128.195.175.79");
      hostname[1] =  global new String("128.195.175.73");
      hostname[2] = global new String("128.195.175.78");
      hostname[3] = global new String("128.195.175.69");
      */
    //}
    mid[0] = (128<<24)|(195<<16)|(175<<8)|79;//dw-1
    mid[1] = (128<<24)|(195<<16)|(175<<8)|73;//dw-2
    mid[2] = (128<<24)|(195<<16)|(175<<8)|78;
    mid[3] = (128<<24)|(195<<16)|(175<<8)|69;

    System.printString("DEBUG -> numThreads = " + numThreads+"\n");
    BarrierServer mybarr;
    BiGraph graph;

    
    // initialization step 1: allocate BiGraph
   // System.printString( "Allocating BiGraph.\n" );

    atomic {
      mybarr = global new BarrierServer(numThreads);
      graph =  BiGraph.create(em.numNodes, em.numDegree, numThreads);
    }
    mybarr.start(mid[1]);


    Em3dWrap[] em3d=new Em3dWrap[numThreads];    
    int increment = em.numNodes/numThreads;


    // initialization step 2: divide work of allocating nodes
    // System.printString( "Launching distributed allocation of nodes.\n" );
    
    atomic {
      int base=0;
      for(int i=0;i<numThreads;i++) {
	  Em3d tmp;
	  if ((i+1)==numThreads)
	      tmp = global new Em3d(graph, base, em.numNodes, em.numIter, em.numDegree, i);
	  else
	      tmp = global new Em3d(graph, base, base+increment, em.numIter, em.numDegree, i);
	  em3d[i]=new Em3dWrap(tmp);
	  base+=increment;
      }
    }

    boolean waitfordone=true;
    while(waitfordone) {
	atomic {
	    if (mybarr.done)
		waitfordone=false;
	}
    }

    

    //TODO check if correct
    //
    System.printString("Starting Barrier run\n");
    for(int i = 0; i<numThreads; i++) {
      em3d[i].em3d.start(mid[i]);
    }
    for(int i = 0; i<numThreads; i++) {
      em3d[i].em3d.join();
    }
    //System.printString("Join Barrier run\n");
    //mybarr.join();
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
