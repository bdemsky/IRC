package Analysis.MLP;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import Analysis.OwnershipAnalysis.AllocationSite;
import Analysis.OwnershipAnalysis.HeapRegionNode;
import Analysis.OwnershipAnalysis.ReachabilitySet;
import IR.Flat.FlatNode;

public class StallSite {

	public static final Integer READ_EFFECT = new Integer(1);
	public static final Integer WRITE_EFFECT = new Integer(2);

	private HashSet<Effect> effectSet;
	private HashSet<HeapRegionNode> hrnSet;
	private HashSet<AllocationSite> allocationSiteSet;
	private ReachabilitySet rechabilitySet;
	private HashSet<StallTag> stallTagSet;

	// if stall site is caller's parameter heap regtion, store its parameter idx
	// for further analysis
	private HashSet<Integer> callerParamIdxSet;

	public StallSite() {
		effectSet = new HashSet<Effect>();
		hrnSet = new HashSet<HeapRegionNode>();
		rechabilitySet = new ReachabilitySet();
		allocationSiteSet = new HashSet<AllocationSite>();
		stallTagSet = new HashSet<StallTag>();
		callerParamIdxSet = new HashSet<Integer>();
	}

	public StallSite(HashSet<HeapRegionNode> hrnSet, StallTag tag) {

		this();

		setHeapRegionNodeSet(hrnSet);
		stallTagSet.add(tag);

		for (Iterator iterator = hrnSet.iterator(); iterator.hasNext();) {
			HeapRegionNode heapRegionNode = (HeapRegionNode) iterator.next();
			setAllocationSite(heapRegionNode.getAllocationSite());
		}
	}

	public StallSite(HashSet<Effect> effectSet, HashSet<HeapRegionNode> hrnSet,
			ReachabilitySet rechabilitySet, HashSet<AllocationSite> alocSet,
			HashSet<StallTag> tagSet, HashSet<Integer> paramIdx) {
		this();
		this.effectSet.addAll(effectSet);
		this.hrnSet.addAll(hrnSet);
		this.rechabilitySet = rechabilitySet;
		this.allocationSiteSet.addAll(alocSet);
		this.stallTagSet.addAll(tagSet);
		this.callerParamIdxSet.addAll(paramIdx);
	}

	public HashSet<Integer> getCallerParamIdxSet() {
		return callerParamIdxSet;
	}

	public void addCallerParamIdxSet(Set<Integer> newParamSet) {
		if (newParamSet != null) {
			callerParamIdxSet.addAll(newParamSet);
		}
	}

	public void setStallTagSet(HashSet<StallTag> tags) {
		stallTagSet = tags;
	}

	public void addRelatedStallTag(StallTag stallTag) {
		stallTagSet.add(stallTag);
	}

	public void setAllocationSite(AllocationSite allocationSite) {
		if (allocationSite != null) {
			allocationSiteSet.add(allocationSite);
		}
	}

	public void setHeapRegionNodeSet(HashSet<HeapRegionNode> newSet) {
		hrnSet.addAll(newSet);
	}

	public HashSet<AllocationSite> getAllocationSiteSet() {
		return allocationSiteSet;
	}

	public void addEffect(String type, String field, Integer effect) {

		Effect e = new Effect(type, field, effect, stallTagSet);
		effectSet.add(e);
	}

	public HashSet<Effect> getEffectSet() {
		return effectSet;
	}

	public HashSet<HeapRegionNode> getHRNSet() {
		return hrnSet;
	}

	public ReachabilitySet getReachabilitySet() {
		return rechabilitySet;
	}

	public HashSet<StallTag> getStallTagSet() {
		return stallTagSet;
	}

	public StallSite copy() {

		StallSite copy = new StallSite(effectSet, hrnSet, rechabilitySet,
				allocationSiteSet, stallTagSet, callerParamIdxSet);
		return copy;
		
	}

	public boolean equals(Object o) {

		if (o == null) {
			return false;
		}

		if (!(o instanceof StallSite)) {
			return false;
		}

		StallSite in = (StallSite) o;

		if (allocationSiteSet.equals(in.getAllocationSiteSet())
				&& stallTagSet.equals(in.getStallTagSet())
				&& effectSet.equals(in.getEffectSet())
				&& hrnSet.equals(in.getHRNSet())
				&& rechabilitySet.equals(in.getReachabilitySet())) {
			return true;
		} else {
			return false;
		}

	}

	@Override
	public String toString() {
		return "StallSite [allocationSiteSet=" + allocationSiteSet
				+ ", callerParamIdxSet=" + callerParamIdxSet + ", effectSet="
				+ effectSet + ", hrnSet=" + hrnSet + ", rechabilitySet="
				+ rechabilitySet + ", stallTagSet=" + stallTagSet + "]";
	}

}

class StallTag {

	private FlatNode fn;

	public StallTag(FlatNode fn) {
		this.fn = fn;
	}

	public FlatNode getKey() {
		return fn;
	}

	public boolean equals(Object o) {

		if (o == null) {
			return false;
		}

		if (!(o instanceof StallTag)) {
			return false;
		}

		StallTag in = (StallTag) o;

		if (getKey().equals(in.getKey())) {
			return true;
		} else {
			return false;
		}

	}

}

class Effect {

	private String field;
	private String type;
	private Integer effect;
	private HashSet<StallTag> stallTagSet;

	public Effect() {
		stallTagSet = new HashSet<StallTag>();
	}

	public Effect(String type, String field, Integer effect,
			HashSet<StallTag> tagSet) {
		this();
		this.type = type;
		this.field = field;
		this.effect = effect;
		stallTagSet.addAll(tagSet);
	}

	public String getField() {
		return field;
	}

	public String getType() {
		return type;
	}

	public Integer getEffect() {
		return effect;
	}

	public HashSet<StallTag> getStallTagSet() {
		return stallTagSet;
	}

	public boolean equals(Object o) {

		if (o == null) {
			return false;
		}

		if (!(o instanceof Effect)) {
			return false;
		}

		Effect in = (Effect) o;

		if (stallTagSet.equals(in.getStallTagSet())
				&& type.equals(in.getType()) && field.equals(in.getField())
				&& effect.equals(in.getEffect())) {
			return true;
		} else {
			return false;
		}

	}

	public String toString() {
		return "Effect [effect=" + effect + ", field=" + field
				+ ", stallTagSet=" + stallTagSet + ", type=" + type + "]";
	}

}