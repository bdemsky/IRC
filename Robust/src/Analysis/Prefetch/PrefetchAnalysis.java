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
    Hashtable<FlatNode, Hashtable<PrefetchPair, Float>> prefetch_hash;

    public PrefetchAnalysis(State state, CallGraph callgraph, TypeUtil typeutil) {
	this.typeutil=typeutil;
	this.state=state;
	this.callgraph=callgraph;
	prefetch_hash = new Hashtable();
	DoPrefetch();
    }

    private void DoPrefetch() {
	Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
	while(classit.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)classit.next();
	    doMethodAnalysis(cn);
	}
    }

    private void doMethodAnalysis(ClassDescriptor cn) {
	    Iterator methodit=cn.getMethods();
	    while(methodit.hasNext()) {
		    /* Classify parameters */
		    MethodDescriptor md=(MethodDescriptor)methodit.next();
		    FlatMethod fm=state.getMethodFlat(md);
		    doFlatNodeAnalysis(fm);
	    }
    }

    private void doFlatNodeAnalysis(FlatMethod fm) {
	    Set<FlatNode> tovisit = fm.getNodeSet(); //Flat nodes to process
	    tovisit.add(fm);
	    while(!tovisit.isEmpty()) {
		    Hashtable<PrefetchPair, Float> nodehash = new Hashtable();
		    FlatNode fn = (FlatNode)tovisit.iterator().next();
		    tovisit.remove(fn);
		    // Do self node prefetch
		    doNodePrefetch(fn);
		    // Do the child node analysis
		    boolean curr_modified = doNodeChildPrefetch(fn);
	    }
    }

    private void doNodePrefetch(FlatNode fn) {
	    //System.out.println("DEBUG -> kind = " + fn.kind());
	    Hashtable<PrefetchPair, Float> nodehash = new Hashtable();
	    switch(fn.kind()) {
		    case FKind.FlatFieldNode:
			    FlatFieldNode currffn = (FlatFieldNode) fn;
			    System.out.print("DEBUG -> is an object\t");
			    System.out.println(currffn.toString());
			    FieldDescriptor currffn_field =  currffn.getField();
			    TempDescriptor currffn_src = currffn.getSrc();
			    if (currffn_field.getType().isPtr()) {
				    Boolean b = new Boolean(false);
				    PrefetchPair pp = new PrefetchPair(currffn_src, (Descriptor) currffn_field, b);
				    //PrefetchPair pp = new PrefetchPair(currffn_src, currffn_field, false);
				    Float prob = new Float((double)1.0);
				    nodehash.put(pp, prob);
				    prefetch_hash.put(fn, nodehash);
			    }
			    break;
		    case FKind.FlatElementNode:
			    FlatElementNode currfen = (FlatElementNode) fn;
			    TempDescriptor currfen_index = currfen.getIndex();
			    TempDescriptor currfen_src = currfen.getSrc();
			    System.out.print("DEBUG -> is an array\t");
			    System.out.println(currfen.toString());
			    PrefetchPair pp = new PrefetchPair(currfen_src, (Descriptor) currfen_index, true);
			    Float prob = new Float((double)1.0);
			    nodehash.put(pp, prob);
			    prefetch_hash.put(fn, nodehash);
			    break;
		    default:
			    break;
	    }
    }

    private boolean doNodeChildPrefetch(FlatNode curr) {
	    boolean isCurrMod = false;

	    for (int i = 0; i < curr.numNext(); i++) {
		    FlatNode child_node = curr.getNext(i);
		    if (prefetch_hash.containsKey(child_node)) {
			    Hashtable<PrefetchPair, Float> child_hash = prefetch_hash.get(child_node);
			    switch(curr.kind()) {
				    case FKind.FlatFieldNode:
					    //processFlatFieldNode(child_hash, curr);
					    break;
				    case FKind.FlatElementNode:
					    break;
				    case FKind.FlatCall:
					    break;
				    case FKind.FlatCondBranch:
					    break;
				    case FKind.FlatNew:
					    break;
				    case FKind.FlatOpNode:
					    break;
				    case FKind.FlatSetElementNode:
					    break;
				    case FKind.FlatSetFieldNode:
					    break;
				    default:
					    /*If FlatNode is not concerned with the prefetch set of its Child then propagate 
					     * prefetches up the FlatNode*/  
					    if (prefetch_hash.containsKey(curr)) {
						    isCurrMod = true;
						    Hashtable<PrefetchPair, Float> currentcopy = prefetch_hash.get(curr);
						    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
						    Enumeration e = currentcopy.keys();
						    while (e.hasMoreElements()) {
							    PrefetchPair pp = (PrefetchPair) e.nextElement();
							    if (child_hash.contains(pp)) {
								    Float cprob = child_hash.get(pp);
								    Float fprob = currentcopy.get(pp);
								    // TODO fix this
								    Float newprob = cprob.floatValue() * fprob.floatValue();
								    tocompare.put(pp, newprob);
								    child_hash.remove(pp);
							    } else {
								    tocompare.put(pp, currentcopy.get(pp));
							    }
						    }
						    for(e = child_hash.keys(); e.hasMoreElements();) {
							    PrefetchPair newpp = (PrefetchPair) e.nextElement();
							    tocompare.put(newpp, child_hash.get(newpp));
						    }

					    } else {
						    prefetch_hash.put(curr, child_hash);
					    }
			    }
		    } 
	    }
	    return isCurrMod;
    }

    void processFlatFieldNode(Hashtable<PrefetchPair, Float> child_hash, FlatNode curr) {
	    boolean isCurrMod = false;
	    Hashtable<PrefetchPair, Float> currcopy = prefetch_hash.get(curr);
	    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
	    ArrayList<PrefetchPair> arrypp = new ArrayList<PrefetchPair>();
	    FlatFieldNode currffn = (FlatFieldNode) curr;
	    Float newprob = new Float((double)1.0);

	    //1.Get each prefetch pair of the child and match it with the destination temp descriptor of curr FlatFieldNode 
	    Enumeration ecld = child_hash.keys();
	    PrefetchPair currpp = null;
	    PrefetchPair childpp = null;
	    while (ecld.hasMoreElements()) {
		    //PrefetchPair pp = (PrefetchPair) ecld.nextElement();
		    childpp = (PrefetchPair) ecld.nextElement();
		    if (childpp.base == currffn.getDst()) {
			    if (currffn.getField().getType().isPtr()) {
				    isCurrMod = true;
				    //if match exists then create a new Prefetch set with the new prefetch pair in a new hash table 
				    System.out.println("Match with the parent base");
				    System.out.print(childpp.base.toString());
				    ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
				    ArrayList<Boolean> newbool = new ArrayList<Boolean>();
				    newdesc.add(currffn.getField());
				    Boolean b = new Boolean(false);
				    newbool.add(b);
				    newdesc.addAll(childpp.desc);
				    newbool.addAll(childpp.isTemp);
				    PrefetchPair newpp =  new PrefetchPair(currffn.getSrc(), newdesc, newbool);
				    tocompare.put(newpp, newprob); 
				    child_hash.remove(childpp);
			    }
		    }
	    }
	    /* Check if curr prefetch set and the child prefetch set have same prefetch pairs
	     * if so calculate the new probability and then remove the common one from the child prefetch set */ 
	    ecld = child_hash.keys();
	    Enumeration e = currcopy.keys();
	    while(ecld.hasMoreElements()) {
		    childpp = (PrefetchPair) ecld.nextElement();
		    for(e = currcopy.keys(); e.hasMoreElements();) {
			    currpp = (PrefetchPair) e.nextElement();
			    if(currpp.equals(childpp)) {
				    /* Calculate the new probability */ 
				    Float cprob = child_hash.get(childpp);
				    Float fprob = currcopy.get(currpp);
				    // TODO fix this
				    Float prob = cprob.floatValue() * fprob.floatValue();
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
	    }

	    /* Merge curr prefetch pairs */
	    e = currcopy.keys();
	    while(e.hasMoreElements()) {
		    currpp = (PrefetchPair) e.nextElement();
		    tocompare.put(currpp, currcopy.get(currpp));  
	    }

	    //2. Compare with the orginal entry of the hashtable
	    //3. If same as old then do nothing
	    //4. If not same then enque parent nodes
	    //5. Process parent nodes into the hashtable 


    }

    void printPrefetchPair(Hashtable<FlatNode, Hashtable<PrefetchPair, Float>> outertable) {

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
