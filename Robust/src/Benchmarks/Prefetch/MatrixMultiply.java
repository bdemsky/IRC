public class MatrixMultiply extends Thread{
	public int L, M, N, l, n;
	public int[][] a;
	public int[][] b;
	public int[][] c;

	public MatrixMultiply(int l, int n) {
		this.l = l;
		this.n = n;
	}

	public void setValues(int l, int n) {
		this.l = l;
		this.n = n;
	}

	public void run() {
		int innerProduct = 0;
		atomic {
			for (int m = 0; m < M; m++) {
				innerProduct += a[l][m]*b[m][n];
			}
			c[l][n] = innerProduct;
		}
	}

	public static void main(String[] args) {
		int tt,k,q,r;
		MatrixMultiply mm = null;

		atomic {
			mm = global new MatrixMultiply(0,0);
			mm.L = 2;
			mm.M = 2;
			mm.N = 2;
		}

		int mid = (128<<24)|(195<<16)|(175<<8)|70;

		atomic {
			mm.a = global new int[mm.L][mm.M];  
			mm.a[0][0] = 2;
			mm.a[0][1] = 2;
			mm.a[1][0] = 2;
			mm.a[1][1] = 2;
		}
		
		 atomic {
			mm.b = global new int[mm.M][mm.N]; 
			mm.b[0][0] = 3;
			mm.b[0][1] = 3;
			mm.b[1][0] = 3;
			mm.b[1][1] = 3;
		 }

		 atomic {
			mm.c = global new int[mm.L][mm.N];
			mm.c[0][0] = 0;
			mm.c[0][1] = 0;
			mm.c[1][0] = 0;
			mm.c[1][1] = 0;
		 }

		// print out the matrices to be multiplied
		atomic {
			k = mm.L;
			q = mm.M;
			r = mm.N;
		}

		System.printString("MatrixMultiply: L=");
		System.printInt(k);
		System.printString("\t");
		System.printString("M=");
		System.printInt(q);
		System.printString("\t");
		System.printString("N=");
		System.printInt(r);
		System.printString("\t");

		System.printString("\n");
		System.printString("a =");
		for (int l = 0; l < k; l++) {
			for (int m = 0; m < q; m++) {
				atomic {
					tt = mm.a[l][m];
				}
				System.printString(" " + tt);
				System.printString("\t");
			}
		}
		System.printString("\n");

		//System.printString("\n");
		System.printString("b =");
		for (int m = 0; m < q; m++) {
			for (int n = 0; n < r; n++) {
				atomic {
					tt = mm.b[m][n];
				}
				System.printString(" " + tt);
				System.printString("\t");
			}
		}
		System.printString("\n");

		// start a thread to compute each c[l,n]
		for (int l = 0; l < k; l++) {
			for (int n = 0; n < r; n++){
				atomic {
					mm.setValues(l,n);
				}
				mm.start(mid);
			}
		}

		// wait for them to finish
		for (int l = 0; l < k; l++){
			for (int n = 0; n < r; n++){
				mm.join();
			}
		}

		// print out the result of the matrix multiply
		System.printString("c =");
		for (int l = 0; l < k; l++) {
			for (int n = 0; n < r; n++) {
				atomic {
					tt = mm.c[l][n];
				}
				System.printString(" " + tt);
				System.printString("\t");
			}
		}
		System.printString("\n");
		while(true) {
			;
		}
	}
}
