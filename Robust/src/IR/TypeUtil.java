package IR;
import java.util.*;

public class TypeUtil {
    public static final String StringClass="java.lang.String";
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
	if ((possiblesuper.getClassDesc()==null)||
	    cd2.getClassDesc()==null)
	    throw new Error();
	return isSuperorType(possiblesuper.getClassDesc(), cd2.getClassDesc());
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
