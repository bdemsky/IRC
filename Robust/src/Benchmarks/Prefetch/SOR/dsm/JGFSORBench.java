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
    datasizes = global new int[4];
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
    BarrierServer mybarr;

    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dw-10
    mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dw-11
    mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dw-12
    mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dw-13
    mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dw-14
    mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dw-15
    mid[6] = (128<<24)|(195<<16)|(136<<8)|168; //dw-16
    mid[7] = (128<<24)|(195<<16)|(136<<8)|169; //dw-17

    double[][] G;
    int num_iterations;
    long RANDOM_SEED;

    atomic {
      numthreads = sor.nthreads;
      datasize = sor.datasizes[sor.size];
      mybarr = global new BarrierServer(numthreads);
      G =  global new double[datasize][];
      num_iterations = sor.JACOBI_NUM_ITER;
      RANDOM_SEED = sor.RANDOM_SEED;
    }
    mybarr.start(mid[0]);

    double omega = 1.25;
    double omega_over_four = omega * 0.25;
    double one_minus_omega = 1.0 - omega;

    // update interior points
    //
    //spawn threads

    SORWrap[] thobjects = new SORWrap[numthreads];

    atomic {
	for(int i=0;i<numthreads;i++) {
	    thobjects[i] =  new SORWrap(global new SORRunner(i,omega,G,num_iterations,numthreads, RANDOM_SEED));
	}
    }

    boolean waitfordone=true;
    while(waitfordone) {
      atomic {
        if (mybarr.done)
          waitfordone=false;
      }
    }

    for(int i=0;i<numthreads;i++) {
	thobjects[i].sor.start(mid[i]);
    }

    for(int i=0;i<numthreads;i++) {
      thobjects[i].sor.join();
    }

    //JGFInstrumentor.stopTimer("Section2:SOR:Kernel", instr.timers);
    atomic {
	for (int i=1; i<G.length-1; i++) {
	    for (int j=1; j<G.length-1; j++) {
		sor.Gtotal += G[i][j];
	    }
	}               
    System.out.println("DEBUG: G.length= " + G.length+" sor.Gtotal= " + sor.Gtotal);
    }
  }

  public int JGFvalidate(){

    double refval[];
    refval = new double[4];
    refval[0] = 0.498574406322512;
    refval[1] = 1.1234778980135105;
    refval[2] = 1.9954895063582696;
    refval[3] = 2.654895063582696;
    double dev = Math.fabs(Gtotal - refval[size]);
    long l = (long) refval[size] * 1000000;
    long r = (long) Gtotal * 1000000;
    if (l != r ){
      return 1;
    } else {
      return 0;
    }
  }
}
