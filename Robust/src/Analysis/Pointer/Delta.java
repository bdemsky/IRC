package Analysis.Pointer;
import java.util.*;
import Analysis.Pointer.AllocFactory.AllocNode;
import Analysis.Pointer.BasicBlock.BBlock;
import IR.Flat.*;

public class Delta {
  HashMap<AllocNode, MySet<Edge>> heapedgeremove;
  HashMap<AllocNode, MySet<Edge>> heapedgeadd;
  HashMap<TempDescriptor, MySet<Edge>> varedgeadd;
  HashMap<TempDescriptor, MySet<Edge>> varedgeremove;
  HashMap<AllocNode, MySet<Edge>> baseheapedge;
  HashMap<TempDescriptor, MySet<Edge>> basevaredge;
  HashMap<AllocNode, Integer> addNodeAges;

  boolean init;
  BBlock block;

  /* Init is set for false for delta propagations inside of one basic block.
   */
  
  public Delta(BBlock block, boolean init) {
    this.init=init;
    this.baseheapedge=new HashMap<AllocNode, MySet<Edge>>();
    this.basevaredge=new HashMap<TempDescriptor, MySet<Edge>>();
    this.heapedgeadd=new HashMap<AllocNode, MySet<Edge>>();
    this.heapedgeremove=new HashMap<AllocNode, MySet<Edge>>();
    this.varedgeadd=new HashMap<TempDescriptor, MySet<Edge>>();
    this.varedgeremove=new HashMap<TempDescriptor, MySet<Edge>>();
    this.addNodeAges=new HashMap<AllocNode, Integer>();
    this.block=block;
  }

  private Delta() {
  }

  public BBlock getBlock() {
    return block;
  }

  public void setBlock(BBlock block) {
    this.block=block;
  }

  public Delta changeParams(HashMap<TempDescriptor, TempDescriptor> tmpMap, BBlock bblock) {
    Delta newdelta=new Delta();
    newdelta.baseheapedge=baseheapedge;
    newdelta.basevaredge=basevaredge;
    newdelta.heapedgeadd=heapedgeadd;
    newdelta.heapedgeremove=heapedgeremove;
    //Update variable edge mappings
    newdelta.varedgeadd=new HashMap<TempDescriptor, MySet<Edge>>();
    for(Map.Entry<TempDescriptor, MySet<Edge>> entry:varedgeadd.entrySet()) {
      varedgeadd.put(tmpMap.get(entry.getKey()), entry.getValue());
    }
    newdelta.varedgeremove=varedgeremove;
    newdelta.block=bblock;
    return newdelta;
  }

  public Delta buildBase(MySet<Edge> edges) {
    Delta newdelta=new Delta();
    newdelta.baseheapedge=baseheapedge;
    newdelta.basevaredge=basevaredge;
    newdelta.heapedgeadd=heapedgeadd;
    newdelta.heapedgeremove=heapedgeremove;
    newdelta.varedgeadd=varedgeadd;
    for(Edge e:edges) {
      if (e.srcvar!=null) {
	if (!newdelta.varedgeadd.containsKey(e.srcvar)) {
	  newdelta.varedgeadd.put(e.srcvar, new MySet<Edge>());
	}
	newdelta.varedgeadd.get(e.srcvar).add(e);
      } else {
	if (!newdelta.heapedgeadd.containsKey(e.src)) {
	  newdelta.heapedgeadd.put(e.src, new MySet<Edge>());
	}
	newdelta.heapedgeadd.get(e.src).add(e);
      }
    }
    return newdelta;
  }

  public Delta diffBlock(BBlock bblock) {
    Delta newdelta=new Delta();
    newdelta.baseheapedge=baseheapedge;
    newdelta.basevaredge=basevaredge;
    newdelta.heapedgeadd=heapedgeadd;
    newdelta.heapedgeremove=heapedgeremove;
    newdelta.varedgeadd=varedgeadd;
    newdelta.varedgeremove=varedgeremove;
    newdelta.block=bblock;
    return newdelta;
  }

  public boolean getInit() {
    return init;
  }

  public void setInit(boolean init) {
    this.init=init;
  }
}