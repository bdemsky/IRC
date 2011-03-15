package Analysis.Pointer;
import java.util.*;
import Analysis.Disjoint.PointerMethod;
import Analysis.Pointer.AllocFactory.AllocNode;
import IR.Flat.*;
import IR.MethodDescriptor;
import java.io.PrintWriter;

public class Graph {
  /* This is field is set is this Graph is just a delta on the parent
   * graph. */

  Graph parent;
  HashMap<AllocNode, MySet<Edge>> nodeMap;
  HashMap<TempDescriptor, MySet<Edge>> varMap;
  HashMap<AllocNode, MySet<Edge>> backMap;
  MySet<Edge> strongUpdateSet;

  public void check() {
    for(Map.Entry<AllocNode, MySet<Edge>> entry:nodeMap.entrySet()) {
      AllocNode node=entry.getKey();
      if (node==null)
	throw new Error("Null node key");
      for(Edge e:entry.getValue())
	if (e.src!=node)
	  throw new Error();
    }
    for(Map.Entry<TempDescriptor, MySet<Edge>> entry:varMap.entrySet()) {
      TempDescriptor tmp=entry.getKey();
      if (tmp==null)
	throw new Error("Null tmp key");
      for(Edge e:entry.getValue())
	if (e.srcvar!=tmp)
	  throw new Error();
    }
  }

  /* Need this information for mapping in callee results */
  MySet<Edge> reachEdge;
  HashSet<AllocNode> reachNode;
  MySet<Edge> externalEdgeSet;
  /* These edges were created by the caller */
  MySet<Edge> callerEdges;

  /* Need this information for mapping in callee results */
  HashSet<AllocNode> nodeAges;
  HashMap<AllocNode, Boolean> oldNodes;

  /* Need this information for mapping in callee results */
  HashSet<AllocNode> callNodeAges;
  HashSet<AllocNode> callOldNodes;
  HashSet<MethodDescriptor> callTargets;

  public Graph(Graph parent) {
    nodeMap=new HashMap<AllocNode, MySet<Edge>>();
    varMap=new HashMap<TempDescriptor, MySet<Edge>>();
    nodeAges=new HashSet<AllocNode>();
    oldNodes=new HashMap<AllocNode, Boolean>();
    this.parent=parent;
  }

  public MySet<Edge> getBackEdges(AllocNode node) {
    MySet<Edge> edgeset=new MySet<Edge>();
    edgeset.addAll(backMap.get(node));
    edgeset.addAll(parent.backMap.get(node));
    return edgeset;
  }

  public boolean containsNode(AllocNode node) {
    return nodeAges.contains(node)||parent!=null&&parent.nodeAges.contains(node);
  }

  public Edge getMatch(Edge old) {
    if (old.srcvar!=null) {
      MySet<Edge> edges=varMap.get(old.srcvar);
      if (edges==null)
	edges=parent.varMap.get(old.srcvar);
      if (edges==null)
	return null;
      return edges.get(old);
    } else {
      MySet<Edge> edges=nodeMap.get(old.src);
      if (edges==null)
	edges=parent.nodeMap.get(old.src);
      if (edges==null)
	return null;
      return edges.get(old);
    }
  }
  
  public MySet<Edge> getEdges(TempDescriptor tmp) {
    if (varMap.containsKey(tmp))
      return varMap.get(tmp);
    else if (parent!=null&&parent.varMap.containsKey(tmp))
      return parent.varMap.get(tmp);
    else return emptySet;
  }

  public MySet<Edge> getEdges(AllocNode node) {
    if (nodeMap.containsKey(node))
      return nodeMap.get(node);
    else if (parent!=null&&parent.nodeMap.containsKey(node))
      return parent.nodeMap.get(node);
    else return emptySet;
  }

  public static MySet<Edge> emptySet=new MySet<Edge>(true);

  public void printGraph(PrintWriter output, String name) {
    output.println("digraph \""+name+"\" {");
    output.println("\tnode [fontsize=10,height=\"0.1\", width=\"0.1\"];");
    output.println("\tedge [fontsize=6];");
    outputTempEdges(output, varMap, null);
    if (parent!=null)
      outputTempEdges(output, parent.varMap, varMap);
    outputHeapEdges(output, nodeMap, null);
    if (parent!=null)
      outputHeapEdges(output, parent.nodeMap, nodeMap);
    output.println("}\n");
  }

  private void outputTempEdges(PrintWriter output, HashMap<TempDescriptor, MySet<Edge>> varMap, 
			       HashMap<TempDescriptor, MySet<Edge>> childvarMap) {
    for(Map.Entry<TempDescriptor, MySet<Edge>> entry:varMap.entrySet()) {
      TempDescriptor tmp=entry.getKey();
      if (childvarMap!=null&&childvarMap.containsKey(tmp))
	continue;
      output.println(tmp.getSymbol()+"[shape=rectangle];");
      for(Edge e:entry.getValue()) {
	if (e.srcvar!=tmp)
	  throw new Error(e.srcvar +" is not equal to "+tmp);
	AllocNode n=e.dst;
	output.println("\t"+tmp.getSymbol()+"->"+n.getID()+";");
      }
    }
  }

  private void outputHeapEdges(PrintWriter output, HashMap<AllocNode, MySet<Edge>> nodeMap, 
			       HashMap<AllocNode, MySet<Edge>> childNodeMap) {
    for(Map.Entry<AllocNode, MySet<Edge>> entry:nodeMap.entrySet()) {
      AllocNode node=entry.getKey();
      if (childNodeMap!=null&&childNodeMap.containsKey(node))
	continue;
      for(Edge e:entry.getValue()) {
	if (e.src!=node)
	  throw new Error(e.src+" is not equal to "+node);
	AllocNode n=e.dst;
	String src=node.getID();
	String dst=n.getID();
	String field=e.fd!=null?e.fd.getSymbol():"[]";
	output.println("\t"+src+"->"+dst+"[label=\""+field+"\"];");
      }
    }
  }
}