package Analysis.MLP;

import java.util.HashSet;
import java.util.Set;

import Analysis.OwnershipAnalysis.HeapRegionNode;
import IR.Flat.TempDescriptor;

public class StallSiteNode extends ConflictNode {

	protected StallSite stallSite;

	public StallSiteNode(String id, TempDescriptor td, StallSite stallSite,
			Set<Set> reachabilitySet) {
		this.id = id;
		this.td = td;
		this.stallSite = stallSite;
		this.reachabilitySet = reachabilitySet;
	}

	public HashSet<HeapRegionNode> getHRNSet() {
		return stallSite.getHRNSet();
	}

	public StallSite getStallSite() {
		return stallSite;
	}
	
	public String toString(){
		String str="StallSiteNode "+id;
		return str;
	}

}
