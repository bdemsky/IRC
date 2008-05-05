public class MatrixMultiply extends Thread{
    MMul mmul;
    public int x0, y0, x1, y1;
    
    public MatrixMultiply(MMul mmul, int x0, int x1, int y0, int y1) {
	this.mmul = mmul;
	this.x0 = x0;
	this.y0 = y0;
	this.x1 = x1;
	this.y1 = y1;
    }
    
    public void run() {
      atomic {
        double la[][][]=mmul.a;
        double lc[][][]=mmul.c;
        double lb[][][]=mmul.btranspose;
        int M=mmul.M;
	int P=mmul.P;
        //Use btranspose for cache performance
	for(int q=0;q<P;q++) {
	    double ra[][]=la[q];
	    double rb[][]=lb[q];
	    double rc[][]=lc[q];
	    for(int i = x0; i< x1; i++){
		double a[]=ra[i];
		double c[]=rc[i];
		for (int j = y0; j < y1; j++) {
		    double innerProduct=0;
		    double b[] = rb[j];
		    for(int k = 0; k < M; k++) {
			innerProduct += a[k] *b[k];
		    }
		    c[j]=innerProduct;
		}
	    }
	}
      }
    }
    
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
	
	int[] mid = new int[4];
	mid[0] = (128<<24)|(195<<16)|(175<<8)|79;
	mid[1] = (128<<24)|(195<<16)|(175<<8)|80;
	mid[2] = (128<<24)|(195<<16)|(175<<8)|69;
	mid[3] = (128<<24)|(195<<16)|(175<<8)|70;
	int p, q, r;
	MatrixMultiply[] mm;
	MatrixMultiply tmp;
	MMul matrix;

	atomic {
	    matrix = global new MMul(NUM_MATRIX, SIZE, SIZE, SIZE);
	    matrix.setValues();
	    matrix.transpose();
	    mm = global new MatrixMultiply[NUM_THREADS];
	    int increment=SIZE/NUM_THREADS;
	    int base=0;
	    for(int i=0;i<NUM_THREADS;i++) {
		if ((i+1)==NUM_THREADS)
		    mm[i]=global new MatrixMultiply(matrix,base, SIZE, 0, SIZE);
		else
		    mm[i]=global new MatrixMultiply(matrix,base, base+increment, 0, SIZE);
		base+=increment;
	    }
	    p = matrix.L;
	    q = matrix.M;
	    r = matrix.N;
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
    public double[][][] b;
    public double[][][] c;
    public double[][][] btranspose;
    
    public MMul(int P, int L, int M, int N) {
	this.L = L;
	this.M = M;
	this.N = N;
	this.P = P;
	a = global new double[P][L][M];  
	b = global new double[P][M][N]; 
	c = global new double[P][L][N]; 
	btranspose = global new double[P][N][M];
    }

    public void setValues() {
	for(int q = 0; q < P; q++) {
	    for(int i = 0; i < L; i++) {
		double ai[] = a[q][i];
		for(int j = 0; j < M; j++) {
		    ai[j] = j+1;
		}
	    }
	    
	    for(int i = 0; i < M; i++) {
		double bi[] = b[q][i];
		for(int j = 0; j < N; j++) {
		    bi[j] = j+1;
		}
	    }
	}
    }
    
    public void transpose() {
	for(int q=0;q<P;q++) {
	    double br[][]=b[q];
	    double bt[][]=btranspose[q];
	    for(int row = 0; row < M; row++) {
		double brow[] = br[row];
		for(int col = 0; col < N; col++) {
		    bt[col][row] = brow[col];
		}
	    }
	}
    }
}
