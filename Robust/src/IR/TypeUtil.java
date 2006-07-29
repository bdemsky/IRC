package IR;
import java.util.*;

public class TypeUtil {
    public static final String StringClass="String";
    public static final String ObjectClass="Object";
    State state;
    Hashtable supertable;
    Hashtable subclasstable;

    public TypeUtil(State state) {
	this.state=state;
	createTables();
    }

    public ClassDescriptor getClass(String classname) {
	ClassDescriptor cd=(ClassDescriptor)state.getClassSymbolTable().get(classname);
	return cd;
    }

    private void createTables() {
	supertable=new Hashtable();

	Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
	while(classit.hasNext()) {
	    ClassDescriptor cd=(ClassDescriptor)classit.next();
	    String superc=cd.getSuper();
	    if (superc!=null) {
		ClassDescriptor cd_super=getClass(superc);
		supertable.put(cd,cd_super);
	    }
	}
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

    public boolean isSuperorType(TypeDescriptor possiblesuper, TypeDescriptor cd2) {
	//Matching type are always okay
	if (possiblesuper.equals(cd2))
	    return true;

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

	    return false;
	}

	if (possiblesuper.isClass()&&
	     cd2.isClass())
	    return isSuperorType(possiblesuper.getClassDesc(), cd2.getClassDesc());
	else if (possiblesuper.isClass()&&
		 cd2.isNull())
	    return true;
	else if (possiblesuper.isNull())
	    throw new Error(); //not sure when this case would occur
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
		   (cd2.isArray()||cd2.isPtr()))
	    return false;
	else if (cd2.isPrimitive()&&(!cd2.isArray())&&
		 (possiblesuper.isArray()||possiblesuper.isPtr()))
	    return false;
	else
	    throw new Error();
    }


    private boolean isSuperorType(ClassDescriptor possiblesuper, ClassDescriptor cd2) {
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
