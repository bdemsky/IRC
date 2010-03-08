package Analysis.MLP;

import java.util.HashSet;
import java.util.Set;

import IR.Flat.TempDescriptor;

public abstract class ConflictNode {

	protected TempDescriptor td;
	protected String id;
	protected HashSet<ConflictEdge> edgeSet;
	protected Set<Set> reachabilitySet;
	protected TempDescriptor alias;
	protected int type;
	
	public static final int FINE_READ = 0;
	public static final int FINE_WRITE = 1;
	public static final int PARENT_READ = 2;
	public static final int PARENT_WRITE = 3;
	public static final int COARSE = 4;
	public static final int PARENT_COARSE = 5;
	public static final int SCC = 6;


	
	public ConflictNode() {
		edgeSet = new HashSet<ConflictEdge>();
	}
	
	public TempDescriptor getTempDescriptor() {
		return td;
	}

	public String getID() {
		return id;
	}
	
	public int getType(){
		return type;
	}

	public void setType(int type){
		this.type=type;
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
	
	public TempDescriptor getAlias(){
		return alias;
	}
	
	public void setAlias(TempDescriptor alias){
		this.alias=alias;
	}

}
