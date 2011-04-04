public class GraphEdge extends Edge_d {
  protected EdgeGraphNode src;
  protected EdgeGraphNode dest;
  protected Object d;

  public GraphEdge(Object d) {
    super();
    this.d = d;
  }

  public GraphEdge(EdgeGraphNode src, EdgeGraphNode dest, Object d) {
    this(d);
    this.src = src;
    this.dest = dest;
  }

  protected final EdgeGraphNode getOpposite(EdgeGraphNode n) {
    return n != src ? src : dest;
  }

  protected final EdgeGraphNode getSrc() {
    return src;
  }

  protected final EdgeGraphNode getDest() {
    return dest;
  }
}