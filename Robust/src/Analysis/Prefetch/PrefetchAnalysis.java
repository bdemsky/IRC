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
		    //System.out.println("DEBUG -> kind = " + fn.kind());
		    // Do self node prefetch
		    doNodePrefetch(fn);
		    // Do the child node analysis
		    boolean curr_modified = doNodeChildPrefetch(fn);
	    }
    }

    private void doNodePrefetch(FlatNode fn) {
	    Hashtable<PrefetchPair, Float> nodehash = new Hashtable();
	    switch(fn.kind()) {
		    case FKind.FlatFieldNode:
			    FlatFieldNode currffn = (FlatFieldNode) fn;
			    System.out.print("DEBUG -> is an object\t");
			    System.out.println(currffn.toString());
			    FieldDescriptor currffn_field =  currffn.getField();
			    TempDescriptor currffn_src = currffn.getSrc();
			    if (currffn_field.getType().isPtr()) {
				    System.out.println("\t pointer " + currffn_field.toString());
				    PrefetchPair pp = new PrefetchPair(currffn_src, (Descriptor) currffn_field, false);
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
					    break;
				    case FKind.FlatElementNode:
					    break;
				    default:
					    if (prefetch_hash.containsKey(curr)) {
						    isCurrMod = true;
						    Hashtable<PrefetchPair, Float> parentcopy = prefetch_hash.get(curr);
						    Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
						    Enumeration e = parentcopy.keys();
						    while (e.hasMoreElements()) {
							    PrefetchPair pp = (PrefetchPair) e.nextElement();
							    if (child_hash.contains(pp)) {
								    Float cprob = child_hash.get(pp);
								    Float fprob = parentcopy.get(pp);
								    // TODO fix this
								    Float newprob = cprob.floatValue() * fprob.floatValue();
								    tocompare.put(pp, newprob);
								    child_hash.remove(pp);
							    } else {
								    tocompare.put(pp, parentcopy.get(pp));
							    }
						    }
						    e = child_hash.keys();
						    while (e.hasMoreElements()) {
							    tocompare.put((PrefetchPair) e.nextElement(), child_hash.get((PrefetchPair) e.nextElement()));
						    }
					    } else {
						    prefetch_hash.put(curr, child_hash);
					    }
			    }
		    } 
	    }
	    return isCurrMod;
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
		System.out.println("DEBUG -> ");
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
	    System.out.println("DEBUG -> " + fn.kind());

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
