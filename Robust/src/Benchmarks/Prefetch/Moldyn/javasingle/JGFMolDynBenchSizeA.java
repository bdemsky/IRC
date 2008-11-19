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
public class JGFMolDynBenchSizeA { 

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
    JGFInstrumentor.printHeader(3,0,nthreads);

    JGFMolDynBench mold;
    mold = new JGFMolDynBench(nthreads); 
    int size = 0;
    JGFInstrumentor.addTimer("Section3:MolDyn:Total", "Solutions",size, instr.timers);
    JGFInstrumentor.addTimer("Section3:MolDyn:Run", "Interactions",size, instr.timers);

      mold.JGFsetsize(size); 

    JGFInstrumentor.startTimer("Section3:MolDyn:Total", instr.timers);

    JGFMolDynBench tmp;
      mold.JGFinitialise(); 
    JGFMolDynBench.JGFapplication(mold); 
    /* Validate data */
    double[] refval = new double[2];
    refval[0] = 1731.4306625334357;
    refval[1] = 7397.392307839352;
    double dval;
    //System.printString("Here #1\n");
      dval = mold.ek[0];
    //System.printString("Here #2\n");
    double dev = Math.fabs(dval - refval[size]);
    //long ldev = (long)dev * 1000000;
    //System.printString("ldev= "+ldev);
    //long ltmp = (long)1.0e-10 * 1000000;
    //System.printString("ltmp= "+ltmp);
    if (dev > 1.0e-10 ){
    //if (ldev > ltmp ){
      System.printString("Validation failed\n");
      System.printString("Kinetic Energy = " + (long)dval + "  " + (long)dev + "  " + size + "\n");
    }
    System.printString("End of JGFvalidate\n");

    JGFInstrumentor.stopTimer("Section3:MolDyn:Total", instr.timers);
    double interactions;
    System.printString("Here #3\n");
    interactions = mold.interactions;
    System.printString("Here #4\n");

    JGFInstrumentor.addOpsToTimer("Section3:MolDyn:Run", (double) interactions, instr.timers);
    JGFInstrumentor.addOpsToTimer("Section3:MolDyn:Total", 1, instr.timers);

    JGFInstrumentor.printTimer("Section3:MolDyn:Run", instr.timers); 
    JGFInstrumentor.printTimer("Section3:MolDyn:Total", instr.timers); 
  }
}

