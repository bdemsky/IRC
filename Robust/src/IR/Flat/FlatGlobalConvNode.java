package IR.Flat;
import IR.TypeDescriptor;
import Analysis.Locality.LocalityBinding;

public class FlatGlobalConvNode extends FlatNode {
  TempDescriptor src;
  LocalityBinding lb;
  boolean makePtr;
  boolean convert=true;
  FlatAtomicEnterNode faen;

  public FlatGlobalConvNode(TempDescriptor src, LocalityBinding lb, boolean makePtr) {
    this.src=src;
    this.lb=lb;
    this.makePtr=makePtr;
  }

  public FlatGlobalConvNode(TempDescriptor src, LocalityBinding lb, boolean makePtr, boolean doactualconvert) {
    this.src=src;
    this.lb=lb;
    this.makePtr=makePtr;
    this.convert=doactualconvert;
  }

  public FlatAtomicEnterNode getAtomicEnter() {
    return faen;
  }

  public void setAtomicEnter(FlatAtomicEnterNode faen) {
    this.faen=faen;
  }

  boolean doConvert() {
    return convert;
  }

  public String toString() {
    String str = "FlatGlobalConvNode_"+src.toString();
    if (makePtr)
      str += "=(PTR)";
    else
      str += "=(OID)";
    return str+src.toString()+" "+lb;
  }

  public int kind() {
    return FKind.FlatGlobalConvNode;
  }

  public LocalityBinding getLocality() {
    return lb;
  }

  public boolean getMakePtr() {
    return makePtr;
  }

  public TempDescriptor getSrc() {
    return src;
  }

  public TempDescriptor [] writesTemps() {
    return new TempDescriptor[] {src};
  }

  public TempDescriptor [] readsTemps() {
    if (!makePtr&&!convert)
      return new TempDescriptor[0];
    else
      return new TempDescriptor[] {src};
  }
}
