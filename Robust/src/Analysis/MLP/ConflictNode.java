package Analysis.MLP;

import java.util.HashSet;

import IR.Flat.TempDescriptor;

public abstract class ConflictNode {

	protected TempDescriptor td;
	protected String id;
	protected HashSet<ConflictEdge> edgeSet;

	public ConflictNode() {
		edgeSet = new HashSet<ConflictEdge>();
	}

	public TempDescriptor getTempDescriptor() {
		return td;
	}

	public String getID() {
		return id;
	}

	public void addEdge(ConflictEdge edge) {
		edgeSet.add(edge);
	}

	public HashSet<ConflictEdge> getEdgeSet() {
		return edgeSet;
	}

}
