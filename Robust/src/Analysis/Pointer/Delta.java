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
  HashSet<AllocNode> baseNodeAges;
  HashSet<AllocNode> addNodeAges;
  HashMap<AllocNode, Boolean> baseOldNodes;
  HashMap<AllocNode, Boolean> addOldNodes;


  boolean init;
  PPoint block;
  boolean callStart;

  /* Init is set for false for delta propagations inside of one basic block.
   */
  
  public Delta(PPoint block, boolean init) {
    this.init=init;
    this.baseheapedge=new HashMap<AllocNode, MySet<Edge>>();
    this.basevaredge=new HashMap<TempDescriptor, MySet<Edge>>();
    this.heapedgeadd=new HashMap<AllocNode, MySet<Edge>>();
    this.heapedgeremove=new HashMap<AllocNode, MySet<Edge>>();
    this.varedgeadd=new HashMap<TempDescriptor, MySet<Edge>>();
    this.varedgeremove=new HashMap<TempDescriptor, MySet<Edge>>();
    this.baseNodeAges=new HashSet<AllocNode>();
    this.addNodeAges=new HashSet<AllocNode>();
    this.baseOldNodes=new HashMap<AllocNode, Boolean>();
    this.addOldNodes=new HashMap<AllocNode, Boolean>();
    this.block=block;
  }

  private Delta() {
  }

  public PPoint getBlock() {
    return block;
  }

  public void setBlock(PPoint block) {
    this.block=block;
  }

  public Delta changeParams(HashMap<TempDescriptor, TempDescriptor> tmpMap, PPoint bblock) {
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

  public Delta diffBlock(PPoint bblock) {
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

  public void addEdge(Edge e) {
    if (e.src!=null) {
      addHeapEdge(e);
    } else {
      addVarEdge(e);
    }
  }

  public void addHeapEdge(Edge e) {
    if (!heapedgeadd.containsKey(e.src))
      heapedgeadd.put(e.src, new MySet<Edge>(e));
    else
      heapedgeadd.get(e.src).add(e);
  }

  public void addVarEdge(Edge e) {
    if (!varedgeadd.containsKey(e.srcvar))
      varedgeadd.put(e.srcvar, new MySet<Edge>(e));
    else
      varedgeadd.get(e.srcvar).add(e);
  }

  public void removeEdge(Edge e) {
    if (e.src!=null) {
      removeHeapEdge(e);
    } else {
      removeVarEdge(e);
    }
  }

  public void removeHeapEdge(Edge e) {
    if (heapedgeadd.containsKey(e.src)&&heapedgeadd.get(e.src).contains(e))
      heapedgeadd.get(e.src).remove(e);
    else {
      if (!heapedgeremove.containsKey(e.src))
	heapedgeremove.put(e.src, new MySet<Edge>(e));
      else
	heapedgeremove.get(e.src).add(e);
    }
  }

  public void removeVarEdge(Edge e) {
    if (varedgeadd.containsKey(e.src)&&varedgeadd.get(e.src).contains(e))
      varedgeadd.get(e.src).remove(e);
    else {
      if (!varedgeremove.containsKey(e.srcvar))
	varedgeremove.put(e.srcvar, new MySet<Edge>(e));
      else
	varedgeremove.get(e.srcvar).add(e);
    }
  }

  public void setInit(boolean init) {
    this.init=init;
  }
}