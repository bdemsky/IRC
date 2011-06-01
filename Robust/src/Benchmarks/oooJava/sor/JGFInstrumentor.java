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
 *      This version copyright (c) The University of Edinburgh, 1999.      *
 *                         All rights reserved.                            *
 *                                                                         *
 **************************************************************************/
//import java.util.*;
public class JGFInstrumentor{

  protected HashMap timers;
  protected HashMap data; 

  public JGFInstrumentor() {
    timers = new HashMap();
    data = new HashMap(); 
  }

  public static void addTimer (String name, HashMap timers){

    if (timers.containsKey(name)) {
      System.printString("JGFInstrumentor.addTimer: warning -  timer " + name + 
          " already exists");
    }
    else {
      timers.put(name, new JGFTimer(name));
    }
  }

  public static void addTimer (String name, String opname, HashMap timers){

    if (timers.containsKey(name)) {
      System.printString("JGFInstrumentor.addTimer: warning -  timer " + name + 
          " already exists");
    }
    else {
      timers.put(name, new JGFTimer(name,opname));
    }

  }

  public static void addTimer (String name, String opname, int size, HashMap timers){

    if (timers.containsKey(name)) {
      System.printString("JGFInstrumentor.addTimer: warning -  timer " + name +
          " already exists");
    }
    else {
      timers.put(name, new JGFTimer(name,opname,size));
    }

  }

  public static void startTimer(String name, HashMap timers){
    if (timers.containsKey(name)) {
      ((JGFTimer) timers.get(name)).start();
    }
    else {
      System.printString("JGFInstrumentor.startTimer: failed -  timer " + name + 
          " does not exist");
    }

  }

  public static void stopTimer(String name, HashMap timers){
    if (timers.containsKey(name)) {
      ((JGFTimer) timers.get(name)).stop();
    }
    else {
      System.printString("JGFInstrumentor.stopTimer: failed -  timer " + name + 
          " does not exist");
    }
  }

  public static void addOpsToTimer(String name, double count, HashMap timers){
    if (timers.containsKey(name)) {
      ((JGFTimer) timers.get(name)).addops(count);
    }
    else {
      System.printString("JGFInstrumentor.addOpsToTimer: failed -  timer " + name + 
          " does not exist");
    }
  }  

  public static void addTimeToTimer(String name, double added_time, HashMap timers){
    if (timers.containsKey(name)) {
      ((JGFTimer) timers.get(name)).addtime(added_time);
    }
    else {
      System.printString("JGFInstrumentor.addTimeToTimer: failed -  timer " + name +
          " does not exist");
    }



  }

  public static double readTimer(String name, HashMap timers){
    double time; 
    if (timers.containsKey(name)) {
      time = ((JGFTimer) timers.get(name)).time;
    }
    else {
      System.printString("JGFInstrumentor.readTimer: failed -  timer " + name + 
          " does not exist");
      time = 0.0; 
    }
    return time; 
  }  

  public static void resetTimer(String name, HashMap timers){
    if (timers.containsKey(name)) {
      ((JGFTimer) timers.get(name)).reset();
    }
    else {
      System.printString("JGFInstrumentor.resetTimer: failed -  timer " + name +
          " does not exist");
    }
  }

  public static void printTimer(String name, HashMap timers){
    if (timers.containsKey(name)) {
      ((JGFTimer) timers.get(name)).print();
    }
    else {
      System.printString("JGFInstrumentor.printTimer: failed -  timer " + name +
          " does not exist");
    }
  }

  public static void printperfTimer(String name, HashMap timers){
    if (timers.containsKey(name)) {
      ((JGFTimer) timers.get(name)).printperf();
    }
    else {
      System.printString("JGFInstrumentor.printTimer: failed -  timer " + name +
          " does not exist");
    }
  }

  public static void storeData(String name, Object obj, HashMap data){
    data.put(name,obj); 
  }

  public static void retrieveData(String name, Object obj, HashMap data){
    obj = data.get(name); 
  }

  public static void printHeader(int section, int size,int nthreads) {

    String header, base; 

    header = "";
    base = "Java Grande Forum Thread Benchmark Suite - Version 1.0 - Section "; 

    if (section == 1)
    {
      header = base + "1";
    }
    else if (section == 2)
    {
      if (size == 0)
        header = base + "2 - Size A";
      else if (size == 1)
        header = base + "2 - Size B";
      else if (size == 2)
        header = base + "2 - Size C";
    }
    else if (section == 3)
    {
      if (size == 0)
        header = base + "3 - Size A";
      else if (size == 1)
        header = base + "3 - Size B";
    }

    System.printString(header); 

    if (nthreads == 1) {
      System.printString("Executing on " + nthreads + " thread");
    }
    else {
      System.printString("Executing on " + nthreads + " threads");
    }

    System.printString("");
  } 
}
