//This is adapted from a benchmark written by John Ellis and Pete Kovac
//of Post Communications.
//It was modified by Hans Boehm of Silicon Graphics.

//This is no substitute for real applications.  No actual application
//is likely to behave in exactly this way.  However, this benchmark was
//designed to be more representative of real applications than other
//Java GC benchmarks of which we are aware.
//It attempts to model those properties of allocation requests that
//are important to current GC techniques.
//It is designed to be used either to obtain a single overall performance
//number, or to give a more detailed estimate of how collector
//performance varies with object lifetimes.  It prints the time
//required to allocate and collect balanced binary trees of various
//sizes.  Smaller trees result in shorter object lifetimes.  Each cycle
//allocates roughly the same amount of memory.
//Two data structures are kept around during the entire process, so
//that the measured performance is representative of applications
//that maintain some live in-memory data.  One of these is a tree
//containing many pointers.  The other is a large array containing
//double precision floating point numbers.  Both should be of comparable
//size.

//The results are only really meaningful together with a specification
//of how much memory was used.  It is possible to trade memory for
//better time performance.  This benchmark should be run in a 32 MB
//heap, though we don't currently know how to enforce that uniformly.

//Unlike the original Ellis and Kovac benchmark, we do not attempt
//measure pause times.  This facility should eventually be added back
//in.  There are several reasons for omitting it for now.  The original
//implementation depended on assumptions about the thread scheduler
//that don't hold uniformly.  The results really measure both the
//scheduler and GC.  Pause time measurements tend to not fit well with
//current benchmark suites.  As far as we know, none of the current
//commercial Java implementations seriously attempt to minimize GC pause
//times.

//Known deficiencies:
//- No way to check on memory use
//- No cyclic data structures
//- No attempt to measure variation with object size
//- Results are sensitive to locking cost, but we dont
//check for proper locking

public class TestRunner  extends Thread {

  public static final int kStretchTreeDepth = 16; // about 4Mb
  public static final int kLongLivedTreeDepth = 14;  // about 1Mb
  public static final int kArraySize  = 250000;  // about 1Mb
  public static final int kMinTreeDepth = 4;
  public static final int kMaxTreeDepth = 14;

  // Nodes used by a tree of a given size
  int TreeSize(int i) {
    return ((1 << (i + 1)) - 1);
  }

  // Number of iterations to use for a given tree depth
  int NumIters(int i) {
    return 2 * TreeSize(kStretchTreeDepth) / TreeSize(i);
  }

  // Build tree top down, assigning to older objects. 
  void Populate(int iDepth, Node thisNode) {
    if (iDepth<=0) {
      return;
    } else {
      iDepth--;
      thisNode.left  = new Node();
      thisNode.right = new Node();
      Populate (iDepth, thisNode.left);
      Populate (iDepth, thisNode.right);
    }
  }

  // Build tree bottom-up
  Node MakeTree(int iDepth) {
    if (iDepth<=0) {
      return new Node();
    } else {
      return new Node(MakeTree(iDepth-1),
          MakeTree(iDepth-1));
    }
  }

  void tc1(int depth) {
    Node tempTree = new Node();
    Populate(depth, tempTree);
    tempTree = null;
  }

  void tc2(int depth) {
    Node tempTree = MakeTree(depth);
    tempTree = null;
  }

  void TimeConstruction(int depth) {
    Node    root;
    int   iNumIters = NumIters(depth);
    Node  tempTree;

    for (int i = 0; i < iNumIters; ++i) {
      tc1(depth);
    }
    for (int i = 0; i < iNumIters; ++i) {
      tc2(depth);
    }
  }

  public void stretch() {
    Node    root;
    Node    longLivedTree;
    Node    tempTree;

    // Stretch the memory space quickly
    tempTree = MakeTree(kStretchTreeDepth);
    tempTree = null;
  }

  public void run() {
    Node  root;
    Node  longLivedTree;

    // Stretch the memory space quickly
    stretch();

    // Create a long lived object
    longLivedTree = new Node();
    Populate(kLongLivedTreeDepth, longLivedTree);

    // Create long-lived array, filling half of it
    float array[] = new float[kArraySize];
    for (int i = 0; i < kArraySize/2; ++i) {
      array[i] = 1.0f/i;
    }

    for (int d = kMinTreeDepth; d <= kMaxTreeDepth; d += 2) {
      TimeConstruction(d);
    }

    if (longLivedTree == null || array[1000] != 1.0f/1000) {
      System.out.println(0xa0);
      System.out.println((int)(array[1000]*1000000));
    }
  }
  
  class Node {
    Node left, right;
    int i, j;
    Node(Node l, Node r) { left = l; right = r; }
    Node() { }
  }
  
  public static void main(String[] args) {
    int threadnum = 62;
    for(int i = 0; i < threadnum; ++i) {
      TestRunner tr = new TestRunner();
      tr.run();
    }
  }
} // class JavaGC
