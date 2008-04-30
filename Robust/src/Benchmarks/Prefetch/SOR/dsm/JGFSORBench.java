/**************************************************************************
 *                                                                         *
 *         Java Grande Forum Benchmark Suite - Thread Version 1.0          *
 *                                                                         *
 *                            produced by                                  *
 *                                                                         *
 *                  Java Grande Benchmarking Project                       *
 *                                                                         *
 *                                at                                       *
 *                                                                         *
 *                Edinburgh Parallel Computing Centre                      *
 *                                                                         * 
 *                email: epcc-javagrande@epcc.ed.ac.uk                     *
 *                                                                         *
 *                                                                         *
 *      This version copyright (c) The University of Edinburgh, 2001.      *
 *                         All rights reserved.                            *
 *                                                                         *
 **************************************************************************/
public class JGFSORBench { 

  int size; 
  int[] datasizes;
  int JACOBI_NUM_ITER;
  long RANDOM_SEED;
  public int nthreads;
  Random R;
  public double Gtotal;
  public int cachelinesize;
  public long sync[][];

  public JGFSORBench(int nthreads){
    this.nthreads = nthreads;
    datasizes = global new int[3];
    datasizes[0] = 1000;
    datasizes[1] = 1500;
    datasizes[2] = 2000;
    JACOBI_NUM_ITER = 100;
    RANDOM_SEED = 10101010;
    R = global new Random(RANDOM_SEED);
    Gtotal = 0.0;
    cachelinesize = 128;
  }

  public void JGFsetsize(int size){
    this.size = size;
  }

  public static void JGFkernel(JGFSORBench sor) {
    int numthreads, datasize;
    Random rand;
    atomic {
      numthreads = sor.nthreads;
      rand = sor.R;
      datasize = sor.datasizes[sor.size];
    }

    double[][] G;
    int M, N;
    atomic {
      //G = global new double[datasize][datasize];
      G = sor.RandomMatrix(datasize, datasize, rand);
      M = G.length;
      N = G[0].length;
    }
    double omega = 1.25;
    int num_iterations;
    atomic {
      num_iterations = sor.JACOBI_NUM_ITER;
    }

    double omega_over_four = omega * 0.25;
    double one_minus_omega = 1.0 - omega;

    // update interior points
    //
    int Mm1 = M-1;
    int Nm1 = N-1;

    //spawn threads
    int tmpcachelinesize;
    atomic {
      tmpcachelinesize = sor.cachelinesize;
    }

    SORRunner[] thobjects;
    atomic {
      thobjects = global new SORRunner[numthreads];
      sor.sync = sor.init_sync(numthreads, tmpcachelinesize);
    }

    //JGFInstrumentor.startTimer("Section2:SOR:Kernel", instr.timers); 

    SORRunner tmp;
    int mid = (128<<24)|(195<<16)|(175<<8)|73;
    for(int i=1;i<numthreads;i++) {
      atomic {
        thobjects[i] =  global new SORRunner(i,omega,G,num_iterations,sor.sync,numthreads);
        tmp = thobjects[i];
      }
      tmp.start(mid);
    }

    atomic {
      thobjects[0] = global new SORRunner(0,omega,G,num_iterations,sor.sync,numthreads);
      tmp = thobjects[0];
    }
    tmp.start(mid);
    tmp.join();

    for(int i=1;i<numthreads;i++) {
      atomic {
        tmp = thobjects[i];
      }
      tmp.join();
    }

    //JGFInstrumentor.stopTimer("Section2:SOR:Kernel", instr.timers);

    atomic {
      for (int i=1; i<Nm1; i++) {
        for (int j=1; j<Nm1; j++) {
          sor.Gtotal += G[i][j];
        }
      }               
    }
  }

  public long[][] init_sync(int nthreads, int cachelinesize) {
    long sync[][] = global new long [nthreads][cachelinesize];
    for (int i = 0; i<nthreads; i++)
      sync[i][0] = 0;
    return sync;
  }

  public double[][] RandomMatrix(int M, int N, Random R)
  {
    double A[][] = global new double[M][N];

    for (int i=0; i<N; i++)
      for (int j=0; j<N; j++)
      {
        A[i][j] = R.nextDouble() * 1e-6;
      }      
    return A;
  }

  public int JGFvalidate(){

    double refval[];
    refval = new double[3];
    refval[0] = 0.498574406322512;
    refval[1] = 1.1234778980135105;
    refval[2] = 1.9954895063582696;
    double dev = Math.fabs(Gtotal - refval[size]);
    if (dev > 1.0e-12 ){
      //System.printString("Validation failed");
      //System.printString("Gtotal = " + (long) Gtotal * 1000000 + "  " +(long) dev * 1000000 + "  " + size);
      return 1;
    } else {
      return 0;
    }
  }

  /*
     public void JGFtidyup(){
     System.gc();
     }  

     public void JGFrun(int size){


     JGFInstrumentor.addTimer("Section2:SOR:Kernel", "Iterations",size);

     JGFsetsize(size); 
     JGFinitialise(); 
     JGFkernel(); 
     JGFvalidate(); 
     JGFtidyup(); 


     JGFInstrumentor.addOpsToTimer("Section2:SOR:Kernel", (double) (JACOBI_NUM_ITER));

     JGFInstrumentor.printTimer("Section2:SOR:Kernel"); 
     }
     */


}
