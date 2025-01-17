/* MatrixMultiplyN.java

   Matrix Multiplication Benchmark using Task Library.
   a, b, and c are two dimensional array.
   It computes a * b and assigns to c.

*/
public class MatrixMultiply extends Task {
  MMul mmul;
  int SIZE;
  int increment;

  public MatrixMultiply(MMul mmul, int num_threads, int size,int increment) {
    this.mmul = mmul;

    SIZE = size;
    this.increment = increment;

    init();
  }

  public void init() {
    todoList =  new GlobalQueue();

    fillTodoList();
  }

  // fill up the Work Pool
  public void fillTodoList() {
    Segment seg;
    int i;

    for(i = 0; i < SIZE; i +=increment) {

      if(i+increment > SIZE) {
        seg =  new Segment(i,SIZE);
      }
      else {
        seg =  new Segment(i, i + increment);
      }
      todoList.push(seg);
    }
  }

  public void execute() {
    double la[][];
    double lc[][];
    double lb[][];
    double rowA[];
    double colB[];
    Segment seg;

    double innerproduct;
    int i,j;
    int x0;
    int x1;
    int size;


    // get matrix 
    {
      seg = (Segment)myWork;
      x0 = seg.x0;  // x start row
      x1 = seg.x1;  // x end row
      la = mmul.a;          //  first mat
      lb = mmul.btranspose; // second mat
      size = SIZE;
    }

    lc = new double[size][size];

    for(i = x0; i < x1 ; i++) {
      {
        rowA = la[i];   // grab first mat's row

        for(j = 0; j < size ; j++) {
          colB = lb[j]; // grab second mat's col

          innerproduct = computeProduct(rowA,colB, size); // computes the value

          lc[i][j] = innerproduct;  // store in dest mat
        } // end of for j
      } 
    }	// end for i 

    {
      for (i = x0; i < x1; i++) {
        for (j = 0; j < size; j++) {
          mmul.c[i][j] = lc[i][j];
          //System.out.println("lc["+i+"]["+j+"]= "+mmul.c[i][j]);
        }
      }
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
    int NUM_THREADS=4;
    int SIZE = 1600;
    int increment = 80;
    int i,j;
    Work[] works;
    MMul matrix;
    MatrixMultiply mm;
    Segment[] currentWorkList;

    if (args.length == 3) {
      NUM_THREADS = Integer.parseInt(args[0]);
      SIZE = Integer.parseInt(args[1]);
      increment = Integer.parseInt(args[2]);  // size of subtask
    }
    else {
      System.out.println("usage: ./MatrixMultiply.bin master <num_threads> <size of matrix> <size of subtask>");
      System.exit(0);
    }

    {
      matrix =  new MMul(SIZE, SIZE, SIZE);
      matrix.setValues();
      matrix.transpose();
      mm =  new MatrixMultiply(matrix, NUM_THREADS, SIZE,increment);

      works =  new Work[NUM_THREADS];
      currentWorkList =  new Segment[NUM_THREADS];

      for(i = 0; i < NUM_THREADS; i++) {
        works[i] =  new Work(mm, NUM_THREADS, i,currentWorkList);
      }
    }

    long st = System.currentTimeMillis();
    long fi;

    Work tmp;
    for (i = 0; i < NUM_THREADS; i++) {
      {
        tmp = works[i];
      }
      tmp.run();
    }

    fi = System.currentTimeMillis();

    double sum= 0;
    {
      sum = matrix.getSum();
    }

    System.out.println("Sum of matrix = " + sum);
    System.out.println("Time Elapse = " + (double)((fi-st)/1000));
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
    a =  new double[L][M];  
    b =  new double[M][N]; 
    c =  new double[L][N]; 
    btranspose =  new double[N][M];
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

public class Segment {
  int x0;
  int x1;

  Segment (int x0, int x1) {
    this.x0 = x0;
    this.x1 = x1;
  }
}

