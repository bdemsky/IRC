/**
 * Author: Alokika Dash
 * University of California, Irvine
 * adash@uci.edu
 *
 * - Helper class of Adtree.java
 **/

public class AdtreeVary {
  int index;
  int mostCommonValue;
  AdtreeNode zeroNodePtr;
  AdtreeNode oneNodePtr;

  public AdtreeVary() {
  }

  /* =============================================================================
   * allocVary
   * =============================================================================
   */
  public AdtreeVary(int index) {
    this.index = index;
    mostCommonValue = -1;
  }

  public void free_vary() {
    zeroNodePtr=null;
    oneNodePtr=null;
  }
}
