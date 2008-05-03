package Analysis.Prefetch;

import java.util.*;
import Analysis.CallGraph.CallGraph;
import Analysis.Locality.LocalityAnalysis;
import Analysis.Prefetch.PrefetchPair;
import Analysis.Prefetch.PairMap;
import Analysis.Prefetch.IndexDescriptor;
import IR.SymbolTable;
import IR.State;
import IR.TypeUtil;
import IR.MethodDescriptor;
import IR.Flat.*;
import IR.*;
import IR.ClassDescriptor;

public class PrefetchAnalysis {
    State state;
    CallGraph callgraph;
    TypeUtil typeutil;
    LoopExit loop;
    
    Set<FlatNode> tovisit;
    public Hashtable<FlatNode, Hashtable<PrefetchPair, Double>> prefetch_hash;//holds all flatnodes and corresponding prefetch set
    public Hashtable<FlatNode, Hashtable<FlatNode, PairMap>> pmap_hash;//holds all flatnodes and mappings between child prefetch pair and parent prefetch pair
    public static final double PROB_DIFF = 0.05;	//threshold for difference in probabilities during first phase of analysis
    public static final double ANALYSIS_THRESHOLD_PROB = 0.10; //threshold for prefetches to stop propagating during first phase of analysis
    public static final double PREFETCH_THRESHOLD_PROB = 0.30;//threshold for prefetches to stop propagating while applying prefetch rules during second phase of analysis
    LocalityAnalysis locality;

    public PrefetchAnalysis(State state, CallGraph callgraph, TypeUtil typeutil, LocalityAnalysis locality) {
	this.typeutil=typeutil;
	this.state=state;
	this.callgraph=callgraph;
	this.locality=locality;
	prefetch_hash = new Hashtable<FlatNode, Hashtable<PrefetchPair,Double>>();
	pmap_hash = new Hashtable<FlatNode, Hashtable<FlatNode, PairMap>>();
	this.loop=new LoopExit(state);
	DoPrefetch();
    }
    
    
    /** This function starts the prefetch analysis */
    private void DoPrefetch() {
	for (Iterator methodit=locality.getMethods().iterator();methodit.hasNext();) {
	    MethodDescriptor md=(MethodDescriptor)methodit.next();
	    if (state.excprefetch.contains(md.getClassMethodName()))
		continue; //Skip this method
	    Hashtable<FlatNode, HashSet<PrefetchPair>> newprefetchset = new Hashtable<FlatNode, HashSet<PrefetchPair>>();
	    FlatMethod fm=state.getMethodFlat(md);
	    doFlatNodeAnalysis(fm);
	    doInsPrefetchAnalysis(fm, newprefetchset);
	    if(newprefetchset.size() > 0) {
		addFlatPrefetchNode(newprefetchset);
	    }
	    newprefetchset = null;
	}
    }
    
    /** This function calls analysis for every node in a method */
    private void doFlatNodeAnalysis(FlatMethod fm) {
	tovisit = fm.getNodeSet(); 
	Hashtable<PrefetchPair, Double> nodehash = new Hashtable<PrefetchPair, Double>();
	/* Create Empty Prefetch Sets for all flat nodes in the global hashtable */
	while(!tovisit.isEmpty()) {
	    FlatNode fn = (FlatNode)tovisit.iterator().next();
	    prefetch_hash.put(fn, nodehash);
	    tovisit.remove(fn);
	}

	/* Visit and process nodes */
	tovisit = fm.getNodeSet(); 
	while(!tovisit.isEmpty()) {
	    FlatNode fn = (FlatNode)tovisit.iterator().next();
	    doChildNodeAnalysis(fm.getMethod(),fn);
	    tovisit.remove(fn);
	}
    }
    
    /**
     * This function generates the prefetch sets for a given Flatnode considering the kind of node
     * It calls severals functions based on the kind of the node and 
     * returns true: if the prefetch set has changed since last time the node was analysed
     * returns false : otherwise 
     */ 
    private void doChildNodeAnalysis(MethodDescriptor md, FlatNode curr) {
	if (curr.kind()==FKind.FlatCondBranch) {
	    processFlatCondBranch((FlatCondBranch)curr, md);
	} else {
	    Hashtable<PrefetchPair, Double> child_prefetch_set_copy = new Hashtable<PrefetchPair, Double>();
	    if(curr.numNext() != 0) {
		FlatNode child_node = curr.getNext(0);
		if(prefetch_hash.containsKey(child_node)) {
		    child_prefetch_set_copy = (Hashtable<PrefetchPair,Double>) prefetch_hash.get(child_node).clone();
		}

	    }
	    switch(curr.kind()) {
	    case FKind.FlatCall:
		processCall((FlatCall)curr,child_prefetch_set_copy);
		break;

	    case FKind.FlatBackEdge:
	    case FKind.FlatCheckNode:
	    case FKind.FlatReturnNode:
	    case FKind.FlatAtomicEnterNode:
	    case FKind.FlatAtomicExitNode:
	    case FKind.FlatFlagActionNode:
	    case FKind.FlatGlobalConvNode:
	    case FKind.FlatNop:
	    case FKind.FlatNew:
	    case FKind.FlatCastNode:
	    case FKind.FlatTagDeclaration:
		processDefaultCase(curr,child_prefetch_set_copy);
		break;
	    case FKind.FlatMethod:
		//TODO change it to take care of FlatMethod, Flatcalls 
		processFlatMethod(curr, child_prefetch_set_copy);
		break;
	    case FKind.FlatFieldNode:
		processFlatFieldNode(curr, child_prefetch_set_copy);
		break;
	    case FKind.FlatElementNode:
		processFlatElementNode(curr, child_prefetch_set_copy);
		break;
	    case FKind.FlatOpNode:
		processFlatOpNode(curr, child_prefetch_set_copy);
		break;
	    case FKind.FlatLiteralNode:
		processFlatLiteralNode(curr, child_prefetch_set_copy);
		break;
	    case FKind.FlatSetElementNode:
		processFlatSetElementNode(curr, child_prefetch_set_copy);
		break;
	    case FKind.FlatSetFieldNode:
		processFlatSetFieldNode(curr, child_prefetch_set_copy);
		break;
	    default:
		throw new Error("No such Flatnode kind");
	    }
	}
    }
    
    /**This function compares all the prefetch pairs in a Prefetch set hashtable and
     * returns: true if something has changed in the new Prefetch set else
     * returns: false
     */
    private boolean comparePrefetchSets(Hashtable<PrefetchPair, Double> oldPrefetchSet, Hashtable<PrefetchPair, Double> newPrefetchSet) {
	if (oldPrefetchSet.size()!=newPrefetchSet.size())
	    return true;

	for(Enumeration e = newPrefetchSet.keys();e.hasMoreElements();) {
	    PrefetchPair pp = (PrefetchPair) e.nextElement();
	    double newprob = newPrefetchSet.get(pp).doubleValue();
	    if (!oldPrefetchSet.containsKey(pp))
		return true;
	    double oldprob = oldPrefetchSet.get(pp).doubleValue();
	    
	    if((newprob - oldprob) > PROB_DIFF) {
		return true;
	    }
	    if (newprob >= PREFETCH_THRESHOLD_PROB && oldprob < PREFETCH_THRESHOLD_PROB) {
		return true;
	    }
	    if (oldprob>newprob) {
		System.out.println("ERROR:" + pp);
		System.out.println(oldprob + " -> "+ newprob);
	    }
	}
	return false;
    }

    private void updatePairMap(FlatNode curr, PairMap pm, int index) {
	if (index>=curr.numNext())
	    return;
	if (!pmap_hash.containsKey(curr.getNext(index))) {
	    pmap_hash.put(curr.getNext(index), new Hashtable<FlatNode, PairMap>());
	}
	pmap_hash.get(curr.getNext(index)).put(curr, pm);
    }

    private void updatePrefetchSet(FlatNode curr, Hashtable<PrefetchPair, Double> newset) {
	Hashtable<PrefetchPair, Double>oldset=prefetch_hash.get(curr);
	if (comparePrefetchSets(oldset, newset)) {
	    for(int i=0;i<curr.numPrev();i++) {
		tovisit.add(curr.getPrev(i));
	    }
	    prefetch_hash.put(curr, newset);
	}
    }

    
    /** This function processes the prefetch set of FlatFieldNode
     * It generates a new prefetch set after comparision with its children
     * Then compares it with its old prefetch set
     * If old prefetch set is not same as new prefetch set then enqueue the parents 
     * of the current FlatFieldNode
     * */
    private void processFlatFieldNode(FlatNode curr, Hashtable<PrefetchPair, Double> child_prefetch_set_copy) {
	Hashtable<PrefetchPair, Double> currcopy = new Hashtable<PrefetchPair, Double>();
	Hashtable<PrefetchPair, Double> tocompare = new Hashtable<PrefetchPair, Double>();
	FlatFieldNode currffn = (FlatFieldNode) curr;
	PairMap pm = new PairMap();
	
	/* Do Self analysis of the current node*/
	FieldDescriptor currffn_field =  currffn.getField();
	TempDescriptor currffn_src = currffn.getSrc();
	if (currffn_field.getType().isPtr()) {
	    PrefetchPair pp = new PrefetchPair(currffn_src, (Descriptor) currffn_field);
	    Double prob = new Double(1.0);
	    tocompare.put(pp, prob);
	}
	
	/* Get each prefetch pair of the child and match it with the destination temp descriptor of curr FlatFieldNode */

	for (Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
	    PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
	    if (childpp.base == currffn.getDst() && (childpp.getDesc()!= null)) {
		if (currffn.getField().getType().isPtr()) {
		    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
		    newdesc.add(currffn.getField());
		    newdesc.addAll(childpp.desc);
		    PrefetchPair newpp =  new PrefetchPair(currffn.getSrc(), newdesc);
		    Double newprob = child_prefetch_set_copy.get(childpp).doubleValue();
		    if (tocompare.containsKey(newpp)) {
			Double oldprob=tocompare.get(newpp);
			newprob=1.0-(1.0-oldprob)*(1.0-newprob);
		    }
		    tocompare.put(newpp, newprob); 
		    pm.addPair(childpp, newpp);
		}
		//drop if not ptr
	    } else if(childpp.base == currffn.getDst() && (childpp.getDesc() == null)) {
		//covered by current prefetch
		child_prefetch_set_copy.remove(childpp);
	    } else if(childpp.containsTemp(currffn.getDst())) {
		child_prefetch_set_copy.remove(childpp);
	    } else {
		Double newprob = child_prefetch_set_copy.get(childpp).doubleValue();
		if (tocompare.containsKey(childpp)) {
		    Double oldprob=tocompare.get(childpp);
		    newprob=1.0-(1.0-oldprob)*(1.0-newprob);
		}
		tocompare.put(childpp, newprob); 
		pm.addPair(childpp, childpp);
	    }
	}

	for(Iterator<PrefetchPair> it=tocompare.keySet().iterator();it.hasNext();) {
	    PrefetchPair pp=it.next();
	    if (tocompare.get(pp)<ANALYSIS_THRESHOLD_PROB)
		it.remove();
	    
	}
	
	updatePairMap(curr, pm, 0);
	updatePrefetchSet(curr, tocompare);
    }
    
    /** This function processes the prefetch set of a FlatElementNode
     * It generates a new prefetch set after comparision with its children
     * It compares the old prefetch set with this new prefetch set and enqueues the parents 
     * of the current node if change occurs and updates the global flatnode hash table
     * */
    private void processFlatElementNode(FlatNode curr, Hashtable<PrefetchPair, Double> child_prefetch_set_copy) {
	
	Hashtable<PrefetchPair, Double> currcopy = new Hashtable<PrefetchPair, Double>();
	Hashtable<PrefetchPair, Double> tocompare = new Hashtable<PrefetchPair, Double>();
	FlatElementNode currfen = (FlatElementNode) curr;
	PairMap pm = new PairMap();
	
	
	/* Do Self analysis of the current node*/
	TempDescriptor currfen_index = currfen.getIndex();
	IndexDescriptor idesc = new IndexDescriptor(currfen_index, 0);
	TempDescriptor currfen_src = currfen.getSrc();
	if(currfen.getDst().getType().isPtr()) {
	    PrefetchPair pp = new PrefetchPair(currfen_src, (Descriptor) idesc);
	    Double prob = new Double(1.0);
	    currcopy.put(pp, prob);
	}
	
	/* Get each prefetch pair of the child and match it with the destination temp descriptor of curr FlatFieldNode */
	PrefetchPair currpp = null;
	for (Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
	    PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
	    if (childpp.base == currfen.getDst() && (childpp.getDesc()!= null)) {
		if (currfen.getDst().getType().isPtr()) {
		    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
		    newdesc.add((Descriptor)idesc);
		    newdesc.addAll(childpp.desc);
		    PrefetchPair newpp =  new PrefetchPair(currfen.getSrc(), newdesc);
		    Double newprob = child_prefetch_set_copy.get(childpp).doubleValue();
		    tocompare.put(newpp, newprob); 
		    pm.addPair(childpp, newpp);
		    child_prefetch_set_copy.remove(childpp);
		    /* Check for independence of prefetch pairs to compute new probability */
		    if(child_prefetch_set_copy.containsKey(newpp)) {
			newprob = (1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).doubleValue()) * (1.0 - tocompare.get(newpp).doubleValue())));
			if(newprob < ANALYSIS_THRESHOLD_PROB) {
			    tocompare.remove(newpp);
			} else {
			    tocompare.put(newpp, newprob); 
			    pm.addPair(newpp, newpp);
			}
			child_prefetch_set_copy.remove(newpp);
		    }
		}
	    } else if(childpp.base == currfen.getDst() && (childpp.getDesc() == null)) {
		child_prefetch_set_copy.remove(childpp);
	    } else if(childpp.containsTemp(currfen.getDst())) {
		child_prefetch_set_copy.remove(childpp);
	    } else {
		continue;
	    }
	}
	/* Check if curr prefetch set and the child prefetch set have same prefetch pairs
	 * if so calculate the new probability */ 
	for(Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
	    PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
	    for(Enumeration e = currcopy.keys(); e.hasMoreElements();) {
		currpp = (PrefetchPair) e.nextElement();
		if(currpp.equals(childpp)) {
		    pm.addPair(childpp, currpp);
		    child_prefetch_set_copy.remove(childpp);
		    break;
		} 
	    }
	}
	
	/* Merge child prefetch pairs */
	for (Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
	    PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
	    tocompare.put(childpp, child_prefetch_set_copy.get(childpp).doubleValue());
	    pm.addPair(childpp, childpp);
	    child_prefetch_set_copy.remove(childpp);
	}
	
	/* Merge curr prefetch pairs */
	for (Enumeration e = currcopy.keys();e.hasMoreElements();) {
	    currpp = (PrefetchPair) e.nextElement();
	    tocompare.put(currpp, currcopy.get(currpp).doubleValue());  
	    currcopy.remove(currpp);
	}
	
	updatePairMap(curr, pm, 0);
	updatePrefetchSet(curr, tocompare);
    }
    
    /** This function processes the prefetch set of a FlatSetFieldNode
     * It generates a new prefetch set after comparision with its children
     * It compares the old prefetch set with this new prefetch set and enqueues the parents 
     * of the current node if change occurs and then updates the global flatnode hash table
     * */
    private void processFlatSetFieldNode(FlatNode curr, Hashtable<PrefetchPair, Double> child_prefetch_set_copy) {
	Hashtable<PrefetchPair, Double> tocompare = new Hashtable<PrefetchPair, Double>();
	FlatSetFieldNode currfsfn = (FlatSetFieldNode) curr;
	PairMap pm = new PairMap();
	
	for (Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
	    PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
	    if(childpp.base == currfsfn.getDst()) {
		int size = childpp.desc.size();
		if(size >=2) { /*e.g. x.f = g (with child prefetches x.f.g, x.f[0].j) */
		    if((childpp.getDescAt(0) instanceof FieldDescriptor) && (childpp.getDescAt(0) == currfsfn.getField())) { 
			ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
			for(int i = 0;i<(childpp.desc.size()-1); i++) {
			    newdesc.add(i,childpp.desc.get(i+1));
			}
			PrefetchPair newpp =  new PrefetchPair(currfsfn.getSrc(), newdesc);
			Double newprob = child_prefetch_set_copy.get(childpp).doubleValue();
			tocompare.put(newpp, newprob); 
			pm.addPair(childpp, newpp);
			child_prefetch_set_copy.remove(childpp);
			/* Check for independence of prefetch pairs in newly generated prefetch pair 
			 * to compute new probability */
			if(child_prefetch_set_copy.containsKey(newpp)) {
			    newprob = (1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).doubleValue()) * (1.0 - tocompare.get(newpp).doubleValue())));
			    if(newprob < ANALYSIS_THRESHOLD_PROB) {
				tocompare.remove(newpp);
			    } else {
				tocompare.put(newpp, newprob); 
				pm.addPair(newpp, newpp);
			    }
			    child_prefetch_set_copy.remove(newpp);
			}
		    }
		} else if(size==1) { /* e.g x.f = g (with child prefetch x.f) */
		    if((childpp.getDescAt(0) instanceof FieldDescriptor) && (childpp.getDescAt(0) == currfsfn.getField())) {
			child_prefetch_set_copy.remove(childpp);
		    }
		} else {
		    continue;
		}
	    }
	}
	
	/* Merge child prefetch pairs */
	for (Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
	    PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
	    tocompare.put(childpp, child_prefetch_set_copy.get(childpp).doubleValue());
	    pm.addPair(childpp, childpp);
	    child_prefetch_set_copy.remove(childpp);
	}
	
	updatePairMap(curr, pm, 0);
	updatePrefetchSet(curr, tocompare);
    }
    
    /** This function processes the prefetch set of a FlatSetElementNode
     * It generates a new prefetch set after comparision with its children
     * It compares the old prefetch set with this new prefetch set and enqueues the parents 
     * of the current node if change occurs and then updates the global flatnode hash table
     * */
    private void processFlatSetElementNode(FlatNode curr, Hashtable<PrefetchPair, Double> child_prefetch_set_copy) {
	Hashtable<PrefetchPair, Double> tocompare = new Hashtable<PrefetchPair, Double>();
	FlatSetElementNode currfsen = (FlatSetElementNode) curr;
	PairMap pm = new PairMap();
	
	for (Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
	    PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
	    if (childpp.base == currfsen.getDst()){
		int sizedesc = childpp.desc.size();
		if((childpp.getDescAt(0) instanceof IndexDescriptor)) {
		    int sizetempdesc = ((IndexDescriptor)(childpp.getDescAt(0))).tddesc.size();
		    if(sizetempdesc == 1) { 
			if((((IndexDescriptor)childpp.getDescAt(0)).tddesc.get(0) == currfsen.getIndex()) && (sizedesc>=2)) {
			    /* For e.g. a[i] = g with child prefetch set a[i].r or a[i].r.f */
			    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
			    for(int i = 0;i<(childpp.desc.size()-1); i++) {
				newdesc.add(i,childpp.desc.get(i+1));
			    }
			    PrefetchPair newpp =  new PrefetchPair(currfsen.getSrc(), newdesc);
			    Double newprob = child_prefetch_set_copy.get(childpp).doubleValue();
			    tocompare.put(newpp, newprob); 
			    pm.addPair(childpp, newpp);
			    child_prefetch_set_copy.remove(childpp);
			    /* Check for independence of prefetch pairs to compute new probability */
			    if(child_prefetch_set_copy.containsKey(newpp)) {
				newprob = (1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).doubleValue()) * (1.0 - tocompare.get(newpp).doubleValue())));
				if(newprob < ANALYSIS_THRESHOLD_PROB) {
				    tocompare.remove(newpp);
				} else {
				    tocompare.put(newpp, newprob); 
				    pm.addPair(newpp, newpp);
				}
				child_prefetch_set_copy.remove(newpp);
			    }
			} else if((((IndexDescriptor)childpp.getDescAt(0)).tddesc.get(0) == currfsen.getIndex()) && (sizedesc==1)) 
			    /* For e.g. a[i] = g with child prefetch set a[i] */
			    child_prefetch_set_copy.remove(childpp);
		    } else {
			continue;
		    }
		}
	    }
	}
	/* Merge child prefetch pairs */
	for (Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
	    PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
	    tocompare.put(childpp, child_prefetch_set_copy.get(childpp).doubleValue());
	    pm.addPair(childpp, childpp);
	    child_prefetch_set_copy.remove(childpp);
	}
	
	updatePairMap(curr, pm, 0);
	updatePrefetchSet(curr, tocompare);
    }

	/** This function applies rules and does analysis for a FlatOpNode 
	 *  And updates the global prefetch hashtable
	 * */
    private void processFlatOpNode(FlatNode curr, Hashtable<PrefetchPair, Double> child_prefetch_set_copy) {
	int index;
	Hashtable<PrefetchPair, Double> tocompare = new Hashtable<PrefetchPair, Double>();
	FlatOpNode currfopn = (FlatOpNode) curr;
	PairMap pm = new PairMap();
	
	if(currfopn.getOp().getOp() == Operation.ASSIGN) {
	    for (Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
		PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
		PrefetchPair copyofchildpp = (PrefetchPair) childpp.clone();
		
		/* For cases like x=y  with child prefetch set x[i].z,x.g*/
		if(childpp.base == currfopn.getDest()) {
		    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
		    newdesc.addAll(childpp.desc);
		    PrefetchPair newpp =  new PrefetchPair(currfopn.getLeft(), newdesc);
		    Double newprob = child_prefetch_set_copy.get(childpp).doubleValue();
		    tocompare.put(newpp, newprob); 
		    pm.addPair(childpp, newpp);
		    child_prefetch_set_copy.remove(childpp);
		    /* Check for independence of prefetch pairs to compute new probability */
		    if(child_prefetch_set_copy.containsKey(newpp)) {
			newprob = (1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).doubleValue()) * (1.0 - tocompare.get(newpp).doubleValue())));
			if(newprob < ANALYSIS_THRESHOLD_PROB) {
			    tocompare.remove(newpp);
			} else {
			    tocompare.put(newpp, newprob); 
			    pm.addPair(newpp, newpp);
			}
			child_prefetch_set_copy.remove(newpp);
		    }
		    /* For cases like x=y  with child prefetch set r[x].p, r[p+x].q where x is a  tempdescriptor*/
		} else if(copyofchildpp.containsTemp(currfopn.getDest())) {
		    PrefetchPair newpp=copyofchildpp.replaceTemp(currfopn.getDest(), new TempDescriptor[] {currfopn.getLeft()});
		    Double newprob = child_prefetch_set_copy.get(childpp).doubleValue();
		    tocompare.put(newpp, newprob); 
		    pm.addPair(childpp, newpp);
		    child_prefetch_set_copy.remove(childpp);
		    /* Check for independence of prefetch pairs to compute new probability*/ 
		    if(child_prefetch_set_copy.containsKey(newpp)) {
			newprob = (1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).doubleValue()) * (1.0 - tocompare.get(newpp).doubleValue())));
			if(newprob < ANALYSIS_THRESHOLD_PROB) {
			    tocompare.remove(newpp);
			} else {
			    tocompare.put(newpp, newprob); 
			    pm.addPair(newpp, newpp);
			}
			child_prefetch_set_copy.remove(newpp);
		    }
		    newpp = null;
		} else {
		    continue;
		}
	    }
	    //case i = i+z with child prefetch set a[i].x
	} else if(currfopn.getRight()!=null && (currfopn.getOp().getOp() == Operation.ADD)) {
	    for (Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
		PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
		PrefetchPair copyofchildpp = (PrefetchPair) childpp.clone();
		
		if(copyofchildpp.containsTemp(currfopn.getDest())) {
		    PrefetchPair newpp=copyofchildpp.replaceTemp(currfopn.getDest(), new TempDescriptor[] {currfopn.getLeft(), currfopn.getRight()});
		    Double newprob = child_prefetch_set_copy.get(childpp).doubleValue();
		    tocompare.put(newpp, newprob); 
		    pm.addPair(childpp, newpp);
		    child_prefetch_set_copy.remove(childpp);
		    /* Check for independence of prefetch pairs to compute new probability*/ 
		    if(child_prefetch_set_copy.containsKey(newpp)) {
			newprob = (1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).doubleValue()) * (1.0 - tocompare.get(newpp).doubleValue())));
			if(newprob < ANALYSIS_THRESHOLD_PROB) {
			    tocompare.remove(newpp);
			} else {
			    tocompare.put(newpp, newprob); 
			    pm.addPair(newpp, newpp);
			}
			child_prefetch_set_copy.remove(newpp);
		    }
		} else {
		    continue;
		}
	    }
	} else if(currfopn.getRight()!=null && (currfopn.getOp().getOp() == Operation.SUB)) {
        for(Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
            PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
            if(childpp.containsTemp(currfopn.getDest())) {
                child_prefetch_set_copy.remove(childpp);
            }
        }
    } else {
	    //FIXME Is not taken care of for cases like x = -y followed by a[x].i
	}
	
	/* Merge child prefetch pairs */
	for (Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
	    PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
	    tocompare.put(childpp, child_prefetch_set_copy.get(childpp).doubleValue());
	    pm.addPair(childpp, childpp);
	    child_prefetch_set_copy.remove(childpp);
	}
	
	updatePairMap(curr, pm, 0);
	updatePrefetchSet(curr, tocompare);
    }
    
    /** This function processes a FlatLiteralNode where cases such as
     * for e.g. i = 0 with child prefetch sets a[i].r, a[j+i].r or a[j].b[i].r
     * are handled */
    private void processFlatLiteralNode(FlatNode curr, Hashtable<PrefetchPair, Double> child_prefetch_set_copy) {
	Hashtable<PrefetchPair, Double> tocompare = new Hashtable<PrefetchPair, Double>();
	FlatLiteralNode currfln = (FlatLiteralNode) curr;
	PairMap pm = new PairMap();
	
	if(currfln.getType().isIntegerType()) {
	    for (Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
		PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
		PrefetchPair copyofchildpp = (PrefetchPair) childpp.clone();
		if(copyofchildpp.containsTemp(currfln.getDst())) {
		    ArrayList<Descriptor> copychilddesc = (ArrayList<Descriptor>) copyofchildpp.getDesc();
		    int sizetempdesc = copychilddesc.size();
		    for(ListIterator it = copychilddesc.listIterator();it.hasNext();) {
			Object o = it.next();
			if(o instanceof IndexDescriptor) {
			    ArrayList<TempDescriptor> td = (ArrayList<TempDescriptor>)((IndexDescriptor)o).tddesc;
			    int sizetddesc = td.size();
			    if(td.contains(currfln.getDst())) {
				int index = td.indexOf(currfln.getDst());
				td.remove(index);
				((IndexDescriptor)o).offset += (Integer)currfln.getValue();
			    }
			}
		    }
		    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
		    newdesc.addAll(copychilddesc);
		    PrefetchPair newpp =  new PrefetchPair(childpp.base, newdesc);
		    Double newprob = (child_prefetch_set_copy.get(childpp)).doubleValue();
		    tocompare.put(newpp, newprob); 
		    pm.addPair(childpp, newpp);
		    child_prefetch_set_copy.remove(childpp);
		    /* Check for independence of prefetch pairs to compute new probability */
		    if(child_prefetch_set_copy.containsKey(newpp)) {
			newprob = (1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).doubleValue()) * (1.0 - tocompare.get(newpp).doubleValue())));
			if(newprob < ANALYSIS_THRESHOLD_PROB) {
			    tocompare.remove(newpp);
			} else {
			    tocompare.put(newpp, newprob); 
			    pm.addPair(newpp, newpp);
			}
			child_prefetch_set_copy.remove(newpp);
		    }
		}
	    }
	}
	
	/* Merge child prefetch pairs */
	for (Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
	    PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
	    tocompare.put(childpp, child_prefetch_set_copy.get(childpp).doubleValue());
	    pm.addPair(childpp, childpp);
	    child_prefetch_set_copy.remove(childpp);
	}
	
	updatePairMap(curr, pm, 0);
	updatePrefetchSet(curr, tocompare);
    }
    
    /** This function processes a FlatMethod where the method propagates
     * the entire prefetch set of its child node */
    private void processFlatMethod(FlatNode curr, Hashtable<PrefetchPair, Double> child_prefetch_set_copy) {
	Hashtable<PrefetchPair, Double> tocompare = new Hashtable<PrefetchPair, Double>();
	FlatMethod currfm = (FlatMethod) curr;
	PairMap pm = new PairMap();
	
	/* Merge child prefetch pairs */
	for (Enumeration ecld = child_prefetch_set_copy.keys();ecld.hasMoreElements();) {
	    PrefetchPair childpp = (PrefetchPair) ecld.nextElement();
	    tocompare.put(childpp, child_prefetch_set_copy.get(childpp).doubleValue());
	    pm.addPair(childpp, childpp);
	}
	
	updatePairMap(curr, pm, 0);
	updatePrefetchSet(curr, tocompare);
    }
    
    /** This function handles the processes the FlatNode of type FlatCondBranch
     * It combines prefetches of both child elements and create a new hash table called
     * branch_prefetch_set to contains the entries of both its children
     */
    private void processFlatCondBranch(FlatCondBranch fcb, MethodDescriptor md) {
	Hashtable<PrefetchPair, Double> tocompare = new Hashtable<PrefetchPair, Double>();//temporary hash table
	PairMap truepm = new PairMap();
	PairMap falsepm = new PairMap();
	Hashtable<PrefetchPair, Double> truechild=prefetch_hash.get(fcb.getNext(0));
	Hashtable<PrefetchPair, Double> falsechild=prefetch_hash.get(fcb.getNext(1));
	
	HashSet<PrefetchPair> allpp=new HashSet<PrefetchPair>();
	allpp.addAll(truechild.keySet());
	allpp.addAll(falsechild.keySet());
	
	for(Iterator<PrefetchPair> ppit=allpp.iterator();ppit.hasNext();) {
	    PrefetchPair pp=ppit.next();
	    double trueprob=0,falseprob=0;
	    if (truechild.containsKey(pp))
		trueprob=truechild.get(pp).doubleValue();
	    if (falsechild.containsKey(pp))
		falseprob=falsechild.get(pp).doubleValue();

	    double newprob=trueprob*fcb.getTrueProb()+falseprob*fcb.getFalseProb();
	    if (loop.isLoopingBranch(md,fcb)&&
		newprob<falseprob) {
		newprob=falseprob;
	    }
	    
	    if(newprob < ANALYSIS_THRESHOLD_PROB) //Skip pp that are below threshold
		continue;

	    tocompare.put(pp, newprob);
	    if (truechild.containsKey(pp))
		truepm.addPair(pp, pp);

	    if (falsechild.containsKey(pp))
		falsepm.addPair(pp, pp);

	}
	
	updatePairMap(fcb, truepm, 0);
	updatePairMap(fcb, falsepm, 1);
	updatePrefetchSet(fcb, tocompare);
    }
    
    /** If FlatNode is not concerned with the prefetch set of its Child then propagate 
     * prefetches up the FlatNode*/  
    private void processDefaultCase(FlatNode curr, Hashtable<PrefetchPair, Double> child_prefetch_set_copy) {
	PairMap pm = new PairMap();
	Hashtable<PrefetchPair, Double> tocompare = new Hashtable<PrefetchPair, Double>();
	
	/* Propagate all child nodes */
	nexttemp:
	for(Enumeration e = child_prefetch_set_copy.keys(); e.hasMoreElements();) {
	    PrefetchPair childpp = (PrefetchPair) e.nextElement();
	    TempDescriptor[] writearray=curr.writesTemps();
	    for(int i=0;i<writearray.length;i++) {
		TempDescriptor wtd=writearray[i];
		if(childpp.base == wtd||
		   childpp.containsTemp(wtd))
		    continue nexttemp;
	    }
	    tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
	    pm.addPair(childpp, childpp);
	}
	
	updatePairMap(curr, pm, 0);
	updatePrefetchSet(curr, tocompare);
    }

    /** If FlatNode is not concerned with the prefetch set of its Child then propagate 
     * prefetches up the FlatNode*/  
    private void processCall(FlatCall curr, Hashtable<PrefetchPair, Double> child_prefetch_set_copy) {
	PairMap pm = new PairMap();
	Hashtable<PrefetchPair, Double> tocompare = new Hashtable<PrefetchPair, Double>();
	
	/* Don't propagate prefetches across cache clear */
	if (!curr.getMethod().getClassMethodName().equals("System.clearPrefetchCache")) {
	/* Propagate all child nodes */
	nexttemp:
	for(Enumeration e = child_prefetch_set_copy.keys(); e.hasMoreElements();) {
	    PrefetchPair childpp = (PrefetchPair) e.nextElement();
	    TempDescriptor[] writearray=curr.writesTemps();
	    for(int i=0;i<writearray.length;i++) {
		TempDescriptor wtd=writearray[i];
		if(childpp.base == wtd||
		   childpp.containsTemp(wtd))
		    continue nexttemp;
	    }
	    tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
	    pm.addPair(childpp, childpp);
	}
	
	}
	updatePairMap(curr, pm, 0);
	updatePrefetchSet(curr, tocompare);
    }
    
    /** This function prints the Prefetch pairs of a given flatnode */
    private void printPrefetchPairs(FlatNode fn) {
	System.out.println(fn);
	if(prefetch_hash.containsKey(fn)) {
	    System.out.print("Prefetch" + "(");
	    Hashtable<PrefetchPair, Double> currhash = (Hashtable) prefetch_hash.get(fn);
	    for(Enumeration pphash= currhash.keys(); pphash.hasMoreElements();) {
		PrefetchPair pp = (PrefetchPair) pphash.nextElement();
		System.out.print(pp.toString() + ", ");
	    }
	    System.out.println(")");
	} else {
	    System.out.println("Flatnode is currently not present in the global hash: Prefetch Set is Empty");
	}
    }

    private void doInsPrefetchAnalysis(FlatMethod fm, Hashtable<FlatNode, HashSet<PrefetchPair>> newprefetchset) {
	Hashtable<FlatNode, HashSet<PrefetchPair>> pset1_hash = new Hashtable<FlatNode, HashSet<PrefetchPair>>();
	HashSet<PrefetchPair> pset1_init = new HashSet<PrefetchPair>();
	LinkedList<FlatNode> newtovisit = new LinkedList<FlatNode>();  
	LinkedList<FlatNode> newvisited = new LinkedList<FlatNode>();  
	
	newtovisit.addLast((FlatNode)fm);
	while(!newtovisit.isEmpty()) {
	    FlatNode fn = (FlatNode) newtovisit.iterator().next();
	    newtovisit.remove(0);
	    pset1_hash.put(fn, pset1_init); //Initialize pset1_hash
	    newvisited.addLast(fn);
	    for(int i=0; i<fn.numNext(); i++) {
		FlatNode nn = fn.getNext(i);
		if(!newtovisit.contains(nn) && !newvisited.contains(nn)){
		    newtovisit.addLast(nn);
		}
	    }
	}
	
	/* Start with a top down sorted order of nodes */
	while(!newvisited.isEmpty()) {
	    applyPrefetchInsertRules((FlatNode) newvisited.getFirst(), newvisited, pset1_hash, newprefetchset);
	    newvisited.remove(0);
	}
	delSubsetPPairs(newprefetchset);
    }
    
    /** This function deletes the smaller prefetch pair subset from a list of prefetch pairs 
     * for e.g. if there are 2 prefetch pairs a.b.c.d and a.b.c for a given flatnode
     * then this function drops a.b.c from the prefetch set of the flatnode */
    private void delSubsetPPairs(Hashtable<FlatNode, HashSet<PrefetchPair>> newprefetchset) {
	for (Enumeration e = newprefetchset.keys();e.hasMoreElements();) {
	    FlatNode fn = (FlatNode) e.nextElement();
	    Set<PrefetchPair> ppairs = newprefetchset.get(fn);
	    Set<PrefetchPair> toremove=new HashSet<PrefetchPair>();

	    for(Iterator<PrefetchPair> it1=ppairs.iterator();it1.hasNext();) {
		PrefetchPair pp1=it1.next();
		if (toremove.contains(pp1))
		    continue;
		int l1=pp1.desc.size()+1;
		for(Iterator<PrefetchPair> it2=ppairs.iterator();it2.hasNext();) {
		    PrefetchPair pp2=it2.next();
		    int l2=pp2.desc.size()+1;

		    if (l1<l2&&isSubSet(pp1,pp2))
			toremove.add(pp1);
		    else
			if (l2>l1&&isSubSet(pp2,pp1))
			    toremove.add(pp2);
		}
	    }
	    
	    ppairs.removeAll(toremove);
	}
    }
    
    /** This function returns: true if the shorter prefetch pair is a subset of the longer prefetch
     * pair else it returns: false */
    private boolean isSubSet(PrefetchPair shrt, PrefetchPair lng) {
	if (shrt.base != lng.base) {
	    return false;
	}
	for (int j = 0; j < shrt.desc.size(); j++) {
	    if(shrt.getDescAt(j) instanceof IndexDescriptor) {
		IndexDescriptor shrtid = (IndexDescriptor) shrt.getDescAt(j);
		if(lng.getDescAt(j) instanceof IndexDescriptor){
		    IndexDescriptor lngid = (IndexDescriptor) lng.getDescAt(j);
		    if(shrtid.equals(lngid)) {
			continue;
		    } else {
			return false;
		    }
		} else {
		    return false;
		}
	    } else  {
		if ((Descriptor)shrt.getDescAt(j) != (Descriptor)lng.getDescAt(j)){
		    return false;
		}
	    }
	}
	return true;
    }

    /**This function compares all the prefetch pairs in a Prefetch set hashtable and
     * returns: true if something has changed in the new Prefetch set else
     * returns: false
     */
    private boolean comparePSet1(HashSet<PrefetchPair> oldPSet, HashSet<PrefetchPair>newPSet) {
	if(oldPSet.size() != newPSet.size()) {
	    return true;
	} else {
	    for(Iterator it = newPSet.iterator();it.hasNext();) {
		if(!oldPSet.contains((PrefetchPair)it.next())) {
		    return true;
		}
	    }
	}
	return false;
    }
    
    /** This function creates a set called pset1 that contains prefetch pairs that have already
     * been prefetched. While traversing the graph of a flat representation in a top down fashion,
     * this function creates pset1 such that it contains prefetch pairs that have been prefetched at
     * the previous nodes */
    
    private void applyPrefetchInsertRules(FlatNode fn, LinkedList<FlatNode> newvisited, Hashtable<FlatNode, HashSet<PrefetchPair>> pset1_hash, Hashtable<FlatNode, HashSet<PrefetchPair>> newprefetchset) {	
	if(fn.kind() == FKind.FlatMethod) {
	    HashSet<PrefetchPair> pset1 = new HashSet<PrefetchPair>();
	    Hashtable<PrefetchPair, Double> prefetchset = prefetch_hash.get(fn);
	    for(Enumeration e = prefetchset.keys();e.hasMoreElements();) {
		PrefetchPair pp = (PrefetchPair) e.nextElement();
		/* Apply initial rule */
		if(prefetchset.get(pp).doubleValue() >= PREFETCH_THRESHOLD_PROB) {
		    pset1.add(pp);
		}
	    }
	    /* Enqueue child node if Pset1 has changed */
	    if (comparePSet1(pset1_hash.get(fn), pset1)) {
		for(int j=0; j<fn.numNext(); j++) {
		    FlatNode nn = fn.getNext(j);
		    newvisited.addLast((FlatNode)nn);
		}
		pset1_hash.put(fn, pset1);
	    }
	    newprefetchset.put(fn, pset1); 
	} else { /* Nodes other than Flat Method Node */
	    HashSet<PrefetchPair> pset2 = new HashSet<PrefetchPair>();
	    HashSet<PrefetchPair> newpset = new HashSet<PrefetchPair>();
	    Hashtable<PrefetchPair, Double> prefetchset = prefetch_hash.get(fn);
	    Hashtable<FlatNode, PairMap> ppairmaphash = pmap_hash.get(fn);
	    for(Enumeration epset = prefetchset.keys(); epset.hasMoreElements();) {
		PrefetchPair pp = (PrefetchPair) epset.nextElement();
		boolean pprobIsGreater = (prefetchset.get(pp).doubleValue() >= PREFETCH_THRESHOLD_PROB);
		boolean mapprobIsLess=false;
		boolean mapIsPresent=true;
		for(int i=0;i<fn.numPrev();i++) {
		    FlatNode parentnode=fn.getPrev(i);
		    PairMap pm = (PairMap) ppairmaphash.get(parentnode);
		    //Find if probability is less for previous node
		    if(pm!=null&&pm.getPair(pp) != null) {
			PrefetchPair mappedpp = pm.getPair(pp);
			if(prefetch_hash.get(parentnode).containsKey(mappedpp)) {
			    double prob = prefetch_hash.get(parentnode).get(mappedpp).doubleValue();
			    if(prob < PREFETCH_THRESHOLD_PROB)
				mapprobIsLess = true;
			} else
			    mapprobIsLess = true;
		    } else {
			mapprobIsLess = true;
		    }
		    /* Build pset2 */
		    if(pm !=null) {
			HashSet pset = pset1_hash.get(parentnode);
			if(pset.isEmpty()||!pset.contains((PrefetchPair) pm.getPair(pp)))
			    mapIsPresent = false;
		    } else
			mapIsPresent=false;
		}
		
		if(mapIsPresent)
		    pset2.add(pp);
		
		if(pprobIsGreater && mapprobIsLess)
		    newpset.add(pp);
	    }
	    
	    HashSet<PrefetchPair> pset1 = new HashSet<PrefetchPair>();
	    pset1.addAll(pset2);
	    pset1.addAll(newpset);
	    /* Enqueue child node if Pset1 has changed */
	    if (comparePSet1(pset1_hash.get(fn), pset1)) {
		for(int i=0; i<fn.numNext(); i++) {
		    FlatNode nn = fn.getNext(i);
		    newvisited.addLast((FlatNode)nn);
		}
		pset1_hash.put(fn, pset1);
	    }
	    
	    /* To insert prefetch, apply rule: if the newpset minus pset2 is nonempty
	     * then insert a new prefetch node here*/

	    HashSet<PrefetchPair> s = new HashSet<PrefetchPair>();
	    s.addAll(newpset);
	    s.removeAll(pset2);
	    newprefetchset.put(fn, s); 
	}
    }

    private void addFlatPrefetchNode(Hashtable<FlatNode, HashSet<PrefetchPair>> newprefetchset) {
	boolean isFNPresent = false; /* Detects presence of FlatNew node */
	/* This modifies the Flat representation graph */
	for(Enumeration e = newprefetchset.keys();e.hasMoreElements();) {
	    FlatNode fn = (FlatNode) e.nextElement();
	    FlatPrefetchNode fpn = new FlatPrefetchNode();
	    if(newprefetchset.get(fn).size() > 0) {
		fpn.insAllpp((HashSet)newprefetchset.get(fn));
		if(fn.kind() == FKind.FlatMethod) {
		    FlatNode nn = fn.getNext(0);
		    fn.setNext(0, fpn);
		    fpn.addNext(nn);
		} else {
		    /* Check if previous node of this FlatNode is a NEW node 
		     * If yes, delete this flatnode and its prefetch set from hash table 
		     * This eliminates prefetches for NULL ptrs*/
		    for(int i = 0; i< fn.numPrev(); i++) {
			FlatNode nn = fn.getPrev(i);
			if(nn.kind() == FKind.FlatNew) {
			    isFNPresent = true;
			}
		    }
		    if(!isFNPresent) {
			while(fn.numPrev() > 0) {
			    FlatNode nn = fn.getPrev(0);
			    for(int j = 0; j<nn.numNext(); j++) {
				if(nn.getNext(j) == fn) {
				    nn.setNext(j, fpn);
				}
			    }
			}
			fpn.addNext(fn);
		    }
		} //end of else
	    } //End of if
	} //end of while
    }
}
