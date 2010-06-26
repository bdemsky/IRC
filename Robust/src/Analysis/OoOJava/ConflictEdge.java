package Analysis.OoOJava;

public class ConflictEdge {

  private ConflictNode u;
  private ConflictNode v;
  private int type;

  public ConflictEdge(ConflictNode u, ConflictNode v, int type) {
    this.u = u;
    this.v = v;
    this.type = type;
  }

  public String toGraphEdgeString() {
    if (type == ConflictGraph.FINE_GRAIN_EDGE) {
      return "\"F_CONFLICT\"";
    } else if (type == ConflictGraph.COARSE_GRAIN_EDGE) {
      return "\"C_CONFLICT\"";
    } else {
      return "CONFLICT\"";
    }
  }

  public ConflictNode getVertexU() {
    return u;
  }

  public ConflictNode getVertexV() {
    return v;
  }
  public int getType() {
    return type;
  }
  
  public boolean isCoarseEdge(){
    if(type==ConflictGraph.COARSE_GRAIN_EDGE){
      return true;
    }
    return false;
  }

  public String toString() {
    return getVertexU() + "-" + getVertexV();
  }

}
