/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dstm2;

/**
 *
 * @author navid
 */
public class Init {

    public static void init(){
        String managerClassName = "dstm2.manager.AggressiveManager";
        Class managerClass = null;
        String adapterClassName = Defaults.ADAPTER;
    
    // discard statistics from previous runs
    
    // Parse and check the args
    
    // Initialize contention manager.
    try {
      managerClass = Class.forName(managerClassName);
      System.out.println("manage r" + managerClass);
      Thread.setContentionManagerClass(managerClass);
    } catch (ClassNotFoundException ex) {
      
    }
    
    // Initialize adapter class
      Thread.setAdapterClass(adapterClassName);
      System.out.println(adapterClassName);
    }
}
