
//import java.util.Enumeration;

/**
 * A class that represents the root of the data structure used
 * to represent the N-bodies in the Barnes-Hut algorithm.
 **/
class Tree
{
  public double DTIME; 
  
  MathVector rmin;
  public double     rsize;
  /**
   * A reference to the root node.
   **/
  public Node       root;
  /**
   * The complete list of bodies that have been created.
   **/
  public  Body       bodyTab;
  /**
   * The complete list of bodies that have been created - in reverse.
   **/
  public  Body       bodyTabRev;

  /**
   * Construct the root of the data structure that represents the N-bodies.
   **/
  public Tree(double DTIME)
  {
    rmin       = new MathVector();
    rsize      = -2.0 * -2.0;
    root       = null;
    bodyTab    = null;
    bodyTabRev = null;

    rmin.value(0, -2.0);
    rmin.value(1, -2.0);
    rmin.value(2, -2.0);
    
    this.DTIME = DTIME;
  }

  /**
   * Return an enumeration of the bodies.
   * @return an enumeration of the bodies.
   **/
  /*final Enumeration bodies()
  {
    return bodyTab.elements();
  }*/

  /**
   * Return an enumeration of the bodies - in reverse.
   * @return an enumeration of the bodies - in reverse.
   **/
  /*final Enumeration bodiesRev()
  {
    return bodyTabRev.elementsRev();
  }*/

  /**
   * Random number generator used by the orignal BH benchmark.
   * @param seed the seed to the generator
   * @return a random number
   **/
  public double myRand(double seed)
  {
    double t = 16807.0*seed + 1;

    double iseed = t - (2147483647.0 * Math.floor(t / 2147483647.0f));
    return iseed;
  }

  /**
   * Generate a doubleing point random number.  Used by
   * the original BH benchmark.
   *
   * @param xl lower bound
   * @param xh upper bound
   * @param r seed
   * @return a doubleing point randon number
   **/
  public double xRand(double xl, double xh, double r)
  {
    double res = xl + (xh-xl)*r/2147483647.0;
    return res;
  }
  
  /**
   * Create the testdata used in the benchmark.
   * @param nbody the number of bodies to create
   **/
  public  void createTestData(int nbody)
  {
    MathVector cmr = new MathVector();
    MathVector cmv = new MathVector();

    Body head = new Body();
    Body prev = head;

    double rsc  = 3.0 * Math.PI() / 16.0;
    double vsc  = Math.sqrt(1.0 / rsc);
    double seed = 123.0;
    //Random rand = new Random((long)seed);
    //int max_int = ~(int)0x1+1;

    for (int i = 0; i < nbody; i++) {
      Body p = new Body();

      prev.setNext(p);
      prev = p;
      p.mass = 1.0/nbody;

      seed      = myRand(seed);
      //seed = Math.abs((double)rand.nextInt()/max_int);
      double t1 = xRand(0.0, 0.999, seed);
      t1        = Math.pow(t1, (-2.0/3.0)) - 1.0;
      double r  = 1.0 / Math.sqrt(t1);

      double coeff = 4.0;
      for (int k = 0; k < cmr.NDIM; k++) {
        seed = myRand(seed);
        //seed = Math.abs((double)rand.nextInt()/max_int);
        r = xRand(0.0, 0.999, seed);
        p.pos.value(k, coeff*r);
      }

      cmr.addition(p.pos);

      double x, y;
      do {
        seed = myRand(seed);
        //seed = Math.abs((double)rand.nextInt()/max_int);
        x    = xRand(0.0, 1.0, seed);
        seed = myRand(seed);
        //seed = Math.abs((double)rand.nextInt()/max_int);
        y    = xRand(0.0, 0.1, seed);
      } while (y > (x*x * Math.pow((1.0f - x*x), 3.5)));

      double v = Math.sqrt(2.0) * x / Math.pow(1 + r*r, 0.25);

      double rad = vsc * v;
      double rsq;
      do {
        for (int k = 0; k < cmr.NDIM; k++) {
          seed     = myRand(seed);
          //seed = Math.abs((double)rand.nextInt()/max_int);
          p.vel.value(k, xRand(-1.0, 1.0, seed));
        }
        rsq = p.vel.dotProduct();
      } while (rsq > 1.0);
      double rsc1 = rad / Math.sqrt(rsq);
      p.vel.multScalar(rsc1);
      cmv.addition(p.vel);
    }

    // mark end of list
    prev.setNext(null);
    // toss the dummy node at the beginning and set a reference to the first element
    bodyTab = head.getNext();

    cmr.divScalar(nbody);
    cmv.divScalar(nbody);

    prev = null;
    Body b = this.bodyTab;
    do {
      b.pos.subtraction(cmr);
      b.vel.subtraction(cmv);
      b.setProcNext(prev);
      prev = b;
      b = b.getNext();
    } while(b != null);
    // set the reference to the last element
    bodyTabRev = prev;
  }


  /**
   * Advance the N-body system one time-step.
   * @param nstep the current time step
   **/
  public void stepSystem(int nstep)
  {
    // free the tree
    this.root = null;

    makeTree(nstep);
    
    Body next = null;
    Body b = this.bodyTabRev;
    do {
      b.hackGravity(this.rsize, this.root);
      b = b.getProcNext();
    } while(b != null);

    vp(this.bodyTabRev, nstep);

  }

  /**
   * Initialize the tree structure for hack force calculation.
   * @param nsteps the current time step
   **/
  public  void makeTree(int nstep)
  {
    Body q = this.bodyTabRev;
    do {
      if (q.mass != 0.0) {
        q.expandBox(this, nstep);
        MathVector xqic = intcoord(q.pos);
        if (this.root == null) {
          this.root = q;
        } else {
          if(root instanceof Body) {
            Body rootb = (Body) root;
            this.root = rootb.loadTree(q, xqic, 1073741824/*Node.IMAX*/ >> 1, this);
          } else if(root instanceof Cell) {
            Cell rootc = (Cell)root;
            this.root = rootc.loadTree(q, xqic, 1073741824/*Node.IMAX*/ >> 1, this);
          }
        }
      }
      q = q.getProcNext();
    } while(q != null);
    if(root instanceof Body) {
      Body rootb = (Body)root;
      rootb.hackcofm();
    } else if(root instanceof Cell) {
      Cell rootc = (Cell)root;
      rootc.hackcofm();
    }
  }

  /**
   * Compute integerized coordinates.
   * @return the coordinates or null if rp is out of bounds
   **/
  public  MathVector intcoord(MathVector vp)
  {
    MathVector xp = new MathVector();

    double xsc = (vp.value(0) - rmin.value(0)) / rsize;
    if (0.0 <= xsc && xsc < 1.0) {
      xp.value(0, Math.floor(1073741824/*Node.IMAX*/ * xsc));
    } else {
      return null;
    }

    xsc = (vp.value(1) - rmin.value(1)) / rsize;
    if (0.0 <= xsc && xsc < 1.0) {
      xp.value(1, Math.floor(1073741824/*Node.IMAX*/ * xsc));
    } else {
      return null;
    }

    xsc = (vp.value(2) - rmin.value(2)) / rsize;
    if (0.0 <= xsc && xsc < 1.0) {
      xp.value(2, Math.floor(1073741824/*Node.IMAX*/ * xsc));
    } else {
      return null;
    }
    return xp;
  }

  public  void vp(Body p, int nstep)
  {
    MathVector dacc = new MathVector();
    MathVector dvel = new MathVector();
    double dthf = 0.5 * this.DTIME;
    
    Body b = p;
    do {
      MathVector acc1 = (MathVector)b.newAcc.clone();
      if (nstep > 0) {
        dacc.subtraction(acc1, b.acc);
        dvel.multScalar(dacc, dthf);
        dvel.addition(b.vel);
        b.vel = (MathVector)dvel.clone();
      }
      b.acc = (MathVector)acc1.clone();
      dvel.multScalar(b.acc, dthf);

      MathVector vel1 = (MathVector)b.vel.clone();
      vel1.addition(dvel);
      MathVector dpos = (MathVector)vel1.clone();
      dpos.multScalar(this.DTIME);
      dpos.addition(b.pos);
      b.pos = (MathVector)dpos.clone();
      vel1.addition(dvel);
      b.vel = (MathVector)vel1.clone();
      
      b = b.getProcNext();
    } while(b != null);
  }
}
