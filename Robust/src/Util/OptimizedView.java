import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Set;

public class OptimizedView extends MultiHash
{
	private int bitMapView;
	private Hashtable table;
	private MultiHash parent;

	public OptimizedView(int bitMapView, Hashtable table, MultiHash parent) {
    	this.bitMapView = bitMapView;
    	this.table 		= table;
    	this.parent		= parent;
    }

	public void remove(Tuple o){
		parent.remove(o);
	}
	public Tuples get(Tuples o){
		Tuples tuple = new Tuple();

		int tupleKey	= generateTupleKey(o);
		if(table.containsKey(tupleKey)){
			Set tupleSet = (Set) table.get(tupleKey);
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
		Tuples tuple = new Tuple(o);
		return tuple;
	}

	public int generateTupleKey(Tuples o){
		ArrayList<Integer> indices = findIndices(bitMapView);
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

	public String toString(){
		return table.toString();
	}
}
