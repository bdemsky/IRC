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
 *      adapted from SciMark 2.0, author Roldan Pozo (pozo@cam.nist.gov)   *
 *                                                                         *
 *      This version copyright (c) The University of Edinburgh, 2001.      *
 *                         All rights reserved.                            *
 *                                                                         *
 **************************************************************************/

class SORRunner extends Thread {

  int id, num_iterations;
  double G[][],omega;
  int nthreads;
  long RANDOM_SEED;

  public SORRunner(int id, double omega, double G[][], int num_iterations, int nthreads, long RANDOM_SEED) {
    this.id = id;
    this.omega=omega;
    this.G=G;
    this.num_iterations=num_iterations;
    this.nthreads = nthreads;
    this.RANDOM_SEED =  RANDOM_SEED;
  }

  public void run() {
    int tmpid, M, N, numthreads;
    double omega_over_four, one_minus_omega;
    int numiterations;
    Barrier barr;
    barr = new Barrier("128.195.136.162");
    int ilow, iupper, slice, tslice, ttslice, Mm1, Nm1;

    atomic {
      N = M = G.length;
      
      omega_over_four = omega * 0.25;
      one_minus_omega = 1.0 - omega;
      numthreads = nthreads;
      tmpid = id;
      numiterations = num_iterations;
      Mm1 = M-1;
      Nm1 = N-1;
      tslice = (Mm1) / 2;
      ttslice = (tslice + numthreads-1)/numthreads;
      slice = ttslice*2;
      ilow=tmpid*slice+1;
      iupper = ((tmpid+1)*slice)+1;

      short[] offsets = new short[4];
      offsets[0] = getoffset{SORRunner, G};
      offsets[1] = (short) 0;
      offsets[2] = (short) ilow;
      offsets[3] = (short) iupper;
      System.rangePrefetch(this, offsets);

      if (iupper > Mm1) iupper =  Mm1+1;
      if (tmpid == (numthreads-1)) iupper = Mm1+1;
      G[0]=global new double[N];
      for(int i=ilow;i<iupper;i++) {
        G[i]=global new double[N];
      }
    }
    Barrier.enterBarrier(barr);
    
    atomic {
      Random rand=new Random(RANDOM_SEED);
      double[] R = G[0];
      for(int j=0;j<M;j++)
        R[j]=rand.nextDouble() * 1e-6;
      for(int i=ilow;i<iupper;i++) {
        R=G[i];
        for(int j=0;j<M;j++)
          R[j]=rand.nextDouble() * 1e-6;
      }
    }
    Barrier.enterBarrier(barr);

    // update interior points
    //

    for (int p=0; p<2*numiterations; p++) {
      atomic {
        for (int i=ilow+(p%2); i<iupper; i=i+2) {

          double [] Gi = G[i];
          double [] Gim1 = G[i-1];

          if(i == 1) { 
            double [] Gip1 = G[i+1];

            for (int j=1; j<Nm1; j=j+2){
              Gi[j] = omega_over_four * (Gim1[j] + Gip1[j] + Gi[j-1]
                  + Gi[j+1]) + one_minus_omega * Gi[j];

            }
          } else if (i == Mm1) {

          } else {

            double [] Gip1 = G[i+1];

            for (int j=1; j<Nm1; j=j+2){
              Gi[j] = omega_over_four * (Gim1[j] + Gip1[j] + Gi[j-1]
                  + Gi[j+1]) + one_minus_omega * Gi[j];

            }
          }
        }
      } //close atomic

      Barrier.enterBarrier(barr);
      atomic {
        for (int i=ilow+(p%2); i<iupper; i=i+2) {

          double [] Gi = G[i];
          double [] Gim1 = G[i-1];

          if(i == 1) { 
          } else if (i == Mm1) {

            double [] Gim2 = G[i-2];

            for (int j=1; j<Nm1; j=j+2){
              if((j+1) != Nm1) {
                Gim1[j+1]=omega_over_four * (Gim2[j+1] + Gi[j+1] + Gim1[j]
                    + Gim1[j+2]) + one_minus_omega * Gim1[j+1];
              }
            }

          } else {

            double [] Gim2 = G[i-2];

            for (int j=1; j<Nm1; j=j+2){
              if((j+1) != Nm1) {
                Gim1[j+1]=omega_over_four * (Gim2[j+1] + Gi[j+1] + Gim1[j]
                    + Gim1[j+2]) + one_minus_omega * Gim1[j+1];
              }
            }
          }
        }
      } //close atomic

      Barrier.enterBarrier(barr);
    }//end of for
  } //end of run()
}
