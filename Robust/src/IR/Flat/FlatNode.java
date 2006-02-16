package IR.Flat;
import java.util.Vector;

public class FlatNode {
    Vector next;


    public String toString() {
	throw new Error();
    }
    public void addNext(FlatNode n) {
	next.add(n);
    }
}
