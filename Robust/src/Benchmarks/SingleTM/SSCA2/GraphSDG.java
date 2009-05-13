public class GraphSDG {
  public long[] startVertex;
  public long[] endVertex;
  public long[] intWeight;

  /* The idea is to store the index of the string weights (as a negative value)
   * in the long Weight array. A negative value because we need to sort on
   * the intWeights in Kernel 2. Hence the long long
   */
  public char[] strWeight;
  public int numEdgesPlaced;

  public GraphSDG() {

  }
}
