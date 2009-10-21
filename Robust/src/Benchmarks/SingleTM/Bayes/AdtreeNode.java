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
  AdtreeVary varyVectorPtr[];

  public AdtreeNode() {

  }

  /* =============================================================================
   * allocNode
   * =============================================================================
   */
  public AdtreeNode(int index, int vecsize) {
    this.varyVectorPtr = new AdtreeVary[vecsize];
    this.index = index;
    this.value = -1;
    this.count = -1;
  }
}
