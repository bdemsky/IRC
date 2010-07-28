
/**
 * A class used to represent internal nodes in the tree
 **/
final class Cell extends Node
{
  // subcells per cell
  public final int NSUB;  // 1 << NDIM

  /**
   * The children of this cell node.  Each entry may contain either 
   * another cell or a body.
   **/
  public Node[] subp;
  public Cell   next;

  public Cell()
  {
    super();
    NSUB = 8;
    subp = new Node[NSUB];
    next = null;
  }

  /**
   * Descend Tree and insert particle.  We're at a cell so 
   * we need to move down the tree.
   * @param p the body to insert into the tree
   * @param xpic
   * @param l
   * @param tree the root of the tree
   * @return the subtree with the new body inserted
   **/
  public  Node loadTree(Body p, MathVector xpic, int l, Tree tree)
  {
    // move down one level
    int si = oldSubindex(xpic, l);
    Node rt = subp[si];
    if (rt != null) {
      if(rt instanceof Body) {
        Body rtb = (Body)rt;
        subp[si] = rtb.loadTree(p, xpic, l >> 1, tree);
      } else if(rt instanceof Cell) {
        Cell rtc = (Cell)rt;
        subp[si] = rtc.loadTree(p, xpic, l >> 1, tree);
      }
    } else { 
      subp[si] = p;
    }
    return this;
  }

  /**
   * Descend tree finding center of mass coordinates
   * @return the mass of this node
   **/
  public  double hackcofm()
  {
    double mq = 0.0;
    MathVector tmpPos = new MathVector();
    MathVector tmpv   = new MathVector();
    for (int i=0; i < NSUB; i++) {
      Node r = subp[i];
      if (r != null) {
        double mr = 0.0;
        if(r instanceof Body) {
          Body rb = (Body)r;
          mr = rb.hackcofm();
        } else if(r instanceof Cell) {
          Cell rc = (Cell)r;
          mr = rc.hackcofm();
        }
        mq = mr + mq;
        tmpv.multScalar(r.pos, mr);
        tmpPos.addition(tmpv);
      }
    }
    mass = mq;
    pos = tmpPos;
    pos.divScalar(mass);

    return mq;
  }

  /**
   * Recursively walk the tree to do hackwalk calculation
   **/
  public  HG walkSubTree(double dsq, HG hg)
  {
    if (subdivp(dsq, hg)) {
      for (int k = 0; k < this.NSUB; k++) {
        Node r = subp[k];
        if (r != null) {
          if(r instanceof Body) {
            Body rb = (Body)r;
            hg = rb.walkSubTree(dsq / 4.0, hg);
          } else if(r instanceof Cell) {
            Cell rc = (Cell)r;
            hg = rc.walkSubTree(dsq / 4.0, hg);
          }
        }
      }
    } else {
      hg = gravSub(hg);
    }
    return hg;
  }

  /**
   * Decide if the cell is too close to accept as a single term.
   * @return true if the cell is too close.
   **/
  public  boolean subdivp(double dsq, HG hg)
  {
    MathVector dr = new MathVector();
    dr.subtraction(pos, hg.pos0);
    double drsq = dr.dotProduct();

    // in the original olden version drsp is multiplied by 1.0
    return (drsq < dsq);
  }

  /**
   * Return a string represenation of a cell.
   * @return a string represenation of a cell.
   **/
  /*public String toString()
  {
    return "Cell " + super.toString();
  }*/

}
