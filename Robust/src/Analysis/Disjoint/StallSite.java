package Analysis.Disjoint;

import java.util.HashSet;
import java.util.Set;

public class StallSite {

	private HashSet<Effect> effectSet;
	private HashSet<AllocSite> allocSiteSet;

	public StallSite(Set<AllocSite> allocSet) {
		effectSet = new HashSet<Effect>();
		allocSiteSet = new HashSet<AllocSite>();
		allocSiteSet.addAll(allocSet);
	}

	public void addEffect(Effect e) {
		effectSet.add(e);
	}

	public HashSet<Effect> getEffectSet() {
		return effectSet;
	}

	public Set<AllocSite> getAllocSiteSet(){
	  return allocSiteSet;
	}

	public boolean equals(Object o) {

		if (o == null) {
			return false;
		}

		if (!(o instanceof StallSite)) {
			return false;
		}

		StallSite in = (StallSite) o;

		if (allocSiteSet.equals(in.getAllocSiteSet())
				&& effectSet.equals(in.getEffectSet()) ){
			return true;
		} else {
			return false;
		}

	}

	@Override
	public String toString() {
		return "StallSite [allocationSiteSet=" + allocSiteSet
				+ ", effectSet=" + effectSet + "]";
	}
}
