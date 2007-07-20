package Analysis.Locality;

import java.util.Hashtable;
import java.util.Stack;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Arrays;
import Analysis.CallGraph.CallGraph;
import IR.SymbolTable;
import IR.State;
import IR.MethodDescriptor;
import IR.Flat.*;

public class LocalityAnalysis {
    State state;
    Stack tovisit;
    Hashtable<LocalityBinding,LocalityBinding> discovered;
    Hashtable<MethodDescriptor, MethodDescriptor> dependence;
    CallGraph callgraph;
    TypeUtil typeutil;
    public static final Integer LOCAL=new Integer(0);
    public static final Integer GLOBAL=new Integer(1);
    public static final Integer EITHER=new Integer(2);
    public static final Integer CONFLICT=new Integer(3);


    public LocalityAnalysis(State state, CallGraph callgraph, TypeUtil typeutil) {
	this.state=state;
	this.discovered=new Hashtable<LocalityBinding,LocalityBinding>();
	this.dependence=new Hashtable<MethodDescriptor, MethodDescriptor>();
	this.tovisit=new Stack();
	this.callgraph=callgraph;
	doAnalysis();
    }

    private void doAnalysis() {
	computeLocalityBindings();
    }
    
    private void computeLocalityBindings() {
	LocalityBinding lb=new LocalityBinding(typeutil.getMain(), false);
	tovisit.add(lb);
	discovered.put(lb, lb);

	while(!tovisit.empty()) {
	    LocalityBinding lb=(LocalityBinding) tovisit.pop();
	    MethodDescriptor md=lb.getMethod();
	    computeCallsFlags(md, lb);
	}
    }
    
    public Hashtable<FlatNode, Hashtable<TempDescriptor,Integer>> computeCallsFlags(MethodDescriptor md, LocalityBinding lb) {
	FlatMethod fm=state.getMethodFlat(md);
	HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
	Hashtable<FlatNode,Hashtable<TempDescriptor, Integer>> temptable=new Hashtable<FlatNode,Hashtable<TempDescriptor, Integer>>();
	tovisit.add(fm.getNext(0));
	
	{
	    // Build table for initial node
	    Hashtable<TempDescriptor,Integer> table=new Hashtable<TempDescriptor,Integer>();
	    temptable.put(fm, table);
	    for(int i=0;i<fm.numParameters();i++) {
		TempDescriptor temp=fm.getParameter(i);
		b=lb.isGlobal(i);
		table.put(temp,b);
	    }
	}
	
	while(!tovisit.isEmpty()) {
	    FlatNode fn=tovisit.iterator().next();
	    Hashtable<TempDescriptor, Integer> currtable=new Hashtable<TempDescriptor, Integer>();
	    for(int i=0;i<fn.numPrev();i++) {
		FlatNode prevnode=fn.getPrev(i);
		if (!temptable.containsKey(prevnode))
		    continue;
		Hashtable<TempDescriptor, Integer> prevtable=temptable.get(prevnode);
		for(Iterator<TempDescriptor> tempit=prevtable.keySet().iterator();tempit.hasNext();) {
		    TempDescriptor temp=tempit.next();
		    Integer tmpint=prevtable.get(temp);
		    Integer oldint=currtable.containsKey(temp)?currtable.get(temp):null;
		    Integer newint=merge(tmpint, oldint);
		    currtable.put(temp, newint);
		}
	    }
	    // Process this node
	    switch(fn.kind()) {
	    case FlatCall:
		processCall(md, (FlatCall)fn, currtable);
		break;
	    case FlatFieldNode:
		processFieldNode((FlatFieldNode)fn, currtable);
		break;
	    case FlatSetFieldNode:
		processSetFieldNode((FlatSetFieldNode)fn, currtable);
		break;
	    case FlatNew:
		processNew((FlatNew)fn, currtable);
		break;
	    case FlatOpNode:
		processOpNode((FlatOpNode)fn, currtable);
		break;
	    case FlatCastNode:
		processCastNode((FlatCastNode)fn, currtable);
		break;
	    case FlatLiteralNode:
		processLiteralNode((FlatLiteralNode)fn, currtable);
		break;
	    case FlatReturnNode:
		processReturnNode((FlatReturnNode)fn, currtable);
		break;
	    case FlatSetElementNode:
		processSetElement((FlatSetElementNode)fn, currtable);
		break;
	    case FlatElementNode:
		processElement((FlatElementNode)fn, currtable);
		break;

	    case FlatCondBranch:
	    case FlatBackEdge:
	    case FlatNop:
		//No action needed for these
		break;
	    case FlatFlagActionNode:
	    case FlatCheckNode:
	    case FlatTagDeclaration:
		throw new Error("Incompatible with tasks!");
	    case FlatMethod:
	    default:
		throw new Error();
	    }
	    Hashtable<TempDescriptor,Integer> oldtable=temptable.get(fn);
	    if (oldtable==null||!oldtable.equals(currtable)) {
		// Update table for this node
		temptable.put(fn, currtable);
		for(int i=0;i<fn.numNext();i++) {
		    tovisit.add(fn.next(i));
		}
	    }
	}
    }

    private static Integer merge(Integer a, Integer b) {
	if (a==null||a.equals(EITHER))
	    return b;
	if (b==null||b.equals(EITHER))
	    return a;
	if (a.equals(b))
	    return a;
	return CONFLICT;
    }
	
    void processCall(LocalityBinding currlb, FlatCall fc, boolean transaction, Hashtable<TempDescriptor, Integer> currtable) {
	MethodDescriptor nodemd=fc.getMethod();
	Set methodset=fc.getThis()==null?callgraph.getMethods(nodemd):
	    callgraph.getMethods(nodemd, fc.getThis().getType());
	Integer currreturnval=null;
	for(Iterator methodit=methodset.iterator();methodit.hasNext();) {
	    MethodDescriptor md=(MethodDescriptor) methodit.next();
	    LocalityBinding lb=new LocalityBinding(md, transaction);
	    for(int i=0;i<fc.numArgs();i++) {
		TempDescriptor arg=fc.getArg(i);
		lb.setGlobal(i,currtable.get(arg));
	    }
	    if (fc.getThis()!=null) {
		Integer thistype=currtable.get(fc.getThis());
		if(thistype.equals(CONFLICT))
		    throw new Error("Using type that can be either local or global");
		if(thistype.equals(GLOBAL)&&!transaction)
		    throw new Error("Using global object outside of transaction");
		lb.setGlobalThis(thistype);
	    } else
		lb.setGlobalThis(EITHER);//default value
	    //lb is built
	    if (!discovered.containsKey(lb)) {
		lb.setGlobalReturn(EITHER);
		tovisit.add(lb);
		discovered.put(lb, lb);
	    } else
		lb=discovered.get(lb);
	    Integer returnval=lb.getGlobalReturn();
	    currreturnval=merge(returnval, currreturnval);
	    dependence.put(md, currlb.getMethod()); 
	}
	currtable.put(fc.getReturnTemp(), currreturnval);
    }

    void processFieldNode(LocalityBinding lb, FlatFieldNode ffn, boolean transaction, Hashtable<TempDescriptor, Integer> currtable) {
	Integer type=currtable.get(ffn.getSrc());
	TempDescriptor dst=ffn.getDst();
	if (type.equals(LOCAL)) {
	    if (ffn.getField().isGlobal())
		currtable.put(dst,GLOBAL);
	    else
		currtable.put(dst,LOCAL);		    
	} else if (type.equals(GLOBAL)) {
	    if (!transaction)
		throw Error("Global access outside of a transaction");
	    if (ffn.getField().getType().isPrimitive())
		currtable.put(dst, LOCAL); // primitives are local
	    else
		currtable.put(dst, GLOBAL);
	} else if (type.equals(EITHER)) {
	    if (ffn.getField().getType().isPrimitive())
		currtable.put(dst, LOCAL); // primitives are local
	    else
		currtable.put(dst, EITHER);
	} else if (type.equals(CONFLICT)) {
	    throw new Error("Access to object that could be either global or local");
	}
    }

    //need to handle primitives
    void processSetFieldNode(LocalityBinding lb, FlatSetFieldNode fsfn, boolean transaction, Hashtable<TempDescriptor, Integer> currtable) {
	Integer srctype=currtable.get(ffn.getSrc());
	Integer dsttype=currtable.get(ffn.getDst());

	if (dsttype.equals(LOCAL)) {
	    if (ffn.getField().isGlobal()) {
		if (!(srctype.equals(GLOBAL)||srctype.equals(EITHER)))
		    throw new Error("Writing possible local reference to global field");
	    } else {
		if (!(srctype.equals(LOCAL)||srctype.equals(EITHER)))
		    throw new Error("Writing possible global reference to local object");
	    }
	} else if (dsttype.equals(GLOBAL)) {
	    if (!transaction)
		throw Error("Global access outside of a transaction");
	    //okay to store primitives in global object
	    if (srctype.equals(LOCAL) && fsfn.getField().getType().isPrimitive())
		return;
	    if (!(srctype.equals(GLOBAL)||srctype.equals(EITHER)))
		throw new Error("Writing possible local reference to global object");
	} else if (dsttype.equals(EITHER)) {
	    if (srctype.equals(CONFLICT))
		throw new Error("Using reference that could be local or global");
	} else if (dsttype.equals(CONFLICT)) {
	    throw new Error("Access to object that could be either global or local");
	}
    }

    void processNew(LocalityBinding lb, FlatNew fn, boolean transaction, Hashtable<TempDescriptor, Integer> currtable) {
	if (fn.isGlobal()&&!transaction) {
	    throw new Error("Allocating global object outside of transaction");
	}
	if (fn.isGlobal())
	    currtable.put(fn.getDst(), GLOBAL);
	else
	    currtable.put(fn.getDst(), LOCAL);
    }

    void processOpNode(FlatOpNode fon, Hashtable<TempDescriptor, Integer> currtable) {
	/* Just propagate value */
	currtable.put(fon.getDest(), currtable.get(fon.getLeft()));
    }

    void processCastNode(FlatCastNode fcn, Hashtable<TempDescriptor, Integer> currtable) {
	currtable.put(fon.getDest(), currtable.get(fon.getSrc()));
    }

    void processLiteralNode(FlatLiteralNode fln, Hashtable<TempDescriptor, Integer> currtable) {
	//null is either
	if (fln.getValue()==null)
	    currtable.put(fln.getDst(), EITHER);
	else
	    currtable.put(fln.getDst(), LOCAL);
    }

    void processReturnNode(LocalityBinding lb, FlatReturnNode frn, Hashtable<TempDescriptor, Integer> currtable) {
	if(frn.getReturnTemp()!=null) {
	    Integer returntype=currtable.get(frn.getReturnTemp());
	    lb.setGlobalReturn(merge(returntype, lb.getGlobalReturn()));
	}
    }

    void processSetElementNode(FlatSetElementNode fsen, Hashtable<TempDescriptor, Integer> currtable) {
	Integer srctype=currtable.get(fsen.getSrc());
	Integer dsttype=currtable.get(fsen.getDst());

	if (dsttype.equals(LOCAL)) {
	    if (!(srctype.equals(LOCAL)||srctype.equals(EITHER)))
		throw new Error("Writing possible global reference to local object");
	} else if (dsttype.equals(GLOBAL)) {
	    if (!(srctype.equals(GLOBAL)||srctype.equals(EITHER)))
		throw new Error("Writing possible local reference to global object");
	    if (!transaction)
		throw Error("Global access outside of a transaction");
	} else if (dsttype.equals(EITHER)) {
	    if (srctype.equals(CONFLICT))
		throw new Error("Using reference that could be local or global");
	} else if (dsttype.equals(CONFLICT)) {
	    throw new Error("Access to object that could be either global or local");
	}
    }

    void processElementNode(FlatElementNode fen, Hashtable<TempDescriptor, Integer> currtable) {
	Integer type=currtable.get(fen.getSrc());
	TempDescriptor dst=fen.getDst();
	if (type.equals(LOCAL)) {
	    currtable.put(dst,LOCAL);		    
	} else if (type.equals(GLOBAL)) {
	    if (!transaction)
		throw Error("Global access outside of a transaction");
	    currtable.put(dst, GLOBAL);
	} else if (type.equals(EITHER)) {
	    currtable.put(dst, EITHER);
	} else if (type.equals(CONFLICT)) {
	    throw new Error("Access to object that could be either global or local");
	}
    }
}
