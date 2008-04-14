import java.io.*;
import java.util.Random;

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

    BiGraph bg;
    int upperlimit;
    int lowerlimit;
    Random rand;
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
        for (int i = 0; i < numIter; i++) {
            /* for  eNodes */
            Node prev = bg.eNodes;
            Node curr = null;
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
            Barrier.enterBarrier(mybarr);

            /* for  hNodes */
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
            Barrier.enterBarrier(mybarr);
        }
    }
        
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

    /**
     * The main roitine that creates the irregular, linked data structure
     * that represents the electric and magnetic fields and propagates the
     * waves through the graph.
     * @param args the command line arguments
     **/
    public static void main(String args[])
    {
        Random rand = new Random(783);
        Em3d em = new Em3d();
        em.parseCmdLine(args, em);
        if (em.printMsgs) 
            System.out.println("Initializing em3d random graph...");
        long start0 = System.currentTimeMillis();
        BiGraph graph1 = new BiGraph();
        int num_threads = 2;
        Barrier mybarr = new Barrier(num_threads);
        BiGraph graph = graph1.create(em.numNodes, em.numDegree, em.printResult, rand);
        
        long end0 = System.currentTimeMillis();

        // compute a single iteration of electro-magnetic propagation
        if (em.printMsgs) 
            System.out.println("Propagating field values for " + em.numIter + 
                    " iteration(s)...");
        long start1 = System.currentTimeMillis();
        Em3d[] em3d = new Em3d[num_threads];
        //em3d[0] = new Em3d(graph, 1, em.numNodes, em.numIter, mybarr);
        em3d[0] = new Em3d(graph, 1, em.numNodes/2, em.numIter, mybarr);
        em3d[1] = new Em3d(graph, (em.numNodes/2)+1, em.numNodes, em.numIter, mybarr);
        for(int i = 0; i<num_threads; i++) {
            em3d[i].start();
        }
        for(int i = 0; i<num_threads; i++) {
            try {
                em3d[i].join();
            } catch (InterruptedException e) {}
        }
        long end1 = System.currentTimeMillis();

        // print current field values
        if (em.printResult) {
            System.out.println(graph);
        }

        if (em.printMsgs) {
            System.out.println("EM3D build time "+ (end0 - start0)/1000.0);
            System.out.println("EM3D compute time " + (end1 - start1)/1000.0);
            System.out.println("EM3D total time " + (end1 - start0)/1000.0);
        }
        System.out.println("Done!");
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
        System.out.println("usage: java Em3d -n <nodes> -d <degree> [-p] [-m] [-h]");
        System.out.println("    -n the number of nodes");
        System.out.println("    -d the out-degree of each node");
        System.out.println("    -i the number of iterations");
        System.out.println("    -p (print detailed results)");
        System.out.println("    -m (print informative messages)");
        System.out.println("    -h (this message)");
    }

}
