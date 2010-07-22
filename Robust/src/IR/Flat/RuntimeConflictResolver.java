package IR.Flat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import Analysis.Disjoint.*;

public class RuntimeConflictResolver {
	
	private static final String getAllocSiteInC = "->AllocSite";
	private static final String queryHashTableInC = "hashtable.contains(";
	private static final String addToHashTableInC = "hashtable.put(";
	private static final String addToQueueInC = "queue.enqueue(";
	private static final String dequeueFromQueueInC = "queue.dequeue()";
	
	public void traverse(FlatSESEEnterNode rblock, 
			Hashtable<Taint, Set<Effect>> effects,
			Hashtable<Taint, Set<Effect>> conflicts,
			ReachGraph rg)
	{
		Set<TempDescriptor> inVars = rblock.getInVarSet();
		Hashtable<NodeKey, Node> created = new Hashtable<NodeKey, Node>();

		
		//Is this even needed?
		if(inVars.size() == 0)
			return;
		
		/*
		 * Basic steps:
		 * 1) Create pruned data structures from givens
		 * 		1a) Use effects sets to verify if we can access something (reads)
		 * 		1b) Mark all conflicts with a special flag (perhaps create wrapper)
		 * 2) Traverse again and build code output structure (as Objects)
		 * 3) printout 
		 */
		
		createTree(rblock, inVars, conflicts, rg, created);
		generateCPrintoutStructure(created);
		printCCode();
	}

	//I'll assume that I'll get a pointer to the first data element named ptr
	private void printHeader(StringBuilder out)
	{
		//TODO find out more about Hashset and stuff on the C-side
		//TODO includes
		//TODO initialize hashset/hashtable
		//TODO initialize Queue
		System.out.println();
	}
	private void printFooter(StringBuilder out)
	{
		//TODO End the while loops and such 
		//TODO free stuff we've used???
	}

	private StringBuilder generateCPrintoutStructure(Hashtable<NodeKey, Node> created) 
	{
		HashSet<Integer> done = new HashSet<Integer>();
		StringBuilder out = new StringBuilder();
		
		printHeader(out);
		for(Node node: created.values())
		{
			//If we haven't seen it and it's a node with more than 1 parent
			//Note: a node with 0 parents is a root node (i.e. inset variable)
			if(!done.contains(new Integer(node.getAllocationSite())) && node.numOfParents != 1)
				addChecker(node, done, out, "ptr");
		}
		printFooter(out);
		
		return out;
	}
	
	private void addChecker(Node node, 
							HashSet<Integer> done, 
							StringBuilder out,
							String prefix)
	{
		if(node.numOfParents != 1) {
			assert prefix.equals("ptr");
			out.append("case " + node.getAllocationSite() + ":\n");
		}
		
		//adds self to hashtable
		out.append(addToHashTableInC + prefix + ");");
		
		for(Reference ref: node.references)
		{
			String childPtr = prefix + "->" + ref.field;
			
			//Checks if the child exists and is correct
			out.append("if(" + childPtr + " != NULL && " + childPtr + getAllocSiteInC + 
					"==" + ref.allocSite + ") { ");
			
			//Checks if we have visited the child before
			out.append("if(!" + queryHashTableInC + childPtr + ") {");
			
			if(ref.reference.numOfParents == 1) 
				addChecker(ref.reference, done, out, childPtr);
			else
				out.append(addToQueueInC + childPtr+ "); } ");
			
			out.append(" }} ");
		}
		
		if(node.numOfParents != 1) 
			out.append("break;\n");
		
		done.add(new Integer(node.getAllocationSite()));
	}
	
	//I'll assume that I'll be just given a pointer named ptr in my function. 
	private void printCCode() {
		// TODO Auto-generated method stub
		
	}
	
	private void createTree(FlatSESEEnterNode rblock,
							Set<TempDescriptor> inVars,
							Hashtable<Taint, Set<Effect>> conflicts, 
							ReachGraph rg,
							Hashtable<NodeKey, Node> created) {
		for(TempDescriptor invar: inVars)
		{
			VariableNode varNode = rg.getVariableNodeNoMutation(invar);
			Hashtable<EffectsKey, EffectsHashPair> table = generateHashtable(rblock, varNode ,conflicts, conflicts);
			//if table is null that means there's no conflicts, therefore we need not create a traversal
			if(table != null)
			{
				Iterator<RefEdge> possibleRootNodes = varNode.iteratorToReferencees();
				
				while(possibleRootNodes.hasNext())
				{
					RefEdge edge = possibleRootNodes.next();
					assert edge != null;

					//always assumed to be a conflict on the root variables. 
					Node singleRoot = new Node(edge.getDst(), true);
					NodeKey rootKey = new NodeKey(singleRoot.allocSite);
					
					if(!created.contains(rootKey)) {
						created.put(rootKey, singleRoot);
						createHelper(singleRoot, edge.getDst().iteratorToReferencees(), created, table);
					}
				}
			}
		}
	}
	
	/*
	 * Plan is to add stuff to the tree depth-first sort of way. That way, we can propogate up conflicts.
	 */
	private void createHelper(Node parent, Iterator<RefEdge> edges, Hashtable<NodeKey, Node> created, Hashtable<EffectsKey, EffectsHashPair> table) {
		assert table != null;
		while(edges.hasNext()) {
			RefEdge edge = edges.next();
			String field = edge.getField();
			HeapRegionNode childHRN = edge.getDst();
			
			EffectsKey lookup = new EffectsKey(childHRN.getAllocSite(), field);
			EffectsHashPair effect = table.get(lookup);
			
			//if there's no effect, we don't traverse this edge.
			if(effect != null) {
				NodeKey key = new NodeKey(childHRN.getAllocSite());
				boolean isNewChild = !created.contains(key);
				Node child;
				
				if(isNewChild) 
					child = new Node(childHRN, effect.conflict);
				else {
					child = created.get(key);
					child.myConflict = effect.conflict;
				}
					
				parent.addChild(field, child);				
				if(effect.conflict)
					propogateConflictFlag(parent);
				
				if(effect.type == Effect.read && isNewChild)
					createHelper(child, childHRN.iteratorToReferencees(), created, table);
			}
		}
	}


	//This will propagate the conflict up the tree. 
	private void propogateConflictFlag(Node node)
	{
		Node curr = node;
		
		while(curr != null && curr.decendentsConflict != true)
		{
			curr.decendentsConflict = true;
			curr = curr.lastParent;
		}
	}
	
	
	
	
	private Hashtable<EffectsKey, EffectsHashPair>  generateHashtable(FlatSESEEnterNode rblock, 
														VariableNode var, 
														Hashtable<Taint, Set<Effect>> effects,
														Hashtable<Taint, Set<Effect>> conflicts)
	{
		//we search effects since conflicts is only a subset of effects
		Taint taint = getProperTaint(rblock, var, effects);
		assert taint !=null;
		
		Set<Effect> localEffects = effects.get(taint);
		Set<Effect> localConflicts = conflicts.get(taint);		
		
		if(localEffects == null || localEffects.isEmpty() || conflicts == null || conflicts.isEmpty())
			return null;
		
		Hashtable<EffectsKey, EffectsHashPair> table = new Hashtable<EffectsKey, EffectsHashPair>();
		
		for(Effect e: localEffects)
		{
			EffectsKey key = new EffectsKey(e);
			EffectsHashPair element = new EffectsHashPair(e, localConflicts.contains(e));
			table.put(key, element);
		}
		
		return table;
	}
	
	private Taint getProperTaint(FlatSESEEnterNode rblock, 
								VariableNode var, 
								Hashtable<Taint, Set<Effect>> effects)
	{
		Set<Taint> taints = effects.keySet();
		for(Taint t: taints)
			if(t.getSESE().equals(rblock) && t.getVar().equals(var.getTempDescriptor()))
				return t;
		
		return null;
	}
	
	private class EffectsKey
	{
		AllocSite allocsite;
		String field;
		
		public EffectsKey(AllocSite a, String f)
		{
			allocsite = a;
			field = f;
		}
		
		public EffectsKey(Effect e)
		{
			allocsite = e.getAffectedAllocSite();
			field = e.getField().getSymbol();
		}
		
		//Hashcode only hashes the object based on AllocationSite and Field
		public int hashCode()
		{
			return allocsite.hashCode() ^ field.hashCode();
		}
		
		//Equals ONLY compares object based on AllocationSite and Field
		public boolean equals(Object o)
		{
		    if (o == null) 
		        return false;

		    if (!(o instanceof EffectsKey))
		        return false;
		    
		    EffectsKey other = (EffectsKey) o;
		    
		    return (other.allocsite.equals(this.allocsite) &&
		    		other.field.equals(this.field));
		}
	}
	
	private class EffectsHashPair
	{
		Effect originalEffect;
		int type;
		boolean conflict;
		
		public EffectsHashPair(Effect e, boolean conflict)
		{
			originalEffect = e;
			type = e.getType();
			this.conflict = conflict;
		}
		
		//Hashcode only hashes the object based on AllocationSite and Field
		public int hashCode()
		{
			return originalEffect.hashCode();
		}
		
		//Equals ONLY compares object based on AllocationSite and Field
		public boolean equals(Object o)
		{
		    if (o == null) 
		        return false;

		    if (!(o instanceof EffectsHashPair))
		        return false;
		    
		    EffectsHashPair other = (EffectsHashPair) o;
		    
		    return (other.originalEffect.getAffectedAllocSite().equals(originalEffect.getAffectedAllocSite()) &&
		    		other.originalEffect.getField().equals(originalEffect.getField()));
		}
		
		public boolean isConflict()
		{
			return conflict;
		}
	}

	private class Reference
	{
		String field;
		int allocSite;
		Node reference;
		
		public Reference(String fieldname, Node ref)
		{
			field = fieldname;
			allocSite = ref.getAllocationSite();
			reference = ref;
		}
	}
	
	private class NodeKey
	{
		int allocsite;
		
		public NodeKey(AllocSite site) 
		{
			allocsite = site.hashCodeSpecific();
		}
		
		public int hashCode()
		{
			return allocsite;
		}
	}
	

private class Node
{
	ArrayList<Reference> references;
	Node lastParent;
	int numOfParents;
	boolean myConflict;
	boolean decendentsConflict;
	AllocSite allocSite;		
	HeapRegionNode original;
	
	public Node(HeapRegionNode me, boolean conflict)
	{
		references = new ArrayList<Reference>();
		lastParent = null;
		numOfParents = 0;
		allocSite = me.getAllocSite();
		original = me;
		this.myConflict = conflict;
		decendentsConflict = false;
	}
	
	@Override
	public int hashCode() 
	{
	  //I assume this gets the allocsite number number
	  return allocSite.hashCodeSpecific();
	}
  
	@Override
	public boolean equals(Object obj) 
	{
       return original.equals(obj);
	}
	
	public int getAllocationSite()
	{
		return allocSite.hashCodeSpecific();
	}
	
	public void addChild(String field, Node child)
	{
		child.lastParent = this;
		child.numOfParents++;
		Reference ref = new Reference(field, child);
		references.add(ref);
	}
}

}
