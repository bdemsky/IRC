#define ROTATE_LEFT(set, node) rotateLeft(set, node)
#define ROTATE_RIGHT(set, node) rotateRight(set, node)

public class RBTree {
  Node root;
  // FIXME Test if this is correct
  public int compare(int a, int b) {
    return (a-b);
  }

  public RBTree(void) {
    root = null;
  }

  public Node lookup(RBTree s, Object k) {
    Node p = s.root;
    while (p != null) {
      int cmp = s.compare(k, p.k);
      if (cmp == 0) {
        return p;
      }
      if (cmp < 0) {
        p = p.l;
      } else {
        p = p.r;
      }
    }
    return null;
  }

  /*
   * Balancing operations.
   *
   * Implementations of rebalancings during insertion and deletion are
   * slightly different than the CLR version.  Rather than using dummy
   * nilnodes, we use a set of accessors that deal properly with null.  They
   * are used to avoid messiness surrounding nullness checks in the main
   * algorithms.
   *
   * From CLR
   */
  public void rotateLeft(Node x) {
    Node r = LDNODE(x, r);
    Node rl = LDNODE(r, l);
    STF(x, r, rl);
    if (rl != null) {
      STF(rl, p, x);
    }
    /* TODO: compute p = xp = x->p.  Use xp for R-Values in following */
    Node xp = LDNODE(x, p);
    STF(r, p, xp);
    if (xp == NULL) {
      STF(this, root, r);
    } else if (LDNODE(xp, l) == x) {
      STF(xp, l, r);
    } else {
      STF(xp, r, r);
    }
  }

  public rotateRight(Node x) {
    Node l = LDNODE(x, l);
    Node lr = LDNODE(l, r);
    STF(x, l, lr);
    if (lr != NULL) {
      STF(lr, p, x);
    }
    Node xp = LDNODE(x, p);
    STF(l, p, xp);
    if (xp == NULL) {
      STF(this, root, l);
    } else if (LDNODE(xp, r) == x) {
      STF(xp, r, l);
    } else {
      STF(xp, l, l);
    }
    STF(l, r, x);
    STF(x, p, l);
  }

  public Node parentOf(Node n) {
    if (n ! = null) {
      return LDNODE(n,p);
    }
    return null;
  }

}
