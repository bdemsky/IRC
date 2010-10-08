/**************************************************************************
 * * Java Grande Forum Benchmark Suite - Thread Version 1.0 * * produced by * *
 * Java Grande Benchmarking Project * * at * * Edinburgh Parallel Computing
 * Centre * * email: epcc-javagrande@epcc.ed.ac.uk * * * This version copyright
 * (c) The University of Edinburgh, 2001. * All rights reserved. * *
 **************************************************************************/

public class JGFMolDynBenchSizeB {

  public static void main(String argv[]) {
    int nthreads;
    if (argv.length != 0) {
      nthreads = Integer.parseInt(argv[0]);
    } else {
      System.out.println("The no of threads has not been specified, defaulting to 1");
      System.out.println("  ");
      nthreads = 1;
    }
    // JGFMolDynBench mold = new JGFMolDynBench(nthreads);
    // mold.JGFrun(1);
    md mold = new md();
    mold.JGFrun(1,nthreads);
  }
}
