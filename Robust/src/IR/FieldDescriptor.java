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

  public FieldDescriptor(Modifiers m, TypeDescriptor t, String identifier, ExpressionNode e, boolean isglobal) {
    super(identifier);
    this.modifier=m;
    this.td=t;
    this.en=e;
    this.safename = "___" + name + "___";
    this.uniqueid=count++;
    this.isglobal=isglobal;
    if (en!=null) throw new Error("Field initializers not implemented");
  }

  public boolean isGlobal() {
    return isglobal;
  }

  public TypeDescriptor getType() {
    return td;
  }

  public boolean equals(Object o) {
    if( o == null ) return false;
    if( !(o instanceof FieldDescriptor) ) return false;

    FieldDescriptor fd = (FieldDescriptor) o;
    if( modifier == null && fd.modifier != null ) return false;
    if( modifier != null && !modifier.equals(fd.modifier) ) return false;
    if( !(td == fd.td) ) return false;
    if( !(en == fd.en) ) return false;
    if( !isglobal == fd.isglobal ) return false;
    if( identifier == null && fd.identifier != null ) return false;
    if( identifier != null && !identifier.equals(fd.identifier) ) return false;
    return true;
  }

  public int hashCode() {
    int hashCode = modifier.hashCode();
    hashCode += td.hashCode();
    if( en != null ) hashCode += 81;
    if( isglobal ) hashCode += 11;
    if( identifier != null ) hashCode += identifier.hashCode();
    return hashCode;
  }

  public String toString() {
    if (en==null)
      return modifier.toString()+td.toString()+" "+getSymbol()+";";
    else
      return modifier.toString()+td.toString()+" "+getSymbol()+"="+en.printNode(0)+";";
  }

  public String toStringBrief() {
    return td.toString()+" "+getSymbol();
  }
}
