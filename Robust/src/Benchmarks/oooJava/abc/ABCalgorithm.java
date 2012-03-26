// This benchmark performs an arbitrary computation that mimics a
// proprietary algorithm of interest.  I, James C. Jenista, was given
// the obscured algorithm (named A-B-C) and expressed it below in
// common single-threaded Java.  It is easy to obtain a parallel
// implementation that is guaranteed to match the original sequential
// behavior with Out-of-Order Java; only a few annotations are
// required.


// The obscured algorithm operates on a tree with 5 levels: 

// The root level has no interesting information except links to
// subtrees.

// Level A is just below the root and represents a subtree for which
// we should calculate an aggregate value aOut.  All nodes in levels
// below A have a value aIn used to recursively calculate aOut.

// Level B is just below level A and each node in level B should
// compute a value bOut from the values bIn at nodes in levels below
// B.  The computation of bOut depends on the value of aOut at the
// B-node's A-parent.

// Level C is just below level B and should compute cOut from the
// values of cIn at the level below C.  The value of cOut depends on
// the value of bOut from the C-node's B-parent.

// The leaf level is below C.





/////////////////////////////////////////////////////////////
//
//  To compile this code in plain Java you must make two 
//  types of edits to this source code.
//
//  1) Uncomment the utility imports below (our Out-of-Order
//  Java compiler is a research compiler and circumvents 
//  normal library importing)
//
//  2) Comment out each line:          "task TASKNAME {"
//  and the matching closing brace:    "}"
//  These task annotations are for OoOJava, but they DO NOT
//  change the behavior of the single-threaded program,
//  meaning if you comment them out you get the same result.
//
/////////////////////////////////////////////////////////////


//import java.util.Vector;
//import java.util.Random;



public class ABCalgorithm {


  public static void main( String args[] ) {
    ABCalgorithm abc = new ABCalgorithm();

    // Assume that in the real algorithm the data structure is built
    // offline, and that we might print or query the results at any
    // future time, so for the purpose of this experiment we are only
    // interested in the execution speed up of the algorithm itself.

    abc.buildTree();

    long start = System.currentTimeMillis();
    abc.doComputation();
    long end = System.currentTimeMillis();

    abc.printResults();

    System.out.println( "\nThe ABC algorithm executed in "+
                        ((end - start) / 1000.0)+" seconds." );
  }


  // There is only one root-level node.  First build the desired tree
  // (from a repeatable random sequence) then call doComputation() to
  // compute all of the values described above.
  private Root root;
  private Random random;


  public void buildTree() {
    // Build a tree from a constant psuedo-random sequence with high
    // branching factors to generate lots of work.  The constants here
    // result in a number of total leaf nodes on the order of
    // 10,000,000.
    random = new Random( 42 );

    root = new Root();

    long totalLeafNodes = 0;

    int numAnodes = 2 + random.nextInt( 6 );
    for( int i = 0; i < numAnodes; ++i ) {
      Anode anode = new Anode( root );

      int numBnodes = 10 + random.nextInt( 20 );
      for( int j = 0; j < numBnodes; ++j ) {
        Bnode bnode = new Bnode( anode, 
                                 random.nextDouble() );

        int numCnodes = 30 + random.nextInt( 150 );
        for( int k = 0; k < numCnodes; ++k ) {
          Cnode cnode = new Cnode( bnode,
                                   random.nextDouble(),
                                   random.nextDouble() );

          int numLnodes = 1500 + random.nextInt( 2500 );
          totalLeafNodes += numLnodes;
          for( int l = 0; l < numLnodes; ++l ) {
            cnode.addLnode( new Lnode( cnode,
                                       random.nextDouble(),
                                       random.nextDouble(),
                                       random.nextDouble() ) );
          }
          bnode.addCnode( cnode );
        }
        anode.addBnode( bnode );
      }
      root.addAnode( anode );
    }

    System.out.println( "Built tree with "+totalLeafNodes+" leaf nodes." );
  }

  public void doComputation() {
    root.computeABC();
  }

  public void printResults() {
    root.printResults();
  }


  // the Root, Anode, Bnode, Cnode, and Lnode nested classes that
  // contain the data and the tree computations for the ABC algorithm

  private class Root {

    private Vector Anodes;

    public Root() {
      Anodes = new Vector();
    }

    public void addAnode( Anode anode ) {
      Anodes.addElement( anode );
    }

    public void computeABC() {
      for( int i = 0; i < Anodes.size(); ++i ) {
        Anode anode = (Anode)Anodes.elementAt( i );
        sese AcomputingA {
          // Ask all A-nodes to complete the A computation.
          anode.computeA();
          
          // Ask A-nodes to instruct deeper levels to do the other
          // computations which depend on only this A-node parent.
          anode.computeBC();          
        }
      }
    }

    public void printResults() {
      for( int i = 0; i < Anodes.size(); ++i ) {
        Anode anode = (Anode)Anodes.elementAt( i );
        anode.printResults();
      }
    }
  }


  private class Anode {

    private Root parent;
    private Vector Bnodes;
    private double aOut;

    public Anode( Root parent ) {
      this.parent = parent;
      Bnodes = new Vector();
    }

    public void addBnode( Bnode bnode ) {
      Bnodes.addElement( bnode );
    }

    public void computeA() {
      // aOut is the sum of all aIn values in this subtree
      aOut = 0.0;
      for( int i = 0; i < Bnodes.size(); ++i ) {
        Bnode bnode = (Bnode)Bnodes.elementAt( i );
        aOut += bnode.computeA();
      }
    }

    public void computeBC() {
      // just ask B-nodes to conduct the other calculations
      for( int i = 0; i < Bnodes.size(); ++i ) {
        Bnode bnode = (Bnode)Bnodes.elementAt( i );
        bnode.computeB();
        bnode.computeC();
      }
    }

    public void printResults() {
      System.out.println( aOut );
      for( int i = 0; i < Bnodes.size(); ++i ) {
        Bnode bnode = (Bnode)Bnodes.elementAt( i );
        bnode.printResults();
      }
    }
  }


  private class Bnode {

    private Anode parent;
    private Vector Cnodes;
    private double aIn;
    private double bOut;

    public Bnode( Anode parent, double aIn ) {
      this.parent = parent;
      Cnodes = new Vector();
      this.aIn = aIn;
    }

    public void addCnode( Cnode cnode ) {
      Cnodes.addElement( cnode );
    }

    public double computeA() {
      // do a local portion of the A computation
      double aOutLocal = aIn;
      for( int i = 0; i < Cnodes.size(); ++i ) {
        Cnode cnode = (Cnode)Cnodes.elementAt( i );
        aOutLocal += cnode.computeA();
      }
      return aOutLocal;
    }

    public void computeB() {
      // bOut is the sum of the product (bIn * A-node ancestor's aOut)
      // for all nodes in this subtree
      bOut = 0.0;
      for( int i = 0; i < Cnodes.size(); ++i ) {
        Cnode cnode = (Cnode)Cnodes.elementAt( i );
        bOut += cnode.computeB();
      }      
    }

    public void computeC() {
      // just ask C-nodes to compute C
      for( int i = 0; i < Cnodes.size(); ++i ) {
        Cnode cnode = (Cnode)Cnodes.elementAt( i );
        cnode.computeC();
      }      
    }

    public void printResults() {
      System.out.print( "  "+bOut+"{" );
      for( int i = 0; i < Cnodes.size(); ++i ) {
        Cnode cnode = (Cnode)Cnodes.elementAt( i );
        cnode.printResults();
      }      
      System.out.println( "}" );
    }
  }


  private class Cnode {

    private Bnode parent;
    private Vector Lnodes;
    private double aIn;
    private double bIn;
    private double cOut;

    public Cnode( Bnode parent, double aIn, double bIn ) {
      this.parent = parent;
      Lnodes = new Vector();
      this.aIn = aIn;
      this.bIn = bIn;
    }

    public void addLnode( Lnode lnode ) {
      Lnodes.addElement( lnode );
    }

    public double computeA() {
      // do a local portion of the A computation
      double aOutLocal = aIn;
      for( int i = 0; i < Lnodes.size(); ++i ) {
        Lnode lnode = (Lnode)Lnodes.elementAt( i );
        aOutLocal += lnode.computeA();
      }      
      return aOutLocal;
    }

    public double computeB() {
      // do a local portion of the B computation
      double bOutLocal = bIn * parent.parent.aOut;
      for( int i = 0; i < Lnodes.size(); ++i ) {
        Lnode lnode = (Lnode)Lnodes.elementAt( i );
        bOutLocal += lnode.computeB();
      }      
      return bOutLocal;      
    }

    public void computeC() {
      // cOut is the product of this node parent bOut and the average
      // value of cIn in this subtree
      double cInTotal = 0.0;
      double cInSamples = (double) Lnodes.size();
      for( int i = 0; i < Lnodes.size(); ++i ) {
        Lnode lnode = (Lnode)Lnodes.elementAt( i );
        cInTotal += lnode.computeC();
      }
      cOut = parent.bOut * cInTotal / cInSamples;
    }

    public void printResults() {
      System.out.print( cOut+"," );
    }
  }


  // leaf-node level
  private class Lnode {

    private Cnode parent;
    private double aIn;
    private double bIn;
    private double cIn;

    public Lnode( Cnode parent, double aIn, double bIn, double cIn ) {
      this.parent = parent;
      this.aIn = aIn;
      this.bIn = bIn;
      this.cIn = cIn;
    }

    // leaf nodes just do a local part of each computation

    public double computeA() {
      return aIn;      
    }

    public double computeB() {
      return bIn * parent.parent.parent.aOut;
    }

    public double computeC() {
      return cIn;
    }    
  }
}
