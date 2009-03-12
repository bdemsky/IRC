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
        short[] offsets = new short[4];
        // Prefetch mmul.btranspose[][] matrix
	//Get all of B first...we need them first
        offsets[0] = getoffset{MMul, btranspose};
        offsets[1] = (short) 0;
        offsets[2] = (short) y0;
        offsets[3] = (short) (y1 - y0 -1);
        System.rangePrefetch(mmul, offsets);

	//Get first part of A
        offsets[0] = getoffset{MMul, a};
        offsets[1] = (short) 0;
        offsets[2] = (short) x0;
        offsets[3] = (short) 15;
        System.rangePrefetch(mmul, offsets);

        //Get first part of C
        offsets[0] = getoffset{MMul, c};
        offsets[1] = (short) 0;
        System.rangePrefetch(mmul, offsets);
	short[] offsets2=new short[2];
	    double la[][]=mmul.a;
	    double lc[][]=mmul.c;
	    double lb[][]=mmul.btranspose;
	    int M=mmul.M;
	    int l=8;
        //Use btranspose for cache performance
	    for(int i = x0; i< x1; i++,l++){
		double a[]=la[i];
		double c[]=lc[i];
		if ((l&15)==0) {
		    offsets2[0] = (short) (x0+l);
		    if ((x0+l+16)>x1) {
			int x=x1-x0-l-1;
			if (x>0) {
			    offsets2[1]=(short) x;
			    System.rangePrefetch(la, offsets2);
			    System.rangePrefetch(lc, offsets2);
			}
		    } else {
			offsets2[1] = (short) 15;
			System.rangePrefetch(la, offsets2);
			System.rangePrefetch(lc, offsets2);
		    }
		}
		for (int j = y0; j < y1; j++) {
		    double innerProduct=0;
		    double b[] = lb[j];
		    for(int k = 0; k < M; k++) {
			innerProduct += a[k] *b[k];
		    }
		    c[j]=innerProduct;
		}
	    }
	}
    }
    
    public static void main(String[] args) {
	int NUM_THREADS = 4;
	int SIZE=600;
	if (args.length>0) {
	    NUM_THREADS=Integer.parseInt(args[0]);
	    if (args.length>1)
		SIZE=Integer.parseInt(args[1]);
	}
	
	int[] mid = new int[8];
	mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc-1.calit2
	mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dc-2.calit2
	mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc-3.calit2
	mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dc-4.calit2
	mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dc-5.calit2
	mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dc-6.calit2
	mid[6] = (128<<24)|(195<<16)|(136<<8)|168; //dc-7.calit2
	mid[7] = (128<<24)|(195<<16)|(136<<8)|169; //dc-8.calit2
 
	int p, q, r;
	MatrixMultiply[] mm;
	MatrixMultiply tmp;
	MMul matrix;
	
	atomic {
	    matrix = global new MMul(SIZE, SIZE, SIZE);
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
	
	// print out the matrices to be multiplied
	System.printString("\n");
	System.printString("MatrixMultiply: L=");
	System.printInt(p);
	System.printString("\t");
	System.printString("M=");
	System.printInt(q);
	System.printString("\t");
	System.printString("N=");
	System.printInt(r);
	System.printString("\n");
	
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

	public int L, M, N;
	public double[][] a;
	public double[][] b;
	public double[][] c;
	public double[][] btranspose;

	public MMul(int L, int M, int N) {
		this.L = L;
		this.M = M;
		this.N = N;
		a = global new double[L][M];  
		b = global new double[M][N]; 
		c = global new double[L][N]; 
		btranspose = global new double[N][M];
	}

	public void setValues() {
		for(int i = 0; i < L; i++) {
            double ai[] = a[i];
			for(int j = 0; j < M; j++) {
				ai[j] = j+1;
			}
		}

		for(int i = 0; i < M; i++) {
            double bi[] = b[i];
			for(int j = 0; j < N; j++) {
				bi[j] = j+1;
			}
		}

		for(int i = 0; i < L; i++) {
            double ci[] = c[i];
			for(int j = 0; j < N; j++) {
				ci[j] = 0;
			}
		}
		for(int i = 0; i < N; i++) {
            double btransposei[] = btranspose[i];
			for(int j = 0; j < M; j++) {
				btransposei[j] = 0;
			}
		}
	}

	public void transpose() {
		for(int row = 0; row < M; row++) {
            double brow[] = b[row];
			for(int col = 0; col < N; col++) {
				btranspose[col][row] = brow[col];
			}
		}
	}
}
