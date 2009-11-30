package Analysis.MLP;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import Analysis.OwnershipAnalysis.TokenTupleSet;
import IR.Flat.TempDescriptor;

public abstract class ConflictNode {

	protected TempDescriptor td;
	protected String id;
	protected HashSet<ConflictEdge> edgeSet;
	protected Set<Set> reachabilitySet;

	public ConflictNode() {
		edgeSet = new HashSet<ConflictEdge>();
	}

	public TempDescriptor getTempDescriptor() {
		return td;
	}

	public String getID() {
		return id;
	}

	public boolean isConflictConnectedTo(ConflictNode node) {

		for (Iterator iterator = edgeSet.iterator(); iterator.hasNext();) {
			ConflictEdge edge = (ConflictEdge) iterator.next();
			if (edge.getType() != ConflictEdge.WRITE_CONFLICT) {
				if (edge.getVertexU().equals(node)
						|| edge.getVertexV().equals(node)) {
					return true;
				}
			}
		}
		return false;
	}

	public void addEdge(ConflictEdge edge) {
		edgeSet.add(edge);
	}

	public HashSet<ConflictEdge> getEdgeSet() {
		return edgeSet;
	}

	public Set<Set> getReachabilitySet() {
		return reachabilitySet;
	}

}
