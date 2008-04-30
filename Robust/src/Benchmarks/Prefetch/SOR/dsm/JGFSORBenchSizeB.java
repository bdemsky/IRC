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

public class JGFSORBenchSizeB{ 

  public static void main(String argv[]){
    int nthreads;

    if(argv.length != 0 ) {
      nthreads = Integer.parseInt(argv[0]);
    } else {
      System.out.println("The no of threads has not been specified, defaulting to 1");
      System.out.println("  ");
      nthreads = 1;
    }

    JGFInstrumentor instr = new JGFInstrumentor();
    JGFInstrumentor.printHeader(2,1,nthreads);

    JGFSORBench sor;
    atomic {
      sor = global new JGFSORBench(nthreads); 
    }

    int size = 1;
    JGFInstrumentor.addTimer("Section2:SOR:Kernel", "Iterations",size, instr.timers);

    atomic {
      sor.JGFsetsize(size); 
    }
    JGFSORBench.JGFkernel(sor); 
    System.printString("End of JGFkernel\n");
    int retval;
    atomic {
      retval = sor.JGFvalidate(); 
    }
    if(retval!=0) {
      System.printString("Validation failed");
    }

    int jacobi;
    atomic {
      jacobi = sor.JACOBI_NUM_ITER;
    }

    JGFInstrumentor.addOpsToTimer("Section2:SOR:Kernel", (double) jacobi, instr.timers);
    JGFInstrumentor.printTimer("Section2:SOR:Kernel", instr.timers); 
  }
}
