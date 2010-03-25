import String;
import System;

import java.util.Hashtable;

/**************************************************************************
*                                                                         *
*             Java Grande Forum Benchmark Suite - Version 2.0             *
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
*      This version copyright (c) The University of Edinburgh, 1999.      *
*                         All rights reserved.                            *
*                                                                         *
**************************************************************************/

public class JGFInstrumentor{

	private  Hashtable timers;
	private  Hashtable data;
//  private static Hashtable timers;
//  private static Hashtable data; 
//
//  static {
//    timers = new Hashtable();
//    data = new Hashtable(); 
//  }
	
	public JGFInstrumentor(){
		timers=new Hashtable();
		data=new Hashtable();
	}

  public  synchronized void addTimer (String name){

    if (timers.containsKey(name)) {
      System.out.println("JGFInstrumentor.addTimer: warning -  timer " + name + 
			 " already exists");
    }
    else {
    	JGFTimer t=new JGFTimer(name);
    	timers.put(name,t);
//      timers.put(name, new JGFTimer(name));
    }
  }
    
  public  synchronized void addTimer (String name, String opname){

    if (timers.containsKey(name)) {
      System.out.println("JGFInstrumentor.addTimer: warning -  timer " + name + 
			 " already exists");
    }
    else {
    	JGFTimer t=new JGFTimer(name,opname);
    	timers.put(name,t);
//      timers.put(name, new JGFTimer(name,opname));
    }
    
  }

  public  synchronized void addTimer (String name, String opname, int size){

    if (timers.containsKey(name)) {
      System.out.println("JGFInstrumentor.addTimer: warning -  timer " + name +
                         " already exists");
    }
    else {
    	JGFTimer t=new JGFTimer(name,opname,size);
    	timers.put(name,t);
//      timers.put(name, new JGFTimer(name,opname,size));
    }

  }

  public  synchronized void startTimer(String name){
    if (timers.containsKey(name)) {
      ((JGFTimer) timers.get(name)).start();
    }
    else {
      System.out.println("JGFInstrumentor.startTimer: failed -  timer " + name + 
			 " does not exist");
    }

  }

  public  synchronized void stopTimer(String name){
    if (timers.containsKey(name)) {
      ((JGFTimer) timers.get(name)).stop();
    }
    else {
      System.out.println("JGFInstrumentor.stopTimer: failed -  timer " + name + 
			 " does not exist");
    }
  }

  public  synchronized void addOpsToTimer(String name, double count){
    if (timers.containsKey(name)) {
      ((JGFTimer) timers.get(name)).addops(count);
    }
    else {
      System.out.println("JGFInstrumentor.addOpsToTimer: failed -  timer " + name + 
			 " does not exist");
    }
  }  

  public  synchronized double readTimer(String name){
    double time; 
    if (timers.containsKey(name)) {
      time = ((JGFTimer) timers.get(name)).time;
    }
    else {
      System.out.println("JGFInstrumentor.readTimer: failed -  timer " + name + 
			 " does not exist");
       time = 0.0; 
    }
    return time; 
  }  

  public  synchronized void resetTimer(String name){
    if (timers.containsKey(name)) {
      ((JGFTimer) timers.get(name)).reset();
    }
    else {
      System.out.println("JGFInstrumentor.resetTimer: failed -  timer " + name +
 			 " does not exist");
    }
  }
  
  public  synchronized void printTimer(String name){
    if (timers.containsKey(name)) {
    	JGFTimer t=(JGFTimer) timers.get(name);
    	t.print();
//      ((JGFTimer) timers.get(name)).print();
    }
    else {
      System.out.println("JGFInstrumentor.printTimer: failed -  timer " + name +
 			 " does not exist");
    }
  }
  
  public  synchronized void printperfTimer(String name){
    if (timers.containsKey(name)) {
      ((JGFTimer) timers.get(name)).printperf();
    }
    else {
      System.out.println("JGFInstrumentor.printTimer: failed -  timer " + name +
 			 " does not exist");
    }
  }
  
//  public static synchronized void storeData(String name, Object obj){
//     data.put(name,obj); 
//  }

  public  synchronized void retrieveData(String name, Object obj){
    obj = data.get(name); 
  }

  public  synchronized void printHeader(int section, int size) {

    String header, base; 
 
    header = "";
    base = "Java Grande Forum Benchmark Suite - Version 2.0 - Section "; 
    
    if(section==1){
    	header = base + "1";
    }else if(section==2){
    	if(size==0){
    		header = base + "2 - Size A";
    	}else if(size==1){
    		header = base + "2 - Size B";
    	}else if(size==2){
    		header = base + "2 - Size C";
    	}
    }else if(section==3){
    	if(size==0){
    		header = base + "3 - Size A";
    	}else if(size==1){
    		header = base + "3 - Size B";
    	}
    }
  
    System.out.println(header); 
    System.out.println("");
    
    

  } 

}
