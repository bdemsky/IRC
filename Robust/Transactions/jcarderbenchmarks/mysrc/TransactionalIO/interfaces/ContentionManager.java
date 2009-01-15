/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.interfaces;

import TransactionalIO.core.BlockDataStructure;
import TransactionalIO.core.ExtendedTransaction;
import TransactionalIO.core.GlobalOffset;
import TransactionalIO.core.TransactionalFile;
import java.util.Vector;

/**
 *
 * @author navid
 */

public interface ContentionManager {

    
  public void resolveConflict(ExtendedTransaction me, ExtendedTransaction other, GlobalOffset obj);
  public void resolveConflict(ExtendedTransaction me, ExtendedTransaction other, BlockDataStructure obj);

  public void resolveConflict(ExtendedTransaction me, Vector/*<ExtendedTransaction>*/ other, GlobalOffset obj);
  public void resolveConflict(ExtendedTransaction me, Vector/*<ExtendedTransaction>*/ other, BlockDataStructure obj);
  
  public void resolveConflict(ExtendedTransaction me, GlobalOffset obj);
  public void resolveConflict(ExtendedTransaction me, BlockDataStructure obj);

  public long getPriority();
  

  public void setPriority(long value);
  
}
