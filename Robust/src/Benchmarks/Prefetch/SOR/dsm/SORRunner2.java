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

  int id,num_iterations;
  double G[][],omega;
    double S[][];
    int sync[][];
  int nthreads;

  public SORRunner(int id, double omega, double G[][], int num_iterations,int[][] sync, int nthreads) {
    this.id = id;
    this.omega=omega;
    this.G=G;
    this.num_iterations=num_iterations;
    this.sync=sync;
    this.nthreads = nthreads;
  }

  public void run() {
    int tmpid, M, N, numthreads;
    double omega_over_four, one_minus_omega;
    int numiterations;
    atomic {
      M = G.length;
      N = G[0].length;
      omega_over_four = omega * 0.25;
      one_minus_omega = 1.0 - omega;
      numthreads = nthreads;
      tmpid = id;
      numiterations = num_iterations;
    }

    // update interior points
    //
    int Mm1 = M-1;
    int Nm1 = N-1;


    int ilow, iupper, slice, tslice, ttslice;

    tslice = (Mm1) / 2;
    ttslice = (tslice + numthreads-1)/numthreads;
    slice = ttslice*2;
    ilow=tmpid*slice+1;
    iupper = ((tmpid+1)*slice)+1;
    if (iupper > Mm1) iupper =  Mm1+1;
    if (tmpid == (numthreads-1)) iupper = Mm1+1;

    for (int p=-2; p<2*numiterations; p++) {
	if (p<0) {
	    int l=ilow;
	    if (l==1)
		l=0;
	    if (p==-2) {
		atomic {
		    S=global new double[iupper-l][N];
		    for (int i=l; i<iupper; i++) {
			double q[]=S[i-l];
			double r[]=G[i];
			for(int j=0;j<N;j++) {
			    q[j]=r[j];
			}
		    }
		}
	    } else {
		atomic {
		    for (int i=l; i<iupper; i++) {
			G[i]=S[i-l];
		    }
		}
	    }
	} else {
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

            double [] Gim2 = G[i-2];

            for (int j=1; j<Nm1; j=j+2){
              if((j+1) != Nm1) {
                Gim1[j+1]=omega_over_four * (Gim2[j+1] + Gi[j+1] + Gim1[j]
                    + Gim1[j+2]) + one_minus_omega * Gim1[j+1];
              }
            }

          } else {

            double [] Gip1 = G[i+1];
            double [] Gim2 = G[i-2];

            for (int j=1; j<Nm1; j=j+2){
              Gi[j] = omega_over_four * (Gim1[j] + Gip1[j] + Gi[j-1]
                  + Gi[j+1]) + one_minus_omega * Gi[j];

              if((j+1) != Nm1) {
                Gim1[j+1]=omega_over_four * (Gim2[j+1] + Gi[j+1] + Gim1[j]
                    + Gim1[j+2]) + one_minus_omega * Gim1[j+1];
              }
            }
          }
        }
      } //close atomic
	}

      int ourcount;
      boolean done=true;
      atomic {
        // Signal this thread has done iteration
        sync[tmpid][0]++;
        ourcount=sync[tmpid][0];
      }

      // Wait for neighbours;
      while(done) {
        atomic {
          if ((tmpid==0 || ourcount <= sync[tmpid-1][0])
              &&((tmpid==(numthreads-1))||ourcount<=sync[tmpid+1][0]))
            done=false;
        }
      }

      System.clearPrefetchCache();
    }//end of for
  } //end of run()
}
