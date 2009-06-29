
#define LDA(a)        a
#define STA(a,v)      a = v
#define LDV(a)        a
#define STV(a,v)      a = v
#define LDF(o,f)      o.f
#define STF(o,f,v)    o.f = v
#define LDNODE(o,f)   LDF(o,f)

#define RED 0
#define BLACK 1

#define SUCCESSOR(n) sucessor(n)
#define PARENT_OF(n) parentOf(n)
#define LEFT_OF(n) leftOf(n)
#define RIGHT_OF(n) rightOf(n)
#define COLOR_OF(n) colorOf(n)
#define SET_COLOR(n, c) setColor(n, c)

public class Node {
  int k;
  Object v;
  RBTree p;
  RBTree l;
  RBTree r;
  int c;

  public Node() { }

  public Node parentOf(Node n) {
    if (n != null) {
      return LDNODE(n,p);
    }
    return null;
  }

  public Node leftOf(Node n) {
    if (n != null) {
      return LDNODE(n, l);
    }
    return null;
  }

  public Node rightOf(Node n) {
    if (n != null) {
      return LDNODE(n, r);
    }
    return null;
  }

  public int colorOf(Node n) {
    if (n != null) {
      return LDNODE(n, c);
    }
    return BLACK;
  }

  public void setColor(Node n, int c) {
    if (n != null) {
      STF(n, c, c);
    }
  }

  /*
   * Return the given node's successor node---the node which has the
   * next key in the the left to right ordering. If the node has
   * no successor, a null pointer is returned rather than a pointer to
   * the nil node
   */
  public Node successor(Node t) {
    if (t == null) {
      return null;
    } else if (LDNODE(t, r) != null) {
      Node p = LDNODE(t, r);
      while (LDNODE(p, l) != null) {
        p = LDNODE(p, l);
      }
      return p;
    } else {
      Node p = LDNODE(t, p);
      Node ch = t;
      while (p != NULL && ch == LDNODE(p, r)) {
        ch = p;
        p = LDNODE(p, p);
      }
      return p;
    }
  }

}
