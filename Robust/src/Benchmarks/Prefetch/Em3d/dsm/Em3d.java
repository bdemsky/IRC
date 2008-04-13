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

  BiGraph bg;
  int upperlimit;
  int lowerlimit;
  //Random rand;
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
    atomic {
      iteration = numIter;
    }

    for (int i = 0; i < iteration; i++) {
      Barrier runBarrier = new Barrier();
      /* for  eNodes */
      Node prev, curr;
      atomic {
        prev = bg.eNodes;
        curr = null;
        for(int j = 0; j<lowerlimit; j++){
          curr = prev;
          prev = prev.next;
        }
        for(int j = lowerlimit; j<=upperlimit; j++) {
          Node n = curr;
          for (int k = 0; k < n.fromCount; k++) {
            n.value -= n.coeffs[k] * n.fromNodes[k].value;
          }
          curr = curr.next;
        }
        runBarrier.enterBarrier(mybarr);
        //mybarr.reset();
      }

      /* for  hNodes */
      atomic {
        prev = bg.hNodes;
        curr = null;
        for(int j = 0; j<lowerlimit; j++){
          curr = prev;
          prev = prev.next;
        }
        for(int j = lowerlimit; j<=upperlimit; j++) {
          Node n = curr;
          for (int k = 0; k < n.fromCount; k++) {
            n.value -= n.coeffs[k] * n.fromNodes[k].value;
          }
          curr = curr.next;
        }
        runBarrier.enterBarrier(mybarr);
        //mybarr.reset();
      }
    }
  }

  /**
   * The main roitine that creates the irregular, linked data structure
   * that represents the electric and magnetic fields and propagates the
   * waves through the graph.
   * @param args the command line arguments
   **/
  public static void main(String args[])
  {
    Random rand = new Random(783);
    Em3d em;
    atomic {
      em = global new Em3d();
      em.parseCmdLine(args, em);
    }
    boolean printMsgs, printResult;
    int numIter;
    atomic {
      printMsgs = em.printMsgs;
      numIter = em.numIter;
      printResult = em.printResult;
    }
    if (printMsgs) 
      System.printString("Initializing em3d random graph...");
    long start0 = System.currentTimeMillis();
    int numThreads = 2;
    System.printString("DEBUG -> numThreads = " + numThreads);
    Barrier mybarr;
    atomic {
      mybarr = global new Barrier(numThreads);
    }
    BiGraph graph;
    BiGraph graph1;
    atomic {
      graph1 = global new BiGraph();
      graph = global new BiGraph();
      graph =  graph1.create(em.numNodes, em.numDegree, em.printResult, rand);
    }

    long end0 = System.currentTimeMillis();

    // compute a single iteration of electro-magnetic propagation
    if (printMsgs) 
      System.printString("Propagating field values for " + numIter + 
          " iteration(s)...");
    long start1 = System.currentTimeMillis();
    Em3d[] em3d;
    atomic {
      em3d = global new Em3d[numThreads];
    }

    atomic {
      em3d[0] = global new Em3d(graph, 1, em.numNodes/2, em.numIter, mybarr);
      em3d[1] = global new Em3d(graph, (em.numNodes/2)+1, em.numNodes, em.numIter, mybarr);
    }

    int mid = (128<<24)|(195<<16)|(175<<8)|73;
    Em3d tmp;
    for(int i = 0; i<numThreads; i++) {
      atomic {
        tmp = em3d[i];
      }
      tmp.start(mid);
    }

    for(int i = 0; i<numThreads; i++) {
      atomic { 
        tmp = em3d[i];
      }
      tmp.join();
    }
    long end1 = System.currentTimeMillis();

    // print current field values
    if (printResult) {
      //System.printString(graph);
    }

    if (printMsgs) {
      System.printString("EM3D build time "+ (long)((end0 - start0)/1000.0));
      System.printString("EM3D compute time " + (long)((end1 - start1)/1000.0));
      System.printString("EM3D total time " + (long)((end1 - start0)/1000.0));
    }
    System.printString("Done!");
  }


  /**
   * Parse the command line options.
   * @param args the command line options.
   **/

  public void parseCmdLine(String args[], Em3d em)
  {
    int i = 0;
    String arg;

    while (i < args.length && args[i].startsWith("-")) {
      arg = args[i++];

      // check for options that require arguments
      if (arg.equals("-n")) {
        if (i < args.length) {
          em.numNodes = new Integer(args[i++]).intValue();
        }
      } else if (arg.equals("-d")) {
        if (i < args.length) {
          em.numDegree = new Integer(args[i++]).intValue();
        }
      } else if (arg.equals("-i")) {
        if (i < args.length) {
          atomic {
            em.numIter = global new Integer(args[i++]).intValue();
          }
        }
      } else if (arg.equals("-p")) {
        em.printResult = true;
      } else if (arg.equals("-m")) {
        em.printMsgs = true;
      } else if (arg.equals("-h")) {
        //em.usage();
      }
    }

    //if (em.numNodes == 0 || em.numDegree == 0) 
      //em.usage();
  }

  /**
   * The usage routine which describes the program options.
   **/
  public void usage()
  {
    System.printString("usage: java Em3d -n <nodes> -d <degree> [-p] [-m] [-h]");
    System.printString("    -n the number of nodes");
    System.printString("    -d the out-degree of each node");
    System.printString("    -i the number of iterations");
    System.printString("    -p (print detailed results)");
    System.printString("    -m (print informative messages)");
    System.printString("    -h (this message)");
  }

}
