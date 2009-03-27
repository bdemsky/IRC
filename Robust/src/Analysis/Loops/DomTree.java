package Analysis.Loops;
import java.util.Set;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Stack;
import IR.Flat.FlatNode;

public class DomTree {
  Hashtable<FlatNode, FlatNode> domtable;
  Vector<FlatNode> vec;
  Hashtable<FlatNode,Integer> vecindex;
  Hashtable<FlatNode, Set<FlatNode>> childtree;

  public DomTree(FlatMethod fm) {
    analyze(fm);
  }

  public FlatNode idom(FlatNode fn) {
    return domtable.get(fn);
  }

  public Set<FlatNode> children(FlatNode fn) {
    return childtree(fn);
  }

  public void analyze(FlatMethod fm) {
    DFS(fm);
    domtable=new Hashtable<FlatNode, FlatNode>();
    domtable.put(fm,fm);
    HashSet<FlatNode> set=new HashSet<FlatNode> ();
    set.add(fm);
    childtree.put(fm,set);

    boolean changed=true;
    while(changed) {
      changed=false;
      for(int i=vec.size()-2;i>=0;i--) {
	FlatNode fn=vec.elementAt(i);
	FlatNode dom=null;
	for(int j=0;j<fn.numPrev();j++) {
	  FlatNode ndom=domtable.get(fn.getPrev(i));
	  dom=intersect(dom,ndom);
	}
	if (!domtable.containsKey(fn)||
	    !domtable.get(fn).equals(ndom)) {
	  domtree.put(fn,dom);
	  if (!childtree.containsKey(dom))
	    childtree.put(dom, new HashSet<FlatNode>());
	  childtree.get(dom).add(fn);
	  changed=true;
	}
      }
    }
  }

  public FlatNode intersect(FlatNode fa, FlatNode fb) {
    int ifa=vecindex.get(fa).intValue();
    int ifb=vecindex.get(fb).intValue();
    while(ifa!=ifb) {
      while (ifa<ifb) {
	fa=domtable.get(fa);
	ifa=vecindex.get(fa).intValue();
      }
      while (ifb<ifa) {
	fb=domtable.get(fb);
	ifb=vecindex.get(fb).intValue();
      }
    }
    return fa;
  }


  public void DFS(FlatMethod fm) {
    vec=new Vector<FlatNode>();
    vecindex=new Hashtable<FlatNode,Integer>();
    HashSet visited=new HashSet();
    Stack<FlatNode> stack=new Stack<FlatNode>();
    stack.push(fm);
    visited.add(next);
    mainloop:
    while(!stack.isEmpty()) {
      FlatNode fn=stack.peek();
      for(int i=0;i<fn.numNext();i++) {
	FlatNode next=fn.getNext(i);
	if (!visited.contains(next)) {
	  visited.add(next);
	  stack.push(next);
	  continue mainloop;
	}
      }
      //We're done with this item, return
      vecindex.put(fn, new Integer(vec.size()));
      vec.add(fn);
      stack.pop();
    }
  }


}