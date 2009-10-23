package Analysis.MLP;

import java.util.Hashtable;

import IR.Flat.TempDescriptor;

public class ParentChildConflictsMap {

	public static final Integer ACCESSIBLE = new Integer(1);
	public static final Integer INACCESSIBLE = new Integer(2);

	private Hashtable<TempDescriptor, Integer> accessibleMap;
	private Hashtable<TempDescriptor, StallSite> stallMap;

	public ParentChildConflictsMap() {

		accessibleMap = new Hashtable<TempDescriptor, Integer>();
		stallMap = new Hashtable<TempDescriptor, StallSite>();

	}

	public Hashtable<TempDescriptor, Integer> getAccessibleMap() {
		return accessibleMap;
	}

	public Hashtable<TempDescriptor, StallSite> getStallMap() {
		return stallMap;
	}
	
	public void addAccessibleVar(TempDescriptor td){
		accessibleMap.put(td, ACCESSIBLE);
	}
	
	public void addInaccessibleVar(TempDescriptor td){
		accessibleMap.put(td, INACCESSIBLE);
	}
	
	public void addStallSite(TempDescriptor td){
		StallSite stallSite=new StallSite();
		stallMap.put(td, stallSite);
	}

	public boolean isAccessible(TempDescriptor td){
		if(accessibleMap.contains(td) && accessibleMap.get(td).equals(ACCESSIBLE)){
			return true;
		}
		return false;
	}
	
	public void contributeEffect(TempDescriptor td, String type, String field, int effect){
		
		StallSite stallSite=stallMap.get(td);
		if(stallSite!=null){
			stallSite.addEffect(type, field, effect);
		}
		
	}
	
}
