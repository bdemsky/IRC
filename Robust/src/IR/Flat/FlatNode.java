package IR.Flat;
import java.util.Vector;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;

public class FlatNode {
  public Vector next;
  protected Vector prev;
  static int idcounter=0;
  public final int nodeid;
  public int numLine=-1;

  public FlatNode() {
    next=new Vector();
    prev=new Vector();
    nodeid=(idcounter++);
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

  public void removeNext(FlatNode n) {
    next.remove(n);
  }
  public void removePrev(FlatNode n) {
    prev.remove(n);
  }

  /** This function modifies the graph */
  public void setNext(int i, FlatNode n) {
    FlatNode old=getNext(i);
    next.set(i, n);
    old.prev.remove(this);
    n.addPrev(this);
  }
  /** This function modifies the graph */
  public void setNewNext(int i, FlatNode n) {
    if (next.size()<=i)
      next.setSize(i+1);
    next.set(i, n);
    n.addPrev(this);
  }
  /** This function modifies the graph */
  public void setprev(int i, FlatNode n) {
    prev.set(i, n);
  }
  /** This function modifies the graph */
  public void setnext(int i, FlatNode n) {
    next.set(i, n);
  }
  public void addPrev(FlatNode p) {
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
  public FlatNode clone(TempMap t) {
    throw new Error("no clone method for"+this);
  }

  public void rewriteUse(TempMap t) {
    System.out.println(toString());
    throw new Error();
  }

  public void rewriteDef(TempMap t) {
    System.out.println(toString());
    throw new Error();
  }

  public Set<FlatNode> getReachableSet(Set<FlatNode> endset) {
    HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
    HashSet<FlatNode> visited=new HashSet<FlatNode>();
    tovisit.add(this);
    while(!tovisit.isEmpty()) {
      FlatNode fn=tovisit.iterator().next();
      tovisit.remove(fn);
      visited.add(fn);
      if (endset!=null&&!endset.contains(fn)) {
        for(int i=0; i<fn.numNext(); i++) {
          FlatNode nn=fn.getNext(i);
          if (!visited.contains(nn))
            tovisit.add(nn);
        }
      }
    }
    return visited;
  }

  public void replace(FlatNode fnnew) {
    fnnew.prev.setSize(prev.size());
    fnnew.next.setSize(next.size());
    for(int i=0; i<prev.size(); i++) {
      FlatNode nprev=(FlatNode)prev.get(i);
      fnnew.prev.set(i,nprev);
      for(int j=0; j<nprev.numNext(); j++) {
        FlatNode n=nprev.getNext(j);
        if (n==this)
          nprev.next.set(j, fnnew);
      }
    }
    for(int i=0; i<next.size(); i++) {
      FlatNode nnext=(FlatNode)next.get(i);
      fnnew.next.set(i,nnext);
      for(int j=0; j<nnext.numPrev(); j++) {
        FlatNode n=nnext.getPrev(j);
        if (n==this)
          nnext.prev.set(j, fnnew);
      }
    }
    next=null;
    prev=null;
  }

  public void setNumLine(int lineNum) {
    this.numLine=lineNum;
  }

  public int getNumLine() {
    return this.numLine;
  }
}
