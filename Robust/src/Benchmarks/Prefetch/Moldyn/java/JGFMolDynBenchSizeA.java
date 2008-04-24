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
import java.io.*;
import java.util.*;

public class JGFMolDynBenchSizeA { 

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
    JGFInstrumentor.printHeader(3,0,nthreads);

    JGFMolDynBench mold = new JGFMolDynBench(nthreads, instr); 
    int size = 0;
    JGFInstrumentor.addTimer("Section3:MolDyn:Total", "Solutions",size, instr.timers);
    JGFInstrumentor.addTimer("Section3:MolDyn:Run", "Interactions",size, instr.timers);

    mold.JGFsetsize(size); 

    JGFInstrumentor.startTimer("Section3:MolDyn:Total", instr.timers);

    mold.JGFinitialise(); 
    mold.JGFapplication(); 
    mold.JGFvalidate(); 

    JGFInstrumentor.stopTimer("Section3:MolDyn:Total", instr.timers);

    JGFInstrumentor.addOpsToTimer("Section3:MolDyn:Run", (double) (mold.interactions), instr.timers);
    JGFInstrumentor.addOpsToTimer("Section3:MolDyn:Total", 1, instr.timers);

    JGFInstrumentor.printTimer("Section3:MolDyn:Run", instr.timers); 
    JGFInstrumentor.printTimer("Section3:MolDyn:Total", instr.timers); 
  }
}

