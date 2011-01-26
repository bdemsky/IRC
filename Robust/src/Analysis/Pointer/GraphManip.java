package Analysis.Pointer;
import IR.Flat.*;
import IR.*;
import Analysis.Pointer.AllocFactory.AllocNode;
import java.util.*;

public class GraphManip {
  static HashSet<Edge> genEdges(TempDescriptor tmp, HashSet<AllocNode> dstSet) {
    HashSet<Edge> edgeset=new HashSet<Edge>();
    for(AllocNode node:dstSet) {
      edgeset.add(new Edge(tmp, node));
    }
    return edgeset;
  }

  static HashSet<Edge> genEdges(HashSet<AllocNode> srcSet, FieldDescriptor fd, HashSet<AllocNode> dstSet) {
    HashSet<Edge> edgeset=new HashSet<Edge>();
    for(AllocNode srcnode:srcSet) {
      for(AllocNode dstnode:dstSet) {
	edgeset.add(new Edge(srcnode, fd, dstnode));
      }
    }
    return edgeset;
  }

  static HashSet<Edge> getDiffEdges(Delta delta, TempDescriptor tmp) {
    HashSet<Edge> edges=new HashSet<Edge>();
    HashSet<Edge> removeedges=delta.varedgeremove.get(tmp);

    for(Edge e:delta.basevaredge.get(tmp)) {
      if (removeedges==null||!removeedges.contains(e))
	edges.add(e);
    }
    if (delta.varedgeadd.containsKey(tmp))
      for(Edge e:delta.varedgeadd.get(tmp)) {
	edges.add(e);
      }
    return edges;
  }

  static HashSet<Edge> getEdges(Graph graph, Delta delta, TempDescriptor tmp) {
    HashSet<Edge> edges=new HashSet<Edge>();
    HashSet<Edge> removeedges=delta.varedgeremove.get(tmp);

    for(Edge e:graph.getEdges(tmp)) {
      if (removeedges==null||!removeedges.contains(e))
	edges.add(e);
    }
    if (delta.varedgeadd.containsKey(tmp))
      for(Edge e:delta.varedgeadd.get(tmp)) {
	edges.add(e);
      }
    return edges;
  }

  static HashSet<Edge> getEdges(Graph graph, Delta delta, HashSet<AllocNode> srcNodes, FieldDescriptor fd) {
    HashSet<Edge> nodes=new HashSet<Edge>();
    for(AllocNode node:srcNodes) {
      HashSet<Edge> removeedges=delta.heapedgeremove.get(node);
      for(Edge e:graph.getEdges(node)) {
	if (e.fd==fd&&(removeedges==null||!removeedges.contains(e)))
	  nodes.add(e);
      }
      if (delta.heapedgeadd.containsKey(node))
	for(Edge e:delta.heapedgeadd.get(node)) {
	  if (e.fd==fd)
	    nodes.add(e);
	}
    }
    return nodes;
  }

  static HashSet<AllocNode> getDiffNodes(Delta delta, TempDescriptor tmp) {
    HashSet<AllocNode> nodes=new HashSet<AllocNode>();
    HashSet<Edge> removeedges=delta.varedgeremove.get(tmp);

    for(Edge e:delta.basevaredge.get(tmp)) {
      if (removeedges==null||!removeedges.contains(e))
	nodes.add(e.dst);
    }
    if (delta.varedgeadd.containsKey(tmp))
      for(Edge e:delta.varedgeadd.get(tmp)) {
	nodes.add(e.dst);
      }
    return nodes;
  }

  static HashSet<AllocNode> getNodes(Graph graph, Delta delta, TempDescriptor tmp) {
    HashSet<AllocNode> nodes=new HashSet<AllocNode>();
    HashSet<Edge> removeedges=delta.varedgeremove.get(tmp);

    for(Edge e:graph.getEdges(tmp)) {
      if (removeedges==null||!removeedges.contains(e))
	nodes.add(e.dst);
    }
    if (delta.varedgeadd.containsKey(tmp))
      for(Edge e:delta.varedgeadd.get(tmp)) {
	nodes.add(e.dst);
      }
    return nodes;
  }

  static HashSet<AllocNode> getDiffNodes(Delta delta, HashSet<AllocNode> srcNodes, FieldDescriptor fd) {
    HashSet<AllocNode> nodes=new HashSet<AllocNode>();
    for(AllocNode node:srcNodes) {
      HashSet<Edge> removeedges=delta.heapedgeremove.get(node);
      for(Edge e:delta.baseheapedge.get(node)) {
	if (e.fd==fd&&(removeedges==null||!removeedges.contains(e)))
	  nodes.add(e.dst);
      }
      if (delta.heapedgeadd.containsKey(node))
	for(Edge e:delta.heapedgeadd.get(node)) {
	  if (e.fd==fd)
	    nodes.add(e.dst);
	}
    }
    return nodes;
  }

  static HashSet<AllocNode> getNodes(Graph graph, Delta delta, HashSet<AllocNode> srcNodes, FieldDescriptor fd) {
    HashSet<AllocNode> nodes=new HashSet<AllocNode>();
    for(AllocNode node:srcNodes) {
      HashSet<Edge> removeedges=delta.heapedgeremove.get(node);
      for(Edge e:graph.getEdges(node)) {
	if (e.fd==fd&&(removeedges==null||!removeedges.contains(e)))
	  nodes.add(e.dst);
      }
      if (delta.heapedgeadd.containsKey(node))
	for(Edge e:delta.heapedgeadd.get(node)) {
	  if (e.fd==fd)
	    nodes.add(e.dst);
	}
    }
    return nodes;
  }
}