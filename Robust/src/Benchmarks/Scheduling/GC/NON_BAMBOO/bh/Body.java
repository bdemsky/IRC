package bh;

/**
 * A class used to representing particles in the N-body simulation.
 **/
final class Body extends Node
{
  public MathVector vel;
  public MathVector acc;
  public MathVector newAcc;
  public double     phi;

  public  Body next;
  public  Body procNext;

  /**
   * Create an empty body.
   **/
  public Body()
  {
    super();
    vel      = new MathVector();
    acc      = new MathVector();
    newAcc   = new MathVector();
    phi      = 0.0;
    next     = null;
    procNext = null;
  }

  /**
   * Set the next body in the list.
   * @param n the body
   **/
  public  void setNext(Body n)
  {
    next = n;
  }

  /**
   * Get the next body in the list.
   * @return the next body
   **/
  public  Body getNext()
  {
    return next;
  }

  /**
   * Set the next body in the list.
   * @param n the body
   **/
  public  void setProcNext(Body n)
  {
    procNext = n;
  }

  /**
   * Get the next body in the list.
   * @return the next body
   **/
  public  Body getProcNext()
  {
    return procNext;
  }

  /**
   * Enlarge cubical "box", salvaging existing tree structure.
   * @param tree the root of the tree.
   * @param nsteps the current time step
   **/
  public  void expandBox(Tree tree, int nsteps)
  {
    MathVector rmid = new MathVector();

    boolean inbox = icTest(tree);
    while (!inbox) {
      double rsize = tree.rsize;
      rmid.addScalar(tree.rmin, 0.5 * rsize);

      for (int k = 0; k < rmid.NDIM; k++) {
        if (pos.value(k) < rmid.value(k)) {
          double rmin = tree.rmin.value(k);
          tree.rmin.value(k, rmin - rsize);
        }
      }
      tree.rsize = 2.0 * rsize;
      if (tree.root != null) {
        MathVector ic = tree.intcoord(rmid);
        if (ic == null) {
          //throw new Error("Value is out of bounds");
          System.exit(-2);
        }
        int k = oldSubindex(ic, IMAX >> 1);
        Cell newt = new Cell();
        newt.subp[k] = tree.root;
        tree.root = newt;
        inbox = icTest(tree);
      }
    }
  }

  /**
   * Check the bounds of the body and return true if it isn't in the
   * correct bounds.
   **/
  public  boolean icTest(Tree tree)
  {
    double pos0 = pos.value(0);
    double pos1 = pos.value(1);
    double pos2 = pos.value(2);

    // by default, it is in bounds
    boolean result = true;

    double xsc = (pos0 - tree.rmin.value(0)) / tree.rsize;
    if (!(0.0 < xsc && xsc < 1.0)) {
      result = false;
    }

    xsc = (pos1 - tree.rmin.value(1)) / tree.rsize;
    if (!(0.0 < xsc && xsc < 1.0)) {
      result = false;
    }

    xsc = (pos2 - tree.rmin.value(2)) / tree.rsize;
    if (!(0.0 < xsc && xsc < 1.0)) {
      result = false;
    }

    return result;
  }

  /**
   * Descend Tree and insert particle.  We're at a body so we need to
   * create a cell and attach this body to the cell.
   * @param p the body to insert
   * @param xpic
   * @param l 
   * @param tree the root of the data structure
   * @return the subtree with the new body inserted
   **/
  public  Node loadTree(Body p, MathVector xpic, int l, Tree tree)
  {
    // create a Cell
    Cell retval = new Cell();
    int si = subindex(tree, l);
    // attach this Body node to the cell
    retval.subp[si] = this;

    // move down one level
    si = oldSubindex(xpic, l);
    Node rt = retval.subp[si];
    if (rt != null)  {
      if(rt instanceof Body) {
        Body rtb = (Body) rt;
        retval.subp[si] = rtb.loadTree(p, xpic, l >> 1, tree);
      } else if(rt instanceof Cell){
        Cell rtc = (Cell) rt;
        retval.subp[si] = rtc.loadTree(p, xpic, l >> 1, tree);
      }
    } else {
      retval.subp[si] = p;
    }
    return retval;
  }

  /**
   * Descend tree finding center of mass coordinates
   * @return the mass of this node
   **/
  public  double hackcofm()
  {
    return mass;
  }

  /**
   * Determine which subcell to select.
   * Combination of intcoord and oldSubindex.
   * @param t the root of the tree
   **/
  public  int subindex(Tree tree, int l)
  {
    MathVector xp = new MathVector();

    double xsc = (pos.value(0) - tree.rmin.value(0)) / tree.rsize;
    xp.value(0, Math.floor(1073741824/*IMAX*/ * xsc));

    xsc = (pos.value(1) - tree.rmin.value(1)) / tree.rsize;
    xp.value(1, Math.floor(1073741824/*IMAX*/ * xsc));

    xsc = (pos.value(2) - tree.rmin.value(2)) / tree.rsize;
    xp.value(2, Math.floor(1073741824/*IMAX*/ * xsc));

    int i = 0;
    for (int k = 0; k < xp.NDIM; k++) {
      if (((int)xp.value(k) & l) != 0) {
        i += 8/*Cell.NSUB*/ >> (k + 1);
      }
    }
    return i;
  }

  /**
   * Evaluate gravitational field on the body.
   * The original olden version calls a routine named "walkscan",
   * but we use the same name that is in the Barnes code.
   **/
  public  void hackGravity(double rsize, Node root)
  {
    MathVector pos0 = (MathVector)pos.clone();

    HG hg = new HG(this, pos);
    if(root instanceof Body) {
      Body rootb = (Body)root;
      hg = rootb.walkSubTree(rsize * rsize, hg);
    } else if(root instanceof Cell) {
      Cell rootc = (Cell)root;
      hg = rootc.walkSubTree(rsize * rsize, hg);
    }
    this.phi = hg.phi0;
    this.newAcc = hg.acc0;
  }

  /**
   * Recursively walk the tree to do hackwalk calculation
   **/
  public  HG walkSubTree(double dsq, HG hg)
  {
    if (this != hg.pskip)
      hg = gravSub(hg);
    return hg;
  }

  /**
   * Return a string represenation of a body.
   * @return a string represenation of a body.
   **/
  /*public String toString()
  {
    return "Body " + super.toString();
  }*/

}