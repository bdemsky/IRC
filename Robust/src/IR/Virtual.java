package IR;
import java.util.*;

public class Virtual {
    State state;
    Hashtable methodnumber;
    Hashtable classmethodcount;
      
    public int getMethodNumber(MethodDescriptor md) {
	return ((Integer)methodnumber.get(md)).intValue();
    }

    public int getMethodCount(ClassDescriptor md) {
	return ((Integer)classmethodcount.get(md)).intValue();
    }

    public Virtual(State state) {
	this.state=state;
	methodnumber=new Hashtable();
	classmethodcount=new Hashtable();
	doAnalysis();
    }

    private void doAnalysis() {
    	Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
	while(classit.hasNext()) {
	    ClassDescriptor cd=(ClassDescriptor)classit.next();
	    numberMethods(cd);
	}
    }

    private int numberMethods(ClassDescriptor cd) {
	if (classmethodcount.containsKey(cd))
	    return ((Integer)classmethodcount.get(cd)).intValue();
	ClassDescriptor superdesc=cd.getSuperDesc();
	int start=0;
	if (superdesc!=null)
	    start=numberMethods(superdesc);
	for(Iterator it=cd.getMethods();it.hasNext();) {
	    MethodDescriptor md=(MethodDescriptor)it.next();
	    if (md.isStatic()||md.getReturnType()==null)
		continue;
	    if (superdesc!=null) {
		Set possiblematches=superdesc.getMethodTable().getSet(md.getSymbol());
		boolean foundmatch=false;
		for(Iterator matchit=possiblematches.iterator();matchit.hasNext();) {
		    MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
		    if (md.matches(matchmd)) {
			int num=((Integer)methodnumber.get(matchmd)).intValue();
			methodnumber.put(md, new Integer(num));
			foundmatch=true;
			break;
		    }
		}
		if (!foundmatch)
		    methodnumber.put(md, new Integer(start++));
	    } else {
		methodnumber.put(md, new Integer(start++));
	    }
	}
	classmethodcount.put(cd, new Integer(start));
	return start;
    }
}

