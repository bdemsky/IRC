package Analysis.Prefetch;

import java.util.*;
import Analysis.CallGraph.CallGraph;
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
	Set<FlatNode> tovisit;
	public Hashtable<FlatNode, Hashtable<PrefetchPair, Float>> prefetch_hash;
	public Hashtable<FlatNode, Hashtable<FlatNode, PairMap>> pmap_hash;
	Hashtable<PrefetchPair, Float> branch_prefetch_set;
	LinkedList<FlatNode> newvisited;
	Hashtable<FlatNode, HashSet<PrefetchPair>> pset1_hash; 
	Hashtable<FlatNode, HashSet<PrefetchPair>> newprefetchset;
	public static final int PROB_DIFF = 10;
	public static final float ANALYSIS_THRESHOLD_PROB = (float)0.10;
	public static final float PREFETCH_THRESHOLD_PROB = (float)0.30;
	public static final float BRANCH_TRUE_EDGE_PROB = (float)0.5;
	public static final float BRANCH_FALSE_EDGE_PROB = (float)0.5;
	
	public PrefetchAnalysis(State state, CallGraph callgraph, TypeUtil typeutil) {
		this.typeutil=typeutil;
		this.state=state;
		this.callgraph=callgraph;
		prefetch_hash = new Hashtable<FlatNode, Hashtable<PrefetchPair,Float>>();
		pmap_hash = new Hashtable<FlatNode, Hashtable<FlatNode, PairMap>>();
		DoPrefetch();
		prefetch_hash = null;
		pmap_hash = null;
	}

	/** This function returns true if a tempdescriptor object is found in the array of descriptors
	 *  for a given prefetch pair else returns false*/
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
	 * tempdescriptors when there is a match */
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
	 * tempdescriptors when there is a match for e.g FlatOpNodes if i= i+j then replace i with i+j */
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
			newprefetchset = new Hashtable<FlatNode, HashSet<PrefetchPair>>();
			pset1_hash = new Hashtable<FlatNode, HashSet<PrefetchPair>>();
			MethodDescriptor md=(MethodDescriptor)methodit.next();
			FlatMethod fm=state.getMethodFlat(md);
			doFlatNodeAnalysis(fm);
			doInsPrefetchAnalysis(fm);
			if(newprefetchset.size() > 0) {
				addFlatPrefetchNode(newprefetchset);
			}
			newprefetchset = null;
			pset1_hash = null;
		}
	}

	/** This function calls analysis for every node in a method */
	private void doFlatNodeAnalysis(FlatMethod fm) {
		tovisit = fm.getNodeSet(); 
		Hashtable<PrefetchPair, Float> nodehash = new Hashtable<PrefetchPair, Float>();
		/* Create Empty Prefetch Sets for all flat nodes in the global hashtable */
		while(!tovisit.isEmpty()) {
			FlatNode fn = (FlatNode)tovisit.iterator().next();
			prefetch_hash.put(fn, nodehash);
			tovisit.remove(fn);
		}

		nodehash = null;

		/* Visit and process nodes */
		tovisit = fm.getNodeSet(); 
		while(!tovisit.isEmpty()) {
			FlatNode fn = (FlatNode)tovisit.iterator().next();
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
		Hashtable<PrefetchPair, Float> child_prefetch_set_copy = new Hashtable<PrefetchPair, Float>();
		Hashtable<FlatNode, PairMap> parentpmap = new Hashtable<FlatNode, PairMap>();
		FlatNode child_node = null;
		if(curr.numNext() != 0) {
			child_node = curr.getNext(0);
			if(prefetch_hash.containsKey(child_node)) {
				child_prefetch_set_copy = (Hashtable<PrefetchPair,Float>) prefetch_hash.get(child_node).clone();
			}
		}

		switch(curr.kind()) {
			case FKind.FlatBackEdge:
				processDefaultCase(curr,child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatCall:
				//TODO change it to take care of FlatMethod, Flatcalls 
				processFlatCall(curr, child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatCheckNode:
				processDefaultCase(curr,child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatMethod:
				//TODO change it to take care of FlatMethod, Flatcalls 
				processFlatMethod(curr, child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatNew:
				processFlatNewNode(curr, child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatReturnNode:
				//TODO change it to take care of FlatMethod, Flatcalls 
				processDefaultCase(curr,child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatFieldNode:
				processFlatFieldNode(curr, child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatElementNode:
				processFlatElementNode(curr, child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatCondBranch:
				branch_prefetch_set =  new Hashtable<PrefetchPair,Float>();
				for (int i = 0; i < curr.numNext(); i++) {
					parentpmap = new Hashtable<FlatNode, PairMap>();
					child_node = curr.getNext(i);
					if (prefetch_hash.containsKey(child_node)) {
						child_prefetch_set_copy = (Hashtable<PrefetchPair,Float>) prefetch_hash.get(child_node).clone();
					}
					processFlatCondBranch(curr, child_prefetch_set_copy, i, branch_prefetch_set, parentpmap);
					parentpmap = null;
				}
				branch_prefetch_set = null;
				break;
			case FKind.FlatOpNode:
				processFlatOpNode(curr, child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatLiteralNode:
				processFlatLiteralNode(curr, child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatSetElementNode:
				processFlatSetElementNode(curr, child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatSetFieldNode:
				processFlatSetFieldNode(curr, child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatAtomicEnterNode:
				processDefaultCase(curr,child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatAtomicExitNode:
				processDefaultCase(curr,child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatCastNode:
				processFlatCastNode(curr, child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatFlagActionNode:
				processDefaultCase(curr,child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatGlobalConvNode:
				processDefaultCase(curr,child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatNop:
				processDefaultCase(curr,child_prefetch_set_copy, parentpmap);
				break;
			case FKind.FlatTagDeclaration:
				processFlatTagDeclaration(curr, child_prefetch_set_copy, parentpmap);
				break;
			default:
				System.out.println("NO SUCH FLATNODE");
				break;
		}

		/* Free Heap Memory */
		child_prefetch_set_copy = null;
		parentpmap = null;
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
			if(newPrefetchSet.size() == 0) {
				return false;
			}
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
						if(diff >= PROB_DIFF) {
							return true;
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
	private void processFlatFieldNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_prefetch_set_copy, 
			Hashtable<FlatNode, PairMap> parentpmap) {
		boolean pSetHasChanged = false;
		Hashtable<PrefetchPair, Float> currcopy = new Hashtable<PrefetchPair, Float>();
		Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
		FlatFieldNode currffn = (FlatFieldNode) curr;
		PairMap pm = new PairMap();

		/* Do Self analysis of the current node*/
		FieldDescriptor currffn_field =  currffn.getField();
		TempDescriptor currffn_src = currffn.getSrc();
		if (currffn_field.getType().isPtr()) {
			PrefetchPair pp = new PrefetchPair(currffn_src, (Descriptor) currffn_field);
			Float prob = new Float((float)1.0);
			currcopy.put(pp, prob);
		}

		/* Get each prefetch pair of the child and match it with the destination temp descriptor of curr FlatFieldNode */
		Enumeration ecld = child_prefetch_set_copy.keys();
		PrefetchPair currpp = null;
		PrefetchPair childpp = null;
		while (ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			if (childpp.base == currffn.getDst() && (childpp.getDesc()!= null)) {
				if (currffn.getField().getType().isPtr()) {
					ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
					newdesc.add(currffn.getField());
					newdesc.addAll(childpp.desc);
					PrefetchPair newpp =  new PrefetchPair(currffn.getSrc(), newdesc);
					Float newprob = child_prefetch_set_copy.get(childpp).floatValue();
					tocompare.put(newpp, newprob); 
					pm.addPair(childpp, newpp);
					child_prefetch_set_copy.remove(childpp);
					/* Check for independence of prefetch pairs to compute new probability */
					if(child_prefetch_set_copy.containsKey(newpp)) {
						newprob = (float)(1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
						if(newprob < ANALYSIS_THRESHOLD_PROB) {
							tocompare.remove(newpp);
						} else {
							tocompare.put(newpp, newprob); 
							pm.addPair(newpp, newpp);
						}
						child_prefetch_set_copy.remove(newpp);
					}
				}
			} else if(childpp.base == currffn.getDst() && (childpp.getDesc() == null)) {
				child_prefetch_set_copy.remove(childpp);
			} else {
				continue;
			}
		}
		/* Check if curr prefetch set and the child prefetch set have same prefetch pairs
		 * if so, calculate the new probability */ 
		ecld = child_prefetch_set_copy.keys();
		Enumeration e = null;
		while(ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			for(e = currcopy.keys(); e.hasMoreElements();) {
				currpp = (PrefetchPair) e.nextElement();
				if(currpp.equals(childpp)) {
					Float prob = currcopy.get(currpp).floatValue();
					currcopy.put(currpp, prob);
					pm.addPair(childpp, currpp);
					child_prefetch_set_copy.remove(childpp);
					break;
				} 
			}
		}

		/* Merge child prefetch pairs */
		ecld = child_prefetch_set_copy.keys();
		while(ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
			pm.addPair(childpp, childpp);
			child_prefetch_set_copy.remove(childpp);
		}

		/* Merge curr prefetch pairs */
		e = currcopy.keys();
		while(e.hasMoreElements()) {
			currpp = (PrefetchPair) e.nextElement();
			tocompare.put(currpp, currcopy.get(currpp));  
			currcopy.remove(currpp);
		}

		/* Create prefetch mappings for child nodes */
		if(!pm.isEmpty()) {
			parentpmap.put(curr, pm);
		}
		pmap_hash.put(curr.getNext(0), parentpmap);

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

		/* Free Heap Memory */
		currcopy = null;
		tocompare = null;
		pm = null;
	}

	/** This function processes the prefetch set of a FlatElementNode
	 * It generates a new prefetch set after comparision with its children
	 * It compares the old prefetch set with this new prefetch set and enqueues the parents 
	 * of the current node if change occurs and updates the global flatnode hash table
	 * */
	private void processFlatElementNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_prefetch_set_copy,
			Hashtable<FlatNode, PairMap> parentpmap) {

		boolean pSetHasChanged = false;
		Hashtable<PrefetchPair, Float> currcopy = new Hashtable<PrefetchPair, Float>();
		Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
		FlatElementNode currfen = (FlatElementNode) curr;
		PairMap pm = new PairMap();


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
		Enumeration ecld = child_prefetch_set_copy.keys();
		PrefetchPair currpp = null;
		PrefetchPair childpp = null;
		while (ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			if (childpp.base == currfen.getDst() && (childpp.getDesc()!= null)) {
				if (currfen.getDst().getType().isPtr()) {
					ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
					newdesc.add((Descriptor)idesc);
					newdesc.addAll(childpp.desc);
					PrefetchPair newpp =  new PrefetchPair(currfen.getSrc(), newdesc);
					Float newprob = child_prefetch_set_copy.get(childpp).floatValue();
					tocompare.put(newpp, newprob); 
					pm.addPair(childpp, newpp);
					child_prefetch_set_copy.remove(childpp);
					/* Check for independence of prefetch pairs to compute new probability */
					if(child_prefetch_set_copy.containsKey(newpp)) {
						newprob = (float)(1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
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
			}
		}
		/* Check if curr prefetch set and the child prefetch set have same prefetch pairs
		 * if so calculate the new probability */ 
		ecld = child_prefetch_set_copy.keys();
		Enumeration e = null;
		while(ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			for(e = currcopy.keys(); e.hasMoreElements();) {
				currpp = (PrefetchPair) e.nextElement();
				if(currpp.equals(childpp)) {
					Float prob = currcopy.get(currpp).floatValue();
					currcopy.put(currpp, prob);
					pm.addPair(childpp, currpp);
					child_prefetch_set_copy.remove(childpp);
					break;
				} 
			}
		}

		/* Merge child prefetch pairs */
		ecld = child_prefetch_set_copy.keys();
		while(ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
			pm.addPair(childpp, childpp);
			child_prefetch_set_copy.remove(childpp);
		}

		/* Merge curr prefetch pairs */
		e = currcopy.keys();
		while(e.hasMoreElements()) {
			currpp = (PrefetchPair) e.nextElement();
			tocompare.put(currpp, currcopy.get(currpp));  
			currcopy.remove(currpp);
		}

		/* Create prefetch mappings for child nodes */
		if(!pm.isEmpty()) {
			parentpmap.put(curr, pm);
		}
		pmap_hash.put(curr.getNext(0), parentpmap);

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

		/* Free heap memory */
		currcopy = null;
		tocompare = null;
		pm = null;
	}

	/** This function processes the prefetch set of a FlatSetFieldNode
	 * It generates a new prefetch set after comparision with its children
	 * It compares the old prefetch set with this new prefetch set and enqueues the parents 
	 * of the current node if change occurs and then updates the global flatnode hash table
	 * */
	private void processFlatSetFieldNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_prefetch_set_copy,
			Hashtable<FlatNode, PairMap> parentpmap) {
		boolean pSetHasChanged = false;
		Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
		FlatSetFieldNode currfsfn = (FlatSetFieldNode) curr;
		PrefetchPair childpp = null;
		PairMap pm = new PairMap();

		Enumeration ecld = child_prefetch_set_copy.keys();
		while (ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			if(childpp.base == currfsfn.getDst()) {
				int size = childpp.desc.size();
				if(size >=2) { /*e.g. x.f = g (with child prefetches x.f.g, x.f[0].j) */
					if((childpp.getDescAt(0) instanceof FieldDescriptor) && (childpp.getDescAt(0) == currfsfn.getField())) { 
						ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
						for(int i = 0;i<(childpp.desc.size()-1); i++) {
							newdesc.add(i,childpp.desc.get(i+1));
						}
						PrefetchPair newpp =  new PrefetchPair(currfsfn.getSrc(), newdesc);
						Float newprob = child_prefetch_set_copy.get(childpp).floatValue();
						tocompare.put(newpp, newprob); 
						pm.addPair(childpp, newpp);
						child_prefetch_set_copy.remove(childpp);
						/* Check for independence of prefetch pairs in newly generated prefetch pair 
						 * to compute new probability */
						if(child_prefetch_set_copy.containsKey(newpp)) {
							newprob = (float)(1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
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
		ecld = child_prefetch_set_copy.keys();
		while(ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
			pm.addPair(childpp, childpp);
			child_prefetch_set_copy.remove(childpp);
		}

		/* Create prefetch mappings for child nodes */
		if(!pm.isEmpty()) {
			parentpmap.put(curr, pm);
		}
		pmap_hash.put(curr.getNext(0), parentpmap);

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

		/* Free heap memory */
		tocompare = null;
		pm = null;
	}

	/** This function processes the prefetch set of a FlatSetElementNode
	 * It generates a new prefetch set after comparision with its children
	 * It compares the old prefetch set with this new prefetch set and enqueues the parents 
	 * of the current node if change occurs and then updates the global flatnode hash table
	 * */
	private void processFlatSetElementNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_prefetch_set_copy,
			Hashtable<FlatNode, PairMap> parentpmap) {
		boolean pSetHasChanged = false;
		Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
		PrefetchPair childpp = null;
		FlatSetElementNode currfsen = (FlatSetElementNode) curr;
		PairMap pm = new PairMap();

		Enumeration ecld = child_prefetch_set_copy.keys();
		while (ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
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
							Float newprob = child_prefetch_set_copy.get(childpp).floatValue();
							tocompare.put(newpp, newprob); 
							pm.addPair(childpp, newpp);
							child_prefetch_set_copy.remove(childpp);
							/* Check for independence of prefetch pairs to compute new probability */
							if(child_prefetch_set_copy.containsKey(newpp)) {
								newprob = (float)(1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
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
		ecld = child_prefetch_set_copy.keys();
		while(ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
			pm.addPair(childpp, childpp);
			child_prefetch_set_copy.remove(childpp);
		}

		/* Create prefetch mappings for child nodes */
		if(!pm.isEmpty()) {
			parentpmap.put(curr, pm);
		}
		pmap_hash.put(curr.getNext(0), parentpmap);

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
		/* Free heap memory */
		tocompare = null;
		pm = null;
	}

	/** This function applies rules and does analysis for a FlatOpNode 
	 *  And updates the global prefetch hashtable
	 * */
	private void processFlatOpNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_prefetch_set_copy,
			Hashtable<FlatNode, PairMap> parentpmap) {
		boolean pSetHasChanged = false;
		int index;
		Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
		FlatOpNode currfopn = (FlatOpNode) curr;
		Enumeration ecld = null; 
		PrefetchPair childpp = null;
		PairMap pm = new PairMap();

		if(currfopn.getOp().getOp()== Operation.ASSIGN) {
			ecld = child_prefetch_set_copy.keys();
			while (ecld.hasMoreElements()) {
				childpp = (PrefetchPair) ecld.nextElement();
				PrefetchPair copyofchildpp = (PrefetchPair) childpp.clone();

				/* For cases like x=y  with child prefetch set x[i].z,x.g*/
				if(childpp.base == currfopn.getDest()) {
					ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
					newdesc.addAll(childpp.desc);
					PrefetchPair newpp =  new PrefetchPair(currfopn.getLeft(), newdesc);
					Float newprob = child_prefetch_set_copy.get(childpp).floatValue();
					tocompare.put(newpp, newprob); 
					pm.addPair(childpp, newpp);
					child_prefetch_set_copy.remove(childpp);
					/* Check for independence of prefetch pairs to compute new probability */
					if(child_prefetch_set_copy.containsKey(newpp)) {
						newprob = (float)(1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
						if(newprob < ANALYSIS_THRESHOLD_PROB) {
							tocompare.remove(newpp);
						} else {
							tocompare.put(newpp, newprob); 
							pm.addPair(newpp, newpp);
						}
						child_prefetch_set_copy.remove(newpp);
					}
					newdesc = null;
					newpp = null;
					/* For cases like x=y  with child prefetch set r[i].x, r[x].p, r[p+x].q*/
				} else if(isTempDescFound(copyofchildpp, currfopn.getDest())) {
					ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
					newdesc.addAll((ArrayList<Descriptor>)getNewDesc(copyofchildpp, currfopn.getDest(), currfopn.getLeft()));
					PrefetchPair newpp =  new PrefetchPair(childpp.base, newdesc);
					Float newprob = child_prefetch_set_copy.get(childpp).floatValue();
					tocompare.put(newpp, newprob); 
					pm.addPair(childpp, newpp);
					child_prefetch_set_copy.remove(childpp);
					/* Check for independence of prefetch pairs to compute new probability*/ 
					if(child_prefetch_set_copy.containsKey(newpp)) {
						newprob = (float)(1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
						if(newprob < ANALYSIS_THRESHOLD_PROB) {
							tocompare.remove(newpp);
						} else {
							tocompare.put(newpp, newprob); 
							pm.addPair(newpp, newpp);
						}
						child_prefetch_set_copy.remove(newpp);
					}
					newdesc = null;
					newpp = null;
				}else {
					continue;
				}
			}
			//case i = i+z with child prefetch set a[i].x
		} else if(currfopn.getRight()!=null && (currfopn.getOp().getOp() == Operation.ADD)) {
			ecld = child_prefetch_set_copy.keys();
			while (ecld.hasMoreElements()) {
				childpp = (PrefetchPair) ecld.nextElement();
				PrefetchPair copyofchildpp = (PrefetchPair) childpp.clone();

				if(isTempDescFound(copyofchildpp, currfopn.getDest())) {
					ArrayList<Descriptor> newdesc = new ArrayList<Descriptor>();
					newdesc.addAll((ArrayList<Descriptor>)getNewDesc(copyofchildpp, currfopn.getDest(), currfopn.getLeft(), currfopn.getRight()));
					PrefetchPair newpp =  new PrefetchPair(childpp.base, newdesc);
					Float newprob = child_prefetch_set_copy.get(childpp).floatValue();
					tocompare.put(newpp, newprob); 
					pm.addPair(childpp, newpp);
					child_prefetch_set_copy.remove(childpp);
					/* Check for independence of prefetch pairs to compute new probability*/ 
					if(child_prefetch_set_copy.containsKey(newpp)) {
						newprob = (float)(1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
						if(newprob < ANALYSIS_THRESHOLD_PROB) {
							tocompare.remove(newpp);
						} else {
							tocompare.put(newpp, newprob); 
							pm.addPair(newpp, newpp);
						}
						child_prefetch_set_copy.remove(newpp);
					}
				}else {
					continue;
				}
			}
		} else {
			//FIXME Is not taken care of for cases like x = -y followed by a[x].i
		}

		/* Merge child prefetch pairs */
		ecld = child_prefetch_set_copy.keys();
		while(ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
			pm.addPair(childpp, childpp);
			child_prefetch_set_copy.remove(childpp);
		}

		/* Create prefetch mappings for child nodes */
		if(!pm.isEmpty()) {
			parentpmap.put(curr, pm);
		}
		pmap_hash.put(curr.getNext(0), parentpmap);

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
		/* Free heap memory */
		tocompare = null;
		pm = null;
	}

	/** This function processes a FlatLiteralNode where cases such as
	 * for e.g. i = 0 with child prefetch sets a[i].r, a[j+i].r or a[j].b[i].r
	 * are handled */
	private void processFlatLiteralNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_prefetch_set_copy,
			Hashtable<FlatNode, PairMap> parentpmap) {
		boolean pSetHasChanged = false;
		Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
		FlatLiteralNode currfln = (FlatLiteralNode) curr;
		Enumeration ecld = null; 
		PrefetchPair childpp = null;
		PairMap pm = new PairMap();

		if(currfln.getType().isIntegerType()) {
			ecld = child_prefetch_set_copy.keys();
			while (ecld.hasMoreElements()) {
				childpp = (PrefetchPair) ecld.nextElement();
				PrefetchPair copyofchildpp = (PrefetchPair) childpp.clone();
				if(isTempDescFound(copyofchildpp,currfln.getDst())) {
					ArrayList<Descriptor> copychilddesc = (ArrayList<Descriptor>) copyofchildpp.getDesc();
					int sizetempdesc = copychilddesc.size();
					ListIterator it = copychilddesc.listIterator();
					for(;it.hasNext();) {
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
					Float newprob = (child_prefetch_set_copy.get(childpp)).floatValue();
					tocompare.put(newpp, newprob); 
					pm.addPair(childpp, newpp);
					child_prefetch_set_copy.remove(childpp);
					/* Check for independence of prefetch pairs to compute new probability */
					if(child_prefetch_set_copy.containsKey(newpp)) {
						newprob = (float)(1.0 - ((1.0 - child_prefetch_set_copy.get(newpp).floatValue()) * (1.0 - tocompare.get(newpp).floatValue())));
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
		ecld = child_prefetch_set_copy.keys();
		while(ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
			pm.addPair(childpp, childpp);
			child_prefetch_set_copy.remove(childpp);
		}

		/* Create prefetch mappings for child nodes */
		if(!pm.isEmpty()) {
			parentpmap.put(curr, pm);
		}
		pmap_hash.put(curr.getNext(0), parentpmap);

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
		/* Free heap memory */
		tocompare = null;
		pm = null;
	}

	/** This function processes a FlatMethod where the method propagates
	 * the entire prefetch set of its child node */
	private void processFlatMethod(FlatNode curr, Hashtable<PrefetchPair, Float> child_prefetch_set_copy,
			Hashtable<FlatNode, PairMap> parentpmap) {
		boolean pSetHasChanged = false;
		Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
		FlatMethod currfm = (FlatMethod) curr;
		Enumeration ecld = null; 
		PrefetchPair childpp = null;
		PairMap pm = new PairMap();

		/* Merge child prefetch pairs */
		ecld = child_prefetch_set_copy.keys();
		while(ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
			pm.addPair(childpp, childpp);
			child_prefetch_set_copy.remove(childpp);
		}

		/* Create prefetch mappings for child nodes */
		if(!pm.isEmpty()) {
			parentpmap.put(curr, pm);
		}
		pmap_hash.put(curr.getNext(0), parentpmap);

		/* Compare with the orginal prefetch pairs */
		pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare);
		/* Enqueue parent nodes */
		if(pSetHasChanged) {
			/* Overwrite the new prefetch set to the global hash table */
			prefetch_hash.put(curr,tocompare); 
		} 
		tocompare = null;
		pm = null;
	}

	/** This Function processes the FlatCalls 
	 * It currently drops the propagation of those prefetchpairs whose base is
	 * same as the destination of the FlatCall 
	 */
	private void processFlatCall(FlatNode curr, Hashtable<PrefetchPair, Float> child_prefetch_set_copy,
			Hashtable<FlatNode, PairMap> parentpmap) {
		boolean pSetHasChanged = false;
		Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
		FlatCall currfcn = (FlatCall) curr;
		PairMap pm = new PairMap();
		Enumeration ecld = null; 
		PrefetchPair childpp = null;

		ecld = child_prefetch_set_copy.keys();
		while(ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			PrefetchPair copyofchildpp = (PrefetchPair) childpp.clone();
			if(currfcn.getReturnTemp() != childpp.base) {
				tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
				pm.addPair(childpp, childpp);
				child_prefetch_set_copy.remove(childpp);
			} else {
				child_prefetch_set_copy.remove(childpp);
			}
		}

		/* Create prefetch mappings for child nodes */
		if(!pm.isEmpty()) {
			parentpmap.put(curr, pm);
		}
		pmap_hash.put(curr.getNext(0), parentpmap);

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
		/* Free heap memory */
		tocompare = null;
		pm = null;
	}

	/** This function handles the processes the FlatNode of type FlatCondBranch
	 * It combines prefetches of both child elements and create a new hash table called
	 * branch_prefetch_set to contains the entries of both its children
	 */
	private void processFlatCondBranch(FlatNode curr, Hashtable<PrefetchPair, Float> child_prefetch_set_copy, int index, 
			Hashtable<PrefetchPair,Float> branch_prefetch_set, Hashtable<FlatNode, PairMap> parentpmap) {
		boolean pSetHasChanged = false;
		Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();//temporary hash table
		FlatCondBranch currfcb = (FlatCondBranch) curr;
		Float newprob = new Float((float)0.0);
		PairMap pm = new PairMap();
		PrefetchPair childpp = null;
		PrefetchPair pp = null;
		Enumeration ecld = null;

		ecld = child_prefetch_set_copy.keys();
		while (ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			/* True Edge */
			if(index == 0) {
				newprob = child_prefetch_set_copy.get(childpp).floatValue() * BRANCH_TRUE_EDGE_PROB;
				if(newprob >= ANALYSIS_THRESHOLD_PROB) {
					tocompare.put(childpp, newprob); 
					pm.addPair(childpp, childpp);
				}
				child_prefetch_set_copy.remove(childpp);
			} else if(index == 1) { /* False Edge */
				newprob = child_prefetch_set_copy.get(childpp).floatValue() * BRANCH_FALSE_EDGE_PROB;
				if(newprob >= ANALYSIS_THRESHOLD_PROB) {
					tocompare.put(childpp, newprob); 
					pm.addPair(childpp, childpp);
				}
				child_prefetch_set_copy.remove(childpp);
			} else {
				System.out.println("DEBUG-> No more children of the FlatCondBranchNode present");
			}
		}

		/* Create prefetch mappings for child nodes */
		if(!pm.isEmpty()) {
			parentpmap.put(curr, pm);
		}
		pmap_hash.put(curr.getNext(index), parentpmap);

		/* Update branch_prefetch_set (global hash table) to combine all prefetch pairs from childnodes of the
		 * cond branch that is currently stored in the tocompare hash table */
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
							if(newprob < ANALYSIS_THRESHOLD_PROB) {
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

		/* Compare prefetch sets and enqueue parent nodes: Only possible after combining prefetch pairs of both child nodes 
		 * into branch_prefetch_set hashtable*/
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
		/* Free heap memory */
		tocompare = null;
		pm = null;
	}

	/** If FlatNode is not concerned with the prefetch set of its Child then propagate 
	 * prefetches up the FlatNode*/  
	private void processDefaultCase(FlatNode curr, Hashtable<PrefetchPair, Float> child_prefetch_set_copy,
			Hashtable<FlatNode, PairMap> parentpmap) {
		boolean pSetHasChanged = false;
		PairMap pm = new PairMap();
		Enumeration e = null;
		Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();

		/* Propagate all child nodes */
		for(e = child_prefetch_set_copy.keys(); e.hasMoreElements();) {
			PrefetchPair childpp = (PrefetchPair) e.nextElement();
			tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
			pm.addPair(childpp, childpp);
			child_prefetch_set_copy.remove(childpp);
		}

		/* Check case for nodes with no children (e.g return null) and create prefetch mappings for child nodes*/
		if(curr.numNext() != 0) {
			if(!pm.isEmpty()) {
				parentpmap.put(curr, pm);
			}
			pmap_hash.put(curr.getNext(0), parentpmap);
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
		/* Free heap memory */
		tocompare = null;
		pm = null;
	}

	/** This functions processes for FlatNewNode
	 * for e.g x = NEW(foo) followed by childnode with prefetch set x.f
	 * then drop the prefetches beyond this FlatNewNode */
	private void processFlatNewNode(FlatNode curr, Hashtable<PrefetchPair, Float> child_prefetch_set_copy,
			Hashtable<FlatNode, PairMap> parentpmap) {
		boolean pSetHasChanged = false;
		Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
		FlatNew currfnn = (FlatNew) curr;
		Float newprob = new Float((float)0.0);
		PairMap pm = new PairMap();
		PrefetchPair childpp = null;
		Enumeration ecld = null;

		ecld = child_prefetch_set_copy.keys();
		while (ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			if(childpp.base == currfnn.getDst()){
				child_prefetch_set_copy.remove(childpp);
			} else {
				tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
				pm.addPair(childpp, childpp);
				child_prefetch_set_copy.remove(childpp);
			}
		}

		/* Create prefetch mappings for child nodes */
		if(!pm.isEmpty()) {
			parentpmap.put(curr, pm);
		}
		pmap_hash.put(curr.getNext(0), parentpmap);

		/* Compare with the old prefetch set */
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

	/** This functions processes for FlatCastNode
	 * for e.g x = (cast type) y followed by childnode with prefetch set x.f
	 * then drop the prefetches beyond this FlatCastNode */
	private void processFlatCastNode(FlatNode curr, Hashtable<PrefetchPair, Float>child_prefetch_set_copy, 
			Hashtable<FlatNode, PairMap> parentpmap) {
		boolean pSetHasChanged = false;
		Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
		FlatCastNode currfcn = (FlatCastNode) curr;
		Float newprob = new Float((float)0.0);
		PairMap pm = new PairMap();
		PrefetchPair childpp = null;
		Enumeration ecld = null;

		ecld = child_prefetch_set_copy.keys();
		while (ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			if(childpp.base == currfcn.getDst()){
				child_prefetch_set_copy.remove(childpp);
			} else {
				tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
				pm.addPair(childpp, childpp);
				child_prefetch_set_copy.remove(childpp);
			}
		}

		/* Create prefetch mappings for child nodes */
		if(!pm.isEmpty()) {
			parentpmap.put(curr, pm);
		}
		pmap_hash.put(curr.getNext(0), parentpmap);

		/* Compare with the old prefetch set */
		pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare);

		/* Enqueue parent nodes */
		if(pSetHasChanged) {
			for(int i=0; i<curr.numPrev(); i++) {
				tovisit.add(curr.getPrev(i));
			}
			/* Overwrite the new prefetch set to the global hash table */
			prefetch_hash.put(curr,tocompare); 
		} 
		/* Free heap memory */
		tocompare = null;
		pm = null;
	}

	/** This functions processes for FlatTagDeclaration
	 * for e.g x = (cast type) y followed by childnode with prefetch set x.f
	 * then drop the prefetches beyond this FlatTagDeclaration */
	private void processFlatTagDeclaration(FlatNode curr, Hashtable<PrefetchPair, Float>child_prefetch_set_copy, 
			Hashtable<FlatNode, PairMap> parentpmap) {
		boolean pSetHasChanged = false;
		Hashtable<PrefetchPair, Float> tocompare = new Hashtable<PrefetchPair, Float>();
		FlatTagDeclaration currftd = (FlatTagDeclaration) curr;
		Float newprob = new Float((float)0.0);
		PairMap pm = new PairMap();
		PrefetchPair childpp = null;
		Enumeration ecld = null;

		ecld = child_prefetch_set_copy.keys();
		while (ecld.hasMoreElements()) {
			childpp = (PrefetchPair) ecld.nextElement();
			if(childpp.base == currftd.getDst()){
				child_prefetch_set_copy.remove(childpp);
			} else {
				tocompare.put(childpp, child_prefetch_set_copy.get(childpp));
				pm.addPair(childpp, childpp);
				child_prefetch_set_copy.remove(childpp);
			}
		}

		/* Create prefetch mappings for child nodes */
		if(!pm.isEmpty()) {
			parentpmap.put(curr, pm);
		}
		pmap_hash.put(curr.getNext(0), parentpmap);

		/* Compare with the old prefetch set */
		pSetHasChanged = comparePrefetchSets(prefetch_hash.get(curr), tocompare);

		/* Enqueue parent nodes */
		if(pSetHasChanged) {
			for(int i=0; i<curr.numPrev(); i++) {
				tovisit.add(curr.getPrev(i));
			}
			/* Overwrite the new prefetch set to the global hash table */
			prefetch_hash.put(curr,tocompare); 
		} 

		/* Free heap memory */
		tocompare = null;
		pm = null;
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

	private void doInsPrefetchAnalysis(FlatMethod fm) {
		HashSet<PrefetchPair> pset1_init = new HashSet<PrefetchPair>();
		LinkedList<FlatNode> newtovisit = new LinkedList<FlatNode>();  
		newvisited = new LinkedList<FlatNode>();  

		newtovisit.addLast((FlatNode)fm);
		while(!newtovisit.isEmpty()) {
			FlatNode fn = (FlatNode) newtovisit.iterator().next();
			newtovisit.remove(0);
			pset1_hash.put(fn, pset1_init);
			newvisited.addLast(fn);
			for(int i=0; i<fn.numNext(); i++) {
				FlatNode nn = fn.getNext(i);
				if(!newtovisit.contains(nn) && !newvisited.contains(nn)){
					newtovisit.addLast(nn);
				}
			}
		}

		/* Free Heap Memory */
		pset1_init = null;
		newtovisit = null;

		/* Delete redundant and subset prefetch pairs */
		delSubsetPPairs();
	
		/* Start with a top down sorted order of nodes */
		while(!newvisited.isEmpty()) {
			applyPrefetchInsertRules((FlatNode) newvisited.getFirst());
			newvisited.remove(0);
		}
	}

	/** This function deletes the smaller prefetch pair subset from a list of prefetch pairs 
	 * for e.g. if there are 2 prefetch pairs a.b.c.d and a.b.c for a given flatnode
	 * then this function drops a.b.c from the prefetch set of the flatnode */
	private void delSubsetPPairs() {
		Enumeration e = prefetch_hash.keys();
		while(e.hasMoreElements()) {
			FlatNode fn = (FlatNode) e.nextElement();
			Hashtable ppairs = prefetch_hash.get(fn);
			Enumeration epp = ((Hashtable)(prefetch_hash.get(fn))).keys();
			Vector<PrefetchPair> pplist = new Vector<PrefetchPair>();
			Vector pplength = new Vector();
			Vector ppisMod = new Vector();
			while(epp.hasMoreElements()) {
				PrefetchPair pp = (PrefetchPair) epp.nextElement();
				pplist.add(pp);
				int length = pp.desc.size()+ 1;
				pplength.add(length);
				ppisMod.add(0);
			}
			int numpp = ((Hashtable)(prefetch_hash.get(fn))).size();
			for (int i = 0; i < numpp; i++) {
				for (int j = i+1; j < numpp; j++) {
					boolean ret;
					int x = ((Integer) (pplength.get(i))).intValue();
					if (((Integer) (pplength.get(i))).intValue() < ((Integer)( pplength.get(j))).intValue()) {
						ret = isSubSet(pplist.get(i), pplist.get(j));
						if (ret) {
							ppisMod.set(i, 1);
						}
					} else {
						ret = isSubSet(pplist.get(j), pplist.get(i));
						if (ret) {
							ppisMod.set(j, 1);
						}
					}
				}
			}
			for (int i = 0; i < numpp; i++) {
				if (((Integer)(ppisMod.get(i))).intValue() == 1) {
					PrefetchPair pp = (PrefetchPair) pplist.get(i);
					ppairs.remove(pp);
				}
			}

			/* Free heap memory */
			pplist = null;
			pplength = null;
			ppisMod = null;
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
		boolean hasChanged = false;

		if(oldPSet.size() != newPSet.size()) {
			return true;
		} else {
			for(Iterator it = newPSet.iterator();it.hasNext();) {
				if(!oldPSet.contains((PrefetchPair)it.next())) {
					return true;
				}
			}
		}
		return hasChanged;
	}

	/** This function creates a set called pset1 that contains prefetch pairs that have already
	 * been prefetched. While traversing the graph of a flat representation in a top down fashion,
	 * this function creates pset1 such that it contains prefetch pairs that have been prefetched at
	 * the previous nodes */

	private void applyPrefetchInsertRules(FlatNode fn) {
		HashSet<PrefetchPair> pset1 = new HashSet<PrefetchPair>();
		HashSet<PrefetchPair> pset2 = new HashSet<PrefetchPair>();
		HashSet<PrefetchPair> newpset = new HashSet<PrefetchPair>();
		Hashtable<PrefetchPair, Float> prefetchset = new Hashtable<PrefetchPair, Float>();
		boolean ppairIsPresent = false;
		boolean mapIsPresent = true;
		boolean pprobIsGreater = false;
		boolean mapprobIsLess = false;
		boolean probIsLess = false;
		boolean pSet1HasChanged = false;
		Enumeration e = null; 
		/* Create pset1 */
		if(fn.kind() == FKind.FlatMethod) {
			if(prefetch_hash.containsKey(fn)) {
				prefetchset = prefetch_hash.get(fn);
				e = prefetchset.keys();
				while(e.hasMoreElements()) {
					PrefetchPair pp = (PrefetchPair) e.nextElement();
					/* Apply initial rule */
					if(((float)prefetchset.get(pp).floatValue()) > PREFETCH_THRESHOLD_PROB) {
						pset1.add(pp);
					}
				}
				/* Enqueue child node is Pset1 has changed */
				pSet1HasChanged = comparePSet1(pset1_hash.get(fn), pset1);
				if(pSet1HasChanged) {
					for(int j=0; j<fn.numNext(); j++) {
						FlatNode nn = fn.getNext(j);
						newvisited.addLast((FlatNode)nn);
					}
				}
				pset1_hash.put(fn, pset1);
				if(pset1.size() > 0) {
					newprefetchset.put(fn, pset1); 
				}
			}
		} else {
			if(prefetch_hash.containsKey(fn)) {
				prefetchset = prefetch_hash.get(fn);
				for(Enumeration epset = prefetchset.keys(); epset.hasMoreElements();) {
					PrefetchPair pp = (PrefetchPair) epset.nextElement();
					/* Create pset2 */
					Hashtable<FlatNode, PairMap> ppairmaphash = new Hashtable<FlatNode, PairMap>();
					ppairmaphash = pmap_hash.get(fn);
					if(!ppairmaphash.isEmpty()) {
						e = ppairmaphash.keys();
						while(e.hasMoreElements()) {
							FlatNode parentnode = (FlatNode) e.nextElement();
							PairMap pm = (PairMap) ppairmaphash.get(parentnode);
							if(pset1_hash.containsKey(parentnode)) {
								HashSet pset = pset1_hash.get(parentnode);
								if(!pset.isEmpty()) {
									if(ppairIsPresent = (pset.contains((PrefetchPair) pm.getPair(pp)))) {
										mapIsPresent = ppairIsPresent && mapIsPresent;
									}
								} else {
									mapIsPresent = false;
								}
							}
						}
						if(mapIsPresent) {
							pset2.add(pp);
						}
					}

					/* Create newprefetchset */
					if(pprobIsGreater = (prefetchset.get(pp).floatValue() > PREFETCH_THRESHOLD_PROB)) {
						ppairmaphash = pmap_hash.get(fn);
						if(!ppairmaphash.isEmpty()) {
							e = ppairmaphash.keys();
							while(e.hasMoreElements()) {
								FlatNode parentnode = (FlatNode) e.nextElement();
								PairMap pm = (PairMap) ppairmaphash.get(parentnode);
								PrefetchPair mappedpp = pm.getPair(pp);
								if(mappedpp != null) {
									if(prefetch_hash.get(parentnode).containsKey(mappedpp)) {
										float prob = (float)prefetch_hash.get(parentnode).get(mappedpp).floatValue();
										if(probIsLess = (prob < PREFETCH_THRESHOLD_PROB))
											mapprobIsLess = mapprobIsLess || probIsLess;
									}
								} else {
									mapprobIsLess = false;
								}
							}
						} else {
							mapprobIsLess = true;
						}
					}
					if(pprobIsGreater && mapprobIsLess) {
						newpset.add(pp);
					}
				}
			}
			if(!pset2.isEmpty())
				pset1.addAll(pset2);
			if(!newpset.isEmpty())
				pset1.addAll(newpset);
			/* Enqueue child node if Pset1 has changed */
			pSet1HasChanged = comparePSet1(pset1_hash.get(fn), pset1);
			if(pSet1HasChanged) {
				for(int i=0; i<fn.numNext(); i++) {
					FlatNode nn = fn.getNext(i);
					newvisited.addLast((FlatNode)nn);
				}
			}
			pset1_hash.put(fn, pset1);


			/* To insert prefetch apply rule: if the newpset intersection pset2 is nonempty
			 * then insert a new prefetch node here*/
			HashSet<PrefetchPair> s = new HashSet<PrefetchPair>();
			if(!newpset.isEmpty()) {
				if(!pset2.isEmpty()) {
					for(Iterator it = newpset.iterator(); it.hasNext();) {
						PrefetchPair pp = (PrefetchPair) it.next();
						if(!pset2.contains(pp)) {
							s.add(pp);
						}
					}
				} else {
					for(Iterator it = newpset.iterator(); it.hasNext();) {
						PrefetchPair pp = (PrefetchPair) it.next();
						s.add(pp);
					}
				}
			}
			if(s.size() > 0) {
				newprefetchset.put(fn, s); 
			}
		}

		/* Free heap memory */
		pset1 = null;
		pset2 = null;
		newpset = null;
		prefetchset = null;
	}

	private void addFlatPrefetchNode(Hashtable<FlatNode, HashSet<PrefetchPair>> newprefetchset) {
		int i;
		Enumeration e = null;
		e = newprefetchset.keys();
		boolean isFNPresent = false; /* Detects presence of FlatNew node */
		/* This modifies the graph */
		while(e.hasMoreElements()) {
			FlatNode fn = (FlatNode) e.nextElement();
			FlatPrefetchNode fpn = new FlatPrefetchNode();
			for(i = 0; i< newprefetchset.get(fn).size(); i++) {
				fpn.insAllpp((HashSet)newprefetchset.get(fn));
			}
			if(fn.kind() == FKind.FlatMethod) {
				FlatNode nn = fn.getNext(0);
				fn.setNext(0, fpn);
				fpn.addNext(nn);
			} else {
				/* Check if previous node of this FlatNode is a NEW node 
				 * If yes, delete this flatnode and its prefetch set from hash table 
				 * This eliminates prefetches for NULL ptrs*/
				for(i = 0; i< fn.numPrev(); i++) {
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
