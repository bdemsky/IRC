package Analysis.Pointer;
import java.util.*;
import IR.Flat.*;
import IR.*;
import Analysis.Pointer.BasicBlock.BBlock;
import Analysis.Pointer.AllocFactory.AllocNode;

public class Pointer {
  HashMap<FlatMethod, BasicBlock> blockMap;
  HashMap<FlatNode, Graph> graphMap;
  State state;
  TypeUtil typeUtil;
  AllocFactory allocFactory;
  LinkedList<Delta> toprocess;

  public Pointer(State state, TypeUtil typeUtil) {
    this.state=state;
    this.blockMap=new HashMap<FlatMethod, BasicBlock>();
    this.graphMap=new HashMap<FlatNode, Graph>();
    this.typeUtil=typeUtil;
    this.allocFactory=new AllocFactory(state, typeUtil);
    this.toprocess=new LinkedList<Delta>();
  }

  public BasicBlock getBBlock(FlatMethod fm) {
    if (!blockMap.containsKey(fm))
      blockMap.put(fm, BasicBlock.getBBlock(fm));
    return blockMap.get(fm);
  }
  
  Delta buildInitialContext() {
    MethodDescriptor md=typeUtil.getMain();
    FlatMethod fm=state.getMethodFlat(md);
    BasicBlock bb=getBBlock(fm);
    BBlock start=bb.getStart();
    Delta delta=new Delta(start, true);
    delta.addHeapEdge(allocFactory.StringArray, new Edge(allocFactory.StringArray, null, allocFactory.Strings));
    delta.addVarEdge(fm.getParameter(0), new Edge(fm.getParameter(0), allocFactory.StringArray));
    return delta;
  }

  void doAnalysis() {
    toprocess.add(buildInitialContext());

    while(!toprocess.isEmpty()) {
      Delta delta=toprocess.remove();
      BBlock bblock=delta.getBlock();
      Vector<FlatNode> nodes=bblock.nodes();
      FlatNode firstNode=nodes.get(0);

      //Get graph for first node
      if (!graphMap.containsKey(firstNode)) {
	graphMap.put(firstNode, new Graph(null));
      }
      Graph graph=graphMap.get(firstNode);

      //First entrance is special...
      if (delta.getInit()) {
	applyInit(delta, graph);
      } else {
	applyDelta(delta, graph);
      }
      
      Graph nodeGraph=null;
      for(int i=1; i<nodes.size();i++) {
	FlatNode currNode=nodes.get(i);
	if (!graphMap.containsKey(currNode)) {
	  graphMap.put(currNode, new Graph(graph, nodeGraph));
	}
	nodeGraph=graphMap.get(currNode);

	if (delta.getInit()) {
	  applyInitDiff(delta, nodeGraph);
	} else {
	  applyDeltaDiff(delta, nodeGraph);	  
	}
      }
    }
    
  }
}