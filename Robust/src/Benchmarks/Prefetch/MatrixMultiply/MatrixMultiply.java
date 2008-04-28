public class MatrixMultiply extends Thread{
	MMul mmul;
	public int x0, y0, x1, y1;

	public MatrixMultiply(MMul mmul, int x0, int y0, int x1, int y1) {
		this.mmul = mmul;
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
	}

	public void run() {
		double localresults[][];

		atomic {
		    //compute the results
		    localresults=new double[1+x1-x0][1+y1-y0];
		
		    //Use b transpose for cache performance
		    for(int i = x0; i<= x1; i++){
			double a[]=mmul.a[i];
			int M=mmul.M;
			for (int j = y0; j <= y1; j++) {
			    double innerProduct=0;
			    double b[] = mmul.btranspose[j];
			    for(int k = 0; k < M; k++) {
				innerProduct += a[k] *b[k];
			    }
			    localresults[i-x0][j-y0]=innerProduct;
			}
		    }
		}

		atomic {
		    //write the results
		    for(int i=x0;i<=x1;i++) {
			double c[]=mmul.c[i];
			for(int j=y0;j<=y1;j++) {
			    c[j]=localresults[i-x0][j-y0];
			}
		    }
		}
	}

	public static void main(String[] args) {
		int mid1 = (128<<24)|(195<<16)|(175<<8)|69;
		int mid2 = (128<<24)|(195<<16)|(175<<8)|69;
		int mid3 = (128<<24)|(195<<16)|(175<<8)|71;
		int NUM_THREADS = 2;
		int p, q, r;
		MatrixMultiply[] mm;
		MatrixMultiply tmp;
		MMul matrix;

		atomic {
			matrix = global new MMul(400, 400, 400);
			matrix.setValues();
			matrix.transpose();
		}

		atomic{
			mm = global new MatrixMultiply[NUM_THREADS];
		}

		atomic {
			mm[0] = global new MatrixMultiply(matrix,0,0,399,200);
			mm[1] = global new MatrixMultiply(matrix,0,201,399,399);
		}

		atomic {
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
			tmp.start(mid1);
		}

		// wait for them to finish
		for (int i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = mm[i];
			}
			tmp.join();
		}

		// print out the result of the matrix multiply
		System.printString("Starting\n");
		System.printString("Matrix Product c =\n");
		double val;
		atomic {
			for (int i = 0; i < p; i++) {
				double c[]=matrix.c[i];
				for (int j = 0; j < r; j++) {
					val = c[j];
				}
			}
		}
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
