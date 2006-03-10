package IR.Flat;
import IR.*;
import java.util.*;

public class BuildCode {
    State state;
    Hashtable temptovar;

    public BuildCode(State st, Hashtable temptovar) {
	state=st;
	this.temptovar=temptovar;
    }
    
    public void buildCode() {
	Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    generateCallStructs(cn);
	}
    }

    private void generateCallStructs(ClassDescriptor cn) {
	Iterator methodit=cn.getMethods();
	while(methodit.hasNext()) {
	    MethodDescriptor md=(MethodDescriptor)methodit.next();
	    
	}
    }
}
