package IR.Flat;
import java.util.Vector;

public class FlatNode {
    protected Vector next;
    protected Vector prev;

    public FlatNode() {
	next=new Vector();
	prev=new Vector();
    }

    public String toString() {
	throw new Error(this.getClass().getName() + "does not implement toString!");
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

    /** This function modifies the graph */
    public void setNext(int i, FlatNode n) {
	FlatNode old=getNext(i);
	next.set(i, n);
	old.prev.remove(this);
	n.addPrev(this);
    }

    protected void addPrev(FlatNode p) {
	prev.add(p);
    }
    public int kind() {
	throw new Error();
    }

    public TempDescriptor [] readsTemps() {
	return new TempDescriptor[0];
    }

    public TempDescriptor [] writesTemps() {
	return new TempDescriptor[0];
    }
}
