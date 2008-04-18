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
/**************************************************************************
*                       Ported for DSTM Benchmark                         *
**************************************************************************/
import java.util.*;

public class JGFInstrumentor{

    private HashMap timers;
    private HashMap data; 

    public JGFInstrumentor()
    {
        timers = new HashMap();
        data = new HashMap(); 
    }

    public void addTimer (String name){

        if (timers.containsKey(name)) {
            System.out.println("JGFInstrumentor.addTimer: warning -  timer " + name + 
                    " already exists");
        }
        else {
            timers.put(name, new JGFTimer(name));
        }
    }

    public void addTimer (String name, String opname){

        if (timers.containsKey(name)) {
            System.out.println("JGFInstrumentor.addTimer: warning -  timer " + name + 
                    " already exists");
        }
        else {
            timers.put(name, new JGFTimer(name,opname));
        }

    }

    public void addTimer (String name, String opname, int size){

        if (timers.containsKey(name)) {
            System.out.println("JGFInstrumentor.addTimer: warning -  timer " + name +
                    " already exists");
        }
        else {
            timers.put(name, new JGFTimer(name,opname,size));
        }

    }

    public void startTimer(String name){
        if (timers.containsKey(name)) {
            ((JGFTimer) timers.get(name)).start();
        }
        else {
            System.out.println("JGFInstrumentor.startTimer: failed -  timer " + name + 
                    " does not exist");
        }

    }

    public void stopTimer(String name){
        if (timers.containsKey(name)) {
            ((JGFTimer) timers.get(name)).stop();
        }
        else {
            System.out.println("JGFInstrumentor.stopTimer: failed -  timer " + name + 
                    " does not exist");
        }
    }

    public void addOpsToTimer(String name, double count){
        if (timers.containsKey(name)) {
            ((JGFTimer) timers.get(name)).addops(count);
        }
        else {
            System.out.println("JGFInstrumentor.addOpsToTimer: failed -  timer " + name + 
                    " does not exist");
        }
    }  

    public void addTimeToTimer(String name, double added_time){
        if (timers.containsKey(name)) {
            ((JGFTimer) timers.get(name)).addtime(added_time);
        }
        else {
            System.out.println("JGFInstrumentor.addTimeToTimer: failed -  timer " + name +
                    " does not exist");
        }



    }

    public double readTimer(String name){
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

    public void resetTimer(String name){
        if (timers.containsKey(name)) {
            ((JGFTimer) timers.get(name)).reset();
        }
        else {
            System.out.println("JGFInstrumentor.resetTimer: failed -  timer " + name +
                    " does not exist");
        }
    }

    public void printTimer(String name){
        if (timers.containsKey(name)) {
            ((JGFTimer) timers.get(name)).print();
        }
        else {
            System.out.println("JGFInstrumentor.printTimer: failed -  timer " + name +
                    " does not exist");
        }
    }

    public void printperfTimer(String name){
        if (timers.containsKey(name)) {
            ((JGFTimer) timers.get(name)).printperf();
        }
        else {
            System.out.println("JGFInstrumentor.printTimer: failed -  timer " + name +
                    " does not exist");
        }
    }

    public void storeData(String name, Object obj){
        data.put(name,obj); 
    }

    public void retrieveData(String name, Object obj){
        obj = data.get(name); 
    }

    public void printHeader(int section, int size,int nthreads) {

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

        System.out.println(header); 

        if (nthreads == 1) {
            System.out.println("Executing on " + nthreads + " thread");
        }
        else {
            System.out.println("Executing on " + nthreads + " threads");
        }

        System.out.println("");

    } 
}
