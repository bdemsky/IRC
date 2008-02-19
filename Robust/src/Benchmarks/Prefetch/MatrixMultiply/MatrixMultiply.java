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
		int innerProduct = 0;
		int i, j;
		int xx0,xx1,yy0,yy1;

		atomic {
			xx0 = x0;
			xx1 = x1;
			yy0 = y0;
			yy1 = y1;
		}

		for(i = xx0; i<= xx1; i++){
			for (j = yy0; j <= yy1; j++) {
				atomic {
					innerProduct = mmul.multiply(i,j);
				}
				atomic {
					mmul.c[i][j] = innerProduct;
				}
			}
		}
	}

	public static void main(String[] args) {
		int mid = (128<<24)|(195<<16)|(175<<8)|70;
		int NUM_THREADS = 4;
		int i, j, p, q, r, val;
		MatrixMultiply[] mm;
		MatrixMultiply tmp;
		MMul matrix;

		atomic {
			matrix = global new MMul(4, 4, 4);
			matrix.setValues();
		}

		atomic{
			mm = global new MatrixMultiply[NUM_THREADS];
		}

		// Currently it is a 4 X 4 matrix divided into 4 blocks 
		atomic {
			mm[0] = global new MatrixMultiply(matrix,0,0,1,1);
			mm[1] = global new MatrixMultiply(matrix,0,2,1,3);
			mm[2] = global new MatrixMultiply(matrix,2,0,3,1);
			mm[3] = global new MatrixMultiply(matrix,2,2,3,3);
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

		//Print Matrices to be multiplied
		System.printString("a =\n");
		for (i = 0; i < p; i++) {
			for (j = 0; j < q; j++) {
				atomic {
					val = matrix.a[i][j];
				}
				System.printString(" " + val);
			}
			System.printString("\n");
		}
		System.printString("\n");

		System.printString("b =\n");
		for (i = 0; i < q; i++) {
			for (j = 0; j < r; j++) {
				atomic {
					val = matrix.b[i][j];
				}
				System.printString(" " + val);
			}
			System.printString("\n");
		}
		System.printString("\n");

		// start a thread to compute each c[l,n]
		for (i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = mm[i];
			}
			tmp.start(mid);
		}

		// wait for them to finish
		for (i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = mm[i];
			}
			tmp.join();
		}

		// print out the result of the matrix multiply
		System.printString("Starting\n");
		System.printString("Matrix Product c =\n");
		for (i = 0; i < p; i++) {
			for (j = 0; j < r; j++) {
				atomic {
					val = matrix.c[i][j];
				}
				System.printString(" " + val);
			}
			System.printString("\n");
		}
		System.printString("Finished\n");
	}
}

public class MMul{

	public int L, M, N;
	public int[][] a;
	public int[][] b;
	public int[][] c;

	public MMul(int L, int M, int N) {
		this.L = L;
		this.M = M;
		this.N = N;
		a = global new int[L][M];  
		b = global new int[M][N]; 
		c = global new int[L][N]; 
	}

	public void setValues() {
		int i;
		int j;
		for(i = 0; i < L; i++) {
			for(j = 0; j < M; j++) {
				a[i][j] = j+1;
			}
		}

		for(i = 0; i < M; i++) {
			for(j = 0; j < N; j++) {
				b[i][j] = j+1;
			}
		}

		for(i = 0; i < L; i++) {
			for(j = 0; j < N; j++) {
				c[i][j] = 0;
			}
		}
	}

	public int multiply(int x, int y) {
		int i;
		int prod = 0;
		for(i = 0; i < M; i++) {
			prod+= a[x][i] * b[i][y];
		}
		return prod;
	}

}
