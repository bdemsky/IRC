/* MatrixMultiplyN.java

   Matrix Multiplication Benchmark using Task Library.
   a, b, and c are two dimensional array.
   It computes a * b and assigns to c.

*/
public class MatrixMultiply extends Task {
  MMul mmul;
  int SIZE;
  int increment;
  int x0;
  int x1;

  public MatrixMultiply(MMul mmul, int num_threads,int size,int x0,int x1) {
    this.mmul = mmul;
    this.SIZE = size;
    this.x0 = x0;
    this.x1 = x1;
  }

  public void execute() {
    double la[][];
    double lc[][];
    double lb[][];
    double rowA[];
    double colB[];

    double innerproduct;
    int i,j;
    int x0;
    int x1;
    int size;

    // get matrix 
    atomic {
      x0 = this.x0;
      x1 = this.x1;
      la = mmul.a;          //  first mat
      lb = mmul.btranspose; // second mat
      size = SIZE;


    lc = new double[size][size];

      for(i = x0; i < x1 ; i++) {
        rowA = la[i];   // grab first mat's row
        double c[] = lc[i];
        for(j = 0; j < size ; j++) {
          colB = lb[j]; // grab second mat's col
          innerproduct = computeProduct(rowA,colB, size); // computes the value
          c[j] = innerproduct;  // store in dest mat
        } // end of for j
      } 
      for (i = x0; i < x1; i++) {
        for (j = 0; j < size; j++) {
          mmul.c[i][j] = lc[i][j];
        }
      }
    }

    atomic {
      dequeueTask();
    }
  }

  public double computeProduct(double[] rowA,double[] colB, int size)
  {
    int i;
    double sum = 0;

    for(i = 0 ;i < size; i++) {
      sum += rowA[i] * colB[i];
    }

    return sum;
  }

  public void done(Object work) {
  }

  public static void main(String[] args) {
    long fi,st;
    st = System.currentTimeMillis();
    int NUM_THREADS=4;
    int SIZE = 1600;
    int increment = 80;
    int i,j;
    MMul matrix;
    MatrixMultiply mm;
    TaskSet ts;

    if (args.length == 3) {
      NUM_THREADS = Integer.parseInt(args[0]);
      SIZE = Integer.parseInt(args[1]);
      increment = Integer.parseInt(args[2]);  // size of subtask
    }
    else {
      System.out.println("usage: ./MatrixMultiply.bin master <num_threads> <size of matrix> <size of subtask>");
      System.exit(0);
    }

    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc1
    mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dc2
    mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc3
    mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dc4
    mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dc5
    mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dc6
    mid[6] = (128<<24)|(195<<16)|(136<<8)|168; //dc7
    mid[7] = (128<<24)|(195<<16)|(136<<8)|169; //dc8


    atomic {
      ts = global new TaskSet(NUM_THREADS);

      for( i = 0; i< NUM_THREADS; i++) {
//        ts.threads[i] = global new Worker(ts);
        ts.threads[i] = global new Worker(ts,i);
      }
    }

    atomic {
      matrix = global new MMul(SIZE, SIZE, SIZE);
      matrix.setValues();
      matrix.transpose();

      for(i = 0; i < SIZE; i +=increment) {
        if(i+increment > SIZE) {
          mm = global new MatrixMultiply(matrix, NUM_THREADS,SIZE,i, SIZE);
        }
        else {
          mm = global new MatrixMultiply(matrix, NUM_THREADS,SIZE, i, i + increment);
        }
        ts.todo.push(mm);
      }

    }

    //long st = System.currentTimeMillis();
    //long fi;

    System.out.println("Starting threads...");
    Worker tmp;
    for (i = 0; i < NUM_THREADS; i++) {
      atomic {
        tmp = ts.threads[i];
      }
      Thread.myStart(tmp,mid[i]);
    }

    while(true) {
      Thread.sleep(200000);
    }

    for (i = 0; i < NUM_THREADS; i++) {
      atomic {
        tmp = ts.threads[i];
      }
      tmp.join();
    }
    //fi = System.currentTimeMillis();

//    System.out.println("Time Elapse = " + (double)((fi-st)/1000));
    double sum= 0;
    atomic {
      sum = matrix.getSum();
    }

    System.out.println("Sum of matrix = " + sum);
    //System.out.println("Time Elapse = " + (double)((fi-st)/1000));
    System.printString("Finished\n");
  }

  public void output() {
    System.out.println("c[0][0] = " + mmul.c[0][0] + "  c["+(SIZE-1)+"]["+(SIZE-1)+"] : " + mmul.c[SIZE-1][SIZE-1]);
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
        bi[j] = j+ 1;
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


