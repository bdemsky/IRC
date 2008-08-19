package IR.Flat;

public class NodePair {
  FlatNode begin;
  FlatNode end;

  public NodePair(FlatNode begin, FlatNode end) {
    this.begin=begin;
    this.end=end;
  }

  public FlatNode getBegin() {
    return begin;
  }

  public FlatNode getEnd() {
    return end;
  }
}
