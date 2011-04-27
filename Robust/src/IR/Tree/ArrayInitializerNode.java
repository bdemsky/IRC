package IR.Tree;
import java.util.Vector;

import IR.TypeDescriptor;

public class ArrayInitializerNode extends ExpressionNode {
  TypeDescriptor type;
  Vector varInitList;

  public ArrayInitializerNode(Vector vil) {
    this.type = null;
    varInitList=vil;
  }

  public int numVarInitializers() {
    return varInitList.size();
  }

  public ExpressionNode getVarInitializer(int i) {
    return (ExpressionNode) varInitList.get(i);
  }

  public void setType(TypeDescriptor type) {
    this.type = type;
  }

  public TypeDescriptor getType() {
    return this.type;
  }

  public String printNode(int indent) {
    String st="{";
    for(int i=0; i<varInitList.size(); i++) {
      ExpressionNode en=(ExpressionNode)varInitList.get(i);
      st+=en.printNode(indent);
      if ((i+1)!=varInitList.size()) {
	st+=", ";
      }
    }
    return st+"}";
  }

  public int kind() {
    return Kind.ArrayInitializerNode;
  }

  public Long evaluate() {
    eval = null;
    return eval; //null;
  }
}
