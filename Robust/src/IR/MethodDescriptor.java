package IR;
import IR.Tree.Modifiers;
import IR.Tree.ExpressionNode;
import java.util.Vector;

/**
 * Descriptor
 *
 * represents a symbol in the language (var name, function name, etc).
 */

public class MethodDescriptor extends Descriptor {

  protected Modifiers modifier;
  protected TypeDescriptor returntype;
  protected String identifier;
  protected Vector params;
  protected SymbolTable paramtable;
  protected ClassDescriptor cd;
  protected VarDescriptor thisvd;
  protected boolean isglobal;
  protected boolean isstaticblock;  // flag to indicate if this is a static block
  protected boolean isinvokedbystatic;  // flag to indicate if this method is invoked by some static block

  protected boolean isdefaultconstructor; // flag to indicate if this is a default constructor
  
  private int lineNum;


  public MethodDescriptor(Modifiers m, TypeDescriptor rt, String identifier) {
    super(identifier);
    this.modifier=m;
    this.returntype=rt;
    this.identifier=identifier;
    this.safename = "___" + name + "___";
    this.uniqueid=count++;
    params=new Vector();
    paramtable=new SymbolTable();
    thisvd=null;
    isstaticblock = false;
    this.isinvokedbystatic = false;
    this.isdefaultconstructor = false;
  }

  public Modifiers getModifiers() {
    return modifier;
  }

  public boolean matches(MethodDescriptor md) {
    /* Check the name */
    if (!identifier.equals(md.identifier))
      return false;
    if (numParameters()!=md.numParameters())
      return false;
    for(int i=0; i<numParameters(); i++) {
      Descriptor d1=getParameter(i);
      Descriptor d2=md.getParameter(i);
      TypeDescriptor td1=(d1 instanceof TagVarDescriptor)?((TagVarDescriptor)d1).getType():((VarDescriptor)d1).getType();
      TypeDescriptor td2=(d2 instanceof TagVarDescriptor)?((TagVarDescriptor)d2).getType():((VarDescriptor)d2).getType();
      if (!td1.equals(td2))
        return false;
    }
    return true;
  }

  public MethodDescriptor(Modifiers m, String identifier) {
    this(m, identifier, false);
  }

  public MethodDescriptor(Modifiers m, String identifier, boolean isglobal) {
    super(identifier);
    this.isglobal=isglobal;
    this.modifier=m;
    this.returntype=null;
    this.identifier=identifier;
    this.safename = "___" + name + "___";
    this.uniqueid=count++;
    params=new Vector();
    paramtable=new SymbolTable();
    thisvd=null;
    isstaticblock = false;
  }


  public boolean isGlobal() {
    return isglobal;
  }

  public boolean isStaticBlock() {
    return isstaticblock;
  }

  public void setAsStaticBlock() {
    isstaticblock = true;
  }

  public boolean isInvokedByStatic() {
    return this.isinvokedbystatic;
  }

  public void setIsInvokedByStatic(boolean isinvokedbystatic) {
    this.isinvokedbystatic = isinvokedbystatic;
  }

  public void setThis(VarDescriptor vd) {
    thisvd=vd;
    paramtable.add(vd);
  }

  public VarDescriptor getThis() {
    return thisvd;
  }

  public String getClassMethodName() {
    return cd+"."+name;
  }

  public String getSafeMethodDescriptor() {
    String st="";
    for(int i=0; i<numParameters(); i++) {
      st+=getParamType(i).getSafeDescriptor();
      if ((i+1)<numParameters())
        st+="_";
    }
    return st;
  }

  public boolean isStatic() {
    return modifier.isStatic();
  }

  public boolean isAbstract() {
    return modifier.isAbstract();
  }

  public boolean isConstructor() {
    return (returntype==null) && !isstaticblock;
  }

  public TypeDescriptor getReturnType() {
    return returntype;
  }

  public void setClassDesc(ClassDescriptor cd) {
    this.cd=cd;
  }

  public ClassDescriptor getClassDesc() {
    return cd;
  }

  public SymbolTable getParameterTable() {
    return paramtable;
  }

  public void addParameter(TypeDescriptor type, String paramname) {
    if (paramname.equals("this"))
      throw new Error("Can't have parameter named this");
    VarDescriptor vd=new VarDescriptor(type, paramname);

    params.add(vd);
    if (paramtable.getFromSameScope(paramname)!=null) {
      throw new Error("Parameter "+paramname+" already defined");
    }
    paramtable.add(vd);
  }

  public void addTagParameter(TypeDescriptor type, String paramname) {
    if (paramname.equals("this"))
      throw new Error("Can't have parameter named this");
    TagVarDescriptor vd=new TagVarDescriptor(null, paramname);

    params.add(vd);
    if (paramtable.getFromSameScope(paramname)!=null) {
      throw new Error("Parameter "+paramname+" already defined");
    }
    paramtable.add(vd);
  }

  public int numParameters() {
    return params.size();
  }

  public Descriptor getParameter(int i) {
    return (Descriptor) params.get(i);
  }

  public String getParamName(int i) {
    return ((Descriptor)params.get(i)).getSymbol();
  }

  public TypeDescriptor getParamType(int i) {
    Descriptor d=(Descriptor)params.get(i);
    if (d instanceof VarDescriptor)
      return ((VarDescriptor)params.get(i)).getType();
    else if (d instanceof TagVarDescriptor)
      return new TypeDescriptor(TypeDescriptor.TAG);
    else throw new Error();
  }

  public String getSafeSymbol() {
    return safename.replace(".","___________").replace("$","___DOLLAR___");
  }

  public String toString() {
    String st="";
    String type="";
    if (cd!=null)
      type=cd+".";
    if (returntype!=null)
      st=modifier.toString()+returntype.toString()+" "+type+identifier+"(";
    else
      st=modifier.toString()+" "+type+identifier+"(";
    for(int i=0; i<params.size(); i++) {
      st+=getParamType(i)+" "+getParamName(i);
      if ((i+1)!=params.size())
        st+=", ";
    }
    st+=")";
    return st;
  }

  public boolean isDefaultConstructor() {
    return this.isdefaultconstructor;
  }

  public void setDefaultConstructor() {
    this.isdefaultconstructor = true;
  }
  
  public String getMethodDeclaration(){
    String st="";
    String type="";
      if (returntype != null)
        st = modifier.toString() + returntype.toString() + " " + type + identifier + "(";
      else
        st = modifier.toString() + identifier + "(";
      for (int i = 0; i < params.size(); i++) {
        st += getParamType(i) + " " + getParamName(i);
        if ((i + 1) != params.size())
          st += ", ";
      }
      st += ")";
      return st;
  }
  
  public void setLineNum(int n){
    lineNum=n;
  }
 
  public int getLineNum(){
    return lineNum;
  }
  
}
