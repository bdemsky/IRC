package IR;
import java.util.*;
import IR.Tree.*;
import java.io.File;
import Main.Main;

public class TypeUtil {
  public static final String StringClass="String";
  public static final String ObjectClass="Object";
  public static final String StartupClass="StartupObject";
  public static final String TagClass="TagDescriptor";
  public static final String ThreadClass="Thread";
  public static final String TaskClass="Task";
  State state;
  Hashtable supertable;
  Hashtable subclasstable;
  BuildIR bir;

  public TypeUtil(State state, BuildIR bir) {
    this.state=state;
    this.bir=bir;
    createTables();
  }

  public void addNewClass(String cl, Set todo) {
    for(int i=0;i<state.classpath.size();i++) {
      String path=(String)state.classpath.get(i);
      File f=new File(path, cl+".java");
      if (f.exists()) {
	try {
	  ParseNode pn=Main.readSourceFile(state, f.getCanonicalPath());
	  bir.buildtree(pn, todo);
	  return;
	} catch (Exception e) {
	  throw new Error(e);
	}
      }
    }
    throw new Error("Couldn't find class "+cl);
  }



  public ClassDescriptor getClass(String classname) {
    ClassDescriptor cd=(ClassDescriptor)state.getClassSymbolTable().get(classname);
    return cd;
  }

  public ClassDescriptor getClass(String classname, HashSet todo) {
    ClassDescriptor cd=(ClassDescriptor)state.getClassSymbolTable().get(classname);
    if (cd==null) {
      //have to find class
      addNewClass(classname, todo);
      cd=(ClassDescriptor)state.getClassSymbolTable().get(classname);
      System.out.println("Build class:"+cd);
      todo.add(cd);
    }
    if (!supertable.containsKey(cd)) {
      String superc=cd.getSuper();
      if (superc!=null) {
	ClassDescriptor cd_super=getClass(superc, todo);
	supertable.put(cd,cd_super);
      }
    }
    return cd;
  }

  private void createTables() {
    supertable=new Hashtable();
  }

  public ClassDescriptor getMainClass() {
    return getClass(state.main);
  }

  public MethodDescriptor getRun() {
    ClassDescriptor cd=getClass(TypeUtil.ThreadClass);
    for(Iterator methodit=cd.getMethodTable().getSet("run").iterator(); methodit.hasNext();) {
      MethodDescriptor md=(MethodDescriptor) methodit.next();
      if (md.numParameters()!=0||md.getModifiers().isStatic())
	continue;
      return md;
    }
    throw new Error("Can't find Thread.run");
  }
  
  public MethodDescriptor getExecute() {
    ClassDescriptor cd = getClass(TypeUtil.TaskClass);
    for(Iterator methodit = cd.getMethodTable().getSet("execute").iterator(); methodit.hasNext();) {
      MethodDescriptor md = (MethodDescriptor) methodit.next();
      if (md.numParameters()!=0 || md.getModifiers().isStatic())
        continue;
      return md;
    }
    throw new Error("Can't find Task.execute");
  }


  public MethodDescriptor getMain() {
    ClassDescriptor cd=getMainClass();
    Set mainset=cd.getMethodTable().getSet("main");
    for(Iterator mainit=mainset.iterator(); mainit.hasNext();) {
      MethodDescriptor md=(MethodDescriptor)mainit.next();
      if (md.numParameters()!=1)
	continue;
      Descriptor pd=md.getParameter(0);
      TypeDescriptor tpd=(pd instanceof TagVarDescriptor) ? ((TagVarDescriptor)pd).getType() : ((VarDescriptor)pd)
                          .getType();
      if (tpd.getArrayCount()!=1)
	continue;
      if (!tpd.getSymbol().equals("String"))
	continue;

      if (!md.getModifiers().isStatic())
	throw new Error("Error: Non static main");
      return md;
    }
    throw new Error(cd+" has no main");
  }

  /** Check to see if md1 is more specific than md2...  Informally
      if md2 could always be called given the arguments passed into
      md1 */

  public boolean isMoreSpecific(MethodDescriptor md1, MethodDescriptor md2) {
    /* Checks if md1 is more specific than md2 */
    if (md1.numParameters()!=md2.numParameters())
      throw new Error();
    for(int i=0; i<md1.numParameters(); i++) {
      if (!this.isSuperorType(md2.getParamType(i), md1.getParamType(i)))
	return false;
    }
    if (md1.getReturnType()==null||md2.getReturnType()==null) {
	if (md1.getReturnType()!=md2.getReturnType())
	    return false;
    } else
	if (!this.isSuperorType(md2.getReturnType(), md1.getReturnType()))
	    return false;

    if (!this.isSuperorType(md2.getClassDesc(), md1.getClassDesc()))
      return false;

    return true;
  }

  public MethodDescriptor getMethod(ClassDescriptor cd, String name, TypeDescriptor[] types) {
    Set methoddescriptorset=cd.getMethodTable().getSet(name);
    MethodDescriptor bestmd=null;
NextMethod:
    for(Iterator methodit=methoddescriptorset.iterator(); methodit.hasNext();) {
      MethodDescriptor currmd=(MethodDescriptor)methodit.next();
      /* Need correct number of parameters */
      if (types.length!=currmd.numParameters())
	continue;
      for(int i=0; i<types.length; i++) {
	if (!this.isSuperorType(currmd.getParamType(i),types[i]))
	  continue NextMethod;
      }
      /* Method okay so far */
      if (bestmd==null)
	bestmd=currmd;
      else {
	if (isMoreSpecific(currmd,bestmd)) {
	  bestmd=currmd;
	} else if (!isMoreSpecific(bestmd, currmd))
	  throw new Error("No method is most specific");

	/* Is this more specific than bestmd */
      }
    }
    if (bestmd==null)
      throw new Error("Could find: "+name + " in "+cd);

    return bestmd;
  }

  public void createFullTable() {
    subclasstable=new Hashtable();

    Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
    while(classit.hasNext()) {
      ClassDescriptor cd=(ClassDescriptor)classit.next();
      ClassDescriptor tmp=cd.getSuperDesc();

      while(tmp!=null) {
	if (!subclasstable.containsKey(tmp))
	  subclasstable.put(tmp,new HashSet());
	HashSet hs=(HashSet)subclasstable.get(tmp);
	hs.add(cd);
	tmp=tmp.getSuperDesc();
      }
    }
  }

  public Set getSubClasses(ClassDescriptor cd) {
    return (Set)subclasstable.get(cd);
  }

  public ClassDescriptor getSuper(ClassDescriptor cd) {
    return (ClassDescriptor)supertable.get(cd);
  }

  public boolean isCastable(TypeDescriptor original, TypeDescriptor casttype) {
    if (original.isChar()&&
        (casttype.isByte()||
         casttype.isShort()))
      return true;

    if (casttype.isChar()&&
        (original.isByte()||
         original.isShort()||
         original.isInt()||
         original.isLong()||
         original.isFloat()||
         original.isDouble()))
      return true;

    return false;
  }

  public boolean isSuperorType(TypeDescriptor possiblesuper, TypeDescriptor cd2) {
    if (possiblesuper.isOffset() || cd2.isOffset()) return true;
    //Matching type are always okay
    if (possiblesuper.equals(cd2))
      return true;

    if ((possiblesuper.isTag() && !cd2.isTag())||
        (!possiblesuper.isTag() && cd2.isTag()))
      return false;

    //Handle arrays
    if (cd2.isArray()||possiblesuper.isArray()) {
      // Object is super class of all arrays
      if (possiblesuper.getSymbol().equals(ObjectClass)&&!possiblesuper.isArray())
	return true;

      // If we have the same dimensionality of arrays & both are classes, we can default to the normal test
      if (cd2.isClass()&&possiblesuper.isClass()
          &&(possiblesuper.getArrayCount()==cd2.getArrayCount())&&
          isSuperorType(possiblesuper.getClassDesc(), cd2.getClassDesc()))
	return true;

      // Object is superclass of all array classes
      if (possiblesuper.getSymbol().equals(ObjectClass)&&cd2.isClass()
          &&(possiblesuper.getArrayCount()<cd2.getArrayCount()))
	return true;

      //Allow arraytype=null statements
      if (possiblesuper.isArray()&&cd2.isNull())
	return true;

      return false;
    }

    if (possiblesuper.isClass()&&
        cd2.isClass())
      return isSuperorType(possiblesuper.getClassDesc(), cd2.getClassDesc());
    else if (possiblesuper.isClass()&&
             cd2.isNull())
      return true;
    else if (possiblesuper.isNull())
      throw new Error();       //not sure when this case would occur
    else if (possiblesuper.isPrimitive()&&
             cd2.isPrimitive()) {
      ///Primitive widenings from 5.1.2
      if (cd2.isByte()&&(possiblesuper.isByte()||possiblesuper.isShort()||
                         possiblesuper.isInt()||possiblesuper.isLong()||
                         possiblesuper.isFloat()||possiblesuper.isDouble()))
	return true;
      if (cd2.isShort()&&(possiblesuper.isShort()||
                          possiblesuper.isInt()||possiblesuper.isLong()||
                          possiblesuper.isFloat()||possiblesuper.isDouble()))
	return true;
      if (cd2.isChar()&&(possiblesuper.isChar()||
                         possiblesuper.isInt()||possiblesuper.isLong()||
                         possiblesuper.isFloat()||possiblesuper.isDouble()))
	return true;
      if (cd2.isInt()&&(possiblesuper.isInt()||possiblesuper.isLong()||
                        possiblesuper.isFloat()||possiblesuper.isDouble()))
	return true;
      if (cd2.isLong()&&(possiblesuper.isLong()||
                         possiblesuper.isFloat()||possiblesuper.isDouble()))
	return true;
      if (cd2.isFloat()&&(possiblesuper.isFloat()||possiblesuper.isDouble()))
	return true;
      if (cd2.isDouble()&&possiblesuper.isDouble())

	return true;
      if (cd2.isBoolean()&&possiblesuper.isBoolean())
	return true;

      return false;
    } else if (possiblesuper.isPrimitive()&&(!possiblesuper.isArray())&&
               cd2.isPtr())
      return false;
    else if (cd2.isPrimitive()&&(!cd2.isArray())&&
             possiblesuper.isPtr())
      return false;
    else
      throw new Error("Case not handled:"+possiblesuper+" "+cd2);
  }


  public boolean isSuperorType(ClassDescriptor possiblesuper, ClassDescriptor cd2) {
    if (possiblesuper==cd2)
      return true;
    else
      return isSuper(possiblesuper, cd2);
  }

  private boolean isSuper(ClassDescriptor possiblesuper, ClassDescriptor cd2) {

    while(cd2!=null) {
      cd2=getSuper(cd2);
      if (cd2==possiblesuper)
	return true;
    }
    return false;
  }
}
