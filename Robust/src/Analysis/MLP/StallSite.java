package Analysis.MLP;

import java.util.HashSet;
import java.util.Iterator;

import Analysis.OwnershipAnalysis.AllocationSite;
import Analysis.OwnershipAnalysis.HeapRegionNode;
import Analysis.OwnershipAnalysis.ReachabilitySet;

public class StallSite {

	public static final Integer READ_EFFECT = new Integer(1);
	public static final Integer WRITE_EFFECT = new Integer(2);

	private HashSet<Effect> effectSet;
	private HashSet<HeapRegionNode> hrnSet;
	private HashSet<AllocationSite> allocationSiteSet;
	private ReachabilitySet rechabilitySet;

	public StallSite() {
		effectSet = new HashSet<Effect>();
		hrnSet = new HashSet<HeapRegionNode>();
		rechabilitySet = new ReachabilitySet();
		allocationSiteSet=new HashSet<AllocationSite>();
	}
	
	public StallSite(HashSet<HeapRegionNode> hrnSet){
		this();
		setHeapRegionNodeSet(hrnSet);
		for (Iterator iterator = hrnSet.iterator(); iterator.hasNext();) {
			HeapRegionNode heapRegionNode = (HeapRegionNode) iterator.next();
			setAllocationSite(heapRegionNode.getAllocationSite());
		}
	}

	public StallSite(HashSet<Effect> effectSet, HashSet<HeapRegionNode> hrnSet,
			ReachabilitySet rechabilitySet) {
		this.effectSet = effectSet;
		this.hrnSet = hrnSet;
		this.rechabilitySet = rechabilitySet;
	}
	
	public void setAllocationSite(AllocationSite allocationSite){
		if(allocationSite!=null){
			allocationSiteSet.add(allocationSite);
		}
	}
	
	public void setHeapRegionNodeSet(HashSet<HeapRegionNode> newSet){
		hrnSet.addAll(newSet);
	}
	
	public HashSet<AllocationSite> getAllocationSiteSet(){
		return allocationSiteSet;
	}

	public void addEffect(String type, String field, Integer effect) {
		Effect e = new Effect(type, field, effect);
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

	public boolean equals(Object o) {

		if (o == null) {
			return false;
		}

		if (!(o instanceof StallSite)) {
			return false;
		}

		StallSite in = (StallSite) o;

		if (effectSet.equals(in.getEffectSet())
				&& hrnSet.equals(in.getHRNSet())
				&& rechabilitySet.equals(in.getReachabilitySet())) {
			return true;
		} else {
			return false;
		}

	}

	public String toString() {
		return "StallSite [effectSet=" + effectSet + ", hrnIDSet=" + hrnSet
				+ ", rechabilitySet=" + rechabilitySet + "]";
	}
	
}

class Effect {

	private String field;
	private String type;
	private Integer effect;

	public Effect(String type, String field, Integer effect) {
		this.type = type;
		this.field = field;
		this.effect = effect;
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

	public boolean equals(Object o) {

		if (o == null) {
			return false;
		}

		if (!(o instanceof StallSite)) {
			return false;
		}

		Effect in = (Effect) o;

		if (type.equals(in.getType()) && field.equals(in.getField())
				&& effect.equals(in.getEffect())) {
			return true;
		} else {
			return false;
		}

	}

}