public class MatrixMultiply extends Task {
	MMul mmul;
	int SIZE;
	int increment;
	
	public MatrixMultiply(MMul mmul, int num_threads, int size) {
		this.mmul = mmul;

/*    if ((size % num_threads) == 0) {
      NUM_TASKS = num_threads*num_threads;
    }
    else {
      NUM_TASKS = (num_threads+1)*(num_threads+1);
    }*/

		SIZE = size;
    increment = 80;

    init();
	}

	public void init() {
		todoList = global new Queue();
		doneList = global new Queue();

		fillTodoList();
	}

  // fill up the Work Pool
	public void fillTodoList() {
    Segment seg;
    int i;

    for(i = 0; i < SIZE; i +=increment) {

      if(i+increment > SIZE) {
        seg = global new Segment(i,SIZE);
      }
      else {
        seg = global new Segment(i, i + increment);
//        System.out.println("Seg = " + i + " - " + (i+increment));
      }
			todoList.push(seg);
    }
//    System.out.println("TodoSIZE = " + todoList.size());
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
    atomic {
			seg = (Segment)myWork;
			x0 = seg.x0;  // x start row
			x1 = seg.x1;  // x end row
      la = mmul.a;          //  first mat
      lb = mmul.btranspose; // second mat
//      lc = mmul.c;          // destination mat
			size = SIZE;
    }

		lc = new double[size][size];
		System.out.println("Seg x0 = " + x0 + " - x1 = " + x1);
		
		for(i = x0; i < x1 ; i++) {
			System.printString("i = " + i + "\n");
		  atomic {
        rowA = la[i];   // grab first mat's row

				for(j = 0; j < size ; j++) {
          colB = lb[j]; // grab second mat's col

					innerproduct = computeProduct(rowA,colB, size); // computes the value

          lc[i][j] = innerproduct;  // store in dest mat
				} // end of for j
			} 
		}	// end for i 
//		}
		System.out.println("Finished comutation");

		atomic {
			for (i = x0; i < x1; i++) {
				for (j = 0; j < size; j++) {
					mmul.c[i][j] = lc[i][j];
				}
			}
		}
  }

  public double computeProduct(double[] rowA,double[] colB, int size)
  {
    int i;
    double sum = 0;

    for(i = 0 ;i < size; i++) {
//      atomic {
        sum += rowA[i] * colB[i];
//      }
    }

    return sum;
  }

	public void done(Object work) {
		atomic {
			doneList.push(work);
		}
	}

  public static void main(String[] args) {
		int NUM_THREADS = 4;
		int SIZE = 1600;
    int i,j;
		Work[] works;
		MMul matrix;
		MatrixMultiply mm;
    Segment[] currentWorkList;

		if (args.length > 0) {
			NUM_THREADS = Integer.parseInt(args[0]);
		}

		int[] mid = new int[NUM_THREADS];
//		mid[0] = (128<<24)|(195<<16)|(180<<8)|21; //dc1
//		mid[1] = (128<<24)|(195<<16)|(180<<8)|24; //dc2
//		mid[2] = (128<<24)|(195<<16)|(180<<8)|26; //dc3
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc4
		mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dc5
		mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc6
		mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dc6
		mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dc6
		mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dc6

		atomic {
			matrix = global new MMul(SIZE, SIZE, SIZE);
			matrix.setValues();
			matrix.transpose();
			mm = global new MatrixMultiply(matrix, NUM_THREADS, SIZE);

			works = global new Work[NUM_THREADS];
      currentWorkList = global new Segment[NUM_THREADS];

			for(i = 0; i < NUM_THREADS; i++) {
				works[i] = global new Work(mm, NUM_THREADS, i,currentWorkList);
			}
		}
    System.out.println("Finished to createObjects");

		Work tmp;
		for (i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = works[i];
			}
			Thread.myStart(tmp,mid[i]);
		}

		for (i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = works[i];
			}
			tmp.join();
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

public class Segment {
	int x0;
	int x1;

	Segment (int x0, int x1) {
		this.x0 = x0;
		this.x1 = x1;
	}
}

