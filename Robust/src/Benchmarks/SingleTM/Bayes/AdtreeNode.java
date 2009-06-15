public class AdtreeNode {
  int index;
  int value;
  int count;
  Vector_t varyVectorPtr;

  public AdtreeNode() {

  }

  /* =============================================================================
   * allocNode
   * =============================================================================
   */
  public static AdtreeNode
    allocNode (int index)
    {
      AdtreeNode nodePtr = new AdtreeNode();
      //adtree_node_t* nodePtr;

      //nodePtr = (adtree_node_t*)malloc(sizeof(adtree_node_t));
      if (nodePtr != null) {
        //nodePtr.varyVectorPtr = vector_alloc(1);
        nodePtr.varyVectorPtr = Vector_t.vector_alloc(1);
        if (nodePtr.varyVectorPtr == null) {
          nodePtr = null;
          //free(nodePtr);
          return null;
        }
        nodePtr.index = index;
        nodePtr.value = -1;
        nodePtr.count = -1;
      }

      return nodePtr;
    }
}
