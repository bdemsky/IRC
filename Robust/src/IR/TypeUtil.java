package IR;
import java.util.*;

public class TypeUtil {
    public static final String StringClass="String";
    State state;
    Hashtable supertable;

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

    public ClassDescriptor getSuper(ClassDescriptor cd) {
	return (ClassDescriptor)supertable.get(cd);
    }

    public boolean isSuperorType(TypeDescriptor possiblesuper, TypeDescriptor cd2) {
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
	} else throw new Error();
    }


    public boolean isSuperorType(ClassDescriptor possiblesuper, ClassDescriptor cd2) {
	if (possiblesuper==cd2)
	    return true;
	else
	    return isSuper(possiblesuper, cd2);
    }

    public boolean isSuper(ClassDescriptor possiblesuper, ClassDescriptor cd2) {
	while(cd2!=null) {
	    cd2=getSuper(cd2);
	    if (cd2==possiblesuper)
		return true;
	}
	return false;
    }
}
