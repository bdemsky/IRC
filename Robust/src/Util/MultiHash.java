import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

public class MultiHash{
	private	int[]	views;
	private Hashtable viewTable	= new Hashtable();

	public MultiHash(){
	}
	
	// Pass in the look up map
	public MultiHash(int[] bitmapArray){
		this.views	= bitmapArray;
		for(int i = 0; i < views.length; i++){
			Hashtable ht = new Hashtable();
			viewTable.put(views[i], ht);
		}
	}

	// For each view add it to its view hashtable
	public void put(Tuples o){
		// Tune the Tuple for each view and add it to its designated hashtable
		for(int i = 0; i < views.length; i++){
			int tupleKey 	= generateTupleKey(o, views[i]);
			Hashtable tuplesTable = (Hashtable) viewTable.get(views[i]);
			if(tuplesTable.containsKey(tupleKey)){
				Set tupleSet = (Set) tuplesTable.get(tupleKey);
				tupleSet.add(o);
			}else{
				Set tupleSet = new HashSet();
				tupleSet.add(o);
				tuplesTable.put(tupleKey, tupleSet);
			}
		}
	}

	public int generateTupleKey(Tuples o, int viewIndex){
		ArrayList<Integer> indices = findIndices(viewIndex);
		ArrayList	obj	=	new ArrayList();
		for(int i = 0; i < indices.size(); i++){
			obj.add(o.get(indices.get(i)));
		}
		return obj.hashCode()^29;
	}

	private ArrayList<Integer> findIndices(int viewIndex){
		int mask = 1;
		ArrayList<Integer> indices = new ArrayList<Integer>();
		for(int i = 0; i < 31; i++){
			if((mask & viewIndex) != 0){
				indices.add(i);
			}
			mask = mask << 1;
		}
		return indices;
	}

	public Tuples get(int bitmap, Tuple o){
		Tuples tuple = new Tuple(); //
		int tupleKey	= generateTupleKey(o, bitmap);
		Hashtable tuplesTable = (Hashtable) viewTable.get(bitmap);
		if(tuplesTable.containsKey(tupleKey)){
			Set tupleSet = (Set) tuplesTable.get(tupleKey);
			tuple = convertToTuple(tupleSet);
			return tuple;
		}
		return null;
	}

	private Tuples convertToTuple(Set tupleSet){
		Object[] tuples = tupleSet.toArray();
		ArrayList o		= new ArrayList();
		for(int i = 0; i < tuples.length; i++){
			o.add(tuples[i]);
		}
		Tuples tuple		= new Tuple(o);
		return tuple;
	}

	public void remove(Tuples o){
//		System.out.println("removed called"+viewTable.toString());
		for(int i = 0; i < views.length; i++){
			int tupleKey	= generateTupleKey(o, views[i]);
			Hashtable tuplesTable = (Hashtable) viewTable.get(views[i]);
			if(tuplesTable.containsKey(tupleKey)){
				tuplesTable.remove(tupleKey);
			}else{
				System.out.println("Cannot find such key");
			}
		}
	}

	public OptimizedView getOptimizedView(int bitMapView){
		Hashtable tmp = (Hashtable) viewTable.get(bitMapView);
		OptimizedView ov = new OptimizedView(bitMapView, tmp, this);
		return ov;
	}

	/* Debug visualizations */
	public void drawTierTwoTable(){
		for(int i = 0; i < views.length; i++){
			Hashtable tmp = (Hashtable) viewTable.get(views[i]);
			System.out.println("Hashtable "+i+":\t"+tmp.keySet().toString());
			Object[] keySets = tmp.keySet().toArray();
			for(int j = 0; j < keySets.length; j++){
				System.out.println(tmp.get(keySets[j]));
			}
		}
	}
	
	public int[] getViews(){
		return views;
	}
	
	public Hashtable getTable(){
		return viewTable;
	}
}
