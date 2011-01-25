package Analysis.Pointer;
import java.util.*;
import Analysis.Disjoint.PointerMethod;
import Analysis.Pointer.AllocFactory.AllocNode;
import IR.Flat.*;

public class Graph {

  /* This is field is set is this Graph is just a delta on the parent
   * graph. */

  Graph parent;
  HashMap<AllocNode, HashSet<Edge>> nodeMap;
  HashMap<TempDescriptor, HashSet<Edge>> varMap;
  HashMap<AllocNode, HashSet<Edge>> backMap;

  public Graph(Graph parent) {
    nodeMap=new HashMap<AllocNode, HashSet<Edge>>();
    backMap=new HashMap<AllocNode, HashSet<Edge>>();
    varMap=new HashMap<TempDescriptor, HashSet<Edge>>();
    this.parent=parent;
  }

  public HashSet<Edge> getEdges(AllocNode node) {
    if (nodeMap.containsKey(node))
      return nodeMap.get(node);
    else if (parent!=null&&parent.nodeMap.containsKey(node))
      return parent.nodeMap.get(node);
    else return emptySet;
  }

  public static HashSet<Edge> emptySet=new HashSet<Edge>();
}