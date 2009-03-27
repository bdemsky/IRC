package Analysis.Loops;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Stack;
import IR.Flat.FlatNode;
import IR.Flat.FlatMethod;

public class DomTree {
  Hashtable<FlatNode, FlatNode> domtable;
  Vector<FlatNode> vec;
  Hashtable<FlatNode,Integer> vecindex;
  Hashtable<FlatNode, Set<FlatNode>> childtree;

  public DomTree(FlatMethod fm, boolean postdominator) {
    analyze(fm, postdominator);
  }

  public FlatNode idom(FlatNode fn) {
    return domtable.get(fn);
  }

  public Set<FlatNode> children(FlatNode fn) {
    return childtree.get(fn);
  }

  public void analyze(FlatMethod fm, boolean postdominator) {
    domtable=new Hashtable<FlatNode, FlatNode>();
    if (postdominator) {
      Set<FlatNode> fnodes=fm.getNodeSet();
      Vector<FlatNode> v=new Vector<FlatNode>();
      for(Iterator<FlatNode> fit=fnodes.iterator();fit.hasNext();) {
	FlatNode fn=fit.next();
	if (fn.numNext()==0)
	  v.add(fn);
      }
      FlatNode[] fnarray=new FlatNode[v.size()];
      for(int i=0;i<v.size();i++) {
	fnarray[i]=v.elementAt(i);
	domtable.put(fnarray[i],fnarray[i]);
	HashSet<FlatNode> set=new HashSet<FlatNode> ();
	set.add(fnarray[i]);
	childtree.put(fnarray[i],set);
      }
      DFS(fnarray, postdominator);
    } else {
      DFS(new FlatNode[] {fm}, postdominator);
      HashSet<FlatNode> set=new HashSet<FlatNode> ();
      domtable.put(fm,fm);
      set.add(fm);
      childtree.put(fm,set);
    }

    boolean changed=true;
    while(changed) {
      changed=false;
      for(int i=vec.size()-2;i>=0;i--) {
	FlatNode fn=vec.elementAt(i);
	FlatNode dom=null;
	for(int j=0;j<(postdominator?fn.numNext():fn.numPrev());j++) {
	  FlatNode ndom=domtable.get(postdominator?fn.getNext(i):fn.getPrev(i));
	  dom=intersect(dom,ndom);
	}
	if (!domtable.containsKey(fn)||
	    !domtable.get(fn).equals(dom)) {
	  domtable.put(fn,dom);
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


  public void DFS(FlatNode[] fm, boolean postdominator) {
    vec=new Vector<FlatNode>();
    vecindex=new Hashtable<FlatNode,Integer>();
    HashSet visited=new HashSet();
    Stack<FlatNode> stack=new Stack<FlatNode>();
    for(int i=0;i<fm.length;i++) {
      stack.push(fm[i]);
      visited.add(fm[i]);
    }
    mainloop:
    while(!stack.isEmpty()) {
      FlatNode fn=stack.peek();
      for(int i=0;i<(postdominator?fn.numPrev():fn.numNext());i++) {
	FlatNode next=postdominator?fn.getPrev(i):fn.getNext(i);
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