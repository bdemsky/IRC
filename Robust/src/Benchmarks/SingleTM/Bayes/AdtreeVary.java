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
      //adtree_vary_t* varyPtr;

      //varyPtr = (adtree_vary_t*)malloc(sizeof(adtree_vary_t));
      if (varyPtr != null) {
        varyPtr.index = index;
        varyPtr.mostCommonValue = -1;
        varyPtr.zeroNodePtr = null;
        varyPtr.oneNodePtr = null;
      }

      return varyPtr;
    }

}
