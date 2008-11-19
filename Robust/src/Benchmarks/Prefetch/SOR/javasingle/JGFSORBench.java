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

import java.util.Random;

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

  public JGFInstrumentor instr;

  public JGFSORBench(int nthreads, JGFInstrumentor instr){
    this.nthreads = nthreads;
    this.instr = instr;
    datasizes = new int[3];
    datasizes[0] = 1000;
    datasizes[1] = 1500;
    datasizes[2] = 2000;
    JACOBI_NUM_ITER = 100;
    RANDOM_SEED = 10101010;
    R = new Random(RANDOM_SEED);
    Gtotal = 0.0;
    cachelinesize = 128;
  }

  public void JGFsetsize(int size){
    this.size = size;
  }

  public static void JGFkernel(JGFSORBench sor, JGFInstrumentor instr) {
    int numthreads;
    numthreads = sor.nthreads;

    double G[][] = sor.RandomMatrix(sor.datasizes[sor.size], sor.datasizes[sor.size], sor.R);
    int M = G.length;
    int N = G[0].length;
    double omega = 1.25;
    int num_iterations = sor.JACOBI_NUM_ITER;


    double omega_over_four = omega * 0.25;
    double one_minus_omega = 1.0 - omega;

    // update interior points
    //
    int Mm1 = M-1;
    int Nm1 = N-1;

    //spawn threads
    int cachelinesize = sor.cachelinesize;

    SORRunner thobjects[] = new SORRunner[numthreads];
    sor.sync = sor.init_sync(numthreads, cachelinesize);

    JGFInstrumentor.startTimer("Section2:SOR:Kernel", instr.timers); 

    for(int i=1;i<numthreads;i++) {
      thobjects[i] = new SORRunner(i,omega,G,num_iterations,sor.sync,numthreads);
      thobjects[i].start();
    }

    thobjects[0] = new SORRunner(0,omega,G,num_iterations,sor.sync,numthreads);
    thobjects[0].start();
    try {
      thobjects[0].join();
    }
    catch (InterruptedException e) {}


    for(int i=1;i<numthreads;i++) {
      try {
        thobjects[i].join();
      }
      catch (InterruptedException e) {}
    }

    JGFInstrumentor.stopTimer("Section2:SOR:Kernel", instr.timers);

    for (int i=1; i<Nm1; i++) {
      for (int j=1; j<Nm1; j++) {
        sor.Gtotal += G[i][j];
      }
    }               

  }

  private long[][] init_sync(int nthreads, int cachelinesize) {
    long sync[][] = new long [nthreads][cachelinesize];
    for (int i = 0; i<nthreads; i++)
      sync[i][0] = 0;
    return sync;
  }

  public void JGFvalidate(){

    double refval[] = {0.498574406322512,1.1234778980135105,1.9954895063582696};
    double dev = Math.abs(Gtotal - refval[size]);
    if (dev > 1.0e-12 ){
      System.out.println("Validation failed");
      System.out.println("Gtotal = " + Gtotal + "  " + dev + "  " + size);
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

  public double[][] RandomMatrix(int M, int N, Random R)
  {
    double A[][] = new double[M][N];

    for (int i=0; i<N; i++)
      for (int j=0; j<N; j++)
      {
        A[i][j] = R.nextDouble() * 1e-6;
      }      
    return A;
  }


}
