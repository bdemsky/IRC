public class MatrixMultiply extends Thread{
    MMul mmul;
    public int x0, y0, x1, y1;
    public int tid, numthreads;

    public MatrixMultiply(MMul mmul, int x0, int x1, int y0, int y1, int tid, int numthreads) {
	this.mmul = mmul;
	this.x0 = x0;
	this.y0 = y0;
	this.x1 = x1;
	this.y1 = y1;
	this.tid=tid;
	this.numthreads=numthreads;
    }
    
    public void run() {
      Barrier barr=new Barrier("128.195.136.162");
      int mynumthreads, mytid, P, myx0, myx1, myy0, myy1;

      atomic {
        short[] off = new short[2];
        off[0] = getoffset{MMul, btranspose}; 
        off[1] = (short) 0;
        System.rangePrefetch(mmul, off);
        off[0] = getoffset{MMul, a}; 
        off[1] = (short) 0;
        System.rangePrefetch(mmul, off);
        off[0] = getoffset{MMul, c}; 
        off[1] = (short) 0;
        System.rangePrefetch(mmul, off);


        mmul.setValues(tid, numthreads);
        myx0=x0;
        myx1=x1;
        myy0=y0;
        myy1=y1;
        mynumthreads=numthreads;
        mytid=tid;
        P=mmul.P;
      }

      Barrier.enterBarrier(barr);
      
      atomic {
        short[] offsets = new short[6];
        // Prefetch mmul.btranspose[][][] matrix
        //Get all of B first...we need them first
        offsets[0] = getoffset{MMul, btranspose};
        offsets[1] = (short) 0;
        offsets[2] = (short) 0;
        offsets[3] = (short) 7;
        offsets[4] = (short) y0;
        offsets[5] = (short) (y1 - y0 -1);
        System.rangePrefetch(mmul, offsets);

        //Get first part of A
        offsets[0] = getoffset{MMul, a};
        offsets[1] = (short) 0;
        offsets[2] = (short) 0;
        offsets[3] = (short) 7;
        offsets[4] = (short) x0;
        offsets[5] = (short) 15;
        System.rangePrefetch(mmul, offsets);

        //Get first part of C
        offsets[0] = getoffset{MMul, c};
        offsets[1] = (short) 0;
        System.rangePrefetch(mmul, offsets);
        short[] offsets2=new short[4];

        double la[][][]=mmul.a;
        double lc[][][]=mmul.c;
        double lb[][][]=mmul.btranspose;
        int M=mmul.M;
        //Use btranspose for cache performance
        int ll=4;
        for(int q=0;q<P;q++,ll++) {
          double ra[][]=la[q]; 
          double rb[][]=lb[q];
          double rc[][]=lc[q];
          if ((ll&7)==0) {
            offsets2[0] = (short) (ll);
            if((ll+8)>P) {
              int lx=P-ll-1;
              if(lx>0) {
                offsets2[1]=(short) lx;
                offsets2[2] = (short) y0;
                offsets2[3] = (short) (y1 - y0 -1);
                System.rangePrefetch(mmul.btranspose, offsets2);
                offsets2[2] = (short) x0;
                offsets2[3] = (short) 15;
                System.rangePrefetch(mmul.a, offsets2);
                System.rangePrefetch(mmul.c, offsets2);
              }
            } else {
              offsets2[1]=(short) 7;
              offsets2[2] = (short) y0;
              offsets2[3] = (short) (y1 - y0 -1);
              System.rangePrefetch(mmul.btranspose, offsets2);
              offsets2[2] = (short) x0;
              offsets2[3] = (short) 15;
              System.rangePrefetch(mmul.a, offsets2);
              System.rangePrefetch(mmul.c, offsets2);
            }
          }

          short[] offsets3=new short[2];
          int l=8;
          for(int i = x0; i< x1; i++,l++){
            double a[]=ra[i]; 
            double c[]=rc[i];
            if((l&15)==0) {
              offsets3[0]=(short) (x0+l);
              if ((x0+l+16)>x1) {
                int x=x1-x0-l-1;
                if (x>0) {
                  offsets3[1]=(short) x;
                  System.rangePrefetch(ra, offsets3);
                  System.rangePrefetch(rc, offsets3);
                }
              } else {
                offsets3[1] = (short) 15;
                System.rangePrefetch(ra, offsets3);
                System.rangePrefetch(rc, offsets3);
              }
            }
            for (int j = y0; j < y1; j++) {
              double innerProduct=0;
              double b[] = rb[j];
              for(int k = 0; k < M; k++) {
                innerProduct += a[k] * b[k];
              }
              c[j]=innerProduct;
            }
          } //end of inner for i
        }//end of outer for q
      }//end of atomic
    }//end of run

    public static void main(String[] args) {
	int NUM_THREADS = 4;
	int SIZE=150;
	int NUM_MATRIX = 1;
	if (args.length>0) {
	    NUM_THREADS=Integer.parseInt(args[0]);
	    if (args.length>1) {
		SIZE=Integer.parseInt(args[1]);
		if (args.length>2)
		    NUM_MATRIX=Integer.parseInt(args[2]);
	    }
	}
	
	int[] mid = new int[8];
	mid[0] = (128<<24)|(195<<16)|(136<<8)|162; 
	mid[1] = (128<<24)|(195<<16)|(136<<8)|163;
	mid[2] = (128<<24)|(195<<16)|(136<<8)|164;
	mid[3] = (128<<24)|(195<<16)|(136<<8)|165;
	mid[4] = (128<<24)|(195<<16)|(136<<8)|166;
	mid[5] = (128<<24)|(195<<16)|(136<<8)|167;
	mid[6] = (128<<24)|(195<<16)|(136<<8)|168;
	mid[7] = (128<<24)|(195<<16)|(136<<8)|169;

	int p, q, r;
	MatrixMultiply[] mm;
	MatrixMultiply tmp;
	MMul matrix;
	BarrierServer mybarr;

	atomic {
	    mybarr = global new BarrierServer(NUM_THREADS);
	}
	mybarr.start(mid[0]);


    System.out.println("NUM_MATRIX= "+NUM_MATRIX+" SIZE= "+SIZE);
	atomic {
	    matrix = global new MMul(NUM_MATRIX, SIZE, SIZE, SIZE);
	    mm = global new MatrixMultiply[NUM_THREADS];
	    int increment=SIZE/NUM_THREADS;
	    int base=0;
	    for(int i=0;i<NUM_THREADS;i++) {
		if ((i+1)==NUM_THREADS)
		    mm[i]=global new MatrixMultiply(matrix,base, SIZE, 0, SIZE, i, NUM_THREADS);
		else
		    mm[i]=global new MatrixMultiply(matrix,base, base+increment, 0, SIZE, i, NUM_THREADS);
		base+=increment;
	    }
	    p = matrix.L;
	    q = matrix.M;
	    r = matrix.N;
	}
	boolean waitfordone=true;
	while(waitfordone) {
	    atomic { //Master aborts come from here
		if (mybarr.done)
		    waitfordone=false;
	    }
	}
	
	// start a thread to compute each c[l,n]
	for (int i = 0; i < NUM_THREADS; i++) {
	    atomic {
		tmp = mm[i];
	    }
	    tmp.start(mid[i]);
	}

      // wait for them to finish
      for (int i = 0; i < NUM_THREADS; i++) {
        atomic {
          tmp = mm[i];
        }
        tmp.join();
      }
    
	// print out the result of the matrix multiply
	System.printString("Finished\n");
    }
}

public class MMul{
    public int L, M, N, P;
    public double[][][] a;
    public double[][][] c;
    public double[][][] btranspose;
    
    public MMul(int P, int L, int M, int N) {
	this.L = L;
	this.M = M;
	this.N = N;
	this.P = P;
	a = global new double[P][L][];
	c = global new double[P][L][];
	btranspose = global new double[P][N][];
    }

    public void setValues(int tid, int numthreads) {
      int delta=numthreads;
      int start=tid;


      for(int q = start; q < P; q+=delta) {
        for(int i = 0; i < L; i++) {
          double ai[] = global new double[M];
          for(int j = 0; j < M; j++) {
            ai[j] = j+1;
          }
          a[q][i]=ai; 
        }
        for(int i = 0; i < L; i++) {
          c[q][i]=global new double[N];
        }
        for(int i = 0; i < N; i++) {
          double bi[] = global new double[M];
          for(int j = 0; j < M; j++) {
            bi[j] = j+1;
          }
          btranspose[q][i]=bi;
        }
      }
    }
}
