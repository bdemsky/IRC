package Analysis.MLP;

import java.util.HashSet;

import Analysis.OwnershipAnalysis.HeapRegionNode;
import IR.Flat.TempDescriptor;

public class StallSiteNode extends ConflictNode {

	protected StallSite stallSite;

	public StallSiteNode(String id, TempDescriptor td, StallSite stallSite) {
		this.id = id;
		this.td = td;
		this.stallSite = stallSite;
	}
	
	public HashSet<HeapRegionNode> getHRNSet(){
		return stallSite.getHRNSet();
	}
	
	public StallSite getStallSite(){
		return stallSite;
	}

}
