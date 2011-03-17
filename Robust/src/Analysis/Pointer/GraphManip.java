package Analysis.Pointer;
import IR.Flat.*;
import IR.*;
import Analysis.Pointer.AllocFactory.AllocNode;
import java.util.*;

public class GraphManip {
  static MySet<Edge> genEdges(TempDescriptor tmp, HashSet<AllocNode> dstSet) {
    MySet<Edge> edgeset=new MySet<Edge>();
    for(AllocNode node:dstSet) {
      edgeset.add(new Edge(tmp, node));
    }
    return edgeset;
  }

  static MySet<Edge> genEdges(TempDescriptor tmp, MySet<Edge> dstSet) {
    MySet<Edge> edgeset=new MySet<Edge>();
    for(Edge e:dstSet) {
      edgeset.add(e.changeSrcVar(tmp));
    }
    return edgeset;
  }

  static MySet<Edge> genEdges(HashSet<AllocNode> srcSet, FieldDescriptor fd, HashSet<AllocNode> dstSet) {
    MySet<Edge> edgeset=new MySet<Edge>();
    for(AllocNode srcnode:srcSet) {
      for(AllocNode dstnode:dstSet) {
	edgeset.add(new Edge(srcnode, fd, dstnode, Edge.NEW));
      }
    }
    return edgeset;
  }

  static MySet<Edge> getDiffEdges(Delta delta, TempDescriptor tmp) {
    MySet<Edge> edges=new MySet<Edge>();
    MySet<Edge> removeedges=delta.varedgeremove.get(tmp);
    
    MySet<Edge> baseedges=delta.basevaredge.get(tmp);
    if (baseedges!=null) {
      for(Edge e:baseedges) {
	if (removeedges==null||!removeedges.contains(e))
	  edges.add(e);
      }
    }
    if (delta.varedgeadd.containsKey(tmp))
      for(Edge e:delta.varedgeadd.get(tmp)) {
	edges.add(e);
      }
    return edges;
  }

  static MySet<Edge> getEdges(Graph graph, Delta delta, TempDescriptor tmp) {
    MySet<Edge> edges=new MySet<Edge>();
    MySet<Edge> removeedges=delta.varedgeremove.get(tmp);

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

  static MySet<Edge> getEdges(Graph graph, Delta delta, HashSet<AllocNode> srcNodes, FieldDescriptor fd) {
    MySet<Edge> nodes=new MySet<Edge>();
    for(AllocNode node:srcNodes) {
      MySet<Edge> removeedges=delta.heapedgeremove.get(node);
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

  static MySet<Edge> getEdges(Graph graph, Delta delta, AllocNode node) {
    MySet<Edge> nodes=new MySet<Edge>();
    MySet<Edge> removeedges=delta.heapedgeremove.get(node);
    for(Edge e:graph.getEdges(node)) {
      if ((removeedges==null||!removeedges.contains(e)))
	nodes.add(e);
    }
    if (delta.heapedgeadd.containsKey(node))
      for(Edge e:delta.heapedgeadd.get(node)) {
	nodes.add(e);
      }
    
    return nodes;
  }

  static HashSet<AllocNode> getDiffNodes(Delta delta, TempDescriptor tmp) {
    HashSet<AllocNode> nodes=new HashSet<AllocNode>();
    MySet<Edge> removeedges=delta.varedgeremove.get(tmp);
    
    MySet<Edge> baseEdges=delta.basevaredge.get(tmp);

    if (baseEdges!=null)
      for(Edge e:baseEdges) {
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
    MySet<Edge> removeedges=delta.varedgeremove.get(tmp);

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
      MySet<Edge> removeedges=delta.heapedgeremove.get(node);
      MySet<Edge> baseEdges=delta.baseheapedge.get(node);
      if (baseEdges!=null)
	for(Edge e:baseEdges) {
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

  static MySet<Edge> getDiffEdges(Delta delta, HashSet<AllocNode> srcNodes) {
    MySet<Edge> newedges=new MySet<Edge>();
    for(Map.Entry<AllocNode, MySet<Edge>> entry:delta.baseheapedge.entrySet()) {
      AllocNode node=entry.getKey();
      if (srcNodes.contains(node)) {
	MySet<Edge> edges=entry.getValue();
	MySet<Edge> removeedges=delta.heapedgeremove.get(node);
	for(Edge e:edges) {
	  if (removeedges==null||!removeedges.contains(e)) {
	    newedges.add(e);
	  }
	}
      }
    }
    for(Map.Entry<AllocNode, MySet<Edge>> entry:delta.heapedgeadd.entrySet()) {
      AllocNode node=entry.getKey();
      if (srcNodes.contains(node)) {
	MySet<Edge> edges=entry.getValue();
	newedges.addAll(edges);
      }
    }
    return newedges;
  }


  static MySet<Edge> getDiffEdges(Delta delta, HashSet<AllocNode> srcNodes, FieldDescriptor fd) {
    MySet<Edge> newedges=new MySet<Edge>();
    for(Map.Entry<AllocNode, MySet<Edge>> entry:delta.baseheapedge.entrySet()) {
      AllocNode node=entry.getKey();
      if (srcNodes.contains(node)) {
	MySet<Edge> edges=entry.getValue();
	MySet<Edge> removeedges=delta.heapedgeremove.get(node);
	for(Edge e:edges) {
	  if ((removeedges==null||!removeedges.contains(e))&&(e.fd==fd)) {
	    newedges.add(e);
	  }
	}
      }
    }
    for(Map.Entry<AllocNode, MySet<Edge>> entry:delta.heapedgeadd.entrySet()) {
      AllocNode node=entry.getKey();
      if (srcNodes.contains(node)) {
	MySet<Edge> edges=entry.getValue();
	for(Edge e:edges) {
	  if (e.fd==fd)
	    newedges.add(e);
	}
      }
    }
    return newedges;
  }

  static MySet<Edge> makeOld(MySet<Edge> edgesin) {
    MySet<Edge> edgeset=new MySet<Edge>();
    for(Edge e:edgesin) {
      edgeset.add(e.makeOld());
    }
    return edgeset;
  }

  static MySet<Edge> dereference(Graph graph, Delta delta, TempDescriptor dst, MySet<Edge> srcEdges, FieldDescriptor fd) {
    MySet<Edge> edgeset=new MySet<Edge>();
    for(Edge edge:srcEdges) {
      MySet<Edge> removeedges=delta.heapedgeremove.get(edge.dst);
      for(Edge e:graph.getEdges(edge.dst)) {
	if (e.fd==fd&&(removeedges==null||!removeedges.contains(e))) {
	  e=e.changeSrcVar(dst);
	  if (!edgeset.contains(e))
	    edgeset.add(e);
	  else {
	    Edge preve=edgeset.get(e);
	    e=e.merge(preve);
	    edgeset.add(e);
	  }
	}
      }
      if (delta.heapedgeadd.containsKey(edge.dst))
	for(Edge e:delta.heapedgeadd.get(edge.dst)) {
	  if (e.fd==fd) {
	    e=e.changeSrcVar(dst);
	    if (!edgeset.contains(e))
	      edgeset.add(e);
	    else {
	      Edge preve=edgeset.get(e);
	      e=e.merge(preve);
	      edgeset.add(e);
	    }
	  }
	}
    }
    return edgeset;
  }

  static MySet<Edge> diffDereference(Delta delta, TempDescriptor dst, MySet<Edge> srcEdges, FieldDescriptor fd) {
    MySet<Edge> edgeset=new MySet<Edge>();
    for(Edge edge:srcEdges) {
      MySet<Edge> removeedges=delta.heapedgeremove.get(edge.dst);
      if (delta.baseheapedge.containsKey(edge.dst)) {
	for(Edge e:delta.baseheapedge.get(edge.dst)) {
	  if (e.fd==fd&&(removeedges==null||!removeedges.contains(e))) {
	    e=e.changeSrcVar(dst);
	    if (!edgeset.contains(e))
	      edgeset.add(e);
	    else {
	      Edge preve=edgeset.get(e);
	      e=e.merge(preve);
	      edgeset.add(e);
	    }
	  }
	}
      }
      if (delta.heapedgeadd.containsKey(edge.dst))
	for(Edge e:delta.heapedgeadd.get(edge.dst)) {
	  if (e.fd==fd) {
	    e=e.changeSrcVar(dst);
	    if (!edgeset.contains(e))
	      edgeset.add(e);
	    else {
	      Edge preve=edgeset.get(e);
	      e=e.merge(preve);
	      edgeset.add(e);
	    }
	  }
	}
    }
    return edgeset;
  }

  static HashSet<AllocNode> getNodes(Graph graph, Delta delta, HashSet<AllocNode> srcNodes, FieldDescriptor fd) {
    HashSet<AllocNode> nodes=new HashSet<AllocNode>();
    for(AllocNode node:srcNodes) {
      MySet<Edge> removeedges=delta.heapedgeremove.get(node);
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