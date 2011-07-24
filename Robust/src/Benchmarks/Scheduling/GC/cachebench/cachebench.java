/** Bamboo Version  
 * Ported by: Jin Zhou  11/18/10
 * **/

task t1(StartupObject s{initialstate}) {
  //System.printString("task t1\n");

  // make a shared array
  int kLongLivedTreeDepth  = 12; // 256kb 16;  // about 4Mb
  Helper helper = new Helper(kLongLivedTreeDepth);
  
  //int kArraySize = 1250;//0;//0; // 1Mb 500000;  // about 4Mb
  //int array[] = new int[kArraySize];
  /*for (int i = 0; i < kArraySize/2; ++i) {
    array[i] = i;
  }*/
  int threadnum = 62; // 56;
  for(int i = 0; i < threadnum; ++i) {
    TestRunner gcb = newflag TestRunner(/*array*/helper.root){run};
  }

  taskexit(s{!initialstate});
}

task t2(TestRunner gcb{run}) {
  //System.printString("task t2\n");
  gcb.run();
  taskexit(gcb{!run});
}


class Node {
  public Node left, right;
  public int i, j;
  Node(Node l, Node r) { left = l; right = r; }
  Node() { }
}

public class Helper {
  public Node root;
  
  public Helper(int depth) {
    root = MakeTree(depth);
  }
  
  Node MakeTree(int iDepth) {
    if (iDepth<=0) {
      return new Node();
    } else {
      return new Node(MakeTree(iDepth-1),
          MakeTree(iDepth-1));
    }
  }
}

public class TestRunner {
  
  flag run;

  public static final int kStretchTreeDepth;//    = 18;	// about 16Mb
  public static final int kLongLivedTreeDepth;//  = 16;  // about 4Mb
  public static final int kArraySize;//  = 500000;  // about 4Mb
  public static final int kMinTreeDepth;// = 4;
  public static final int kMaxTreeDepth;// = 16;
  
  Node sharedroot;
  //int[] sharedarray;
  
  public TestRunner(Node sroot) {
  //public TestRunner(int[] sarray) {
    kStretchTreeDepth    = 16;// 4Mb 18;  // about 16Mb
    kLongLivedTreeDepth  = 14; // 1Mb 16;  // about 4Mb
    kArraySize  = 250000; // 1Mb 500000;  // about 4Mb
    kMinTreeDepth = 4;
    kMaxTreeDepth = 8;//14;
    this.sharedroot = sroot;
    //this.sharedarray = sarray;
  }

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
  
  void traverseTree(Node root, int depth) {
    if(root == null) {
      return;
    }
    int sum = root.i + root.j;
    tc1(depth);
    traverseTree(root.left, depth);
    tc2(depth);
    traverseTree(root.right, depth);
  }
  
  void tc3(int depth) {
    // access the shared tree
    traverseTree(this.sharedroot, depth);
    /*int sum = 0;
    for(int i = 0; i < sharedarray.length; i++) {
      tc1(depth);
      sum += sharedarray[i];
      //tc2(depth);
    }*/
  }

  void TimeConstruction(int depth) {
    Node    root;
    //long    tStart, tFinish;
    int 	iNumIters = NumIters(depth);
    Node	tempTree;

    for (int i = 0; i < iNumIters; ++i) {
      tc3(depth);  
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
    Node	root;
    //Node	longLivedTree;
    
    // Stretch the memory space quickly
    stretch();

    // Create a long lived object
    //longLivedTree = new Node();
    //Populate(kLongLivedTreeDepth, longLivedTree);

    // Create long-lived array, filling half of it
    float array[] = new float[kArraySize];
    for (int i = 0; i < kArraySize/2; ++i) {
      array[i] = 1.0f/i;
    }

    for (int d = kMinTreeDepth; d <= kMaxTreeDepth; d += 2) {
      TimeConstruction(0);
    }

    if (/*longLivedTree == null || */array[1000] != 1.0f/1000) {
      //System.out.println("Failed");
      System.printI(0xa0);
      System.printI((int)(array[1000]*1000000));
    }
    // fake reference to LongLivedTree
    // and array
    // to keep them from being optimized away
  }
} // class JavaGC
