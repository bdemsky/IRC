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
public class JGFMolDynBenchSizeB { 

    public static void main(String argv[]){
    int nthreads;
    if(argv.length != 0 ) {
      nthreads = Integer.parseInt(argv[0]);
    } else {
      System.printString("The no of threads has not been specified, defaulting to 1\n");
      System.printString("  " + "\n");
      nthreads = 1;
    }

    JGFInstrumentor instr = new JGFInstrumentor();
    JGFInstrumentor.printHeader(3,1,nthreads);

    JGFMolDynBench mold;
    atomic {
      mold = global new JGFMolDynBench(nthreads); 
    }
    int size = 1;
    JGFInstrumentor.addTimer("Section3:MolDyn:Total", "Solutions",size, instr.timers);
    JGFInstrumentor.addTimer("Section3:MolDyn:Run", "Interactions",size, instr.timers);

    atomic {
      mold.JGFsetsize(size); 
    }

    JGFInstrumentor.startTimer("Section3:MolDyn:Total", instr.timers);

    JGFMolDynBench tmp;
    atomic {
      mold.JGFinitialise(); 
    }
    JGFMolDynBench.JGFapplication(mold); 

    /* Validate data */
    double[] refval = new double[2];
    refval[0] = 1731.4306625334357;
    refval[1] = 7397.392307839352;
    double dval;
    atomic {
      dval = mold.ek[0].d;
    }
    double dev = Math.fabs(dval - refval[size]);
    long l = (long) refval[size] *1000000;
    long r = (long) dval * 1000000;
    if (l != r ){
      System.printString("Validation failed\n");
      System.printString("Kinetic Energy = " + (long)dval + "  " + (long)dev + "  " + size + "\n");
    }

    JGFInstrumentor.stopTimer("Section3:MolDyn:Total", instr.timers);
    double interactions;
    atomic {
      interactions = mold.interactions;
    }

    JGFInstrumentor.addOpsToTimer("Section3:MolDyn:Run", (double) interactions, instr.timers);
    JGFInstrumentor.addOpsToTimer("Section3:MolDyn:Total", 1, instr.timers);

    JGFInstrumentor.printTimer("Section3:MolDyn:Run", instr.timers); 
    JGFInstrumentor.printTimer("Section3:MolDyn:Total", instr.timers); 
    System.printString("Finished\n");
  }
}

