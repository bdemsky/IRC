package Analysis.Pointer;
import java.util.*;
import Analysis.Disjoint.PointerMethod;
import Analysis.Pointer.AllocFactory.AllocNode;
import IR.Flat.*;

public class Graph {
  /* This is field is set is this Graph is just a delta on the parent
   * graph. */

  Graph parent;
  HashMap<AllocNode, MySet<Edge>> nodeMap;
  HashMap<TempDescriptor, MySet<Edge>> varMap;
  HashMap<AllocNode, MySet<Edge>> backMap;
  MySet<Edge> strongUpdateSet;
  MySet<Edge> reachEdge;
  HashSet<AllocNode> reachNode;

  /* Need this information for mapping in callee results */
  HashSet<AllocNode> nodeAges;

  public Graph(Graph parent) {
    nodeMap=new HashMap<AllocNode, MySet<Edge>>();
    backMap=new HashMap<AllocNode, MySet<Edge>>();
    varMap=new HashMap<TempDescriptor, MySet<Edge>>();
    nodeAges=new HashSet<AllocNode>();
    this.parent=parent;
  }

  public boolean containsNode(AllocNode node) {
    return nodeAges.contains(node)||parent!=null&&parent.nodeAges.contains(node);
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

  public static MySet<Edge> emptySet=new MySet<Edge>();
}