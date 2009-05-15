/*the graph data structure*/
public class Graph {
  public int numVertices;
  public int numEdges;

  public int numDirectedEdges;
  public int numUndirectedEdges;

  public int numIntEdges;
  public int numStrEdges;

  public long[] outDegree;
  public long[] outVertexIndex;
  public long[] outVertexList;
  public long[] paralEdgeIndex;

  public long inDegree;
  public long inVertexIndex;
  public long inVertexList;

  public long[]  intWeight;
  public char[] strWeight;

  public Graph() {

  }
}
