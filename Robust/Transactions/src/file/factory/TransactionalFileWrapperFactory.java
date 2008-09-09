/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dstm2.file.factory;

import java.io.File;
import java.util.HashMap;

/**
 *
 * @author navid
 */
public class TransactionalFileWrapperFactory {



    private TransactionalFileWrapperFactory() {
    }
  //  private static HashMap<INode, Adapter> filemappings;
    private static HashMap filemappings;
    
    private static native long getINodeNative(String filename);
    
    static{
        System.load("/home/navid/libnav.so");
    }
    
    static INode getINodefromFileName(String filename) {
        return new INode(getINodeNative(filename));
    }
    
    public static void main(String args[]){
        System.out.print("in java " + getINodeNative("/home/navid/myfile.txt") +"\n");
        System.out.print("in java " + getINodeNative("/home/navid/HellWorld.java") +"\n");
    }

    public synchronized static Adapter createTransactionalFile(String filename, String mode) {

        
        long inodenumber = getINodeNative(filename);
        INode inode = new INode(inodenumber);
        
        

        if (filemappings.containsKey(inode)) {
            return (Adapter)filemappings.get(inode);

        } else {
            Adapter adapter = new Adapter();
            filemappings.put(inode, adapter);
            return adapter;
        }

    }
}
