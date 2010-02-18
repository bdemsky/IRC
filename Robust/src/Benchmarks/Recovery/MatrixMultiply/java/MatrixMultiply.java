/* MatrixMultiplyN.java

   Matrix Multiplication Benchmark using Task Library.
   a, b, and c are two dimensional array.
   It computes a * b and assigns to c.

*/
public class MatrixMultiply {
	MMul mmul;
	int x0, y0, x1, y1;
	
	public MatrixMultiply(MMul mmul, int x0, int x1, int y0, int y1) {
		this.mmul = mmul;
		this.x0 = x0;
		this.x1 = x1;
		this.y0 = y0;
		this.y1 = y1;
	}

	public void execute() {
    double la[][] = mmul.a;
    double lc[][] = mmul.c;
    double lb[][] = mmul.btranspose;
		int M = mmul.M;

    for(int i = x0; i < x1; i++){
      double a[] = la[i];
      double c[] = lc[i];
      for (int j = y0; j < y1; j++) {
        double innerProduct = 0;
        double b[] = lb[j];
        for(int k = 0; k < M; k++) {
          innerProduct += a[k] *b[k];
        }
        c[j] = innerProduct;
      }
    }
  }

  public static void main(String[] args) {
		int SIZE;
    int i,j;
		MMul matrix;
		MatrixMultiply mm;

		if (args.length == 1) {
      SIZE = Integer.parseInt(args[0]);
		}
    else {
      System.out.println("usage: ./MatrixMultiply.bin <size of matrix>");
			System.exit(0);
    }

		matrix = new MMul(SIZE, SIZE, SIZE);
		matrix.setValues();
		matrix.transpose();
		mm = new MatrixMultiply(matrix, 0, SIZE, 0, SIZE);

    long st = System.currentTimeMillis();
    long fi;

		mm.execute();

    fi = System.currentTimeMillis();

    double sum = 0;
    {
      sum = matrix.getSum();
    }
        
    System.out.println("Sum of matrix = " + sum);
    System.out.println("Time Elapse = " + (double)((fi-st)/1000));
    System.printString("Finished\n");
	}
  
  public void output() {
    System.out.println("Sum = " + mmul.getSum());
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
		a = new double[L][M];  
		b = new double[M][N]; 
		c = new double[L][N]; 
		btranspose = new double[N][M];
	}

	public void setValues() {
		for(int i = 0; i < L; i++) {
			double ai[] = a[i];
			for(int j = 0; j < M; j++) {
//				ai[j] = j+1;
				ai[j] = 1;
			}
		}

		for(int i = 0; i < M; i++) {
			double bi[] = b[i];
			for(int j = 0; j < N; j++) {
//				bi[j] = j+1;
				bi[j] = 1;
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

  public double getSum() {
    double sum =0;

    for(int row =0; row < L; row++) {
      double cr[] = c[row];
      for(int col = 0; col < N; col++) {
        sum += cr[col];
      }
    }
    return sum;
  }
}
