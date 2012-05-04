/**************************************************************************
 * * Java Grande Forum Benchmark Suite - Version 2.0 * * produced by * * Java
 * Grande Benchmarking Project * * at * * Edinburgh Parallel Computing Centre *
 * * email: epcc-javagrande@epcc.ed.ac.uk * * * This version copyright (c) The
 * University of Edinburgh, 1999. * All rights reserved. * *
 **************************************************************************/

public class JGFMonteCarloBench extends CallAppDemo {

  public JGFMonteCarloBench(){
    super();
  }
  
  public void JGFsetsize(int size) {
    this.size = size;
  }

  public void JGFinitialise(int workload) {

    initialise(workload);

  }

  public void JGFapplication() {

    runiters();

  }

  public void JGFvalidate() {
    double refval[] = new double[2];
    refval[0] = -0.0333976656762814;
    refval[1] = -0.03215796752868655;
    
    double dev = Math.abs(getAppDemo().JGFavgExpectedReturnRateMC - refval[size]);
    if (dev > 1.0e-12) {
      System.out.println("Validation failed");
      System.out.println(" expectedReturnRate= " + getAppDemo().JGFavgExpectedReturnRateMC  + "  " + dev
          + "  " + size);
    }else{
      System.out.println("VALID");
    }
  }

  public void JGFrun(int size,int workload) {

    JGFsetsize(size);
    JGFinitialise(workload);
    JGFapplication();
    JGFvalidate();

  }

  public static void main(String argv[]) {

    JGFMonteCarloBench mc = new JGFMonteCarloBench();
    int size=1;
    int workload=100;
    if(argv.length>0){
      size=Integer.parseInt(argv[0]);
    }
    if(argv.length>1){
      workload=Integer.parseInt(argv[1]);
    }
    mc.JGFrun(size,workload);

  }

}
