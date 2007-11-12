package Analysis.Prefetch;

import java.util.*;
import Analysis.CallGraph.CallGraph;
import Analysis.Prefetch.PrefetchPair;
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
    Set<FlatNode> tovisit;
    Hashtable<FlatNode, Hashtable<PrefetchPair, Float>> prefetch_hash;
    Hashtable<PrefetchPair, Float> branch_prefetch_set;
    public static final int ROUNDED_MODE = 30;
    public static final float THRESHOLD_PROB = (float)0.30;
    public static final float BRANCH_TRUE_EDGE_PROB = (float)0.5;
    public static final float BRANCH_FALSE_EDGE_PROB = (float)0.5;

    /** This function finds if a tempdescriptor object is found in a given prefetch pair
     *  It returns true if found else returns false*/
    private boolean isTempDescFound(PrefetchPair pp, TempDescriptor td) {
	    ArrayList<Descriptor> desc = (ArrayList<Descriptor>) pp.getDesc();
	    ListIterator it = desc.listIterator();
	    for(;it.hasNext();) {
		    Object o = it.next();
		    if(o instanceof IndexDescriptor) {
			    ArrayList<TempDescriptor> tdarray = (ArrayList<TempDescriptor>)((IndexDescriptor)o).tddesc;
			    if(tdarray.contains(td)) {
				    return true;
			    }
		    }
	    }
	    return false;
    }

    /** This function creates a new Arraylist of Descriptors by replacing old tempdescriptors with new
     * tempdescriptors when there is a match for FlatOpNodes or FlatLiteralNodes */

    private ArrayList<Descriptor> getNewDesc(PrefetchPair pp, TempDescriptor td, TempDescriptor newtd) {
	    ArrayList<Descriptor> desc = (ArrayList<Descriptor>) pp.getDesc();
	    ListIterator it = desc.listIterator();
	    for(;it.hasNext();) {
		    Object currdesc = it.next();
		    if(currdesc instanceof IndexDescriptor) {
			    ArrayList<TempDescriptor> tdarray = (ArrayList<TempDescriptor>)((IndexDescriptor)currdesc).tddesc;
			    if(tdarray.contains(td)) {
				    int index = tdarray.indexOf(td);
				    tdarray.set(index, newtd);
			    }
		    }
	    }
	    return desc;
    }

    /** This function creates a new Arraylist of Descriptors by replacing old tempdescriptors with new
     * tempdescriptors when there is a match for FlatOpNodes i= i+j then replace i with i+j */
    private ArrayList<Descriptor> getNewDesc(PrefetchPair pp, TempDescriptor td, TempDescriptor left, TempDescriptor right) {
	    ArrayList<Descriptor> desc = (ArrayList<Descriptor>) pp.getDesc();
	    ListIterator it = desc.listIterator();
	    for(;it.hasNext();) {
		    Object currdesc = it.next();
		    if(currdesc instanceof IndexDescriptor) {
			    ArrayList<TempDescriptor> tdarray = (ArrayList<TempDescriptor>)((IndexDescriptor)currdesc).tddesc;
			    if(tdarray.contains(td)) {
				    int index = tdarray.indexOf(td);
				    tdarray.set(index, left);
				    tdarray.add(right);
			    }
		    }
	    }
	    return desc;
    }

    public PrefetchAnalysis(State state, CallGraph callgraph, TypeUtil typeutil) {
	    this.typeutil=typeutil;
	    this.state=state;
	    this.callgraph=callgraph;
	    prefetch_hash = new Hashtable<FlatNode, Hashtable<PrefetchPair,Float>>();
	    DoPrefetch();
    }

    /** This function starts the prefetch analysis */
    private void DoPrefetch() {
	    Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
	    while(classit.hasNext()) {
		    ClassDescriptor cn=(ClassDescriptor)classit.next();
		    doMethodAnalysis(cn);
	    }
    }

    /** This function calls analysis for every method in a class */
    private void doMethodAnalysis(ClassDescriptor cn) {
	    Iterator methodit=cn.getMethods();
	    while(methodit.hasNext()) {
		    /* Classify parameters */
		    MethodDescriptor md=(MethodDescriptor)methodit.next();
		    FlatMethod fm=state.getMethodFlat(md);
		    doFlatNodeAnalysis(fm);
	    }
    }

    /** This function calls analysis for every node in a method */
    private void doFlatNodeAnalysis(FlatMethod fm) {
	    tovisit = fm.getNodeSet(); //Flat nodes to process
	    Hashtable<PrefetchPair, Float> nodehash = new Hashtable<PrefetchPair, Float>();
	    /* Create Empty Prefetch Sets for all flat nodes in the global hashtable */
	    while(!tovisit.isEmpty()) {
		    FlatNode fn = (FlatNode)tovisit.iterator().next();
		    prefetch_hash.put(fn, nodehash);
		    tovisit.remove(fn);
	    }
	    tovisit = fm.getNodeSet(); //Flat nodes to process
	    while(!tovisit.isEmpty()) {
		    FlatNode fn = (FlatNode)tovisit.iterator().next();
		    /* Generate prefetch pairs after the child node analysis */
		    doChildNodeAnalysis(fn);
		    tovisit.remove(fn);
	    }
    }

    /**
     * This function generates the prefetch sets for a given Flatnode considering the kind of node
     * It calls severals functions based on the kind of the node and 
     * returns true: if the prefetch set has changed since last time the node was analysed
     * returns false : otherwise 
     */ 
    private void doChildNodeAnalysis(FlatNode curr) {
	    Hashtable<PrefetchPair, Float> child_hash = new Hashtable<PrefetchPair, Float>();
	    if(curr.kind()==FKind.FlatCondBranch) {
		    branch_prefetch_set =  new Hashtable<PrefetchPair,Float>();
	    }
	    for (int i = 0; i < curr.numNext(); i++) {
		    FlatNode child_node = curr.getNext(i);
		    if (prefetch_hash.containsKey(child_node)) {
			    child_hash = (Hashtable<PrefetchPair,Float>) prefetch_hash.get(child_node).clone();
		    }
		    switch(curr.kind()) {
			    case FKind.FlatBackEdge:
				    processDefaultCase(curr,child_hash);
				    break;
			    case FKind.FlatCall:
				    //TODO change it to take care of FlatMethod, Flatcalls 
				    processFlatCall(curr, child_hash);
				    break;
			    case FKind.FlatCheckNode:
				    processDefaultCase(curr,child_hash);
				    break;
			    case FKind.FlatMethod:
				    //TODO change it to take care of FlatMethod, Flatcalls 
				    processFlatMethod(curr, child_hash);
				    break;
			    case FKind.FlatNew:
				    processFlatNewNode(curr, child_hash);
				    break;
			    case FKind.FlatReturnNode:
				    //TODO change it to take care of FlatMethod, Flatcalls 
				    processDefaultCase(curr,child_hash);
				    break;
			    case FKind.FlatFieldNode:
				    processFlatFieldNode(curr, child_hash);
				    break;
			    case FKind.FlatElementNode:
				    processFlatElementNode(curr, child_hash);
				    break;
			    case FKind.FlatCondBranch:
				    processFlatCondBranch(curr, child_hash, i, branch_prefetch_set);
				    break;
			    case FKind.FlatOpNode:
				    processFlatOpNode(curr, child_hash);
				    break;
			    case FKind.FlatLiteralNode:
				    processFlatLiteralNode(curr, child_hash);
				    break;
			    case FKind.FlatSetElementNode:
				    processFlatSetElementNode(curr, child_hash);
				    break;
			    case FKind.FlatSetFieldNode:
				    processFlatSetFieldNode(curr, child_hash);
				    break;
			    case FKind.FlatAtomicEnterNode:
				    processDefaultCase(curr,child_hash);
				    break;
			    case FKind.FlatAtomicExitNode:
				    processDefaultCase(curr,child_hash);
				    break;
			    case FKind.FlatCastNode:
				    processDefaultCase(curr,child_hash);
				    break;
			    case FKind.FlatFlagActionNode:
				    processDefaultCase(curr,child_hash);
				    break;
			    case FKind.FlatGlobalConvNode:
				    processDefaultCase(curr,child_hash);
				    break;
			    case FKind.FlatNop:
				    processDefaultCase(curr,child_hash);
				    break;
			    case FKind.FlatTagDeclaration:
				    processDefaultCase(curr,child_hash);
				    break;
			    default:
				    System.out.println("NO SUCH FLATNODE");
				    break;
		    }
	    } 
    }
    
    /**This function compares all the prefetch pairs in a Prefetch set hashtable and
     * returns: true if something has changed in the new Prefetch set else
     * returns: false
     */
    private boolean comparePrefetchSets(Hashtable<PrefetchPair, Float> oldPrefetchSet, Hashtable<PrefetchPair, Float>
		    newPrefetchSet) {
	    boolean hasChanged = false;
	    PrefetchPair oldpp = null;
	    PrefetchPair newpp = null;

	    if(oldPrefetchSet.size() != newPrefetchSet.size()) {
		    return true;
	    } else {
		    Enumeration e = newPrefetchSet.keys();
		    while(e.hasMoreElements()) {
			    newpp = (PrefetchPair) e.nextElement();
			    float newprob = newPrefetchSet.get(newpp);
			    for(Enumeration elem = oldPrefetchSet.keys(); elem.hasMoreElements();) {
				    oldpp = (PrefetchPair) elem.nextElement();
				    if(oldpp.equals(newpp)) {
					    /*Compare the difference in their probabilities */ 
					    float oldprob = oldPrefetchSet.get(oldpp);
					    int diff = (int) ((newprob - oldprob)/oldprob)*100; 
					    if(diff >= ROUNDED_MODE) {
						    return true;
					    }else {
					    }
					    break;
				    }
			    }
		    }
	    }
	    return hasChanged;
    }

    /** This function processes the prefetch set of FlatFieldNode
     * It generates a new prefetch set after comparision with its children
     * Then compares it with its old prefetch set
     * If old prefetch set is not same as new prefetch set then enqueue the parents 
     * of the current FlatFieldNode
     * */
    private void processFlatFieldNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_hash) {
	    boolean pSetHasChanged = false;
	    Hashtable<PrefetchPair, Float> currcopy = new Hashtable<PrefetchPair, Float>();
	    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
	    FlatFieldNode currffn = (FlatFieldNode) curr;

	    /* Do Self analysis of the current node*/
	    FieldDescriptor currffn_field =  currffn.getField();
	    TempDescriptor currffn_src = currffn.getSrc();
	    if (currffn_field.getType().isPtr()) {
		    PrefetchPair pp = new PrefetchPair(currffn_src, (Descriptor) currffn_field);
		    Float prob = new Float((float)1.0);
		    currcopy.put(pp, prob);
	    }

	    /* Get each prefetch pair of the child and match it with the destination temp descriptor of curr FlatFieldNode */
	    Enumeration ecld = child_hash.keys();
	    PrefetchPair currpp = null;
	    PrefetchPair childpp = null;
	    while (ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    if (childpp.base == currffn.getDst() && (childpp.getDesc()!= null)) {
			    if (currffn.getField().getType().isPtr()) {
				    /* Create a new Prefetch set*/
				    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
				    newdesc.add(currffn.getField());
				    newdesc.addAll(childpp.desc);
				    PrefetchPair newpp =  new PrefetchPair(currffn.getSrc(), newdesc);
				    Float newprob = child_hash.get(childpp).floatValue();
				    tocompare.put(newpp, newprob); 
				    child_hash.remove(childpp);
				    /* Check for independence of prefetch pairs if any in the child prefetch set
				     * to compute new probability */
				    if(child_hash.containsKey(newpp)) {
					    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
					    if(newprob < THRESHOLD_PROB) {
						    tocompare.remove(newpp);
					    } else {
						    tocompare.put(newpp, newprob); 
					    }
					    child_hash.remove(newpp);
				    }
			    }
		    } else if(childpp.base == currffn.getDst() && (childpp.getDesc() == null)) {
			    child_hash.remove(childpp);
		    } else {
			    continue;
		    }
	    }
	    /* Check if curr prefetch set and the child prefetch set have same prefetch pairs
	     * if so calculate the new probability and then remove the common one from the child prefetch set */ 
	    ecld = child_hash.keys();
	    Enumeration e = null;
	    while(ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    for(e = currcopy.keys(); e.hasMoreElements();) {
			    currpp = (PrefetchPair) e.nextElement();
			    if(currpp.equals(childpp)) {
				    /* Probability of the incoming edge for a FlatFieldNode is always 100% */
				    Float prob = currcopy.get(currpp).floatValue();
				    currcopy.put(currpp, prob);
				    child_hash.remove(childpp);
				    break;
			    } 
		    }
	    }

	    /* Merge child prefetch pairs */
	    ecld = child_hash.keys();
	    while(ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    tocompare.put(childpp, child_hash.get(childpp));
		    child_hash.remove(childpp);
	    }

	    /* Merge curr prefetch pairs */
	    e = currcopy.keys();
	    while(e.hasMoreElements()) {
		    currpp = (PrefetchPair) e.nextElement();
		    tocompare.put(currpp, currcopy.get(currpp));  
		    currcopy.remove(currpp);
	    }

	    /* Compare with the orginal prefetch pairs */
	    pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare);
	    /* Enqueue parent nodes */
	    if(pSetHasChanged) {
		    for(int i=0; i<curr.numPrev(); i++) {
			    tovisit.add(curr.getPrev(i));
		    }
		    /* Overwrite the new prefetch set to the global hash table */
		    prefetch_hash.put(curr,tocompare); 
	    } 
    }

    /** This function processes the prefetch set of a FlatElementNode
     * It generates a new prefetch set after comparision with its children
     * It compares the old prefetch set with this new prefetch set and enqueues the parents 
     * of the current node if change occurs and updates the global flatnode hash table
     * */
    private void processFlatElementNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_hash) {
	    
	    boolean pSetHasChanged = false;
	    Hashtable<PrefetchPair, Float> currcopy = new Hashtable<PrefetchPair, Float>();
	    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
	    FlatElementNode currfen = (FlatElementNode) curr;


	    /* Do Self analysis of the current node*/
	    TempDescriptor currfen_index = currfen.getIndex();
	    IndexDescriptor idesc = new IndexDescriptor(currfen_index, 0);
	    TempDescriptor currfen_src = currfen.getSrc();
	    if(currfen.getDst().getType().isPtr()) {
		    PrefetchPair pp = new PrefetchPair(currfen_src, (Descriptor) idesc);
		    Float prob = new Float((float)1.0);
		    currcopy.put(pp, prob);
	    }

	    /* Get each prefetch pair of the child and match it with the destination temp descriptor of curr FlatFieldNode */
	    Enumeration ecld = child_hash.keys();
	    PrefetchPair currpp = null;
	    PrefetchPair childpp = null;
	    while (ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    if (childpp.base == currfen.getDst() && (childpp.getDesc()!= null)) {
			    if (currfen.getDst().getType().isPtr()) {
				    //if match exists then create a new Prefetch set with the new prefetch pair in a new hash table 
				    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
				    newdesc.add((Descriptor)idesc);
				    newdesc.addAll(childpp.desc);
				    PrefetchPair newpp =  new PrefetchPair(currfen.getSrc(), newdesc);
				    Float newprob = child_hash.get(childpp).floatValue();
				    tocompare.put(newpp, newprob); 
				    child_hash.remove(childpp);
				    /* Check for independence of prefetch pairs if any in the child prefetch set
				     * to compute new probability */
				    if(child_hash.containsKey(newpp)) {
					    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
					    if(newprob < THRESHOLD_PROB) {
						    tocompare.remove(newpp);
					    } else {
						    tocompare.put(newpp, newprob); 
					    }
					    child_hash.remove(newpp);
				    }
			    }
		    } else if(childpp.base == currfen.getDst() && (childpp.getDesc() == null)) {
			    child_hash.remove(childpp);
		    }
	    }
	    /* Check if curr prefetch set and the child prefetch set have same prefetch pairs
	     * if so calculate the new probability and then remove the common one from the child prefetch set */ 
	    ecld = child_hash.keys();
	    Enumeration e = null;
	    while(ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    for(e = currcopy.keys(); e.hasMoreElements();) {
			    currpp = (PrefetchPair) e.nextElement();
			    if(currpp.equals(childpp)) {
				    /* Probability of the incoming edge for a FlatElementNode is always 100% */
				    Float prob = currcopy.get(currpp).floatValue();
				    currcopy.put(currpp, prob);
				    child_hash.remove(childpp);
				    break;
			    } 
		    }
	    }

	    /* Merge child prefetch pairs */
	    ecld = child_hash.keys();
	    while(ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    tocompare.put(childpp, child_hash.get(childpp));
		    child_hash.remove(childpp);
	    }

	    /* Merge curr prefetch pairs */
	    e = currcopy.keys();
	    while(e.hasMoreElements()) {
		    currpp = (PrefetchPair) e.nextElement();
		    tocompare.put(currpp, currcopy.get(currpp));  
		    currcopy.remove(currpp);
	    }

	    /* Compare with the orginal prefetch pairs */
	    pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare);
	    /* Enqueue parent nodes */
	    if(pSetHasChanged) {
		    for(int i=0; i<curr.numPrev(); i++) {
			    tovisit.add(curr.getPrev(i));
		    }
		    /* Overwrite the new prefetch set to the global hash table */
		    prefetch_hash.put(curr,tocompare); 
	    } 
    }

    /** This function processes the prefetch set of a FlatSetFieldNode
     * It generates a new prefetch set after comparision with its children
     * It compares the old prefetch set with this new prefetch set and enqueues the parents 
     * of the current node if change occurs and then updates the global flatnode hash table
     * */
    private void processFlatSetFieldNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_hash) {
	    boolean pSetHasChanged = false;
	    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
	    FlatSetFieldNode currfsfn = (FlatSetFieldNode) curr;
	    PrefetchPair childpp = null;

	    /* Get each prefetch pair of the child and match it with the destination temp descriptor of curr FlatFieldNode */
	    Enumeration ecld = child_hash.keys();
	    while (ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    if(childpp.base == currfsfn.getDst()) {
			    int size = childpp.desc.size();
			    if(size >=2) {
				    if((childpp.getDescAt(0) instanceof FieldDescriptor) && (childpp.getDescAt(0) == currfsfn.getField())) { 
					    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
					    for(int i = 0;i<(childpp.desc.size()-1); i++) {
						    newdesc.add(i,childpp.desc.get(i+1));
					    }
					    PrefetchPair newpp =  new PrefetchPair(currfsfn.getSrc(), newdesc);
					    Float newprob = child_hash.get(childpp).floatValue();
					    tocompare.put(newpp, newprob); 
					    child_hash.remove(childpp);
					    /* Check for independence of prefetch pairs in newly generated and child_hash prefetch pairs
					     * to compute new probability */
					    if(child_hash.containsKey(newpp)) {
						    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
						    if(newprob < THRESHOLD_PROB) {
							    tocompare.remove(newpp);
						    } else {
							    tocompare.put(newpp, newprob); 
						    }
						    child_hash.remove(newpp);
					    }
				    }
			    } else if(size==1) {
				    if((childpp.getDescAt(0) instanceof FieldDescriptor) && (childpp.getDescAt(0) == currfsfn.getField())) {
					    child_hash.remove(childpp);
				    }
			    } else {
				    continue;
			    }
		    }
	    }

	    /* Merge child prefetch pairs */
	    ecld = child_hash.keys();
	    while(ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    tocompare.put(childpp, child_hash.get(childpp));
		    child_hash.remove(childpp);
	    }

	    /* Compare with the orginal prefetch pairs */
	    pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare);
	    /* Enqueue parent nodes */
	    if(pSetHasChanged) {
		    for(int i=0; i<curr.numPrev(); i++) {
			    tovisit.add(curr.getPrev(i));
		    }
		    /* Overwrite the new prefetch set to the global hash table */
		    prefetch_hash.put(curr,tocompare); 
	    } 
    }

    /** This function processes the prefetch set of a FlatSeElementNode
     * It generates a new prefetch set after comparision with its children
     * It compares the old prefetch set with this new prefetch set and enqueues the parents 
     * of the current node if change occurs and then updates the global flatnode hash table
     * */
    private void processFlatSetElementNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_hash) {
	    boolean pSetHasChanged = false;
	    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
	    PrefetchPair childpp = null;
	    FlatSetElementNode currfsen = (FlatSetElementNode) curr;

	    /* Get each prefetch pair of the child and match it with the destination temp descriptor of curr FlatFieldNode */
	    Enumeration ecld = child_hash.keys();
	    while (ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    if (childpp.base == currfsen.getDst()){
			    int sizedesc = childpp.desc.size();
			    if((childpp.getDescAt(0) instanceof IndexDescriptor)) {
				    int sizetempdesc = ((IndexDescriptor)(childpp.getDescAt(0))).tddesc.size();
				    if((((IndexDescriptor)childpp.getDescAt(0)).tddesc.get(0) == currfsen.getIndex()) && (sizetempdesc==1) && (sizedesc>=2)) {
					    //if match exists then create a new Prefetch set with the new prefetch pair in a new hash table 
					    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
					    for(int i = 0;i<(childpp.desc.size()-1); i++) {
						    newdesc.add(i,childpp.desc.get(i+1));
					    }
					    PrefetchPair newpp =  new PrefetchPair(currfsen.getSrc(), newdesc);
					    Float newprob = child_hash.get(childpp).floatValue();
					    tocompare.put(newpp, newprob); 
					    child_hash.remove(childpp);
					    /* Check for independence of prefetch pairs if any in the child prefetch set
					     * to compute new probability */
					    if(child_hash.containsKey(newpp)) {
						    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
						    if(newprob < THRESHOLD_PROB) {
							    tocompare.remove(newpp);
						    } else {
							    tocompare.put(newpp, newprob); 
						    }
						    child_hash.remove(newpp);
					    }
				    } else if((((IndexDescriptor)childpp.getDescAt(0)).tddesc.get(0) == currfsen.getIndex()) && (sizetempdesc==1) && (sizedesc==1)) { 
					    child_hash.remove(childpp);
				    } else {
					    continue;
				    }
			    }
		    }
	    }
	    /* Merge child prefetch pairs */
	    ecld = child_hash.keys();
	    while(ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    tocompare.put(childpp, child_hash.get(childpp));
		    child_hash.remove(childpp);
	    }
	    /* Compare with the orginal prefetch pairs */
	    pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare);
	    /* Enqueue parent nodes */
	    if(pSetHasChanged) {
		    for(int i=0; i<curr.numPrev(); i++) {
			    tovisit.add(curr.getPrev(i));
		    }
		    /* Overwrite the new prefetch set to the global hash table */
		    prefetch_hash.put(curr,tocompare); 
	    } 
    }

    /** This function applies rules and does analysis for a FlatOpNode 
     *  And updates the global prefetch hashtable
     * */
    private void processFlatOpNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_hash) {
	    boolean pSetHasChanged = false;
	    int index;
	    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
	    FlatOpNode currfopn = (FlatOpNode) curr;
	    Enumeration ecld = null; 
	    PrefetchPair childpp = null;

	    /* Check the  Operation type of flatOpNode */
	    if(currfopn.getOp().getOp()== Operation.ASSIGN) {
		    ecld = child_hash.keys();
		    while (ecld.hasMoreElements()) {
			    childpp = (PrefetchPair) ecld.nextElement();
			    PrefetchPair copyofchildpp = (PrefetchPair) childpp.clone();
			
			    /* Base of child prefetch pair same as destination of the current FlatOpNode 
			     * For cases like x=y followed by childnode t=x[i].z or t=x.g*/
			    if(childpp.base == currfopn.getDest()) {
				    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
				    newdesc.addAll(childpp.desc);
				    PrefetchPair newpp =  new PrefetchPair(currfopn.getLeft(), newdesc);
				    Float newprob = child_hash.get(childpp).floatValue();
				    tocompare.put(newpp, newprob); 
				    child_hash.remove(childpp);
				    /* Check for independence of prefetch pairs if any in the child prefetch set
				     * to compute new probability */
				    if(child_hash.containsKey(newpp)) {
					    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
					    if(newprob < THRESHOLD_PROB) {
						    tocompare.remove(newpp);
					    } else {
						    tocompare.put(newpp, newprob); 
					    }
					    child_hash.remove(newpp);
				    }
				    /* Any member of the desc of child prefetch pair is same as destination of the current FlatOpNode 
				     * For cases like x=y followed by t = r[i].x or t =r[x].p or t = r[p+x].q*/
			    } else if(isTempDescFound(copyofchildpp, currfopn.getDest())) {
				    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
				    newdesc.addAll((ArrayList<Descriptor>)getNewDesc(copyofchildpp, currfopn.getDest(), currfopn.getLeft()));
				    PrefetchPair newpp =  new PrefetchPair(childpp.base, newdesc);
				    Float newprob = child_hash.get(childpp).floatValue();
				    tocompare.put(newpp, newprob); 
				    child_hash.remove(childpp);
				    /* Check for independence of prefetch pairs if any in the child prefetch set
				     * to compute new probability*/ 
				    if(child_hash.containsKey(newpp)) {
					    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
					    if(newprob < THRESHOLD_PROB) {
						    tocompare.remove(newpp);
					    } else {
						    tocompare.put(newpp, newprob); 
					    }
					    child_hash.remove(newpp);
				    }
			    }else {
				    continue;
			    }
		    }
	    } else if(currfopn.getRight()!=null && (currfopn.getOp().getOp() == Operation.ADD)) {
		    //case i = i+z  followed by a[i].x
		    ecld = child_hash.keys();
		    while (ecld.hasMoreElements()) {
			    childpp = (PrefetchPair) ecld.nextElement();
			    PrefetchPair copyofchildpp = (PrefetchPair) childpp.clone();
			
			    if(isTempDescFound(copyofchildpp, currfopn.getDest())) {
				    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
				    newdesc.addAll((ArrayList<Descriptor>)getNewDesc(copyofchildpp, currfopn.getDest(), currfopn.getLeft(), currfopn.getRight()));
				    PrefetchPair newpp =  new PrefetchPair(childpp.base, newdesc);
				    Float newprob = child_hash.get(childpp).floatValue();
				    tocompare.put(newpp, newprob); 
				    child_hash.remove(childpp);
				    /* Check for independence of prefetch pairs if any in the child prefetch set
				     * to compute new probability*/ 
				    if(child_hash.containsKey(newpp)) {
					    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
					    if(newprob < THRESHOLD_PROB) {
						    tocompare.remove(newpp);
					    } else {
						    tocompare.put(newpp, newprob); 
					    }
					    child_hash.remove(newpp);
				    }
			    }else {
				    continue;
			    }
		    }
	    } else {
		    //FIXME Is not taken care of for cases like x = -y followed by a[x].i
	    }

	    /* Merge child prefetch pairs */
	    ecld = child_hash.keys();
	    while(ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    tocompare.put(childpp, child_hash.get(childpp));
		    child_hash.remove(childpp);
	    }

	    /* Compare with the orginal prefetch pairs */
	    pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare);
	    /* Enqueue parent nodes */
	    if(pSetHasChanged) {
		    for(int i=0; i<curr.numPrev(); i++) {
			    tovisit.add(curr.getPrev(i));
		    }
		    /* Overwrite the new prefetch set to the global hash table */
		    prefetch_hash.put(curr,tocompare); 
	    } 
    }

    private void processFlatLiteralNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_hash) {
	    boolean pSetHasChanged = false;
	    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
	    FlatLiteralNode currfln = (FlatLiteralNode) curr;
	    Enumeration ecld = null; 
	    PrefetchPair childpp = null;

	    if(currfln.getType().isIntegerType()) {
		    ecld = child_hash.keys();
		    while (ecld.hasMoreElements()) {
			    childpp = (PrefetchPair) ecld.nextElement();
			    PrefetchPair copyofchildpp = (PrefetchPair) childpp.clone();
			    /* Check for same index in child prefetch pairs 
			     * for cases like i = 0 followed by t = a[i].r or t = a[j+i].r or t = a[j].b[i].r*/
			    if(isTempDescFound(copyofchildpp,currfln.getDst())) {
				    ArrayList<Descriptor> copychilddesc = (ArrayList<Descriptor>) copyofchildpp.getDesc();
				    ListIterator it = copychilddesc.listIterator();
				    for(;it.hasNext();) {
					    Object o = it.next();
					    if(o instanceof IndexDescriptor) {
						    ArrayList<TempDescriptor> td = (ArrayList<TempDescriptor>)((IndexDescriptor)o).tddesc;
						    if(td.contains(currfln.getDst())) {
							    int index = td.indexOf(currfln.getDst());
							    ((IndexDescriptor)o).offset = (Integer)currfln.getValue();
							    td.remove(index);
						    }
					    }
				    }
				    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
				    newdesc.addAll(copychilddesc);
				    PrefetchPair newpp =  new PrefetchPair(childpp.base, newdesc);
				    Float newprob = (child_hash.get(childpp)).floatValue();
				    tocompare.put(newpp, newprob); 
				    child_hash.remove(childpp);
				    /* Check for independence of prefetch pairs if any in the child prefetch set
				     * to compute new probability */
				    if(child_hash.containsKey(newpp)) {
					    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
					    if(newprob < THRESHOLD_PROB) {
						    tocompare.remove(newpp);
					    } else {
						    tocompare.put(newpp, newprob); 
					    }
					    child_hash.remove(newpp);
				    }
			    }
		    }
	    }

	    /* Merge child prefetch pairs */
	    ecld = child_hash.keys();
	    while(ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    tocompare.put(childpp, child_hash.get(childpp));
		    child_hash.remove(childpp);
	    }

	    /* Compare with the orginal prefetch pairs */
	    pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare);
	    /* Enqueue parent nodes */
	    if(pSetHasChanged) {
		    for(int i=0; i<curr.numPrev(); i++) {
			    tovisit.add(curr.getPrev(i));
		    }
		    /* Overwrite the new prefetch set to the global hash table */
		    prefetch_hash.put(curr,tocompare); 
	    } 

    }

    private void processFlatMethod(FlatNode curr, Hashtable<PrefetchPair, Float> child_hash) {
	    boolean pSetHasChanged = false;
	    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
	    FlatMethod currfm = (FlatMethod) curr;
	    Enumeration ecld = null; 
	    PrefetchPair childpp = null;

	    /* Merge child prefetch pairs */
	    ecld = child_hash.keys();
	    while(ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    tocompare.put(childpp, child_hash.get(childpp));
		    child_hash.remove(childpp);
	    }

	    /* Compare with the orginal prefetch pairs */
	    pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare);
	    /* Enqueue parent nodes */
	    if(pSetHasChanged) {
		    /* Overwrite the new prefetch set to the global hash table */
		    prefetch_hash.put(curr,tocompare); 
	    } 
    }

    /** This Function processes the FlatCalls 
     * It currently drops the propagation of those prefetchpairs that are passed as
     * arguments in the FlatCall 
     */

    private void processFlatCall(FlatNode curr, Hashtable<PrefetchPair, Float> child_hash) {
	    boolean pSetHasChanged = false;
	    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
	    FlatCall currfcn = (FlatCall) curr;
	    Enumeration ecld = null; 
	    PrefetchPair childpp = null;
	    boolean isSameArg = false;

	    for(int i= 0; i<currfcn.numArgs(); i++) {
	    }

	    ecld = child_hash.keys();
	    while(ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    PrefetchPair copyofchildpp = (PrefetchPair) childpp.clone();
		    int numargs = currfcn.numArgs();
		    for(int i= 0; i<currfcn.numArgs(); i++) {
			    if(currfcn.getArg(i) == childpp.base){
				    isSameArg = true;
			    }
		    }
		    if(!(currfcn.getThis() == childpp.base) && !(isSameArg)) {
			    tocompare.put(childpp, child_hash.get(childpp));
			    child_hash.remove(childpp);
		    } else {
			    child_hash.remove(childpp);
		    }
	    }

	    /* Compare with the orginal prefetch pairs */
	    pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare);
	    /* Enqueue parent nodes */
	    if(pSetHasChanged) {
		    for(int i=0; i<curr.numPrev(); i++) {
			    tovisit.add(curr.getPrev(i));
		    }
		    /* Overwrite the new prefetch set to the global hash table */
		    prefetch_hash.put(curr,tocompare); 
	    } 
    }

    /** This function handles the processes the FlatNode of type FlatCondBranch
     * It combines prefetches of both child elements and create a new hash table called
     * branch_prefetch_set to contains the entries of both its children
     */
    private void processFlatCondBranch(FlatNode curr, Hashtable<PrefetchPair, Float> child_hash, int index, 
		    Hashtable<PrefetchPair,Float> branch_prefetch_set) {
	    boolean pSetHasChanged = false;
	    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
	    FlatCondBranch currfcb = (FlatCondBranch) curr;
	    Float newprob = new Float((float)0.0);
	    PrefetchPair childpp = null;
	    PrefetchPair pp = null;
	    Enumeration ecld = null;

	    ecld = child_hash.keys();
	    while (ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    /* Create a new Prefetch set*/
		    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
		    newdesc.addAll(childpp.desc);
		    PrefetchPair newpp =  new PrefetchPair(childpp.base, newdesc);
		    /* True Edge */
		    if(index == 0) {
			    newprob = child_hash.get(childpp).floatValue() * BRANCH_TRUE_EDGE_PROB;
			    if(newprob >= THRESHOLD_PROB) {
				    tocompare.put(newpp, newprob); 
				    child_hash.remove(newpp);
			    }
		    } else if(index == 1) { /* False Edge */
			    newprob = child_hash.get(childpp).floatValue() * BRANCH_FALSE_EDGE_PROB;
			    if(newprob >= THRESHOLD_PROB) {
				    tocompare.put(newpp, newprob); 
				    child_hash.remove(newpp);
			    }
		    } else {
			    System.out.println("DEBUG-> No more children of the FlatCondBranchNode present");
		    }
	    }

	    /* Merge child prefetch pairs */
	    ecld = child_hash.keys();
	    while(ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    tocompare.put(childpp, child_hash.get(childpp));
		    child_hash.remove(childpp);
	    }

	    /* Update the new branch_prefetch_hashtable to store all new prefetch pairs */
	    if(!tocompare.isEmpty()) {
		    if(index == 0) {
			    branch_prefetch_set.putAll(tocompare);
		    }else if(index == 1) {
			    if(branch_prefetch_set.isEmpty()) {
				    branch_prefetch_set.putAll(tocompare);
			    } else {
				    Enumeration e = tocompare.keys();
				    while(e.hasMoreElements()) {
					    pp = (PrefetchPair) e.nextElement();
					    if(branch_prefetch_set.containsKey(pp)) {
						    newprob = (float)(branch_prefetch_set.get(pp).floatValue() + tocompare.get(pp).floatValue());
						    if(newprob < THRESHOLD_PROB) {
							    branch_prefetch_set.remove(pp); 
						    } else {
							    branch_prefetch_set.put(pp, newprob); 
						    }
						    tocompare.remove(pp);
					    }
				    }
				    e = tocompare.keys();
				    while(e.hasMoreElements()) {
					    pp = (PrefetchPair) e.nextElement();
					    branch_prefetch_set.put(pp,tocompare.get(pp));
					    tocompare.remove(pp);
				    }
			    }
		    } else {
			    System.out.println("DEBUG-> No more children of the FlatCondBranchNode present");
		    }
	    }

	    /* Enqueue parent nodes */
	    if(index == 1) {
		    pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), branch_prefetch_set);
		    if(pSetHasChanged) {
			    for(int i=0; i<curr.numPrev(); i++) {
				    tovisit.add(curr.getPrev(i));
			    }
			    /* Overwrite the new prefetch set to the global hash table */
			    prefetch_hash.put(curr,branch_prefetch_set); 
		    } 

	    }
    }

    
    /** If FlatNode is not concerned with the prefetch set of its Child then propagate 
     * prefetches up the FlatNode*/  
    private void processDefaultCase(FlatNode curr, Hashtable<PrefetchPair, Float> child_hash) {
	    boolean pSetHasChanged = false;
	    Enumeration e = null;
	    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();

	    for(e = child_hash.keys(); e.hasMoreElements();) {
		    PrefetchPair newpp = (PrefetchPair) e.nextElement();
		    tocompare.put(newpp, child_hash.get(newpp));
	    }

	    /* Compare with old Prefetch sets */
	    pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare); 
	    if(pSetHasChanged){
		    for(int i=0; i<curr.numPrev(); i++) {
			    tovisit.add(curr.getPrev(i));
		    }
		    /* Overwrite the new prefetch set to the global hash table */
		    prefetch_hash.put(curr,tocompare); 
	    }
    }

    private void processFlatNewNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_hash) {
	    boolean pSetHasChanged = false;
	    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
	    FlatNew currfnn = (FlatNew) curr;
	    Float newprob = new Float((float)0.0);
	    PrefetchPair childpp = null;
	    Enumeration ecld = null;

	    ecld = child_hash.keys();
	    while (ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    if(childpp.base == currfnn.getDst()){
			    child_hash.remove(childpp);
		    } else {
			    tocompare.put(childpp, child_hash.get(childpp));
			    child_hash.remove(childpp);
		    }
	    }

	    /* Compare with the orginal prefetch pairs */
	    pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare);
	    /* Enqueue parent nodes */
	    if(pSetHasChanged) {
		    for(int i=0; i<curr.numPrev(); i++) {
			    tovisit.add(curr.getPrev(i));
		    }
		    /* Overwrite the new prefetch set to the global hash table */
		    prefetch_hash.put(curr,tocompare); 
	    } 
    }

    /** This function prints the Prefetch pairs of a given flatnode */
    private void printPrefetchPairs(FlatNode fn) {
	    if(prefetch_hash.containsKey(fn)) {
		    System.out.print("Prefetch" + "(");
		    Hashtable<PrefetchPair, Float> currhash = (Hashtable) prefetch_hash.get(fn);
		    for(Enumeration pphash= currhash.keys(); pphash.hasMoreElements();) {
			    PrefetchPair pp = (PrefetchPair) pphash.nextElement();
			    System.out.print(pp.toString() + ", ");
		    }
		    System.out.println(")");
	    } else {
		    System.out.println("Flatnode is currently not present in the global hash: Prefetch Set is Empty");
	    }
    }

    private void doAnalysis() {
	Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
	while(classit.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)classit.next();
	    Iterator methodit=cn.getMethods();
	    while(methodit.hasNext()) {
		/* Classify parameters */
		MethodDescriptor md=(MethodDescriptor)methodit.next();
		FlatMethod fm=state.getMethodFlat(md);
		printMethod(fm);
	    }
	}
    }

    private void printMethod(FlatMethod fm) {
	System.out.println(fm.getMethod()+" {");
        HashSet tovisit=new HashSet();
        HashSet visited=new HashSet();
        int labelindex=0;
        Hashtable nodetolabel=new Hashtable();
        tovisit.add(fm);
        FlatNode current_node=null;
        //Assign labels 1st
        //Node needs a label if it is
        while(!tovisit.isEmpty()) {
            FlatNode fn=(FlatNode)tovisit.iterator().next();
            tovisit.remove(fn);
            visited.add(fn);

            for(int i=0;i<fn.numNext();i++) {
                FlatNode nn=fn.getNext(i);
                if(i>0) {
                    //1) Edge >1 of node
                    nodetolabel.put(nn,new Integer(labelindex++));
                }
                if (!visited.contains(nn)&&!tovisit.contains(nn)) {
                    tovisit.add(nn);
                } else {
                    //2) Join point
                    nodetolabel.put(nn,new Integer(labelindex++));
                }
            }
        }
        //Do the actual printing
        tovisit=new HashSet();
        visited=new HashSet();
        tovisit.add(fm);
        while(current_node!=null||!tovisit.isEmpty()) {
            if (current_node==null) {
                current_node=(FlatNode)tovisit.iterator().next();
                tovisit.remove(current_node);
            }
            visited.add(current_node);
            if (nodetolabel.containsKey(current_node))
                System.out.println("L"+nodetolabel.get(current_node)+":");
            if (current_node.numNext()==0) {
		System.out.println("   "+current_node.toString());
		current_node=null;
            } else if(current_node.numNext()==1) {
		System.out.println("   "+current_node.toString());
                FlatNode nextnode=current_node.getNext(0);
                if (visited.contains(nextnode)) {
                    System.out.println("goto L"+nodetolabel.get(nextnode));
                    current_node=null;
                } else
                    current_node=nextnode;
            } else if (current_node.numNext()==2) {
                /* Branch */
                System.out.println("   "+((FlatCondBranch)current_node).toString("L"+nodetolabel.get(current_node.getNext(1))));
                if (!visited.contains(current_node.getNext(1)))
                    tovisit.add(current_node.getNext(1));
                if (visited.contains(current_node.getNext(0))) {
                    System.out.println("goto L"+nodetolabel.get(current_node.getNext(0)));
                    current_node=null;
                } else
                    current_node=current_node.getNext(0);
            } else throw new Error();
        }
        System.out.println("}");
    }

}
