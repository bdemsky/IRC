package Analysis.Prefetch;

import java.util.*;
import Analysis.CallGraph.CallGraph;
import Analysis.Prefetch.PrefetchPair;
import IR.SymbolTable;
import IR.State;
import IR.TypeUtil;
import IR.MethodDescriptor;
import IR.Flat.*;
import IR.ClassDescriptor;

public class PrefetchAnalysis {
    State state;
    CallGraph callgraph;
    TypeUtil typeutil;
    Hashtable<FlatNode, HashSet<PrefetchPair>> prefetch_hash;

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
		    HashSet<FlatNode> parentnodes = new HashSet<FlatNode>();
		    HashSet<PrefetchPair> s = new HashSet<PrefetchPair>();
		    FlatNode fn = (FlatNode)tovisit.iterator().next();
		    //Create a set of parent nodes for any given node
		    for(int i = 0; i < fn.numPrev(); i++){
			    if(fn.getPrev(i) != null)
				    parentnodes.add(fn.getPrev(i));
		    }
		    tovisit.remove(fn);
		    //System.out.println("DEBUG -> kind = " + fn.kind());
		    switch(fn.kind()) {
			    case FKind.FlatCondBranch:
				    //TODO: make this a method
				    FlatCondBranch fcb = (FlatCondBranch) fn;
				    System.out.print("DEBUG -> conditional\t");
				    System.out.println(fcb.toString(""));
				    break;
			    case FKind.FlatAtomicEnterNode:
				    break;
			    case FKind.FlatAtomicExitNode:
				    break;
			    case FKind.FlatGlobalConvNode:
				    break;
			    case FKind.FlatTagDeclaration:
				    break;
			    case FKind.FlatCall:
				    break;
			    case FKind.FlatFieldNode:
				    //TODO: make this a method
				    // This implementation takes care of a case where int x = f.g
				    // => f needs to be prefetched and moved up in the parentnode
				    FlatFieldNode ffn = (FlatFieldNode) fn;
				    System.out.print("DEBUG -> is an object\t");
				    System.out.println(ffn.toString());
				    TempDescriptor currnode = ffn.getSrc();
				    double prob = 1.0;
				    if(ffn.getDst().getType().isPtr()) {
					    PrefetchPair pp = new PrefetchPair(currnode,(float)prob);
					    if (prefetch_hash.containsKey(fn)) {
						    s = prefetch_hash.remove(fn);
					    } 
					    s.add(pp);
					    prefetch_hash.put(fn, s);
				    }
				    /* Traverse parent nodes */
				    for (int i = 0; i < parentnodes.size(); i++) {
					    FlatNode pnode = (FlatNode) parentnodes.iterator().next();
					    if (prefetch_hash.containsKey(pnode)) {
						    //Get PrefetchPair  and for each TempDescriptor in the prefetch pair 
						    // compare it with the temp descriptor of its child
						    HashSet <PrefetchPair> pp = prefetch_hash.remove(pnode);
						    boolean found = false;
						    for (int j = 0; j < pp.size(); j++) {
							    PrefetchPair tmp = (PrefetchPair) pp.iterator().next();
							    //If match exists then find new probability
							    if (tmp.td.toString() == currnode.toString()) {
								    tmp.num = tmp.num * (float)prob;
								    prefetch_hash.put(pnode, pp);
								    found = true;
								    break;
							    } 
						    }

						    //If match does not exists then add the current prefetchpair to parentprefetchpair
						    if (!found) {
							    PrefetchPair moveup = new PrefetchPair(currnode, (float)prob);
							    pp.add(moveup);
							    prefetch_hash.put(pnode, pp);
						    }
					    } 
				    }
				    break;
			    case FKind.FlatElementNode:
				    //TODO: make this a method
				    FlatElementNode fen = (FlatElementNode)fn;
				    if (fen.getDst().getType().isPtr()) {
					    System.out.print("DEBUG -> is a array\t");
					    System.out.println(fen.toString());
					    PrefetchPair pp = new PrefetchPair(fen.getSrc(),(float)1.0);
					    if (prefetch_hash.containsKey(fn)) {
						    s = prefetch_hash.get(fn);
						    s.add(pp);
						    prefetch_hash.put(fn, s);
					    }
					    //TODO: add the else part
				    }   
				    break;
			    case FKind.FlatSetElementNode:
				    break;
			    case FKind.FlatSetFieldNode:
				    break;
			    case FKind.FlatNew:
				    break;
			    case FKind.FlatOpNode:
				    break;
			    case FKind.FlatCastNode:
				    break;
			    case FKind.FlatLiteralNode:
				    break;
			    case FKind.FlatReturnNode:
				    break;
			    case FKind.FlatNop:
				    //System.out.println("/* nop */");
				    break;
			    case FKind.FlatCheckNode:
				    break;
			    case FKind.FlatFlagActionNode:
				    break;
		    }
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
