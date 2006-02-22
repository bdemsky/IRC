package IR.Flat;
import java.util.Vector;

public class FlatNode {
    protected Vector next;
    protected Vector prev;

    public String toString() {
	throw new Error();
    }
    public int numNext() {
	return next.size();
    }
    public FlatNode getNext(int i) {
	return (FlatNode) next.get(i);
    }

    public int numPrev() {
	return prev.size();
    }
    public FlatNode getPrev(int i) {
	return (FlatNode) prev.get(i);
    }
    
    public void addNext(FlatNode n) {
	next.add(n);
	n.addPrev(this);
    }
    protected void addPrev(FlatNode p) {
	prev.add(p);
    }
}
