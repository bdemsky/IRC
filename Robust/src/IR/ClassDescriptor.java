package IR;
import java.util.*;
import IR.Tree.*;
import Util.Lattice;

public class ClassDescriptor extends Descriptor {
  private static int UIDCount=1; // start from 1 instead of 0 for multicore gc
  private final int classid;
  String superclass;
  ClassDescriptor superdesc;
  boolean hasFlags=false;
  String packagename;

  Modifiers modifiers;

  SymbolTable fields;
  Vector fieldvec;
  SymbolTable flags;
  SymbolTable methods;
  
  int numstaticblocks = 0;
  int numstaticfields = 0;
  
  // for interfaces
  Vector<String> superinterfaces;
  SymbolTable superIFdesc;
  private int interfaceid;
  
  // for inner classes
  boolean isInnerClass=false;
  
  // inner classes/enum can have these
  String surroundingclass=null;
  ClassDescriptor surroudingdesc=null;
  SymbolTable innerdescs;
  
  // for enum type
  boolean isEnum = false;
  SymbolTable enumdescs;
  HashMap<String, Integer> enumConstantTbl;
  int enumconstantid = 0;
  
  boolean isClassLibrary=false;
  
  public ClassDescriptor(String classname, boolean isInterface) {
    this("", classname, isInterface);
  }

  public ClassDescriptor(String packagename, String classname, boolean isInterface) {
    super(classname);
    superclass=null;
    flags=new SymbolTable();
    fields=new SymbolTable();
    fieldvec=new Vector();
    methods=new SymbolTable();
    if(isInterface) {
      this.classid = -2;
      this.interfaceid = -1;
    } else {
      classid=UIDCount++;
    }
    this.packagename=packagename;
    superinterfaces = new Vector<String>();
    superIFdesc = new SymbolTable();
    this.innerdescs = new SymbolTable();
    this.enumdescs = new SymbolTable();
  }

  public int getId() {
    if(this.isInterface()) {
      return this.interfaceid;
    }
    return classid;
  }

  public Iterator getMethods() {
    return methods.getDescriptorsIterator();
  }

  public Iterator getFields() {
    return fields.getDescriptorsIterator();
  }

  public Iterator getFlags() {
    return flags.getDescriptorsIterator();
  }
  
  public Iterator getSuperInterfaces() {
    return this.superIFdesc.getDescriptorsIterator();
  }

  public SymbolTable getFieldTable() {
    return fields;
  }

  public Vector getFieldVec() {
    return fieldvec;
  }

  public SymbolTable getFlagTable() {
    return flags;
  }

  public SymbolTable getMethodTable() {
    return methods;
  }
  
  public SymbolTable getSuperInterfaceTable() {
    return this.superIFdesc;
  }

  public String getSafeDescriptor() {
    return "L"+safename.replace('.','/');
  }

  public String printTree(State state) {
    int indent;
    String st=modifiers.toString()+"class "+getSymbol();
    if (superclass!=null)
      st+="extends "+superclass.toString();
    if(this.superinterfaces != null) {
      st += "implements ";
      boolean needcomma = false;
      for(int i = 0; i < this.superinterfaces.size(); i++) {
        if(needcomma) {
          st += ", ";
        }
        st += this.superinterfaces.elementAt(i);
        needcomma = true;
      }
    }
    st+=" {\n";
    indent=TreeNode.INDENT;
    boolean printcr=false;

    for(Iterator it=getFlags(); it.hasNext();) {
      FlagDescriptor fd=(FlagDescriptor)it.next();
      st+=TreeNode.printSpace(indent)+fd.toString()+"\n";
      printcr=true;
    }
    if (printcr)
      st+="\n";

    printcr=false;

    for(Iterator it=getFields(); it.hasNext();) {
      FieldDescriptor fd=(FieldDescriptor)it.next();
      st+=TreeNode.printSpace(indent)+fd.toString()+"\n";
      printcr=true;
    }
    if (printcr)
      st+="\n";
    
    for(Iterator it=this.getInnerClasses(); it.hasNext();) {
      ClassDescriptor icd=(ClassDescriptor)it.next();
      st+=icd.printTree(state)+"\n";
      printcr=true;
    }
    if (printcr)
      st+="\n";
    
    for(Iterator it=this.getEnum(); it.hasNext();) {
      ClassDescriptor icd = (ClassDescriptor)it.next();
      st += icd.getModifier().toString() + " enum " + icd.getSymbol() + " {\n  ";
      Set keys = icd.getEnumConstantTbl().keySet();
      String[] econstants = new String[keys.size()];
      Iterator it_keys = keys.iterator();
      while(it_keys.hasNext()) {
        String key = (String)it_keys.next();
        econstants[icd.getEnumConstant(key)] = key;
      }
      for(int i = 0; i < econstants.length; i++) {
        st += econstants[i];
        if(i < econstants.length-1) {
          st += ", ";
        }
      }
      st+="\n}\n";
      printcr=true;
    }
    if (printcr)
      st+="\n";

    for(Iterator it=getMethods(); it.hasNext();) {
      MethodDescriptor md=(MethodDescriptor)it.next();
      st+=TreeNode.printSpace(indent)+md.toString()+" ";
      BlockNode bn=state.getMethodBody(md);
      st+=bn.printNode(indent)+"\n\n";
    }
    st+="}\n";
    return st;
  }

  public MethodDescriptor getCalledMethod(MethodDescriptor md) {
    ClassDescriptor cn=this;
    while(true) {
      if (cn==null) {
	return null;
      }
      Set possiblematches=cn.getMethodTable().getSet(md.getSymbol());
      boolean foundmatch=false;
      for(Iterator matchit=possiblematches.iterator(); matchit.hasNext();) {
	MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
	if (md.matches(matchmd)) {
	  return matchmd;
	}
      }
      
      //Not found...walk one level up
      cn=cn.getSuperDesc();
    }
  }

  public void addFlag(FlagDescriptor fd) {
    if (flags.contains(fd.getSymbol()))
      throw new Error(fd.getSymbol()+" already defined");
    hasFlags=true;
    flags.add(fd);
  }

  public boolean hasFlags() {
    return hasFlags||getSuperDesc()!=null&&getSuperDesc().hasFlags();
  }

  public void addField(FieldDescriptor fd) {
    if (fields.contains(fd.getSymbol()))
      throw new Error(fd.getSymbol()+" already defined");
    fields.add(fd);
    fieldvec.add(fd);
    if(fd.isStatic()) {
      this.incStaticFields();
    }
    fd.setClassDescriptor(this);
  }

  public void addMethod(MethodDescriptor md) {
    methods.add(md);
  }

  public void setModifiers(Modifiers modifiers) {
    this.modifiers=modifiers;
  }

  public void setSuper(String superclass) {
    this.superclass=superclass;
  }

  public ClassDescriptor getSuperDesc() {
    return superdesc;
  }

  public void setSuper(ClassDescriptor scd) {
    this.superdesc=scd;
  }

  public String getSuper() {
    return superclass;
  }
  
  public void addSuperInterface(String superif) {
    this.superinterfaces.addElement(superif);
  }
  
  public Vector<String> getSuperInterface() {
    return this.superinterfaces;
  }
  
  public void addSuperInterfaces(ClassDescriptor sif) {
    this.superIFdesc.add(sif);
  }
  
  public void incStaticBlocks() {
    this.numstaticblocks++;
  }
  
  public int getNumStaticBlocks() {
    return this.numstaticblocks;
  }
  
  public void incStaticFields() {
    this.numstaticfields++;
  }
  
  public int getNumStaticFields() {
    return this.numstaticfields;
  }
  
  public boolean isAbstract() {
    return this.modifiers.isAbstract();
  }
  
  public boolean isInterface() {
    return (this.classid == -2);
  }
  
  public void setInterfaceId(int id) {
    this.interfaceid = id;
  }
  
  public boolean isStatic() {
    return this.modifiers.isStatic();
  }
  
  public void setAsInnerClass() {
    this.isInnerClass = true;
  }
  
  public boolean isInnerClass() {
    return this.isInnerClass;
  }
  
  public void setSurroundingClass(String sclass) {
    this.surroundingclass=sclass;
  }

  public String getSurrounding() {
    return this.surroundingclass;
  }

  public ClassDescriptor getSurroundingDesc() {
    return this.surroudingdesc;
  }

  public void setSurrounding(ClassDescriptor scd) {
    this.surroudingdesc=scd;
  }
  
  public void addInnerClass(ClassDescriptor icd) {
    this.innerdescs.add(icd);
  }
  
  public Iterator getInnerClasses() {
    return this.innerdescs.getDescriptorsIterator();
  }

  public SymbolTable getInnerClassTable() {
    return this.innerdescs;
  }
  
  public void setAsEnum() {
    this.isEnum = true;
  }
  
  public boolean isEnum() {
    return this.isEnum;
  }
  
  public void addEnum(ClassDescriptor icd) {
    this.enumdescs.add(icd);
  }
  
  public Iterator getEnum() {
    return this.enumdescs.getDescriptorsIterator();
  }

  public SymbolTable getEnumTable() {
    return this.enumdescs;
  }
  
  public void addEnumConstant(String econstant) {
    if(this.enumConstantTbl == null) {
      this.enumConstantTbl = new HashMap<String, Integer>();
    }
    if(this.enumConstantTbl.containsKey(econstant)) {
      return;
    } else {
      this.enumConstantTbl.put(econstant, this.enumconstantid++);
    }
    return;
  }
  
  public int getEnumConstant(String econstant) {
    if(this.enumConstantTbl.containsKey(econstant)) {
      return this.enumConstantTbl.get(econstant).intValue();
    } else {
      return -1;
    }
  }
  
  public HashMap<String, Integer> getEnumConstantTbl() {
    return this.enumConstantTbl;
  }
  
  public Modifiers getModifier() {
    return this.modifiers;
  }
  
  public void setClassLibrary(){
    this.isClassLibrary=true;
  }
  
  public boolean isClassLibrary(){
    return isClassLibrary;
  }
  
}
