/*
Lonestar Benchmark Suite for irregular applications that exhibit 
amorphous data-parallelism.

Center for Grid and Distributed Computing
The University of Texas at Austin

Copyright (C) 2007, 2008, 2009 The University of Texas at Austin

Licensed under the Eclipse Public License, Version 1.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.eclipse.org/legal/epl-v10.html

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

File: ArrayIndexedGraph.java 

*/

public class ArrayIndexedGraph {
  protected HashSet nodes;
  protected final int numberOfNeighbors;

  public ArrayIndexedGraph(int numberOfNeighbors) {
//    this.nodes = Collections.synchronizedSet(new HashSet());
    this.nodes=new HashSet();
    this.numberOfNeighbors = numberOfNeighbors;
  }


  public ArrayIndexedNode createNode(OctTreeNodeData data) {
    ArrayIndexedNode node = new ArrayIndexedNode(data,numberOfNeighbors);
    return node;
  }


  public boolean addNode(ArrayIndexedNode node) {
    return nodes.add(node);
  }


  public boolean removeNode(ArrayIndexedNode node) {
    ArrayIndexedNode inode = (ArrayIndexedNode) node;
    ArrayIndexedNode[] neighbors = inode.neighbors;
    for(int i=0;i<inode.neighbors.length;i++){
      neighbors[i]=null;
    }    
    return nodes.remove(node);
  }


  public boolean containsNode(ArrayIndexedNode n) {
    return nodes.contains(n);
  }


  public ArrayIndexedNode getRandom() {
    return (ArrayIndexedNode)nodes.iterator().next();
  }


  public Iterator iterator() {
    return nodes.iterator();
  }

  // This method is not supported in an IndexedGraph because it is
  // not clear which neighbor the added neighbor should become.

  public boolean addNeighbor(ArrayIndexedNode src, ArrayIndexedNode dst) {
//    throw new UnsupportedOperationException(
//        "ArrayIndexedGraph.addNeighbor(Node<NodeData>, Node<NodeData>) unimplemented");
      System.out.println("This method is not supported in an IndexedGraph");
      System.exit(0);
  }

  protected final int neighborIndex(ArrayIndexedNode src, ArrayIndexedNode dst) {
    ArrayIndexedNode isrc = (ArrayIndexedNode) src;
    ArrayIndexedNode[] neighbors = isrc.neighbors;
    
    for(int i=0;i<neighbors.length;i++){
      if(neighbors[i].equals(dst)){
        return i;
      }
    }
    
    return 0;    
//    List neighborList = Arrays.asList(neighbors);
//    return neighborList.indexOf(dst);
  }


  public boolean removeNeighbor(ArrayIndexedNode src, ArrayIndexedNode dst) {
    int idx = neighborIndex(src, dst);
    if (0 <= idx) {
      ArrayIndexedNode isrc = (ArrayIndexedNode) src;
      isrc.neighbors[idx] = null;
      return true;
    }
    return false;
  }


  public boolean hasNeighbor(ArrayIndexedNode src, ArrayIndexedNode dst) {
    return 0 <= neighborIndex(src, dst);
  }


  public HashSet getInNeighbors(ArrayIndexedNode node) {
//    throw new UnsupportedOperationException("ArrayIndexedGraph.getInNeighbors(Node<NodeData>) unimplemented");
    System.out.println("UnsupportedOperationException(ArrayIndexedGraph.getInNeighbors(Node<NodeData>) unimplemented");
    System.exit(0);
  }


  public HashSet getOutNeighbors(ArrayIndexedNode node) {
    ArrayIndexedNode inode = (ArrayIndexedNode) node;
    HashSet result=new HashSet();
    for(int i=0; i<inode.neighbors.length ; i++){
      ArrayIndexedNode element=inode.neighbors[i];
      result.add(element);
    }
    return result;
//    return (Collection) Collections.unmodifiableList(Arrays.asList(inode.neighbors));
  }


  public int getNumNodes() {
    return nodes.size();
  }

 
  public void setNeighbor(ArrayIndexedNode src, int idx, ArrayIndexedNode dst) {
    ArrayIndexedNode isrc = (ArrayIndexedNode) src;
    isrc.neighbors[idx] = (ArrayIndexedNode) dst;
  }

 
  public ArrayIndexedNode getNeighbor(ArrayIndexedNode node, int idx) {
    ArrayIndexedNode inode = (ArrayIndexedNode) node;
    return inode.neighbors[idx];
  }


  public void removeNeighbor(ArrayIndexedNode node, int idx) {
    ArrayIndexedNode inode = (ArrayIndexedNode) node;
    inode.neighbors[idx] = null;
  }


  public OctTreeNodeData setNodeData(ArrayIndexedNode node, OctTreeNodeData new_data) {
    ArrayIndexedNode ainode = (ArrayIndexedNode) node;
    OctTreeNodeData old_data = (OctTreeNodeData) ainode.data;
    ainode.data = new_data;
    return old_data;
  }

 
  public OctTreeNodeData getNodeData(ArrayIndexedNode node) {
    return ((ArrayIndexedNode) node).data;
  }
 
  public boolean isDirected() {
    return true;
  }
  
  
  
}
