package Analysis.Locality;

import java.util.*;
import Analysis.CallGraph.CallGraph;
import IR.SymbolTable;
import IR.State;
import IR.TypeUtil;
import IR.MethodDescriptor;
import IR.Flat.*;

public class LocalityAnalysis {
    State state;
    Stack lbtovisit;
    Hashtable<LocalityBinding,LocalityBinding> discovered;
    Hashtable<LocalityBinding, Set<LocalityBinding>> dependence;
    Hashtable<LocalityBinding, Hashtable<FlatNode,Hashtable<TempDescriptor, Integer>>> temptab;
    Hashtable<LocalityBinding, Hashtable<FlatNode, Integer>> atomictab;
    Hashtable<LocalityBinding, Hashtable<FlatAtomicEnterNode, Set<TempDescriptor>>> tempstosave;

    CallGraph callgraph;
    TypeUtil typeutil;
    public static final Integer LOCAL=new Integer(0);
    public static final Integer GLOBAL=new Integer(1);
    public static final Integer EITHER=new Integer(2);
    public static final Integer CONFLICT=new Integer(3);

    public LocalityAnalysis(State state, CallGraph callgraph, TypeUtil typeutil) {
	this.typeutil=typeutil;
	this.state=state;
	this.discovered=new Hashtable<LocalityBinding,LocalityBinding>();
	this.dependence=new Hashtable<LocalityBinding, Set<LocalityBinding>>();
	this.temptab=new Hashtable<LocalityBinding, Hashtable<FlatNode,Hashtable<TempDescriptor, Integer>>>();
	this.atomictab=new Hashtable<LocalityBinding, Hashtable<FlatNode, Integer>>();
	this.lbtovisit=new Stack();
	this.callgraph=callgraph;
	this.tempstosave=new Hashtable<LocalityBinding, Hashtable<FlatAtomicEnterNode, Set<TempDescriptor>>>();

	doAnalysis();
    }

    public Set<LocalityBinding> getLocalityBindings() {
	return discovered.keySet();
    }

    public Hashtable<FlatNode, Hashtable<TempDescriptor, Integer>> getNodeTempInfo(LocalityBinding lb) {
	return temptab.get(lb);
    }
    
    public Hashtable<FlatNode, Integer> getAtomic(LocalityBinding lb) {
	return atomictab.get(lb);
    }

    private void doAnalysis() {
	computeLocalityBindings();
    }
    
    private void computeLocalityBindings() {
	LocalityBinding lbmain=new LocalityBinding(typeutil.getMain(), false);
	lbmain.setGlobal(0, LOCAL);
	lbtovisit.add(lbmain);
	discovered.put(lbmain, lbmain);
	
	while(!lbtovisit.empty()) {
	    LocalityBinding lb=(LocalityBinding) lbtovisit.pop();
	    Integer returnglobal=lb.getGlobalReturn();
	    MethodDescriptor md=lb.getMethod();
	    Hashtable<FlatNode,Hashtable<TempDescriptor, Integer>> temptable=new Hashtable<FlatNode,Hashtable<TempDescriptor, Integer>>();
	    Hashtable<FlatNode, Integer> atomictable=new Hashtable<FlatNode, Integer>();
	    computeCallsFlags(md, lb, temptable, atomictable);
	    atomictab.put(lb, atomictable);
	    temptab.put(lb, temptable);

	    if (!md.isStatic()&&!returnglobal.equals(lb.getGlobalReturn())) {
		//return type is more precise now
		//rerun everything that call us
		lbtovisit.addAll(dependence.get(lb));
	    }
	}
    }


    public void computeCallsFlags(MethodDescriptor md, LocalityBinding lb, Hashtable<FlatNode, Hashtable<TempDescriptor, Integer>> temptable, Hashtable<FlatNode, Integer> atomictable) {
	FlatMethod fm=state.getMethodFlat(md);
	HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
	tovisit.add(fm.getNext(0));
	{
	    // Build table for initial node
	    Hashtable<TempDescriptor,Integer> table=new Hashtable<TempDescriptor,Integer>();
	    temptable.put(fm, table);
	    atomictable.put(fm, lb.isAtomic()?1:0);
	    int offset=md.isStatic()?0:1;
	    if (!md.isStatic()) {
		table.put(fm.getParameter(0), lb.getGlobalThis());
	    }
	    for(int i=offset;i<fm.numParameters();i++) {
		TempDescriptor temp=fm.getParameter(i);
		Integer b=lb.isGlobal(i-offset);
		table.put(temp,b);
	    }
	}

	while(!tovisit.isEmpty()) {
	    FlatNode fn=tovisit.iterator().next();
	    tovisit.remove(fn);
	    Hashtable<TempDescriptor, Integer> currtable=new Hashtable<TempDescriptor, Integer>();
	    int atomicstate=0;
	    for(int i=0;i<fn.numPrev();i++) {
		FlatNode prevnode=fn.getPrev(i);
		if (atomictable.containsKey(prevnode)) {
		    atomicstate=atomictable.get(prevnode).intValue();
		}
		if (!temptable.containsKey(prevnode))
		    continue;
		Hashtable<TempDescriptor, Integer> prevtable=temptable.get(prevnode);
		for(Iterator<TempDescriptor> tempit=prevtable.keySet().iterator();tempit.hasNext();) {
		    TempDescriptor temp=tempit.next();
		    Integer tmpint=prevtable.get(temp);
		    Integer oldint=currtable.containsKey(temp)?currtable.get(temp):EITHER;
		    Integer newint=merge(tmpint, oldint);
		    currtable.put(temp, newint);
		}
	    }
	    atomictable.put(fn, atomicstate);
	    // Process this node
	    switch(fn.kind()) {
	    case FKind.FlatAtomicEnterNode:
		processAtomicEnterNode((FlatAtomicEnterNode)fn, atomictable);
		break;
	    case FKind.FlatAtomicExitNode:
		processAtomicExitNode((FlatAtomicExitNode)fn, atomictable);
		break;
	    case FKind.FlatCall:
		processCallNode(lb, (FlatCall)fn, currtable, isAtomic(atomictable, fn));
		break;
	    case FKind.FlatFieldNode:
		processFieldNode(lb, (FlatFieldNode)fn, isAtomic(atomictable, fn), currtable);
		break;
	    case FKind.FlatSetFieldNode:
		processSetFieldNode(lb, (FlatSetFieldNode)fn, isAtomic(atomictable,fn), currtable);
		break;
	    case FKind.FlatNew:
		processNew(lb, (FlatNew)fn, isAtomic(atomictable, fn), currtable);
		break;
	    case FKind.FlatOpNode:
		processOpNode((FlatOpNode)fn, currtable);
		break;
	    case FKind.FlatCastNode:
		processCastNode((FlatCastNode)fn, currtable);
		break;
	    case FKind.FlatLiteralNode:
		processLiteralNode((FlatLiteralNode)fn, currtable);
		break;
	    case FKind.FlatReturnNode:
		processReturnNode(lb, (FlatReturnNode)fn, currtable);
		break;
	    case FKind.FlatSetElementNode:
		processSetElementNode(lb, (FlatSetElementNode)fn, currtable, isAtomic(atomictable, fn));
		break;
	    case FKind.FlatElementNode:
		processElementNode(lb, (FlatElementNode)fn, currtable, isAtomic(atomictable, fn));
		break;
	    case FKind.FlatCondBranch:
	    case FKind.FlatBackEdge:
	    case FKind.FlatNop:
		//No action needed for these
		break;
	    case FKind.FlatFlagActionNode:
	    case FKind.FlatCheckNode:
	    case FKind.FlatTagDeclaration:
		throw new Error("Incompatible with tasks!");
	    case FKind.FlatMethod:
	    default:
		throw new Error();
	    }
	    Hashtable<TempDescriptor,Integer> oldtable=temptable.get(fn);
	    if (oldtable==null||!oldtable.equals(currtable)) {
		// Update table for this node
		temptable.put(fn, currtable);
		for(int i=0;i<fn.numNext();i++) {
		    tovisit.add(fn.getNext(i));
		}
	    }
	}
    }

    private static boolean isAtomic(Hashtable<FlatNode, Integer> atomictable, FlatNode fn) {
	return atomictable.get(fn).intValue()>0;
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

    void processCallNode(LocalityBinding currlb, FlatCall fc, Hashtable<TempDescriptor, Integer> currtable, boolean isatomic) {
	MethodDescriptor nodemd=fc.getMethod();
	Set methodset=fc.getThis()==null?callgraph.getMethods(nodemd):
	    callgraph.getMethods(nodemd, fc.getThis().getType());
	Integer currreturnval=EITHER; //Start off with the either value
	for(Iterator methodit=methodset.iterator();methodit.hasNext();) {
	    MethodDescriptor md=(MethodDescriptor) methodit.next();
	    LocalityBinding lb=new LocalityBinding(md, isatomic);
	    for(int i=0;i<fc.numArgs();i++) {
		TempDescriptor arg=fc.getArg(i);
		lb.setGlobal(i,currtable.get(arg));
	    }
	    if (fc.getThis()!=null) {
		Integer thistype=currtable.get(fc.getThis());
		if (thistype==null)
		    thistype=EITHER;
		if(thistype.equals(CONFLICT))
		    throw new Error("Using type that can be either local or global in context:\n"+currlb.getExplanation());
		if(thistype.equals(GLOBAL)&&!isatomic)
		    throw new Error("Using global object outside of transaction in context:\n"+currlb.getExplanation());
		lb.setGlobalThis(thistype);
	    } else
		lb.setGlobalThis(EITHER);//default value
	    //lb is built
	    if (!discovered.containsKey(lb)) {
		lb.setGlobalReturn(EITHER);
		lb.setParent(currlb);
		lbtovisit.add(lb);
		discovered.put(lb, lb);
	    } else
		lb=discovered.get(lb);
	    Integer returnval=lb.getGlobalReturn();
	    currreturnval=merge(returnval, currreturnval);
	    if (!dependence.containsKey(lb))
		dependence.put(lb, new HashSet<LocalityBinding>());
	    dependence.get(lb).add(currlb);
	}
	if (fc.getReturnTemp()!=null) {
	    currtable.put(fc.getReturnTemp(), currreturnval);
	}
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
		throw new Error("Global access outside of a transaction in context:\n"+lb.getExplanation());
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
	    throw new Error("Access to object that could be either global or local in context:\n"+lb.getExplanation());
	}
    }

    //need to handle primitives
    void processSetFieldNode(LocalityBinding lb, FlatSetFieldNode fsfn, boolean transaction, Hashtable<TempDescriptor, Integer> currtable) {
	Integer srctype=currtable.get(fsfn.getSrc());
	Integer dsttype=currtable.get(fsfn.getDst());
	
	if (dsttype.equals(LOCAL)) {
	    if (fsfn.getField().isGlobal()) {
		if (!(srctype.equals(GLOBAL)||srctype.equals(EITHER)))
		    throw new Error("Writing possible local reference to global field in context: \n"+lb.getExplanation());
	    } else {
		if (!(srctype.equals(LOCAL)||srctype.equals(EITHER)))
		    throw new Error("Writing possible global reference to local object in context: \n"+lb.getExplanation());
	    }
	} else if (dsttype.equals(GLOBAL)) {
	    if (!transaction)
		throw new Error("Global access outside of a transaction in context:\n"+lb.getExplanation());
	    //okay to store primitives in global object
	    if (srctype.equals(LOCAL) && fsfn.getField().getType().isPrimitive())
		return;
	    if (!(srctype.equals(GLOBAL)||srctype.equals(EITHER)))
		throw new Error("Writing possible local reference to global object in context:\n"+lb.getExplanation());
	} else if (dsttype.equals(EITHER)) {
	    if (srctype.equals(CONFLICT))
		throw new Error("Using reference that could be local or global in context:\n"+lb.getExplanation());
	} else if (dsttype.equals(CONFLICT)) {
	    throw new Error("Access to object that could be either global or local in context:\n"+lb.getExplanation());
	}
    }

    void processNew(LocalityBinding lb, FlatNew fn, boolean transaction, Hashtable<TempDescriptor, Integer> currtable) {
	if (fn.isGlobal()&&!transaction) {
	    throw new Error("Allocating global object outside of transaction in context:"+lb.getExplanation());
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
	currtable.put(fcn.getDst(), currtable.get(fcn.getSrc()));
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

    void processSetElementNode(LocalityBinding lb, FlatSetElementNode fsen, Hashtable<TempDescriptor, Integer> currtable, boolean isatomic) {
	Integer srctype=currtable.get(fsen.getSrc());
	Integer dsttype=currtable.get(fsen.getDst());

	if (dsttype.equals(LOCAL)) {
	    if (!(srctype.equals(LOCAL)||srctype.equals(EITHER)))
		throw new Error("Writing possible global reference to local object in context:\n"+lb.getExplanation());
	} else if (dsttype.equals(GLOBAL)) {
	    if (!(srctype.equals(GLOBAL)||srctype.equals(EITHER)))
		throw new Error("Writing possible local reference to global object in context:\n"+lb.getExplanation());
	    if (!isatomic)
		throw new Error("Global access outside of a transaction in context:\n"+lb.getExplanation());
	} else if (dsttype.equals(EITHER)) {
	    if (srctype.equals(CONFLICT))
		throw new Error("Using reference that could be local or global in context:\n"+lb.getExplanation());
	} else if (dsttype.equals(CONFLICT)) {
	    throw new Error("Access to object that could be either global or local in context:\n"+lb.getExplanation());
	}
    }

    void processElementNode(LocalityBinding lb, FlatElementNode fen, Hashtable<TempDescriptor, Integer> currtable, boolean isatomic) {
	Integer type=currtable.get(fen.getSrc());
	TempDescriptor dst=fen.getDst();
	if (type.equals(LOCAL)) {
	    currtable.put(dst,LOCAL);
	} else if (type.equals(GLOBAL)) {
	    if (!isatomic)
		throw new Error("Global access outside of a transaction in context:\n"+lb.getExplanation());
	    currtable.put(dst, GLOBAL);
	} else if (type.equals(EITHER)) {
	    currtable.put(dst, EITHER);
	} else if (type.equals(CONFLICT)) {
	    throw new Error("Access to object that could be either global or local in context:\n"+lb.getExplanation());
	}
    }

    void processAtomicEnterNode(FlatAtomicEnterNode fen, Hashtable<FlatNode, Integer> atomictable) {
	int atomic=atomictable.get(fen).intValue();
	atomictable.put(fen, new Integer(atomic+1));
    }

    void processAtomicExitNode(FlatAtomicExitNode fen, Hashtable<FlatNode, Integer> atomictable) {
	int atomic=atomictable.get(fen).intValue();
	atomictable.put(fen, new Integer(atomic-1));
    }

    private Hashtable<FlatNode, Set<TempDescriptor>> computeLiveTemps(FlatMethod fm) {
	Hashtable<FlatNode, Set<TempDescriptor>> nodetotemps=new Hashtable<FlatNode, Set<TempDescriptor>>();

	Set<FlatNode> toprocess=fm.getNodeSet();

	while(!toprocess.isEmpty()) {
	    FlatNode fn=toprocess.iterator().next();
	    toprocess.remove(fn);

	    List<TempDescriptor> reads=Arrays.asList(fn.readsTemps());
	    List<TempDescriptor> writes=Arrays.asList(fn.readsTemps());

	    HashSet<TempDescriptor> tempset=new HashSet<TempDescriptor>();
	    for(int i=0;i<fn.numNext();i++) {
		FlatNode fnnext=fn.getNext(i);
		if (nodetotemps.containsKey(fnnext))
		    tempset.addAll(nodetotemps.get(fnnext));
	    }
	    tempset.removeAll(writes);
	    tempset.addAll(reads);
	    if (!nodetotemps.containsKey(fn)||
		nodetotemps.get(fn).equals(tempset)) {
		nodetotemps.put(fn, tempset);
		for(int i=0;i<fn.numPrev();i++)
		    toprocess.add(fn.getPrev(i));
	    }
	}
	return nodetotemps;
    }

    /* Need to checkpoint all temps that could be read from along any
     * path that are either:
       1) Written to by any assignment inside the transaction
       2) Read from a global temp.

       Generate tempstosave map from
       localitybinding->flatatomicenternode->Set<TempDescriptors>
    */

    private void computeTempstoCheckpoint(LocalityBinding lb) {
	Hashtable<FlatNode, Integer> atomictab=getAtomic(lb);
	Hashtable<FlatNode, Hashtable<TempDescriptor, Integer>> temptab=getNodeTempInfo(lb);
	MethodDescriptor md=lb.getMethod();
	FlatMethod fm=state.getMethodFlat(md);

	Hashtable<FlatNode, Set<TempDescriptor>> nodetotemps=computeLiveTemps(fm);
	

    }
}
