package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import IR.Descriptor;

public class InferGraph {

  Set<InferNode> nodeSet;

  // having one location for the top location
  private static final int topLocationID = 1;

  // unique ID seed
  private static int uniqueID = 10000;

  // maps descriptors (field and local var descriptors) to its unique integer id
  Map<Descriptor, Integer> mapDescriptorToUniqueID;

  // maps field/var descriptros to infer nodes
  Map<Descriptor, InferNode> mapDescToInferNode;

  boolean debug = true;

  public InferGraph() {
    nodeSet = new HashSet<InferNode>();
    mapDescToInferNode = new HashMap<Descriptor, InferNode>();
    mapDescriptorToUniqueID = new HashMap<Descriptor, Integer>();
  }

  public void addValueFlowEdge(Descriptor fromDesc, Descriptor toDesc) {

  }

  public InferNode getInferNode(Descriptor desc) {
    if (mapDescToInferNode.containsKey(desc)) {

    }
    return null;
  }

  public void assignTopLocationToDescriptor(Descriptor desc) {
    mapDescriptorToUniqueID.put(desc, Integer.valueOf((topLocationID)));
    debug_uniqueid_print(desc);
  }

  public void assignUniqueIDtoDescriptor(Descriptor desc) {
    mapDescriptorToUniqueID.put(desc, getUniqueID());
    debug_uniqueid_print(desc);
  }

  private int getUniqueID() {
    return uniqueID++;
  }

  private void debug_uniqueid_print(Descriptor d) {
    if (debug) {
      int id = mapDescriptorToUniqueID.get(d).intValue();
      System.out.print(d + " -> ");
      if (id == topLocationID) {
        System.out.println("TOP");
      } else {
        System.out.println(id);
      }

    }
  }
}
