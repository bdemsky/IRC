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


public class JGFMolDynBench extends md  {

  public static int nthreads;

  public JGFMolDynBench(int nthreads) {
        this.nthreads=nthreads;
  }

//   int size;

  public void JGFsetsize(int size){
    this.size = size;
  }

  public void JGFinitialise(){

      initialise();

  }

  public void JGFapplication(){ 

    runiters();

  } 


  public void JGFvalidate(){
    double refval[] = new double[2];
                                refval[0]= 1731.4306625334357;
                                refval[1]=7397.392307839352;
    double dev = Math.abs(ek[0] - refval[size]);
    if (dev > 1.0e-10 ){
      System.out.println("Validation failed");
      System.out.println("Kinetic Energy = " + ek[0] + "  " + dev + "  " + size);
    }
  }

  public void JGFrun(int size){
    JGFsetsize(size); 

    JGFinitialise(); 
    JGFapplication(); 
    JGFvalidate(); 

  }


}
 
