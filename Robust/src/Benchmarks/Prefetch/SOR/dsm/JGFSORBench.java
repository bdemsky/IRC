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
  }

  public void JGFsetsize(int size){
    this.size = size;
  }

  public static void JGFkernel(JGFSORBench sor) {
    int numthreads, datasize;
    BarrierServer mybarr;
    Random rand;

    int[] mid = new int[4];
    mid[0] = (128<<24)|(195<<16)|(175<<8)|79;
    mid[1] = (128<<24)|(195<<16)|(175<<8)|73;
    mid[2] = (128<<24)|(195<<16)|(175<<8)|78;
    mid[3] = (128<<24)|(195<<16)|(175<<8)|69;

    atomic {
      numthreads = sor.nthreads;
      rand = sor.R;
      datasize = sor.datasizes[sor.size];
      mybarr = global new BarrierServer(numthreads);
    }
    mybarr.start(mid[0]);

    double[][] G;
    int M, N;
    atomic {
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
    SORRunner[] thobjects;
    atomic {
      thobjects = global new SORRunner[numthreads];
    }

    //JGFInstrumentor.startTimer("Section2:SOR:Kernel", instr.timers); 

    boolean waitfordone=true;
    while(waitfordone) {
      atomic {
        if (mybarr.done)
          waitfordone=false;
      }
    }

    SORRunner tmp;
    for(int i=1;i<numthreads;i++) {
      atomic {
        thobjects[i] =  global new SORRunner(i,omega,G,num_iterations,numthreads);
        tmp = thobjects[i];
      }
      tmp.start(mid[i]);
    }
    atomic {
      thobjects[0] =  global new SORRunner(0,omega,G,num_iterations,numthreads);
      tmp = thobjects[0];
    }
    tmp.start(mid[0]);
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
    long l = (long) refval[size] * 1000000;
    long r = (long) Gtotal * 1000000;
    if (l != r ){
      //System.printString("Validation failed");
      //System.printString("Gtotal = " + (long) Gtotal * 1000000 + "  " +(long) dev * 1000000 + "  " + size);
      return 1;
    } else {
      return 0;
    }
  }
}
