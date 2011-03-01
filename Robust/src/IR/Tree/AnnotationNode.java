package IR.Tree;

public class AnnotationNode extends TreeNode {
  //currently it only supports marker annotation that have no variables.
  String name;
  
  public AnnotationNode(String name){
    //constructor for marker annotation
    this.name=name;
  }
  
}
