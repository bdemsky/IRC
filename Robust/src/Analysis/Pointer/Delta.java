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

  public Delta check() {
    for(Map.Entry<AllocNode, MySet<Edge>> entry:heapedgeadd.entrySet()) {
      AllocNode node=entry.getKey();
      if (node==null)
	throw new Error("null node key");
      for(Edge e:entry.getValue())
	if (e.src!=node)
	  throw new Error(e.src+" is not equal to "+node);
    }

    for(Map.Entry<TempDescriptor, MySet<Edge>> entry:varedgeadd.entrySet()) {
      TempDescriptor tmp=entry.getKey();
      if (tmp==null)
	throw new Error("null temp key");
      for(Edge e:entry.getValue())
	if (e.srcvar!=tmp)
	  throw new Error(e.srcvar+" is not equal to "+tmp);
    }
    return this;
  }

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

  public boolean isEmpty() {
    return baseheapedge.isEmpty()&&basevaredge.isEmpty()&&heapedgeadd.isEmpty()&&heapedgeremove.isEmpty()&&varedgeadd.isEmpty()&&(varedgeremove==null||varedgeremove.isEmpty())&&baseNodeAges.isEmpty()&&addNodeAges.isEmpty()&&baseOldNodes.isEmpty()&&addOldNodes.isEmpty();
  }

  public void print() {
    System.out.println("----------------------------------------------");
    System.out.println("init:"+init);
    System.out.println("baseheapedge:"+baseheapedge);
    System.out.println("basevaredge:"+basevaredge);
    System.out.println("heapedgeadd:"+heapedgeadd);
    System.out.println("heapedgeremove:"+heapedgeremove);
    System.out.println("varedgeadd:"+varedgeadd);
    if (varedgeremove==null)
      System.out.println("varedgeremove: null");
    else
      System.out.println("varedgeremove:"+varedgeremove);
    System.out.println("baseNodeAges:"+baseNodeAges);
    System.out.println("addNodeAges:"+addNodeAges);
    System.out.println("baseOldNodes:"+baseOldNodes);
    System.out.println("addOldNodes:"+addOldNodes);
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
      TempDescriptor origTmp=entry.getKey();
      TempDescriptor newTmp=tmpMap.get(entry.getKey());
      MySet<Edge> edgeset=entry.getValue();
      if (!edgeset.isEmpty()) {
	newdelta.varedgeadd.put(newTmp, new MySet<Edge>());
	for(Edge e:edgeset) {
	  newdelta.varedgeadd.get(newTmp).add(e.rewrite(origTmp, newTmp));
	}
      }
    }
    newdelta.varedgeremove=varedgeremove;
    newdelta.addNodeAges=addNodeAges;
    newdelta.baseNodeAges=baseNodeAges;
    newdelta.addOldNodes=addOldNodes;
    newdelta.baseOldNodes=baseOldNodes;
    newdelta.block=bblock;
    return newdelta;
  }

  public Delta buildBase(MySet<Edge> edges) {
    Delta newdelta=new Delta();
    newdelta.baseheapedge=baseheapedge;
    newdelta.basevaredge=basevaredge;
    newdelta.heapedgeremove=heapedgeremove;
    newdelta.heapedgeadd=new HashMap<AllocNode, MySet<Edge>>();
    newdelta.varedgeadd=new HashMap<TempDescriptor, MySet<Edge>>();
    newdelta.addNodeAges=addNodeAges;
    newdelta.baseNodeAges=baseNodeAges;
    newdelta.addOldNodes=addOldNodes;
    newdelta.baseOldNodes=baseOldNodes;

    for (Map.Entry<AllocNode, MySet<Edge>> entry:heapedgeadd.entrySet()) {
      newdelta.heapedgeadd.put(entry.getKey(), new MySet<Edge>(entry.getValue()));
    }

    for (Map.Entry<TempDescriptor, MySet<Edge>> entry:varedgeadd.entrySet()) {
      newdelta.varedgeadd.put(entry.getKey(), new MySet<Edge>(entry.getValue()));
    }


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
    newdelta.addNodeAges=addNodeAges;
    newdelta.baseNodeAges=baseNodeAges;
    newdelta.addOldNodes=addOldNodes;
    newdelta.baseOldNodes=baseOldNodes;
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
      Edge.mergeEdgeInto(heapedgeadd.get(e.src), e);
  }

  public void addVarEdge(Edge e) {
    if (!varedgeadd.containsKey(e.srcvar)) {
      varedgeadd.put(e.srcvar, new MySet<Edge>(e));
    } else
      Edge.mergeEdgeInto(varedgeadd.get(e.srcvar), e);
  }

  public void removeEdges(MySet<Edge> eset) {
    for(Edge e:eset) {
      removeEdge(e);
    }
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
    if (!heapedgeremove.containsKey(e.src))
      heapedgeremove.put(e.src, new MySet<Edge>(e));
    else
      heapedgeremove.get(e.src).add(e);

  }

  public void removeVarEdge(Edge e) {
    if (varedgeadd.containsKey(e.srcvar)&&varedgeadd.get(e.srcvar).contains(e))
      varedgeadd.get(e.srcvar).remove(e);
    if (!varedgeremove.containsKey(e.srcvar))
      varedgeremove.put(e.srcvar, new MySet<Edge>(e));
    else
      varedgeremove.get(e.srcvar).add(e);
  }

  public void setInit(boolean init) {
    this.init=init;
  }
}