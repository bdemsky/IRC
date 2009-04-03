package IR.Flat;
import IR.FieldDescriptor;

public class FlatSetElementNode extends FlatNode {
  TempDescriptor src;
  TempDescriptor dst;
  TempDescriptor index;

  public FlatSetElementNode(TempDescriptor dst, TempDescriptor index, TempDescriptor src) {
    this.index=index;
    this.src=src;
    this.dst=dst;
  }

  public FlatNode clone(TempMap t) {
    return new FlatSetElementNode(t.tempMap(dst), t.tempMap(index), t.tempMap(src));
  }
  public void rewriteUse(TempMap t) {
    src=t.tempMap(src);
    dst=t.tempMap(dst);
    index=t.tempMap(index);
  }
  public void rewriteDef(TempMap t) {
  }
  public boolean needsBoundsCheck() {
    return true;
  }

  public TempDescriptor getSrc() {
    return src;
  }

  public TempDescriptor getIndex() {
    return index;
  }

  public TempDescriptor getDst() {
    return dst;
  }

  public String toString() {
    return "FlatSetElementNode_"+dst.toString()+"["+index.toString()+"]="+src.toString();
  }

  public int kind() {
    return FKind.FlatSetElementNode;
  }

  public TempDescriptor [] readsTemps() {
    return new TempDescriptor [] {src,dst,index};
  }
}
