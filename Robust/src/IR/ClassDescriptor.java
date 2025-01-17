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
  String classname;

  Modifiers modifiers;

  SymbolTable fields;
  Vector fieldvec;
  SymbolTable flags;
  SymbolTable methods;
  boolean inline=false;

  ChainHashMap mandatoryImports;
  ChainHashMap multiImports;

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
  //adding another variable to indicate depth of this inner class 
  int innerDepth = 0;
  ClassDescriptor surroundingdesc=null;
  SymbolTable surroundingNameTable = null;
  MethodDescriptor surroundingBlock=null;
  boolean inStaticContext=false;

  SymbolTable innerdescs;

  // for enum type
  boolean isEnum = false;
  SymbolTable enumdescs;
  HashMap<String, Integer> enumConstantTbl;
  int enumconstantid = 0;

  String sourceFileName;

  public ClassDescriptor(String classname, boolean isInterface) {
    this("", classname, isInterface);
  }

  public ClassDescriptor(String packagename, String classname, boolean isInterface) {
    //make the name canonical by class file path (i.e. package)
    super(packagename!=null?packagename+"."+classname:classname);
    this.classname=classname;
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

  public String getClassName() {
    return classname;
  }

  public String getPackage() {
    return packagename;
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
    return "L"+safename.replace(".","___________").replace("$","___DOLLAR___");
  }

  public String getSafeSymbol() {
    return safename.replace(".","___________").replace("$","___DOLLAR___");
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

    for(Iterator it=getFlags(); it.hasNext(); ) {
      FlagDescriptor fd=(FlagDescriptor)it.next();
      st+=TreeNode.printSpace(indent)+fd.toString()+"\n";
      printcr=true;
    }
    if (printcr)
      st+="\n";

    printcr=false;

    for(Iterator it=getFields(); it.hasNext(); ) {
      FieldDescriptor fd=(FieldDescriptor)it.next();
      st+=TreeNode.printSpace(indent)+fd.toString()+"\n";
      printcr=true;
    }
    if (printcr)
      st+="\n";

    for(Iterator it=this.getInnerClasses(); it.hasNext(); ) {
      ClassDescriptor icd=(ClassDescriptor)it.next();
      st+=icd.printTree(state)+"\n";
      printcr=true;
    }
    if (printcr)
      st+="\n";

    for(Iterator it=this.getEnum(); it.hasNext(); ) {
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

    for(Iterator it=getMethods(); it.hasNext(); ) {
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
        // TODO: the original code returned "null" if no super class
        // ever defines the method.  Is there a situation where this is
        // fine and the client should take other actions?  If not, we should
        // change this warning to an error.
        System.out.println("ClassDescriptor.java: WARNING "+md+
                           " did not resolve to an actual method.");
        return null;
      }
      Set possiblematches=cn.getMethodTable().getSetFromSameScope(md.getSymbol());
      for(Iterator matchit=possiblematches.iterator(); matchit.hasNext(); ) {
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

  public void setInline() {
    this.inline=true;
  }

  public boolean getInline() {
    return inline;
  }

  public void setSuper(String superclass) {
    this.superclass=superclass;
  }

  public ClassDescriptor getSuperDesc() {
    return superdesc;
  }

  public void setSuperDesc(ClassDescriptor scd) {
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
  //Will have to call this whenever we are adding the this$ member, ideally should have used a single entrance to add the field. so that entrance could be used to set this flag.
  public void setInnerDepth( int theDepth ) {
	innerDepth = theDepth;
  }

  public int getInnerDepth() {
  	return innerDepth;
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
    return this.surroundingdesc;
  }

  public void setSurrounding(ClassDescriptor scd) {
    this.surroundingdesc=scd;
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

  public void setSourceFileName(String sourceFileName) {
    this.sourceFileName=sourceFileName;
  }

  public void setImports(ChainHashMap singleImports, ChainHashMap multiImports) {
    this.mandatoryImports = singleImports;
    this.multiImports = multiImports;
  }

  public String getSourceFileName() {
    return this.sourceFileName;
  }

  public ChainHashMap getSingleImportMappings() {
    return this.mandatoryImports;
  }
  
  public ChainHashMap getMultiImportMappings() {
    return this.multiImports;
  }

  //Returns the full name/path of another class referenced from this class via imports.
  public String getCanonicalImportMapName(String otherClassname) {
    if(mandatoryImports.containsKey(otherClassname)) {
      return (String) mandatoryImports.get(otherClassname);
    } else if(multiImports.containsKey(otherClassname)) {
      //Test for error
      Object o = multiImports.get(otherClassname);
      if(o instanceof Error) {
        throw new Error("Class " + otherClassname + " is ambiguous. Cause: more than 1 package import contain the same class.");
      } else {
        //At this point, if we found a unique class
        //we can treat it as a single, mandatory import.
        mandatoryImports.put(otherClassname, o);
        return (String) o;
      }
    } else {
      return otherClassname;
    }
  }
  
  public void setSurroundingNameTable(SymbolTable nametable) {
    this.surroundingNameTable = nametable;
  }
  
  public SymbolTable getSurroundingNameTable() {
    return this.surroundingNameTable;
  }
  
  public void setSurroundingBlock(MethodDescriptor md) {
    this.surroundingBlock = md;
  }
  
  public MethodDescriptor getSurroundingBlock() {
    return this.surroundingBlock;
  }
  
  public void setInStaticContext() {
    this.inStaticContext = true;
  }
  
  public boolean getInStaticContext() {
    return this.inStaticContext;
  }
}
