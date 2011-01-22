package Analysis.Pointer;
import java.util.*;
import Analysis.Disjoint.PointerMethod;
import Analysis.Pointer.AllocFactory.AllocNode;
import IR.Flat.*;

public class Graph {

  /* This is field is set is this Graph is just a delta on the parent
   * graph. */

  Graph parent;
  HashSet<TempDescriptor> tempset;
  HashSet<AllocNode> allocset;

  HashMap<AllocNode, Vector<Edge>> nodeMap;
  HashMap<TempDescriptor, Vector<Edge>> tmpMap;

  public Graph(Graph parent) {
    nodeMap=new HashMap<AllocNode, Vector<Edge>>();
    tmpMap=new HashMap<TempDescriptor, Vector<Edge>>();
    tempset=new HashSet<TempDescriptor>();
    allocset=new HashSet<AllocNode>();
    
    this.parent=parent;
  }

  
}