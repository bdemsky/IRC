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
/**************************************************************************
*                       Ported for DSTM Benchmark                         *
**************************************************************************/


package crypt;
import jgfutil.*; 


public class JGFCryptBench extends IDEATest { 

  private int size; 
  private int datasizes[];
  public int nthreads;
  global JGFInstrumentor instr;

  public JGFCryptBench(int nthreads, JGFInstrumentor instr)
  {
    this.nthreads = nthreads;
    this.instr = instr;
    datasizes = new int[3];
    datasizes[0] = 3000000;
    datasizes[1] = 20000000;
    datasizes[2] = 50000000;
  }


  public void JGFsetsize(int size){
    this.size = size;
  }

  public void JGFinitialise(){
    array_rows = datasizes[size];
    buildTestData();
  }
 
  public void JGFkernel(){
    Do(); 
  }

  public void JGFvalidate(){
    boolean error;

    error = false; 
    for (int i = 0; i < array_rows; i++){
      error = (plain1 [i] != plain2 [i]); 
      if (error){
	System.printString("Validation failed");
	System.printString("Original Byte " + i + " = " + plain1[i]); 
	System.printString("Encrypted Byte " + i + " = " + crypt1[i]); 
	System.printString("Decrypted Byte " + i + " = " + plain2[i]); 
	//break;
      }
    }
  }


  public void JGFtidyup(){
    freeTestData(); 
  }  



  public void JGFrun(int size){


    instr.addTimer("Section2:Crypt:Kernel", "Kbyte",size);

    JGFsetsize(size); 
    JGFinitialise(); 
    JGFkernel(); 
    JGFvalidate(); 
    JGFtidyup(); 

     
    instr.addOpsToTimer("Section2:Crypt:Kernel", (2*array_rows)/1000.); 
    instr.printTimer("Section2:Crypt:Kernel"); 
  }
}
