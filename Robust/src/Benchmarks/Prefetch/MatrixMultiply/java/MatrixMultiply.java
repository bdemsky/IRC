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
        int localresults[][];

        //compute the results
        localresults=new int[1+x1-x0][1+y1-y0];

        //Use b transpose for cache performance
        for(int i = x0; i<= x1; i++){
            int a[]=mmul.a[i];
            int M=mmul.M;
            for (int j = y0; j <= y1; j++) {
                int innerProduct=0;
                int b[] = mmul.btranspose[j];
                for(int k = 0; k < M; k++) {
                    innerProduct += a[k] *b[k];
                }
                localresults[i-x0][j-y0]=innerProduct;
            }
        }

        //write the results
        for(int i=x0;i<=x1;i++) {
            int c[]=mmul.c[i];
            for(int j=y0;j<=y1;j++) {
                c[j]=localresults[i-x0][j-y0];
            }
        }
    }

    public static void main(String[] args) {
        int NUM_THREADS = 1;
        int p, q, r;
        MatrixMultiply[] mm;
        MatrixMultiply tmp;
        MMul matrix;

        matrix = new MMul(400, 400, 400);
        matrix.setValues();
        matrix.transpose();

        mm = new MatrixMultiply[NUM_THREADS];
        mm[0] = new MatrixMultiply(matrix,0,0,399,399);

        p = matrix.L;
        q = matrix.M;
        r = matrix.N;

        // print out the matrices to be multiplied
        System.out.print("MatrixMultiply: L=");
        System.out.print(p);
        System.out.print("\t");
        System.out.print("M=");
        System.out.print(q);
        System.out.print("\t");
        System.out.print("N=");
        System.out.print(r);
        System.out.print("\n");

        // start a thread to compute each c[l,n]
        for (int i = 0; i < NUM_THREADS; i++) {
            tmp = mm[i];
            tmp.start();
        }

        // wait for them to finish
        for (int i = 0; i < NUM_THREADS; i++) {
            try {
                mm[i].join();
            } catch (InterruptedException e) {
                System.out.println("Join Error");
            } 

        }

        // print out the result of the matrix multiply
        System.out.println("Starting\n");
        System.out.println("Matrix Product c =\n");
        int val;
        for (int i = 0; i < p; i++) {
            int c[]=matrix.c[i];
            for (int j = 0; j < r; j++) {
                val = c[j];
            }
        }
        System.out.println("Finished\n");
    }
}

class MMul{

    public int L, M, N;
    public int[][] a;
    public int[][] b;
    public int[][] c;
    public int[][] btranspose;

    public MMul(int L, int M, int N) {
        this.L = L;
        this.M = M;
        this.N = N;
        a = new int[L][M];  
        b = new int[M][N]; 
        c = new int[L][N]; 
        btranspose = new int[N][M];
    }

    public void setValues() {
        for(int i = 0; i < L; i++) {
            int ai[] = a[i];
            for(int j = 0; j < M; j++) {
                ai[j] = j+1;
            }
        }

        for(int i = 0; i < M; i++) {
            int bi[] = b[i];
            for(int j = 0; j < N; j++) {
                bi[j] = j+1;
            }
        }

        for(int i = 0; i < L; i++) {
            int ci[] = c[i];
            for(int j = 0; j < N; j++) {
                ci[j] = 0;
            }
        }
        for(int i = 0; i < N; i++) {
            int btransposei[] = btranspose[i];
            for(int j = 0; j < M; j++) {
                btransposei[j] = 0;
            }
        }
    }

    public void transpose() {
        for(int row = 0; row < M; row++) {
            int brow[] = b[row];
            for(int col = 0; col < N; col++) {
                btranspose[col][row] = brow[col];
            }
        }
    }
}
