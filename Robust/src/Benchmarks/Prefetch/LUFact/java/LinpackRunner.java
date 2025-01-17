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

class LinpackRunner extends Thread {
  int id,lda,n,info,ipvt[];
  double a[][];
  TournamentBarrier br;
  int nthreads;

  public LinpackRunner(int id, double a[][], int lda, int n, int ipvt[],TournamentBarrier br, int nthreads) {
    this.id = id;
    this.a=a;
    this.lda=lda;
    this.n=n;
    this.ipvt=ipvt;
    this.br=br;
    //this.instr = instr;
    this.nthreads = nthreads;
  }

  double abs (double d) {
    //return (d >= 0) ? d : -d;
    if (d >= 0) return d;
    else return -d;
  }

  public void run() {
    double[] col_k, col_j;
    double t;
    int j,k,kp1,l,nm1;
    int info;
    int slice,ilow,iupper;
    // gaussian elimination with partial pivoting
    info = 0;
    nm1 = n - 1;
    if (nm1 >=  0) {
      for (k = 0; k < nm1; k++) {
        col_k = a[k];
        kp1 = k + 1;
        // find l = pivot index
        l = idamax(n-k,col_k,k,1) + k;
        if(id==0) {
          ipvt[k] = l;
        }
        // synchronise threads
        br.DoBarrier(id);
        // zero pivot implies this column already triangularized
        if (col_k[l] != 0) {
          br.DoBarrier(id);
          // interchange if necessary
          if(id == 0 ) {
            if (l != k) {
              t = col_k[l];
              col_k[l] = col_k[k];
              col_k[k] = t;
            }
          }
          // synchronise threads
          br.DoBarrier(id);
          // compute multipliers
          t = -1.0/col_k[k];
          if(id == 0) {
            dscal(n-(kp1),t,col_k,kp1,1);
          }
          // synchronise threads
          br.DoBarrier(id);
          // row elimination with column indexing
          slice = ((n-kp1) + nthreads-1)/nthreads;
          ilow = (id*slice)+kp1;
          iupper = ((id+1)*slice)+kp1;
          if (iupper > n ) iupper=n;
          if (ilow > n ) ilow=n;
          for (j = ilow; j < iupper; j++) {
            col_j = a[j];
            t = col_j[l];
            if (l != k) {
              col_j[l] = col_j[k];
              col_j[k] = t;
            }
            daxpy(n-(kp1),t,col_k,kp1,1,
                col_j,kp1,1);
          }
          // synchronise threads
          br.DoBarrier(id);
        } else {
          info = k;
        }
        br.DoBarrier(id);
      }
    }

    if(id==0) {
      ipvt[n-1] = n-1;
    }
    if (a[(n-1)][(n-1)] == 0) info = n-1;
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
}

