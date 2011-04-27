package Analysis.OoOJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class SESELock {

  private HashSet<ConflictNode> conflictNodeSet;
  private HashSet<ConflictEdge> conflictEdgeSet;
  private HashMap<ConflictNode, Integer> nodeTypeMap;
  private int id;

  public SESELock() {
    conflictNodeSet = new HashSet<ConflictNode>();
    conflictEdgeSet = new HashSet<ConflictEdge>();
    nodeTypeMap = new HashMap<ConflictNode, Integer>();
  }

  public void addConflictNode(ConflictNode node, int type) {
    conflictNodeSet.add(node);
    setNodeType(node, type);
  }

  public void setNodeType(ConflictNode node, int type) {
    nodeTypeMap.put(node, new Integer(type));
  }

  public int getNodeType(ConflictNode node) {
    return nodeTypeMap.get(node).intValue();
  }

  public void addConflictEdge(ConflictEdge e) {
    conflictEdgeSet.add(e);
  }

  public boolean containsConflictEdge(ConflictEdge e) {
    return conflictEdgeSet.contains(e);
  }

  public HashSet<ConflictNode> getConflictNodeSet() {
    return conflictNodeSet;
  }

  public boolean isWriteNode(ConflictNode node) {
    if (node.getWriteEffectSet().isEmpty()) {
      return false;
    } else {
      return true;
    }
  }

  public boolean hasSelfCoarseEdge(ConflictNode node) {

    Set<ConflictEdge> set = node.getEdgeSet();
    for (Iterator iterator = set.iterator(); iterator.hasNext(); ) {
      ConflictEdge conflictEdge = (ConflictEdge) iterator.next();

      if (conflictEdge.isCoarseEdge() && conflictEdge.getVertexU() == conflictEdge.getVertexV()) {
	return true;
      }
    }
    return false;
  }

  public boolean hasSelfEdge(ConflictNode node) {

    Set<ConflictEdge> set = node.getEdgeSet();
    for (Iterator iterator = set.iterator(); iterator.hasNext(); ) {
      ConflictEdge conflictEdge = (ConflictEdge) iterator.next();

      if ((!conflictEdge.isCoarseEdge()) && conflictEdge.getVertexU() == conflictEdge.getVertexV()) {
	return true;
      }
    }
    return false;
  }

  public boolean hasCoarseEdgeWithParentCoarse(ConflictNode node) {

    Set<ConflictEdge> set = node.getEdgeSet();
    for (Iterator iterator = set.iterator(); iterator.hasNext(); ) {
      ConflictEdge conflictEdge = (ConflictEdge) iterator.next();

      ConflictNode cNode;
      if (conflictEdge.getVertexU() == node) {
	cNode = conflictEdge.getVertexV();
      } else {
	cNode = conflictEdge.getVertexU();
      }
      Integer cNodeTypeIn = nodeTypeMap.get(cNode);
      if (cNodeTypeIn != null && cNodeTypeIn.intValue() == ConflictNode.PARENT_COARSE) {
	return true;
      }
    }
    return false;
  }

  public ConflictNode getNewNodeConnectedWithGroup(ConflictEdge newEdge) {

    // check whether or not the new node has a fine-grained edges to all
    // current nodes.

    ConflictNode newNode;
    if (conflictNodeSet.contains(newEdge.getVertexU())) {
      newNode = newEdge.getVertexV();
    } else if (conflictNodeSet.contains(newEdge.getVertexV())) {
      newNode = newEdge.getVertexU();
    } else {
      return null;
    }


    int count = 0;
    Set<ConflictEdge> edgeSet = newNode.getEdgeSet();
    for (Iterator iterator = edgeSet.iterator(); iterator.hasNext(); ) {
      ConflictEdge conflictEdge = (ConflictEdge) iterator.next();
      if (!conflictEdge.getVertexU().equals(newNode)
          && conflictNodeSet.contains(conflictEdge.getVertexU())) {
	count++;
      } else if (!conflictEdge.getVertexV().equals(newNode)
                 && conflictNodeSet.contains(conflictEdge.getVertexV())) {
	count++;
      }
    }

    if(conflictNodeSet.contains(newNode)) {
      count++;
    }

    if(isWriteNode(newNode)) {
      if (count == conflictNodeSet.size()) {
	// connected to all current nodes in group
	return newNode;
      }
    } else {
      // it is read node
      int writeNodeCount=0;
      for (Iterator iterator = conflictNodeSet.iterator(); iterator.hasNext(); ) {
	ConflictNode node = (ConflictNode) iterator.next();
	if(isWriteNode(node)) {
	  writeNodeCount++;
	}
      }
      if (count == writeNodeCount) {
	// connected to all current write nodes in group
	return newNode;
      }
    }

    return null;

  }

  public void addEdge(ConflictEdge edge) {
    conflictNodeSet.add(edge.getVertexU());
    conflictNodeSet.add(edge.getVertexV());
  }

  public int getID() {
    return id;
  }

  public void setID(int id) {
    this.id = id;
  }

  public boolean containsConflictNode(ConflictNode node) {

    return conflictNodeSet.contains(node);

  }

  public boolean testEdge(ConflictEdge newEdge) {

    if (!conflictNodeSet.contains(newEdge.getVertexU())
        && !conflictNodeSet.contains(newEdge.getVertexV())) {
      return false;
    }

    ConflictNode nodeToAdd = conflictNodeSet.contains(newEdge.getVertexU())?newEdge.getVertexV()
			     :newEdge.getVertexU();

    HashSet<ConflictNode> nodeSet = new HashSet<ConflictNode>(conflictNodeSet);

    for (Iterator edgeIter = nodeToAdd.getEdgeSet().iterator(); edgeIter.hasNext(); ) {
      ConflictEdge edge = (ConflictEdge) edgeIter.next();
      if (nodeSet.contains(edge.getVertexU())) {
	nodeSet.remove(edge.getVertexU());
      } else if (nodeSet.contains(edge.getVertexV())) {
	nodeSet.remove(edge.getVertexV());
      }
    }

    return nodeSet.isEmpty();

  }

  public String toString() {
    String rtr = "";

    for (Iterator<ConflictNode> iterator = conflictNodeSet.iterator(); iterator.hasNext(); ) {
      ConflictNode node = (ConflictNode) iterator.next();
      rtr += " " + node + "::" + getNodeType(node);
    }

    return rtr;
  }

}
