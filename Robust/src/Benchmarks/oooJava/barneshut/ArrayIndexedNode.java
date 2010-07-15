public class ArrayIndexedNode extends Node {
  protected OctTreeNodeData data;
  protected ArrayIndexedNode[] neighbors;

  public ArrayIndexedNode(OctTreeNodeData data, int numberOfNeighbors) {
    this.data = data;
//    neighbors = (ArrayIndexedNode[]) Array.newInstance(this.getClass(), numberOfNeighbors);
    neighbors = new ArrayIndexedNode[numberOfNeighbors];
    for(int i=0;i<neighbors.length;i++){
      neighbors[i]=null;
    }
//    Arrays.fill(neighbors, null);
  }


  public OctTreeNodeData getData() {
    return  data;
  }


  public OctTreeNodeData setData(OctTreeNodeData node) {
    return this.data=node;
  }
  
}