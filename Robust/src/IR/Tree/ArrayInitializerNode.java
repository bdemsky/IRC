package IR.Tree;
import java.util.Vector;
import IR.TypeDescriptor;
import IR.MethodDescriptor;

public class ArrayInitializerNode extends ExpressionNode {
  TypeDescriptor td;
  Vector varInitList;

  public ArrayInitializerNode(TypeDescriptor type, Vector vil) {
    td=type;
    varInitList=vil;
  }

  public TypeDescriptor getType() {
    return td;
  }

  public int numVarInitializers() {
    return varInitList.size();
  }

  public ExpressionNode getVarInitializer(int i) {
    return (ExpressionNode) varInitList.get(i);
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
}
