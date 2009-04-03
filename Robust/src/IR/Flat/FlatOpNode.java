package IR.Flat;
import java.util.Vector;
import IR.*;

public class FlatOpNode extends FlatNode {
  TempDescriptor dest;
  TempDescriptor left;
  TempDescriptor right;
  Operation op;

  public FlatOpNode(TempDescriptor dest, TempDescriptor left, TempDescriptor right, Operation op) {
    this.dest=dest;
    this.left=left;
    this.right=right;
    this.op=op;
  }

  public FlatNode clone(TempMap t) {
    return new FlatOpNode(t.tempMap(dest), t.tempMap(left), t.tempMap(right), op);
  }
  public void rewriteDef(TempMap t) {
    dest=t.tempMap(dest);
  }
  public void rewriteUse(TempMap t) {
    left=t.tempMap(left);
    right=t.tempMap(right);
  }

  public TempDescriptor getDest() {
    return dest;
  }

  public TempDescriptor getLeft() {
    return left;
  }

  public TempDescriptor getRight() {
    return right;
  }

  public Operation getOp() {
    return op;
  }

  public String toString() {
    String str = "FlatOpNode_"+dest.toString();
    if (right!=null)
      str += "="+left.toString()+op.toString()+right.toString();
    else if (op.getOp()==Operation.ASSIGN)
      str += " = "+left.toString();
    else
      str += " "+op.toString() +" "+left.toString();
    return str;
  }

  public int kind() {
    return FKind.FlatOpNode;
  }

  public TempDescriptor [] readsTemps() {
    if (right!=null)
      return new TempDescriptor [] {left,right};
    else
      return new TempDescriptor [] {left};
  }

  public TempDescriptor [] writesTemps() {
    return new TempDescriptor [] {dest};
  }
}
