package IR.Tree;
import IR.TypeDescriptor;
import IR.TypeUtil;


public class LiteralNode extends ExpressionNode {
  public final static int INTEGER=1;
  public final static int FLOAT=2;
  public final static int BOOLEAN=3;
  public final static int CHAR=4;
  public final static int STRING=5;
  public final static int NULL=6;

  Object value;
  TypeDescriptor type;
  String typestr;

  public LiteralNode(String type, Object o) {
    typestr=type;
    value=o;
    type=null;
  }

  public String getTypeString() {
    return typestr;
  }

  public TypeDescriptor getType() {
    return type;
  }

  public void setType(TypeDescriptor td) {
    type=td;
  }

  public Object getValue() {
    return value;
  }

  public String printNode(int indent) {
    if (typestr.equals("null"))
      return "null";
    if (typestr.equals("string")) {
      return '"'+escapeString(value.toString())+'"';
    }
    return "/*"+typestr+ "*/"+value.toString();
  }
  private static String escapeString(String st) {
    String new_st="";
    for(int i=0; i<st.length(); i++) {
      char x=st.charAt(i);
      if (x=='\n')
	new_st+="\\n";
      else if (x=='"')
	new_st+="'"+'"'+"'";
      else new_st+=x;
    }
    return new_st;
  }
  public int kind() {
    return Kind.LiteralNode;
  }

  public Long evaluate() {
    eval = null;
    if(this.type.isChar() || this.type.isInt()) {
      eval = Long.parseLong(this.value.toString());
    }
    return eval;
  }
}
