package IR.Flat;
import IR.TypeDescriptor;

public class FlatGenDefReachNode extends FlatNode {
  String outputName;

  public FlatGenDefReachNode(String outputName) {
    this.outputName = outputName;
  }

  public String getOutputName() {
    return outputName;
  }

  public FlatNode clone(TempMap t) {
    return new FlatGenDefReachNode(outputName);
  }
  public void rewriteUse(TempMap t) {
  }
  public void rewriteDst(TempMap t) {
  }


  public String toString() {
    return "FlatGenDefReachNode_"+outputName;
  }

  public int kind() {
    return FKind.FlatGenDefReachNode;
  }

  public TempDescriptor [] writesTemps() {
    return new TempDescriptor[0];
  }
}
