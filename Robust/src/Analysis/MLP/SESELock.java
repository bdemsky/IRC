package Analysis.MLP;

import java.util.HashSet;
import java.util.Iterator;

public class SESELock {
	
	private HashSet<ConflictNode> conflictNodeSet;
	private int id;
	
	public SESELock(){
		conflictNodeSet=new HashSet<ConflictNode>();
	}
	
	public void addEdge(ConflictEdge edge){
		conflictNodeSet.add(edge.getVertexU());
		conflictNodeSet.add(edge.getVertexV());
	}
	
	public int getID(){
		return id;
	}
	
	public void setID(int id){
		this.id=id;
	}
	
	public boolean containsConflictNode(ConflictNode node){
		
		return conflictNodeSet.contains(node);		
		
	}
	
	
	public boolean testEdge(ConflictEdge newEdge){
		
		
		if( !conflictNodeSet.contains(newEdge.getVertexU()) && !conflictNodeSet.contains(newEdge.getVertexV()) ){
			return false;
		}
		
		ConflictNode nodeToAdd=conflictNodeSet.contains(newEdge.getVertexU())?newEdge.getVertexV():newEdge.getVertexU();
		
		HashSet<ConflictNode> nodeSet=new HashSet<ConflictNode>(conflictNodeSet);

		for(Iterator edgeIter=nodeToAdd.getEdgeSet().iterator();edgeIter.hasNext();){
			ConflictEdge edge=(ConflictEdge)edgeIter.next();
			if(nodeSet.contains(edge.getVertexU())){
				nodeSet.remove(edge.getVertexU());
			}else if(nodeSet.contains(edge.getVertexV())){
				nodeSet.remove(edge.getVertexV());
			}
		}
		
		return nodeSet.isEmpty();
		
	}
	
	public String toString(){
		String rtr="";
		
		for (Iterator<ConflictNode> iterator = conflictNodeSet.iterator(); iterator.hasNext();) {
			ConflictNode node = (ConflictNode) iterator.next();
			rtr+=" "+node;
		}
		
		return rtr;
	}

}
