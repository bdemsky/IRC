package Analysis.Pointer;
import java.util.*;
import Analysis.Pointer.AllocFactory.AllocNode;
import Analysis.Pointer.BasicBlock.BBlock;
import IR.Flat.*;

public class Delta {
  HashMap<AllocNode, Vector<Edge>> heapedgeremove;
  HashMap<TempDescriptor, Vector<Edge>> varedgeremove;
  HashMap<AllocNode, Vector<Edge>> heapedgeadd;
  HashMap<TempDescriptor, Vector<Edge>> varedgeadd;

  boolean init;
  BBlock block;

  public Delta(BBlock block, boolean init) {
    this.heapedgeadd=new HashMap<AllocNode, Vector<Edge>>();
    this.varedgeadd=new HashMap<TempDescriptor, Vector<Edge>>();
    this.heapedgeremove=new HashMap<AllocNode, Vector<Edge>>();
    this.varedgeremove=new HashMap<TempDescriptor, Vector<Edge>>();
    this.init=init;
    this.block=block;
  }

  public void addHeapEdge(AllocNode node, Edge e) {
    if (!heapedgeadd.containsKey(node))
      heapedgeadd.put(node, new Vector<Edge>());
    heapedgeadd.get(node).add(e);
  }

  public void addVarEdge(TempDescriptor tmp, Edge e) {
    if (!varedgeadd.containsKey(tmp))
      varedgeadd.put(tmp, new Vector<Edge>());
    varedgeadd.get(tmp).add(e);
  }

  public void setBlock(BBlock block) {
    this.block=block;
  }

  public BBlock getBlock() {
    return block;
  }

  public boolean getInit() {
    return init;
  }
}