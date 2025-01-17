package IR.Flat;
import IR.FieldDescriptor;

public class FlatElementNode extends FlatNode {
  TempDescriptor src;
  TempDescriptor dst;
  TempDescriptor index;

  public FlatElementNode(TempDescriptor src, TempDescriptor index, TempDescriptor dst) {
    this.index=index;
    this.src=src;
    this.dst=dst;
  }

  public boolean needsBoundsCheck() {
    return true;
  }

  public FlatNode clone(TempMap t) {
    return new FlatElementNode(t.tempMap(src), t.tempMap(index), t.tempMap(dst));
  }
  public void rewriteDef(TempMap t) {
    dst=t.tempMap(dst);
  }
  public void rewriteUse(TempMap t) {
    index=t.tempMap(index);
    src=t.tempMap(src);
  }

  public TempDescriptor getIndex() {
    return index;
  }

  public TempDescriptor getSrc() {
    return src;
  }

  public TempDescriptor getDst() {
    return dst;
  }

  public String toString() {
    return "FlatElementNode_"+dst.toString()+"="+src.toString()+"["+index.toString()+"]";
  }

  public int kind() {
    return FKind.FlatElementNode;
  }

  public TempDescriptor [] writesTemps() {
    return new TempDescriptor[] {dst};
  }

  public TempDescriptor [] readsTemps() {
    return new TempDescriptor[] {src,index};
  }
}
