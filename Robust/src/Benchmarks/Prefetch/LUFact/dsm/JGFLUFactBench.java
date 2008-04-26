/**************************************************************************
 *                                                                         *
 *         Java Grande Forum Benchmark Suite - Thread Version 1.0          *
 *                                                                         *
 *                            produced by                                  *
 *                                                                         *
 *                  Java Grande Benchmarking Project                       *
 *                                                                         *
 *                                at                                       *
 *                                                                         *
 *                Edinburgh Parallel Computing Centre                      *
 *                                                                         * 
 *                email: epcc-javagrande@epcc.ed.ac.uk                     *
 *                                                                         *
 *                                                                         *
 *      This version copyright (c) The University of Edinburgh, 2001.      *
 *                         All rights reserved.                            *
 *                                                                         *
 **************************************************************************/
public class JGFLUFactBench {
  public int nthreads;
  private int size;
  private int[] datasizes;
  public int cachelinesize;
  double a[][];
  double b[];
  double x[];
  double ops,total,norma,normx;
  double resid,time;
  double kf;
  int n,i,ntimes,info,lda,ldaa,kflops;
  int ipvt[];

  public JGFLUFactBench(int nthreads) {
    this.nthreads=nthreads;
    datasizes = global new int[3];
    datasizes[0] = 500;
    datasizes[1] = 1000;
    datasizes[2] = 2000;
    cachelinesize = 128;
  }

  public void JGFsetsize(int size) {
    this.size = size;
  }

  public void JGFinitialise() {
    n = datasizes[size]; 
    ldaa = n; 
    lda = ldaa + 1;

    a = global new double[ldaa][lda];
    b = global new double [ldaa];
    x = global new double [ldaa];
    ipvt = global new int [ldaa];

    long nl = (long) n;   //avoid integer overflow
    ops = (2.0*(nl*nl*nl))/3.0 + 2.0*(nl*nl);
    norma = matgen(a,lda,n,b);    
  }

  public static void JGFkernel(JGFLUFactBench lub) {
    int numthreads;
    atomic {
      numthreads = lub.nthreads;
    }

    /* spawn threads */
    LinpackRunner[] thobjects;
    TournamentBarrier br;
    atomic {
      thobjects = global new LinpackRunner[numthreads];
      br = global new TournamentBarrier(numthreads);
    }

    //JGFInstrumentor.startTimer("Section2:LUFact:Kernel", instr.timers);  

    LinpackRunner tmp;
    int mid = (128<<24)|(195<<16)|(175<<8)|73;
    for(int i=1;i<numthreads;i++) {
      atomic {
        thobjects[i] = global new LinpackRunner(i,lub.a,lub.lda,lub.n,lub.ipvt,br,lub.nthreads);
        tmp = thobjects[i];
      }
      tmp.start(mid);
    }

    atomic {
      thobjects[0] = global new LinpackRunner(0,lub.a,lub.lda,lub.n,lub.ipvt,br,lub.nthreads);
      tmp = thobjects[0];
    }
    tmp.start(mid);
    tmp.join();

    for(int i=1;i<numthreads;i++) {
      atomic {
        tmp = thobjects[i];
      }
      tmp.join();
    }

    atomic {
      lub.dgesl(lub.a,lub.lda,lub.n,lub.ipvt,lub.b,0);
    }
    //JGFInstrumentor.stopTimer("Section2:LUFact:Kernel", instr.timers); 
  }

  public int JGFvalidate() {
    int i;
    double eps,residn;
    double[] ref;

    ref = new double[3]; 
    ref[0] = 6.0;
    ref[1] = 12.0;
    ref[2] = 20.0;

    for (i = 0; i < n; i++) {
      x[i] = b[i];
    }
    norma = matgen(a,lda,n,b);
    for (i = 0; i < n; i++) {
      b[i] = -(b[i]);
    }

    dmxpy(n,b,n,lda,x,a);
    resid = 0.0;
    normx = 0.0;
    for (i = 0; i < n; i++) {
      //resid = (resid > abs(b[i])) ? resid : abs(b[i]);
      //normx = (normx > abs(x[i])) ? normx : abs(x[i]);
      if (resid <= abs(b[i])) resid = abs(b[i]);
      if (normx <= abs(x[i])) normx = abs(x[i]);
    }
    eps =  epslon((double)1.0);
    residn = resid/( n*norma*normx*eps );

    /*******************Compare longs ***********/
    long lresidn, lref;
    lresidn = (long) residn * 1000000;
    lref = (long) ref[size] * 1000000;

    if (lresidn > lref) {
      //System.printString("Validation failed");
      System.printString("Computed Norm Res = " + (long) residn * 1000000);
      System.printString("Reference Norm Res = " + (long) ref[size] * 1000000); 
      return 1;
    } else {
      return 0;
    }
  }

  double abs (double d) {
    if (d >= 0) return d;
    else return -d;
  }

  double matgen (double a[][], int lda, int n, double b[])
  {
    double norma;
    int init, i, j;

    init = 1325;
    norma = 0.0;
    /*  Next two for() statements switched.  Solver wants
        matrix in column order. --dmd 3/3/97
        */
    for (i = 0; i < n; i++) {
      for (j = 0; j < n; j++) {
        init = 3125*init % 65536;
        a[j][i] = (init - 32768.0)/16384.0;
        if (a[j][i] > norma) {
          norma = a[j][i];
        }
      }
    }
    for (i = 0; i < n; i++) {
      b[i] = 0.0;
    }
    for (j = 0; j < n; j++) {
      for (i = 0; i < n; i++) {
        b[i] += a[j][i];
      }
    }
    return norma;
  }

  void dgesl( double a[][], int lda, int n, int ipvt[], double b[], int job)
  {
    double t;
    int k,kb,l,nm1,kp1;

    nm1 = n - 1;
    if (job == 0) {
      // job = 0 , solve  a * x = b.  first solve  l*y = b
      if (nm1 >= 1) {
        for (k = 0; k < nm1; k++) {
          l = ipvt[k];
          t = b[l];
          if (l != k){
            b[l] = b[k];
            b[k] = t;
          }
          kp1 = k + 1;
          daxpy(n-(kp1),t,a[k],kp1,1,b,kp1,1);
        }
      }
      // now solve  u*x = y
      for (kb = 0; kb < n; kb++) {
        k = n - (kb + 1);
        b[k] /= a[k][k];
        t = -b[k];
        daxpy(k,t,a[k],0,1,b,0,1);
      }
    } else {
      // job = nonzero, solve  trans(a) * x = b.  first solve  trans(u)*y = b
      for (k = 0; k < n; k++) {
        t = ddot(k,a[k],0,1,b,0,1);
        b[k] = (b[k] - t)/a[k][k];
      }
      // now solve trans(l)*x = y 
      if (nm1 >= 1) {
        for (kb = 1; kb < nm1; kb++) {
          k = n - (kb+1);
          kp1 = k + 1;
          b[k] += ddot(n-(kp1),a[k],kp1,1,b,kp1,1);
          l = ipvt[k];
          if (l != k) {
            t = b[l];
            b[l] = b[k];
            b[k] = t;
          }
        }
      }
    }
  }

  /*
     constant times a vector plus a vector.
     jack dongarra, linpack, 3/11/78.
     */
  void daxpy( int n, double da, double dx[], int dx_off, int incx,
      double dy[], int dy_off, int incy)
  {
    int i,ix,iy;

    if ((n > 0) && (da != 0)) {
      if (incx != 1 || incy != 1) {
        // code for unequal increments or equal increments not equal to 1
        ix = 0;
        iy = 0;
        if (incx < 0) ix = (-n+1)*incx;
        if (incy < 0) iy = (-n+1)*incy;
        for (i = 0;i < n; i++) {
          dy[iy +dy_off] += da*dx[ix +dx_off];
          ix += incx;
          iy += incy;
        }
        return;
      } else {
        // code for both increments equal to 1
        for (i=0; i < n; i++)
          dy[i +dy_off] += da*dx[i +dx_off];
      }
    }
  }

  /*
     forms the dot product of two vectors.
     jack dongarra, linpack, 3/11/78.
     */
  double ddot( int n, double dx[], int dx_off, int incx, double dy[],
      int dy_off, int incy)
  {
    double dtemp;
    int i,ix,iy;
    dtemp = 0;
    if (n > 0) {
      if (incx != 1 || incy != 1) {
        // code for unequal increments or equal increments not equal to 1
        ix = 0;
        iy = 0;
        if (incx < 0) ix = (-n+1)*incx;
        if (incy < 0) iy = (-n+1)*incy;
        for (i = 0;i < n; i++) {
          dtemp += dx[ix +dx_off]*dy[iy +dy_off];
          ix += incx;
          iy += incy;
        }
      } else {
        // code for both increments equal to 1
        for (i=0;i < n; i++)
          dtemp += dx[i +dx_off]*dy[i +dy_off];
      }
    }
    return(dtemp);
  }

  /*
     scales a vector by a constant.
     jack dongarra, linpack, 3/11/78.
     */
  void dscal( int n, double da, double dx[], int dx_off, int incx)
  {
    int i,nincx;
    if (n > 0) {
      if (incx != 1) {
        // code for increment not equal to 1
        nincx = n*incx;
        for (i = 0; i < nincx; i += incx)
          dx[i +dx_off] *= da;
      } else {
        // code for increment equal to 1
        for (i = 0; i < n; i++)
          dx[i +dx_off] *= da;
      }
    }
  }

  /*
     finds the index of element having max. absolute value.
     jack dongarra, linpack, 3/11/78.
     */
  int idamax( int n, double dx[], int dx_off, int incx)
  {
    double dmax, dtemp;
    int i, ix, itemp=0;

    if (n < 1) {
      itemp = -1;
    } else if (n ==1) {
      itemp = 0;
    } else if (incx != 1) {
      // code for increment not equal to 1
      dmax = abs(dx[0 +dx_off]);
      ix = 1 + incx;
      for (i = 1; i < n; i++) {
        dtemp = abs(dx[ix + dx_off]);
        if (dtemp > dmax)  {
          itemp = i;
          dmax = dtemp;
        }
        ix += incx;
      }
    } else {
      // code for increment equal to 1
      itemp = 0;
      dmax = abs(dx[0 +dx_off]);
      for (i = 1; i < n; i++) {
        dtemp = abs(dx[i + dx_off]);
        if (dtemp > dmax) {
          itemp = i;
          dmax = dtemp;
        }
      }
    }
    return (itemp);
  }

  /*
     estimate unit roundoff in quantities of size x.
     this program should function properly on all systems
     satisfying the following two assumptions,
     1.  the base used in representing dfloating point
     numbers is not a power of three.
     2.  the quantity  a  in statement 10 is represented to
     the accuracy used in dfloating point variables
     that are stored in memory.
     the statement number 10 and the go to 10 are intended to
     force optimizing compilers to generate code satisfying
     assumption 2.
     under these assumptions, it should be true that,
     a  is not exactly equal to four-thirds,
     b  has a zero for its last bit or digit,
     c  is not exactly equal to one,
     eps  measures the separation of 1.0 from
     the next larger dfloating point number.
     the developers of eispack would appreciate being informed
     about any systems where these assumptions do not hold.

   *****************************************************************
   this routine is one of the auxiliary routines used by eispack iii
   to avoid machine dependencies.
   *****************************************************************

   this version dated 4/6/83.
   */
  double epslon (double x)
  {
    double a,b,c,eps;

    a = 4.0e0/3.0e0;
    eps = 0;
    while (eps == 0) {
      b = a - 1.0;
      c = b + b + b;
      eps = abs(c-1.0);
    }
    return(eps*abs(x));
  }

  void dmxpy ( int n1, double y[], int n2, int ldm, double x[], double m[][])
  {
    int j,i;
    // cleanup odd vector
    for (j = 0; j < n2; j++) {
      for (i = 0; i < n1; i++) {
        y[i] += x[j]*m[j][i];
      }
    }
  }
  /*
     public static void JGFvalidate(JGFLUFactBench lub) {
     int i;
     double eps,residn;
     double[] ref;

     ref = new double[3]; 
     ref[0] = 6.0;
     ref[1] = 12.0;
     ref[2] = 20.0;

     atomic {
     for (i = 0; i < lub.n; i++) {
     lub.x[i] = lub.b[i];
     }
     lub.norma = lub.matgen(lub.a,lub.lda,lub.n,lub.b);
     for (i = 0; i < lub.n; i++) {
     lub.b[i] = -(lub.b[i]);
     }

     lub.dmxpy(lub.n,lub.b,lub.n,lub.lda,lub.x,lub.a);
     lub.resid = 0.0;
     lub.normx = 0.0;
     for (i = 0; i < lub.n; i++) {
//resid = (resid > abs(b[i])) ? resid : abs(b[i]);
//normx = (normx > abs(x[i])) ? normx : abs(x[i]);
if (lub.resid <= abs(lub.b[i])) lub.resid = lub.abs(lub.b[i]);
if (lub.normx <= abs(lub.x[i])) lub.normx = lub.abs(lub.x[i]);
}
eps =  lub.epslon((double)1.0);
residn = lub.resid/( lub.n*lub.norma*lub.normx*eps );
}

if (residn > ref[size]) {
System.printString("Validation failed");
System.printString("Computed Norm Res = " + (long) residn);
System.printString("Reference Norm Res = " + (long) ref[size]); 
}
}
*/

}
