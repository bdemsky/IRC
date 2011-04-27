package IR.Tree;
import IR.Operation;
import IR.TypeDescriptor;

public class OpNode extends ExpressionNode {
  ExpressionNode left;
  ExpressionNode right;
  Operation op;
  TypeDescriptor td;
  TypeDescriptor lefttype;
  TypeDescriptor righttype;

  public OpNode(ExpressionNode l, ExpressionNode r, Operation o) {
    left=l;
    right=r;
    op=o;
  }

  public OpNode(ExpressionNode l, Operation o) {
    left=l;
    right=null;
    op=o;
  }

  public ExpressionNode getLeft() {
    return left;
  }

  public ExpressionNode getRight() {
    return right;
  }

  public Operation getOp() {
    return op;
  }

  public String printNode(int indent) {
    if (right==null)
      return op.toString()+"("+left.printNode(indent)+")";
    else
      return left.printNode(indent)+" "+op.toString()+" "+right.printNode(indent);
  }

  public void setLeftType(TypeDescriptor argtype) {
    this.lefttype=argtype;
  }

  public TypeDescriptor getLeftType() {
    return lefttype;
  }

  public void setRightType(TypeDescriptor argtype) {
    this.righttype=argtype;
  }

  public TypeDescriptor getRightType() {
    return righttype;
  }

  public TypeDescriptor getType() {
    return td;
  }

  public void setType(TypeDescriptor td) {
    this.td=td;
  }

  public int kind() {
    return Kind.OpNode;
  }

  public Long evaluate() {
    eval = null;
    Long l = this.left.evaluate();
    if(l != null) {
      if (this.op.getOp() == Operation.LOGIC_NOT)
        eval = Long.valueOf(l.longValue() > 0?0:1);
      else if (this.op.getOp() == Operation.COMP)
        eval = Long.valueOf((long)(~l.longValue()));
      else if (this.op.getOp() == Operation.UNARYMINUS)
        eval = Long.valueOf(-l.longValue() );
      else if (this.op.getOp() == Operation.UNARYPLUS)
        eval = Long.valueOf(+l.longValue());
      else {
        Long r = this.right.evaluate();
        if(r != null) {
          //if (this.op.getOp() == Operation.LOGIC_OR)
          //  return Long.valueOf((long)(l.longValue() || r.longValue()));
          //else if (this.op.getOp() == Operation.LOGIC_AND)
          //  return Long.valueOf((long)(l.longValue() && r.longValue()));
          /*else */ if (this.op.getOp() == Operation.BIT_OR)
            eval = Long.valueOf(l.longValue() | r.longValue());
          else if (this.op.getOp() == Operation.BIT_XOR)
            eval = Long.valueOf(l.longValue() ^ r.longValue());
          else if (this.op.getOp() == Operation.BIT_AND)
            eval = Long.valueOf(l.longValue() & r.longValue());
          else if (this.op.getOp() == Operation.EQUAL)
            eval = Long.valueOf((l.longValue() == r.longValue())?1:0);
          else if (this.op.getOp() == Operation.NOTEQUAL)
            eval = Long.valueOf((l.longValue() != r.longValue())?1:0);
          else if (this.op.getOp() == Operation.LT)
            eval = Long.valueOf((l.longValue() < r.longValue())?1:0);
          else if (this.op.getOp() == Operation.GT)
            eval = Long.valueOf((l.longValue() > r.longValue())?1:0);
          else if (this.op.getOp() == Operation.LTE)
            eval = Long.valueOf((l.longValue() <= r.longValue())?1:0);
          else if (this.op.getOp() == Operation.GTE)
            eval = Long.valueOf((l.longValue() >= r.longValue())?1:0);
          else if (this.op.getOp() == Operation.LEFTSHIFT)
            eval = Long.valueOf(l.longValue() << r.longValue());
          else if (this.op.getOp() == Operation.RIGHTSHIFT)
            eval = Long.valueOf(l.longValue() >> r.longValue());
          else if (this.op.getOp() == Operation.URIGHTSHIFT)
            eval = Long.valueOf(l.longValue() >>> r.longValue());
          else if (this.op.getOp() == Operation.SUB)
            eval = Long.valueOf(l.longValue() - r.longValue());
          else if (this.op.getOp() == Operation.ADD)
            eval = Long.valueOf(l.longValue() + r.longValue());
          else if (this.op.getOp() == Operation.MULT)
            eval = Long.valueOf(l.longValue() * r.longValue());
          else if (this.op.getOp() == Operation.DIV)
            eval = Long.valueOf(l.longValue() / r.longValue());
          else if (this.op.getOp() == Operation.MOD)
            eval = Long.valueOf(l.longValue() % r.longValue());
          else if (this.op.getOp() == Operation.ASSIGN)
            eval = Long.valueOf(r.longValue());
        }
      }
    }
    return eval;
  }
}
