/**
 * Author: Alokika Dash
 * University of California, Irvine
 * adash@uci.edu
 *
 * - Helper class of Adtree.java
 **/
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

      if (nodePtr != null) {
        nodePtr.varyVectorPtr = Vector_t.vector_alloc(1);
        if (nodePtr.varyVectorPtr == null) {
          nodePtr = null;
          return null;
        }
        nodePtr.index = index;
        nodePtr.value = -1;
        nodePtr.count = -1;
      }

      return nodePtr;
    }
}
