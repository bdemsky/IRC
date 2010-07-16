public class FibHeap {

  public int degree;
  public TaggedTree ttree;
  public Vector forest;
  
  public FibHeap() {
    this.degree = 0;
    this.ttree = null;
    this.forest = null;
  }
  
  public FibHeap(int degree,
                 TaggedTree ttree,
                 Vector forest) {
    this.degree = degree;
    this.ttree = ttree;
    this.forest = forest;
  }
  
  public boolean isEmpty() {
    return this.degree == 0;
  }
  
  public int minFH() {
    return this.ttree.tree.root;
  }
  
  public FibHeap insertFH(int x) {
    TaggedTree tt = new TaggedTree(0, new Tree(x, null));
    FibHeap fh = new FibHeap(1, tt, null);
    
    return this.meldFH(fh);
  }
  
  public FibHeap meldFH(FibHeap fh) {
    if(this.isEmpty()) {
      return fh;
    } else {
      int root1 = fh.ttree.tree.root;
      int root2 = this.ttree.tree.root;
      TaggedTree root = null;
      Vector forest = fh.forest;
      if(forest == null) {
        forest = new Vector();
      }
      if(root1 <= root2) {
        root = fh.ttree;
        forest.insertElementAt(this.ttree, 0);
      } else {
        root = this.ttree;
        forest.insertElementAt(fh.ttree, 0);
      }
      if(this.forest != null) {
        for(int i = 0; i < this.forest.size(); i++) {
          forest.addElement(this.forest.elementAt(i));
        }
      }
      return new FibHeap(fh.degree+this.degree, root, forest);
    }
  }
  
  private void insert(Vector a,
                      TaggedTree tt) {
    int index = tt.degree;
    if(a.elementAt(index) == null) {
      a.setElementAt(tt.tree, index);
    } else {
      Tree at = (Tree)a.elementAt(index);
      a.setElementAt(null, index);
      // link these two tree
      Tree it = tt.tree.link(at);
      TaggedTree itt = new TaggedTree(index+1, it);
      insert(a, itt);
    }
  }
  
  private FibHeap getMin_t(Vector a,
                           int mini,
                           Tree mint,
                           Vector b,
                           int i,
                           int d) {
    if(i >= d) {
      return new FibHeap(this.degree-1, new TaggedTree(mini, mint), b);
    } else {
      Tree at = (Tree)a.elementAt(i);
      if(at == null) {
        return getMin_t(a, mini, mint, b, i+1, d);
      } else {
        if(mint.root <= at.root) {
          b.insertElementAt(new TaggedTree(i, at), 0);
          return getMin_t(a, mini, mint, b, i+1, d);
        } else {
          b.insertElementAt(new TaggedTree(mini, mint), 0);
          return getMin_t(a, i, at, b, i+1, d);
        }
      }
    }
  }
  
  private int locallog(int n) {
    if(n == 1) {
      return 0;
    } else {
      return 1 + locallog(n/2);  
    }
  }
  
  public FibHeap deleteMinFH() {
    if(this.isEmpty()) {
      // error here
      System.exit(0xa0);
    }
    if(this.degree == 1) {
      return new FibHeap();
    }
    // newArray (0,d) Zero >>= \a -> applyToAll (ins a) f 
    // Allocate an array indexed by degrees.
    int d = locallog(this.degree - 1);
    Vector a = new Vector(d+1);
    for(int i = 0; i < d+1; i++) {
      a.addElement(null);
    }
    // Insert every tree into this array.  If, when inserting a tree of 
    // degree k, there already exists a tree of degree k, link the
    // two trees and reinsert the new larger tree.
    for(int i = 0; i < this.forest.size(); i++) {
      TaggedTree tt = (TaggedTree)this.forest.elementAt(i);
      insert(a, tt);
    }
    // sequence (map (ins a) (getChildren tt)) 
    Vector vec = this.ttree.getChildren();
    for(int i = 0; i < vec.size(); i++) {
      TaggedTree tt = (TaggedTree)vec.elementAt(i);
      insert(a, tt);
    }
    // getMin a >>= \ (tt,f) -> return (FH (n-1) tt f))
    Tree test = (Tree)a.elementAt(d);
    if(test == null) {
      // error here
      System.exit(0xa1);
    } else {
      return getMin_t(a, d, test, new Vector(), 0, d);
    }
  }
  
  private Vector combine(int index,
                         Vector ts,
                         Vector next,
                         Vector rest) {
    if(ts.size() == 0) {
      return startup(index+1, next, rest);
    } else if (ts.size() == 1) {
      Vector vec = startup(index+1, next, rest);
      vec.insertElementAt(new TaggedTree(index, (Tree)ts.elementAt(0)), 0);
      return vec;
    } else {
      Tree t1 = (Tree)ts.elementAt(0);
      Tree t2 = (Tree)ts.elementAt(1); 
      next.insertElementAt(t1.link(t2), 0);
      Vector nts = new Vector();
      for(int i = 2; i < ts.size(); i++) {
        nts.addElement(ts.elementAt(i));
      }
      return combine(index, nts, next, rest);
    }
  }
  
  private Vector startup(int index,
                         Vector ts,
                         Vector rest) {
    if(ts.size() == 0) {
      if(rest.size() == 0) {
        return new Vector();
      } else {
        Vector tts = (Vector)rest.elementAt(0);
        Vector nrest = new Vector();
        for(int i = 1; i < rest.size(); i++) {
          nrest.addElement(rest.elementAt(i));
        }
        return startup(index+1, tts, nrest);
      }
    } else {
      if(rest.size() == 0) {
        return combine(index, ts, new Vector(), new Vector());
      } else {
        Vector tts = (Vector)rest.elementAt(0);
        Vector nrest = new Vector();
        for(int i = 1; i < rest.size(); i++) {
          nrest.addElement(rest.elementAt(i));
        }
        return combine(index, ts, tts, nrest);
      }
    }
  }
  
  private FibHeap chooseMin(FibHeap fh,
                            TaggedTree tt) {
    FibHeap rfh = null;
    if(fh.ttree.tree.root <= tt.tree.root) {
      fh.forest.insertElementAt(tt, 0);
      rfh = new FibHeap(this.degree-1, fh.ttree, fh.forest);
    } else {
      fh.forest.insertElementAt(fh.ttree, 0);
      rfh = new FibHeap(this.degree-1, tt, fh.forest);
    }
    return rfh;
  }
  
  public FibHeap deleteMinFH_t() {
    if(this.isEmpty()) {
      // error here
      System.exit(0xa2);
    }
    if(this.degree == 1) {
      return new FibHeap();
    }
    // The second version of deleteMin uses accumArray to group trees of like
    // size.  It then performs the linking and all remaining steps purely 
    // functionally.
    int d = locallog(this.degree - 1);
    // arrange a 2 dimentional array to group the trees
    Vector a = new Vector(d+1);
    for(int i = 0; i < d+1; i++) {
      a.addElement(new Vector());
    }
    for(int i = 0; i < this.forest.size(); i++) {
      TaggedTree tt = (TaggedTree)this.forest.elementAt(i);
      int de = tt.degree;
      ((Vector)a.elementAt(de)).addElement(tt.tree);
    }
    // sequence (map (ins a) (getChildren tt)) 
    Vector vec = this.ttree.getChildren();
    for(int i = 0; i < vec.size(); i++) {
      TaggedTree tt = (TaggedTree)vec.elementAt(i);
      int de = tt.degree;
      ((Vector)a.elementAt(de)).addElement(tt.tree);
    }
    Vector ts = (Vector)a.elementAt(0);
    Vector na = new Vector();
    for(int i = 1; i < a.size(); i++) {
      na.addElement(a.elementAt(i));
    }
    Vector vvec = startup(0, ts, na);
    
    // getMin()
    TaggedTree rtt = (TaggedTree)vvec.elementAt(0);
    FibHeap rfh = new FibHeap(this.degree-1, rtt, new Vector());
    Vector nvvec = new Vector();
    for(int i = 1; i < vvec.size(); i++) {
      nvvec.addElement(vvec.elementAt(i));
    }
    vvec = nvvec;
    while(vvec.size() != 0) {
      rfh = chooseMin(rfh, (TaggedTree)vvec.elementAt(0));
      Vector tvvec = new Vector();
      for(int i = 1; i < vvec.size(); i++) {
        tvvec.addElement(vvec.elementAt(i));
      }
      vvec = tvvec;
    }
    return rfh;
  }
}