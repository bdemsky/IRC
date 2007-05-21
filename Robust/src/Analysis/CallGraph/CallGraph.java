package Analysis.CallGraph;
import IR.State;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import IR.Flat.FlatCall;
import IR.Flat.FKind;
import java.util.*;
import IR.ClassDescriptor;
import IR.MethodDescriptor;
import IR.TypeDescriptor;

public class CallGraph {
    State state;
    Hashtable methods;
    Hashtable methodmap;

    public CallGraph(State state) {
	this.state=state;
	methods=new Hashtable();
	methodmap=new Hashtable();
	buildMethodTable();
	buildGraph();
    }
    
    private void buildMethodTable() {
	//Iterator through classes
	Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    Iterator methodit=cn.getMethods();
	    //Iterator through methods
	    while(methodit.hasNext()) {
		MethodDescriptor md=(MethodDescriptor)methodit.next();
		if (md.isStatic()||md.getReturnType()==null)
		    continue;
		ClassDescriptor superdesc=cn.getSuperDesc();
		if (superdesc!=null) {
		    Set possiblematches=superdesc.getMethodTable().getSet(md.getSymbol());
		    boolean foundmatch=false;
		    for(Iterator matchit=possiblematches.iterator();matchit.hasNext();) {
			MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
			if (md.matches(matchmd)) {
			    if (!methods.containsKey(matchmd))
				methods.put(matchmd,new HashSet());
			    ((HashSet)methods.get(matchmd)).add(md);
			    break;
			}
		    }
		}
	    }
	}
    }


    public Set getMethods(MethodDescriptor md, TypeDescriptor type) {
	return getMethods(md);
    }

    /** Given a call to MethodDescriptor, lists the methods which
        could actually be called due to virtual dispatch. */
    public Set getMethods(MethodDescriptor md) {
	HashSet ns=new HashSet();
	ns.add(md);
	Set s=(Set)methods.get(md);
	if (s!=null)
	    for(Iterator it=s.iterator();it.hasNext();) {
		MethodDescriptor md2=(MethodDescriptor)it.next();
		ns.addAll(getMethods(md2));
	    }
	return ns;
    }

    /** Given a call to MethodDescriptor, lists the methods which
        could actually be call by that method. */
    public Set getMethodCalls(MethodDescriptor md) {
	HashSet ns=new HashSet();
	ns.add(md);
	Set s=(Set)methodmap.get(md);
	if (s!=null)
	    for(Iterator it=s.iterator();it.hasNext();) {
		MethodDescriptor md2=(MethodDescriptor)it.next();
		ns.addAll(getMethodCalls(md2));
	    }
	return ns;
    }

    private void buildGraph() { 
	Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    Iterator methodit=cn.getMethods();
	    //Iterator through methods
	    while(methodit.hasNext()) {
		MethodDescriptor md=(MethodDescriptor)methodit.next();
		analyzeMethod(md);
	    }
	}
    }

    private void analyzeMethod(MethodDescriptor md) {
	FlatMethod fm=state.getMethodFlat(md);
	HashSet toexplore=new HashSet();
	toexplore.add(fm);
	HashSet explored=new HashSet();
	//look at all the nodes in the flat representation
	while(!toexplore.isEmpty()) {
	    FlatNode fn=(FlatNode)(toexplore.iterator()).next();
	    toexplore.remove(fn);
	    explored.add(fn);
	    for(int i=0;i<fn.numNext();i++) {
		FlatNode fnnext=fn.getNext(i);
		if (!explored.contains(fnnext))
		    toexplore.add(fnnext);
	    }
	    if (fn.kind()==FKind.FlatCall) {
		FlatCall fc=(FlatCall)fn;
		MethodDescriptor calledmethod=fc.getMethod();
		Set methodsthatcouldbecalled=fc.getThis()==null?getMethods(calledmethod):
		    getMethods(calledmethod, fc.getThis().getType());
		if (!methodmap.containsKey(md))
		    methodmap.put(md,new HashSet());
		((HashSet)methodmap.get(md)).addAll(methodsthatcouldbecalled);
	    }
	}
    }
}
