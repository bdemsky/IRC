package IR.Tree;
import java.util.Vector;
import IR.TypeDescriptor;
import IR.MethodDescriptor;
import IR.Tree.ExpressionNode;

public class CreateObjectNode extends ExpressionNode {
  TypeDescriptor td;
  Vector argumentlist;
  MethodDescriptor md;
  FlagEffects fe;
  boolean isglobal;
  String disjointId;
  ArrayInitializerNode ain;
//The next 2 are unused but i will delete them once i am fully done with inner classes.
  TypeDescriptor surroundingClass;
  boolean isCreatedFromSurroundingClassName;
  
  ExpressionNode surroundingClassObject;
  boolean isSurroundingClassExpSet;

  public CreateObjectNode(TypeDescriptor type, boolean isglobal, String disjointId) {
    td=type;
   // surroundingClass = new TypeDescriptor("becauseTDdoesnthavedefaultconstructor");
    argumentlist=new Vector();
    this.isglobal=isglobal;
    this.disjointId=disjointId;
    this.ain = null;
    isSurroundingClassExpSet = false;
  }

  public boolean isGlobal() {
    return isglobal;
  }

  public String getDisjointId() {
    return disjointId;
  }

  public void addFlagEffects(FlagEffects fe) {
    this.fe=fe;
  }

  public FlagEffects getFlagEffects() {
    return fe;
  }

  public void addArgument(ExpressionNode en) {
    argumentlist.add(en);
  }

  public void setConstructor(MethodDescriptor md) {
    this.md=md;
  }

  public MethodDescriptor getConstructor() {
    return md;
  }

  public TypeDescriptor getType() {
    return td;
  }

  public int numArgs() {
    return argumentlist.size();
  }

  public ExpressionNode getArg(int i) {
    return (ExpressionNode) argumentlist.get(i);
  }

  public void addArrayInitializer(ArrayInitializerNode ain) {
    this.ain = ain;
  }

  public ArrayInitializerNode getArrayInitializer() {
    return this.ain;
  }

  public String printNode(int indent) {
    String st;
    boolean isarray=td.isArray();
    if (isarray)
      st="new "+td.toString()+"[";
    else
      st="new "+td.toString()+"(";
    for(int i=0; i<argumentlist.size(); i++) {
      ExpressionNode en=(ExpressionNode)argumentlist.get(i);
      st+=en.printNode(indent);
      if ((i+1)!=argumentlist.size()) {
        if (isarray)
          st+="][";
        else
          st+=", ";
      }
    }
    if (isarray)
      st += "]";
    else
      st += ")";
    if(isarray && this.ain != null) {
      st += "{";
      st += this.ain.printNode(indent);
      st += "}";
    }
    return st;
  }

  public int kind() {
    return Kind.CreateObjectNode;
  }

  public Long evaluate() {
    eval = null;
    return eval; //null;
  }

  public void setSurroundingExpression( ExpressionNode en ) {
	
	//System.out.println( "The expression node is : " + en );
  	surroundingClassObject = en ;
	//System.out.println( "The expression node is : " + surroundingClassObject );
	isSurroundingClassExpSet = true;
  }
  
  public ExpressionNode getSurroundingClassExpression() {
  	if( false == isSurroundingClassExpSet )
		return null;
	return surroundingClassObject;
  }
}
