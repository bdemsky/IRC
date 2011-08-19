package IR.Tree;

public class LoopNode extends BlockStatementNode {
  BlockNode initializer;
  ExpressionNode condition;
  BlockNode update;
  BlockNode body;
  int type=0;
  public static int FORLOOP=1;
  public static int WHILELOOP=2;
  public static int DOWHILELOOP=3;
  String label=null;

  public LoopNode(BlockNode initializer,ExpressionNode condition, BlockNode update, BlockNode body, String label) {
    this.initializer=initializer;
    this.condition=condition;
    this.update=update;
    this.body=body;
    initializer.setStyle(BlockNode.EXPRLIST);
    update.setStyle(BlockNode.EXPRLIST);
    type=FORLOOP;
    this.label=label;
  }

  public LoopNode(ExpressionNode condition, BlockNode body, int type, String label) {
    this.condition=condition;
    this.body=body;
    this.type=type;
    this.label=label;
  }

  public BlockNode getInitializer() {
    return initializer;
  }

  public ExpressionNode getCondition() {
    return condition;
  }

  public BlockNode getUpdate() {
    return update;
  }

  public BlockNode getBody() {
    return body;
  }

  public String printNode(int indent) {
    if (type==FORLOOP) {
      return "for("+initializer.printNode(0)+";"+condition.printNode(0)+
             ";"+update.printNode(0)+") "+body.printNode(indent)+"\n";
    } else if (type==WHILELOOP) {
      return "while("+condition.printNode(0)+") "+body.printNode(indent+INDENT)+"\n";
    } else if (type==DOWHILELOOP) {
      return "do "+ body.printNode(indent+INDENT)+
             "while("+condition.printNode(0)+")\n";
    } else throw new Error();
  }

  public int getType() {
    return type;
  }

  public int kind() {
    return Kind.LoopNode;
  }
  
  public void setLabel(String l) {
    label=l;
  }

  public String getLabel() {
    return label;
  }
}
