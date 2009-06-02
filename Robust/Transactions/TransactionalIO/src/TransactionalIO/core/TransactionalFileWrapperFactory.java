/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TransactionalIO.core;

import java.io.File;
import java.util.HashMap;

/**
 *
 * @author navid
 */
public class TransactionalFileWrapperFactory {

    static {
        System.load("/scratch/TransactionalIO/libnav.so");
    }

    private TransactionalFileWrapperFactory() {
    }
  //  private static HashMap<INode, GlobalInodeState> filemappings;
    public static HashMap filemappings = new HashMap();
    
    private static native long getINodeNative(String filename);
    
    static INode getINodefromFileName(String filename) {
        return new INode(getINodeNative(filename), filename);
    }

    
    
    public synchronized static GlobalINodeState getTateransactionalFileINodeState(INode inode) {
            
        
        return (GlobalINodeState)filemappings.get(inode.getNumber());
            
    }
    public synchronized static GlobalINodeState createTransactionalFile(INode inode, String filename, String mode) {

        long inodenumber = inode.getNumber();
        if (inodenumber != -1)
            
            if (filemappings.containsKey(inode.getNumber())) {
                return (GlobalINodeState)filemappings.get(inode.getNumber());

            } else {
                
            
                long length = new File(filename).length();
                
                GlobalINodeState inodestate = new GlobalINodeState(inode, length);
                filemappings.put(inode.getNumber(), inodestate);
                return inodestate;
            }
        
        else
        {       
            return null;
        }

    }
        
}
