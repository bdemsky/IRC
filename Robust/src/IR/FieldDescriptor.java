package IR;
import IR.Tree.Modifiers;
import IR.Tree.ExpressionNode;

/**
 * Descriptor
 *
 * represents a symbol in the language (var name, function name, etc).
 */

public class FieldDescriptor extends Descriptor {

  public static FieldDescriptor arrayLength=new FieldDescriptor(new Modifiers(Modifiers.PUBLIC|Modifiers.FINAL), new TypeDescriptor(TypeDescriptor.INT), "length", null, false);

  protected Modifiers modifier;
  protected TypeDescriptor td;
  protected String identifier;
  protected ExpressionNode en;
  private boolean isglobal;
  private boolean isenum;
  private int enumvalue;
  
  private ClassDescriptor cn;

  public FieldDescriptor(Modifiers m, TypeDescriptor t, String identifier, ExpressionNode e, boolean isglobal) {
    super(identifier);
    this.modifier=m;
    this.td=t;
    this.en=e;
    this.safename = "___" + name + "___";
    this.uniqueid=count++;
    this.isglobal=isglobal;
    this.isenum = false;
    this.enumvalue = -1;
  }
  
  public ClassDescriptor getClassDescriptor() {
    return this.cn;
  }
  
  public void setClassDescriptor(ClassDescriptor cn) {
    this.cn = cn;
  }

  public String getSafeSymbol() {
    if (isStatic()) {
      return cn.getSafeSymbol()+safename;
    } else
      return safename;
  }
  
  public boolean isEnum() {
    return this.isenum;
  }
  
  public int enumValue() {
    return this.enumvalue;
  }
  
  public void setAsEnum() {
    this.isenum = true;
  }
  
  public void setEnumValue(int value) {
    this.enumvalue = value;
  }

  public ExpressionNode getExpressionNode(){
      return en;
  }

  public boolean isFinal() {
    return modifier.isFinal();
  }
  
  public boolean isStatic() {
    return modifier.isStatic();
  }
  
  public boolean isVolatile() {
    return modifier.isVolatile();
  }
  
  public boolean isGlobal() {
    return isglobal;
  }

  public TypeDescriptor getType() {
    return td;
  }

	public void changeSafeSymbol(int id) {
		safename+=id;
	}
	
  public String toString() {
    if (en==null)
      return modifier.toString()+td.toString()+" "+getSymbol()+";";
    else
      return modifier.toString()+td.toString()+" "+getSymbol()+"="+en.printNode(0)+";";
  }

  public String toStringBrief() {
    return td.toPrettyString()+" "+getSymbol();
  }

  public String toPrettyStringBrief() {
    return td.toPrettyString()+" "+getSymbol();
  }
}
