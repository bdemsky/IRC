package Analysis.MLP;

import java.util.HashSet;

import Analysis.OwnershipAnalysis.ReachabilitySet;

public class StallSite {
	
	public static final Integer READ_EFFECT=new Integer(1);
	public static final Integer WRITE_EFFECT=new Integer(2);
	
	private HashSet<Effect> effectSet;
	private int hrnID;
	private ReachabilitySet rechabilitySet;
	
	public StallSite(){
		effectSet=new HashSet<Effect>();
	}
	
	public void addEffect(String type, String field, Integer effect){
		Effect e=new Effect(type,field,effect);
		effectSet.add(e);
	}
	
}

class Effect{
	
	private String field;
	private String type;
	private Integer effect;
	
	public Effect(String type, String field, Integer effect){
		this.type=type;
		this.field=field;
		this.effect=effect;
	}
	
}