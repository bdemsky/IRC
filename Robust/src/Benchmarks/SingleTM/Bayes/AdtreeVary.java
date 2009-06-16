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
  public AdtreeVary
    allocVary (int index)
    {
      AdtreeVary varyPtr= new AdtreeVary();

      if (varyPtr != null) {
        varyPtr.index = index;
        varyPtr.mostCommonValue = -1;
        varyPtr.zeroNodePtr = null;
        varyPtr.oneNodePtr = null;
      }

      return varyPtr;
    }

}
