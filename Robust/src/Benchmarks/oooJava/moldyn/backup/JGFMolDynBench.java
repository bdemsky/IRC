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

public class JGFMolDynBench {

  public static void main(String args[]) {
    int datasize = 8; //8,13
    int group = 16;
    MD md = new MD(datasize, group);
    md.initialise();
    int movemx = 50;

    
    for (int move=0;move<movemx;move++) {
      md.domove();
      md.init();
      
      for(int i = 0; i < md.group; ++i) {
        MDRunner runner = new MDRunner(i, md);
        runner.init();
        runner.run();                
        md.update(runner);

      } 
      md.sum();
      md.scale();
    }
   
    
    
    md.validate();
    
  }
  
}
  
