package Analysis.MLP;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

public class MethodSummary {
	
	public static final Integer VOID=new Integer(0);
	public static final Integer ACCESSIBLE = new Integer(1);
	public static final Integer INACCESSIBLE=new Integer(2);

	private int childSESECount;
	private HashSet<PreEffectsKey> effectsSet;
	private Integer accessibility;
	private StallSite returnStallSite;
	
	private Hashtable<Integer, HashSet<StallTag>> mapParamIndexToRelatedStallTag;
	
	public MethodSummary() {
		effectsSet = new HashSet<PreEffectsKey>();
		accessibility = MethodSummary.VOID;
		childSESECount = 0;
		returnStallSite=null;
		mapParamIndexToRelatedStallTag=new Hashtable<Integer, HashSet<StallTag>>();
	}
	
	public void addStallParamIdxSet(Set<Integer> paramIdxSet, HashSet<StallTag> stallTagSet){
		
		if(paramIdxSet!=null){
			for (Iterator iterator = paramIdxSet.iterator(); iterator.hasNext();) {
				Integer paramIdx = (Integer) iterator.next();
				mapParamIndexToRelatedStallTag.put(paramIdx, stallTagSet);
			}
		}
	}
	
	public HashSet<StallTag> getStallTagByParamIdx(Integer paramIdx){
		return mapParamIndexToRelatedStallTag.get(paramIdx);
	}
	
	
	public Set<Integer> getStallParamIdxSet(){
		return mapParamIndexToRelatedStallTag.keySet();
	}
	
	public void setReturnStallSite(StallSite ss){
		returnStallSite=ss;
	}
	
	public StallSite getReturnStallSite(){
		return returnStallSite;
	}

	public void increaseChildSESECount() {
		childSESECount++;
	}

	public int getChildSESECount() {
		return childSESECount;
	}

	public Integer getReturnValueAccessibility() {
		return accessibility;
	}

	public void setReturnValueAccessibility(Integer accessibility) {
		this.accessibility = accessibility;
	}

	public HashSet<PreEffectsKey> getEffectsSet() {
		return effectsSet;
	}

	@Override
	public String toString() {
		return "MethodSummary [accessibility=" + accessibility
				+ ", childSESECount=" + childSESECount + ", effectsSet="
				+ effectsSet + "]";
	}
	
	public HashSet<PreEffectsKey> getEffectsSetByParamIdx(int paramIdx){
		
		HashSet<PreEffectsKey> returnSet=new HashSet<PreEffectsKey>();

		for (Iterator iterator = effectsSet.iterator(); iterator.hasNext();) {
			PreEffectsKey preEffectsKey = (PreEffectsKey) iterator.next();
			if(preEffectsKey.getParamIndex().equals(new Integer(paramIdx))){
				returnSet.add(preEffectsKey);
			}
		}
		
		return returnSet;
	}
	
}

class PreEffectsKey {

	public static final Integer READ_EFFECT = new Integer(1);
	public static final Integer WRITE_EFFECT = new Integer(2);

	private String type;
	private String field;
	private Integer effectType;
	private Integer paramIndex;

	public PreEffectsKey(Integer paramIndex, String field, String type,
			Integer effectType) {
		this.paramIndex = paramIndex;
		this.field = field;
		this.type = type;
		this.effectType = effectType;
	}

	public String getType() {
		return type;
	}

	public String getField() {
		return field;
	}

	public Integer getEffectType() {
		return effectType;
	}

	public Integer getParamIndex() {
		return paramIndex;
	}

	public String toString() {
		return "PreEffectsKey [effectType=" + effectType + ", field=" + field
				+ ", paramIndex=" + paramIndex + ", type=" + type + "]";
	}

	public boolean equals(Object o) {

		if (o == null) {
			return false;
		}

		if (!(o instanceof PreEffectsKey)) {
			return false;
		}

		PreEffectsKey in = (PreEffectsKey) o;

		if (paramIndex.equals(in.getParamIndex())
				&& field.equals(in.getField()) && type.equals(in.getType())
				&& effectType.equals(in.getEffectType())) {
			return true;
		} else {
			return false;
		}

	}

}
