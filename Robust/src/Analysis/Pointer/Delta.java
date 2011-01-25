package Analysis.Pointer;
import java.util.*;
import Analysis.Pointer.AllocFactory.AllocNode;
import Analysis.Pointer.BasicBlock.BBlock;
import IR.Flat.*;

public class Delta {
  HashMap<AllocNode, HashSet<Edge>> heapedgeremove;
  HashMap<AllocNode, HashSet<Edge>> heapedgeadd;
  HashMap<TempDescriptor, HashSet<Edge>> varedgeadd;
  HashMap<TempDescriptor, HashSet<Edge>> varedgeremove;
  HashMap<AllocNode, HashSet<Edge>> baseheapedge;
  HashMap<TempDescriptor, HashSet<Edge>> basevaredge;

  boolean init;
  BBlock block;

  /* Init is set for false for delta propagations inside of one basic block.
   */
  
  public Delta(BBlock block, boolean init) {
    this.init=init;
    this.baseheapedge=new HashMap<AllocNode, HashSet<Edge>>();
    this.basevaredge=new HashMap<TempDescriptor, HashSet<Edge>>();
    this.heapedgeadd=new HashMap<AllocNode, HashSet<Edge>>();
    this.heapedgeremove=new HashMap<AllocNode, HashSet<Edge>>();
    this.varedgeadd=new HashMap<TempDescriptor, HashSet<Edge>>();
    this.varedgeremove=new HashMap<TempDescriptor, HashSet<Edge>>();
    this.block=block;
  }

  public BBlock getBlock() {
    return block;
  }

  public boolean getInit() {
    return init;
  }

  public void setInit(boolean init) {
    this.init=init;
  }
}