/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dstm2;

/**
 *
 * @author navid
 */
public class Init 

     
{

    public

    static void init(String managername, boolean inevitable, String adaptername) {
        Defaults.INEVITABLE = inevitable;
        System.out.println(managername);
        Class managerClass = null;
        String adapterClassName = adaptername;
        try {
            managerClass = Class.forName(managername);
            System.out.println("Manager: " + managerClass);
            System.out.println("Ineviatble: " + Defaults.INEVITABLE);
            Thread.setContentionManagerClass(managerClass);
        } catch (ClassNotFoundException ex) {
        }
        Thread.setAdapterClass(adapterClassName);
        System.out.println(adapterClassName);
    }
}
