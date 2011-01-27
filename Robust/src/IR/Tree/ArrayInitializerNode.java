package IR.Tree;
import java.util.Vector;

public class ArrayInitializerNode extends ExpressionNode {
  Vector varInitList;

  public ArrayInitializerNode(Vector vil) {
    varInitList=vil;
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
