package Analysis.MLP;

import java.util.HashSet;

import Analysis.OwnershipAnalysis.ReachabilitySet;

public class StallSite {

	public static final Integer READ_EFFECT = new Integer(1);
	public static final Integer WRITE_EFFECT = new Integer(2);

	private HashSet<Effect> effectSet;
	private HashSet<Integer> hrnIDSet;
	private ReachabilitySet rechabilitySet;

	public StallSite() {
		effectSet = new HashSet<Effect>();
		hrnIDSet = new HashSet<Integer>();
		rechabilitySet = new ReachabilitySet();
	}

	public StallSite(HashSet<Effect> effectSet, HashSet<Integer> hrnIDSet,
			ReachabilitySet rechabilitySet) {
		this.effectSet = effectSet;
		this.hrnIDSet = hrnIDSet;
		this.rechabilitySet = rechabilitySet;
	}

	public void addEffect(String type, String field, Integer effect) {
		Effect e = new Effect(type, field, effect);
		effectSet.add(e);
	}

	public HashSet<Effect> getEffectSet() {
		return effectSet;
	}

	public HashSet<Integer> getHRNIDSet() {
		return hrnIDSet;
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
				&& hrnIDSet.equals(in.getHRNIDSet())
				&& rechabilitySet.equals(in.getReachabilitySet())) {
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