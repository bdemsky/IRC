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
public class JGFLUFactBenchSizeA { 

  public static void main(String argv[]){
    int nthreads;
    if(argv.length != 0 ) { 
      nthreads = Integer.parseInt(argv[0]); 
    } else { 
      System.printString("The no of threads has not been specified, defaulting to 1");
      System.printString("  ");
      nthreads = 1;
    }

    JGFInstrumentor instr = new JGFInstrumentor();
    JGFInstrumentor.printHeader(2,0,nthreads);
    JGFLUFactBench lub;
    atomic {
      //lub = global new JGFLUFactBench(nthreads, instr); 
      lub = global new JGFLUFactBench(nthreads); 
    }

    int size = 0;
    //lub.JGFrun(0);
    JGFInstrumentor.addTimer("Section2:LUFact:Kernel", "Mflops", size, instr.timers);
    atomic {
      lub.JGFsetsize(size); 
      lub.JGFinitialise();
    }
    JGFLUFactBench.JGFkernel(lub);
    int retval;
    atomic {
      retval = lub.JGFvalidate();
    }
    if(retval == 1) {
      System.printString("Validation failed");
    }
    //JGFLUFactBench.JGFvalidate(lub);

    // atomic {
    // lub.JGFvalidate();
    //}
    double ops;
    atomic {
      ops = lub.ops;
    }
    JGFInstrumentor.addOpsToTimer("Section2:LUFact:Kernel", ((long)ops)/1.0e06, instr.timers);
    JGFInstrumentor.printTimer("Section2:LUFact:Kernel", instr.timers); 
  }
}

