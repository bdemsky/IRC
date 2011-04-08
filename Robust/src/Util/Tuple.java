import java.util.ArrayList;

public class Tuple implements Tuples{
	ArrayList o;

	public Tuple(){
		o = new ArrayList();
	}

	public Tuple(ArrayList o){
		this.o = o;
	}

	public Object get(int i){
		return o.get(i);
	}

	public void remove(int i){
		o.remove(i);
	}

	public ArrayList getList(){
		return o;
	}

	public int size(){
		return o.size();
	}

	public int hashCode(){
		return o.hashCode();
	}

	public String toString(){
		String tmp="";
		for(int i = 0; i < o.size(); i++){
			tmp += o.get(i)+" ";
		}
		return tmp;
	}
}
