package IR.Flat;

public class FlatCheckNode extends FlatNode {
  TempDescriptor [] temps;
  String [] vars;
  String spec;

  public FlatCheckNode(String spec, String[] vars, TempDescriptor[] temps) {
    this.spec=spec;
    this.vars=vars;
    this.temps=temps;
  }

  public int kind() {
    return FKind.FlatCheckNode;
  }

  public String getSpec() {
    return spec;
  }

  public String[] getVars() {
    return vars;
  }

  public TempDescriptor [] getTemps() {
    return temps;
  }

  public TempDescriptor [] readsTemps() {
    return temps;
  }
}
