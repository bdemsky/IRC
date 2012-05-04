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
  public double Gtotal;

  public JGFSORBench(int nthreads){
    this.nthreads = nthreads;
    datasizes = new int[4];
    datasizes[0] = 1000;
    datasizes[1] = 1500;
    datasizes[2] = 2000;
    datasizes[3] = 8000;
    JACOBI_NUM_ITER = 100;
    RANDOM_SEED = 10101010;
    Gtotal = 0.0;
  }

  public void JGFsetsize(int size){
    this.size = size;
  }

  public static void JGFkernel(JGFSORBench sor) {
    int numthreads, datasize;
    double[][] G;
    int num_iterations;
    long RANDOM_SEED;

    numthreads = sor.nthreads;
    datasize = sor.datasizes[sor.size];
    G =  new double[datasize][datasize];
    num_iterations = sor.JACOBI_NUM_ITER;
    RANDOM_SEED = sor.RANDOM_SEED;

    double omega = 1.25;
    double omega_over_four = omega * 0.25;
    double one_minus_omega = 1.0 - omega;

    // update interior points
       
    SORWrap sorWrap = new SORWrap(new SORRunner(0,omega,G,num_iterations,numthreads, RANDOM_SEED));
    sorWrap.sor.run();

    for (int i=1; i<G.length-1; i++) {
      for (int j=1; j<G.length-1; j++) {
        sor.Gtotal += G[i][j];
      }
    }      

  }

  public int JGFvalidate(){

    double refval[];
    refval = new double[4];
    refval[0] = 0.498574406322512;
    refval[1] = 1.1234778980135105;
    refval[2] = 1.9954895063582696;
//    refval[3] = 2.654895063582696;
//    refval[3] = 31.984487737222523;
    refval[3]=31.984779;
    double dev = Math.abs(Gtotal - refval[size]);
    if (dev > 1.0e-6 ){
      System.out.println("Validation failed");
      System.out.println("Gtotal = " + Gtotal + "  dev=" + dev + "  sizeParam=" + size);
    }else{
      System.out.println("VALID dev="+dev);
    }

  }
}
