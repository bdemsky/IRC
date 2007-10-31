package Analysis.Prefetch;

import java.util.*;
import Analysis.CallGraph.CallGraph;
import Analysis.Prefetch.PrefetchPair;
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
    public static final int ROUNDED_MODE = 5;

    public PrefetchAnalysis(State state, CallGraph callgraph, TypeUtil typeutil) {
	this.typeutil=typeutil;
	this.state=state;
	this.callgraph=callgraph;
	prefetch_hash = new Hashtable();
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
		    boolean curr_modified = doChildNodeAnalysis(fn);
		    tovisit.remove(fn);
	    }
    }

    /**
     * This function generates the prefetch sets for a given Flatnode considering the kind of node
     * It calls severals functions based on the kind of the node and 
     * returns true: if the prefetch set has changed since last time the node was analysed
     * returns false : otherwise 
     */ 
    private boolean doChildNodeAnalysis(FlatNode curr) {
	    boolean pSetHasChanged = false;
	    Hashtable<PrefetchPair, Float> child_hash = new Hashtable<PrefetchPair, Float>();
	    for (int i = 0; i < curr.numNext(); i++) {
		    FlatNode child_node = curr.getNext(i);
		    if (prefetch_hash.containsKey(child_node)) {
			    child_hash = (Hashtable<PrefetchPair,Float>) prefetch_hash.get(child_node).clone();
		    }
		    switch(curr.kind()) {
			    case FKind.FlatFieldNode:
				    processFlatFieldNode(curr, child_hash);
				    break;
			    case FKind.FlatElementNode:
				    processFlatElementNode(curr, child_hash);
				    break;
			    case FKind.FlatCondBranch:
				    //processFlatCondBranchNode();
				    break;
			    case FKind.FlatNew:
				    //processFlatNewNode(curr, child_hash);
				    break;
			    case FKind.FlatOpNode:
				    processFlatOpNode(curr, child_hash);
				    break;
			    case FKind.FlatSetElementNode:
				    processFlatSetElementNode(curr, child_hash);
				    break;
			    case FKind.FlatSetFieldNode:
				    processFlatSetFieldNode(curr, child_hash);
				    break;
			    default:
				    /*If FlatNode is not concerned with the prefetch set of its Child then propagate 
				     * prefetches up the FlatNode*/  
				    Enumeration e = null;
				    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
				    for(e = child_hash.keys(); e.hasMoreElements();) {
					    PrefetchPair newpp = (PrefetchPair) e.nextElement();
					    tocompare.put(newpp, child_hash.get(newpp));
				    }

				    /* Compare with old Prefetch sets */
				    pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare); 
				    if(pSetHasChanged){
					    for(int j=0; j<curr.numPrev(); j++) {
						    tovisit.add(curr.getPrev(j));
					    }
					    /* Overwrite the new prefetch set to the global hash table */
					    prefetch_hash.put(curr,tocompare); 
				    }
				    break;
		    }
	    } 
	    return pSetHasChanged;
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
					    }
					    break;
				    } else {
					    return true;
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
		    Boolean b = new Boolean(false);
		    PrefetchPair pp = new PrefetchPair(currffn_src, (Descriptor) currffn_field, b);
		    Float prob = new Float((float)1.0);
		    currcopy.put(pp, prob);
	    }

	    /* Get each prefetch pair of the child and match it with the destination temp descriptor of curr FlatFieldNode */
	    Enumeration ecld = child_hash.keys();
	    PrefetchPair currpp = null;
	    PrefetchPair childpp = null;
	    while (ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    if (childpp.base == currffn.getDst() && (childpp.getDesc()!= null || childpp.getisTempDesc()!=null)) {
			    if (currffn.getField().getType().isPtr()) {
				    /* Create a new Prefetch set */
				    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
				    ArrayList<Boolean> newbool = new ArrayList<Boolean>();
				    newdesc.add(currffn.getField());
				    Boolean b = new Boolean(false);
				    newbool.add(b);
				    newdesc.addAll(childpp.desc);
				    newbool.addAll(childpp.isTempDesc);
				    PrefetchPair newpp =  new PrefetchPair(currffn.getSrc(), newdesc, newbool);
				    Float newprob = child_hash.get(childpp).floatValue();
				    tocompare.put(newpp, newprob); 
				    child_hash.remove(childpp);
				    /* Check for independence of prefetch pairs if any in the child prefetch set
				     * to compute new probability */
				    if(child_hash.containsKey(newpp)) {
					    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
					    tocompare.put(newpp, newprob); 
					    child_hash.remove(newpp);
				    }
			    }
		    } else if(childpp.base == currffn.getDst() && (childpp.getDesc() == null || 
					    childpp.getisTempDesc() == null)) {
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
	    TempDescriptor currfen_src = currfen.getSrc();
	    if(currfen.getDst().getType().isPtr()) {
		    PrefetchPair pp = new PrefetchPair(currfen_src, (Descriptor) currfen_index, true);
		    Float prob = new Float((float)1.0);
		    currcopy.put(pp, prob);
	    }

	    /* Get each prefetch pair of the child and match it with the destination temp descriptor of curr FlatFieldNode */
	    Enumeration ecld = child_hash.keys();
	    PrefetchPair currpp = null;
	    PrefetchPair childpp = null;
	    while (ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    if (childpp.base == currfen.getDst() && (childpp.getDesc()!= null || childpp.getisTempDesc()!=null)) {
			    if (currfen.getDst().getType().isPtr()) {
				    //TODO  Modify the Prefetch Pair to insert cases like f=a[i+1]
				    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
				    ArrayList<Boolean> newbool = new ArrayList<Boolean>();
				    newdesc.add(currfen.getIndex());
				    Boolean b = new Boolean(true);
				    newbool.add(b);
				    newdesc.addAll(childpp.desc);
				    newbool.addAll(childpp.isTempDesc);
				    PrefetchPair newpp =  new PrefetchPair(currfen.getSrc(), newdesc, newbool);
				    Float newprob = child_hash.get(childpp).floatValue();
				    tocompare.put(newpp, newprob); 
				    child_hash.remove(childpp);
				    /* Check for independence of prefetch pairs if any in the child prefetch set
				     * to compute new probability */
				    if(child_hash.containsKey(newpp)) {
					    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
					    tocompare.put(newpp, newprob); 
					    child_hash.remove(newpp);
				    }
			    }
		    } else if(childpp.base == currfen.getDst() && (childpp.getDesc() == null || 
					    childpp.getisTempDesc() == null)) {
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
		    if (childpp.base == currfsfn.getDst() && (FieldDescriptor)childpp.getDescAt(0)== currfsfn.getField()) {
			    if(childpp.getDesc()!= null && childpp.getisTempDesc()!=null) {
				    if(currfsfn.getSrc().getType().isPtr()) {
					    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
					    ArrayList<Boolean> newbool = new ArrayList<Boolean>();
					    newdesc.addAll(1,childpp.desc);
					    newbool.addAll(1,childpp.isTempDesc);
					    PrefetchPair newpp =  new PrefetchPair(currfsfn.getSrc(), newdesc, newbool);
					    Float newprob = child_hash.get(childpp).floatValue();
					    tocompare.put(newpp, newprob); 
					    child_hash.remove(childpp);
					    /* Check for independence of prefetch pairs if any in the child prefetch set
					     * to compute new probability */
					    if(child_hash.containsKey(newpp)) {
						    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
						    tocompare.put(newpp, newprob); 
						    child_hash.remove(newpp);
					    }
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
		    //TODO Add comparision for cases like a[i+1]=f.The following only works for cases like a[i]=f
		    if ((childpp.base == currfsen.getDst()) && ((TempDescriptor)childpp.getDescAt(0)== currfsen.getIndex())) {
			    if(childpp.getDesc()!= null && childpp.getisTempDesc()!=null) {
				    if(currfsen.getSrc().getType().isPtr()) {
					    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
					    ArrayList<Boolean> newbool = new ArrayList<Boolean>();
					    newdesc.addAll(1,childpp.desc);
					    newbool.addAll(1,childpp.isTempDesc);
					    PrefetchPair newpp =  new PrefetchPair(currfsen.getSrc(), newdesc, newbool);
					    Float newprob = child_hash.get(childpp).floatValue();
					    tocompare.put(newpp, newprob); 
					    child_hash.remove(childpp);
					    /* Check for independence of prefetch pairs if any in the child prefetch set
					     * to compute new probability */
					    if(child_hash.containsKey(newpp)) {
						    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
						    tocompare.put(newpp, newprob); 
						    child_hash.remove(newpp);
					    }
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
			    ArrayList<Descriptor> copychildpp = (ArrayList<Descriptor>) childpp.desc.clone();
			    ArrayList<Boolean> copybool = (ArrayList<Boolean>) childpp.isTempDesc.clone();
			    /* Base of child prefetch pair same as destination of the current FlatOpNode */
			    if(childpp.base == currfopn.getDest()) {
				    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
				    ArrayList<Boolean> newbool = new ArrayList<Boolean>();
				    newdesc.addAll(childpp.desc);
				    newbool.addAll(childpp.isTempDesc);
				    PrefetchPair newpp =  new PrefetchPair(currfopn.getLeft(), newdesc, newbool);
				    Float newprob = child_hash.get(childpp).floatValue();
				    tocompare.put(newpp, newprob); 
				    child_hash.remove(childpp);
				    /* Check for independence of prefetch pairs if any in the child prefetch set
				     * to compute new probability */
				    if(child_hash.containsKey(newpp)) {
					    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
					    tocompare.put(newpp, newprob); 
					    child_hash.remove(newpp);
				    }
			    /* Any member of the desc of child prefetch pair is same as destination of the current FlatOpNode */
			    } else if(copychildpp.contains(currfopn.getDest())) {
				    index = copychildpp.indexOf((TempDescriptor)currfopn.getDest());
				    copychildpp.set(index, currfopn.getLeft());
				    /* Check if TempDescriptor */
				    if(copybool.get(index).booleanValue() == false){
					    copybool.set(index, true);
				    }
				    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
				    ArrayList<Boolean> newbool = new ArrayList<Boolean>();
				    newdesc.addAll(copychildpp);
				    newbool.addAll(copybool);
				    PrefetchPair newpp =  new PrefetchPair(childpp.base, newdesc, newbool);
				    Float newprob = child_hash.get(childpp).floatValue();
				    tocompare.put(newpp, newprob); 
				    child_hash.remove(childpp);
				    /* Check for independence of prefetch pairs if any in the child prefetch set
				     * to compute new probability */
				    if(child_hash.containsKey(newpp)) {
					    newprob = (float)(1.0 - ((1.0 - child_hash.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
					    tocompare.put(newpp, newprob); 
					    child_hash.remove(newpp);
				    }

			    }else {
				   continue;
			    }
		    }
	    } else if(currfopn.getRight()!=null) {
		    //FIXME
	    } else {
		    //FIXME
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

    /** This function prints the Prefetch pairs of a given flatnode */
    void printPrefetchPairs(FlatNode fn) {
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
