package Analysis.Locality;
import IR.State;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class BranchAnalysis {
  LocalityAnalysis locality;
  State state;
  public BranchAnalysis(LocalityAnalysis locality, LocalityBinding lb, Set<FlatNode> nodeset, Set<FlatNode> storeset, State state) {
    this.locality=locality;
    this.state=state;
    doAnalysis(lb, nodeset, storeset);
  }

  Hashtable<Set<FlatNode>, Vector<FlatNode>> table=new Hashtable<Set<FlatNode>, Vector<FlatNode>>();
  Hashtable<FlatNode, FlatNode[]> fnmap;
  Hashtable<FlatNode, Set<FlatNode>> groupmap;

  public int jumpValue(FlatNode fn, int i) {
    FlatNode next=fnmap.get(fn)[i];
    Set<FlatNode> group=groupmap.get(fn);
    if (group==null)
      return -1;
    while (next.numNext()==1&&group.contains(next)) {
      next=fnmap.get(next)[0];
    }
    if (group.contains(next))
      return -1;
    Vector<FlatNode> exits=table.get(group);
    int exit=exits.indexOf(next);
    if (exit<0)
      throw new Error();
    return exit;
  }

  public int numJumps(FlatNode fn) {
    Set<FlatNode> group=groupmap.get(fn);
    if (group==null)
      return -1;
    Vector<FlatNode> exits=table.get(group);
    return exits.size();
  }

  public Vector<FlatNode> getJumps(FlatNode fn) {
    Set<FlatNode> group=groupmap.get(fn);
    if (group==null)
      throw new Error();
    Vector<FlatNode> exits=table.get(group);
    return exits;
  }

  public Set<FlatNode> getTargets() {
    HashSet<FlatNode> targets=new HashSet<FlatNode>();
    Collection<Set<FlatNode>> groups=groupmap.values();
    for(Iterator<Set<FlatNode>> setit=groups.iterator();setit.hasNext();) {
      Set<FlatNode> group=setit.next();
      targets.addAll(table.get(group));
    }
    return targets;
  }

  int grouplabelindex=0;

  public boolean hasGroup(FlatNode fn) {
    return groupmap.contains(fn);
  }

  Hashtable<Set<FlatNode>, String> grouplabel=new Hashtable<Set<FlatNode>, String>();

  private boolean seenGroup(FlatNode fn) {
    return grouplabel.containsKey(groupmap.get(fn));
  }

  private String getGroup(FlatNode fn) {
    if (!grouplabel.containsKey(groupmap.get(fn)))
      grouplabel.put(groupmap.get(fn), new String("LG"+(grouplabelindex++)));
    return grouplabel.get(groupmap.get(fn));
  }

  public void generateGroupCode(FlatNode fn, PrintWriter output, Hashtable<FlatNode, Integer> nodetolabels) {
    if (seenGroup(fn)) {
      String label=getGroup(fn);
      output.println("goto "+label+";");
    } else {
      String label=getGroup(fn);
      output.println(label+":");
      if (numJumps(fn)==1) {
	FlatNode fndst=getJumps(fn).get(0);
	output.println("goto L"+nodetolabels.get(fndst)+";");
      } else if (numJumps(fn)==2) {
	Vector<FlatNode> exits=getJumps(fn);
	output.println("if(RESTOREBRANCH())");
	output.println("goto L"+nodetolabels.get(exits.get(1))+";");
	output.println("else");
	output.println("goto L"+nodetolabels.get(exits.get(0))+";");
      } else {
	Vector<FlatNode> exits=getJumps(fn);
	output.println("switch(RESTOREBRANCH()) {");
	for(int i=0;i<exits.size();i++) {
	  output.println("case "+i+":");
	  output.println("goto L"+nodetolabels.get(exits.get(i))+";");
	}
	output.println("}");
      }
    }
  }

  public void doAnalysis(LocalityBinding lb, Set<FlatNode> nodeset, Set<FlatNode> storeset) {
    Set<FlatNode> transset=computeTransSet(lb);
    fnmap=computeMap(transset, nodeset, storeset);
    groupmap=new Hashtable<FlatNode, Set<FlatNode>>();

    for(Iterator<FlatNode> fnit=transset.iterator();fnit.hasNext();) {
      FlatNode fn=fnit.next();
      if ((fn.numNext()>1&&storeset.contains(fn))||fn.kind()==FKind.FlatBackEdge||fn.kind()==FKind.FlatNop) {
	FlatNode[] children=fnmap.get(fn);
	if (children==null)
	  continue;
	if (!groupmap.containsKey(fn)) {
	  groupmap.put(fn, new HashSet<FlatNode>());
	  groupmap.get(fn).add(fn);
	}
	for(int i=0;i<children.length;i++) {
	  FlatNode child=children[i];
	  if ((child.numNext()>1&&storeset.contains(child))||child.kind()==FKind.FlatBackEdge||child.kind()==FKind.FlatNop) {
	    mergegroups(fn, child, groupmap);
	  }
	}
      }
    }
    //now we have groupings...
    Collection<Set<FlatNode>> groups=groupmap.values();
    for(Iterator<Set<FlatNode>> setit=groups.iterator();setit.hasNext();) {
      Set<FlatNode> group=setit.next();
      Vector<FlatNode> exits=new Vector<FlatNode>();
      table.put(group, exits);
      for(Iterator<FlatNode> fnit=group.iterator();fnit.hasNext();) {
	FlatNode fn=fnit.next();
	FlatNode[] nextnodes=fnmap.get(fn);
	for(int i=0;i<nextnodes.length;i++) {
	  FlatNode nextnode=nextnodes[i];
	  if (!group.contains(nextnode)) {
	    //outside edge
	    if (!exits.contains(nextnode)) {
	      exits.add(nextnode);
	    }
	  }
	}
      }
    }
  }

  public void mergegroups(FlatNode fn1, FlatNode fn2, Hashtable<FlatNode, Set<FlatNode>> groupmap) {
    if (!groupmap.containsKey(fn1)) {
      groupmap.put(fn1, new HashSet<FlatNode>());
      groupmap.get(fn1).add(fn1);
    }
    if (!groupmap.containsKey(fn2)) {
      groupmap.put(fn2, new HashSet<FlatNode>());
      groupmap.get(fn2).add(fn2);
    }
    if (groupmap.get(fn1)!=groupmap.get(fn2)) {
      groupmap.get(fn1).addAll(groupmap.get(fn2));
      for(Iterator<FlatNode> fnit=groupmap.get(fn2).iterator();fnit.hasNext();) {
	FlatNode fn3=fnit.next();
	groupmap.put(fn3, groupmap.get(fn1));
      }
    }
  }

  public Hashtable<FlatNode, FlatNode[]> computeMap(Set<FlatNode> transset, Set<FlatNode> nodeset, Set<FlatNode> storeset) {
    Set<FlatNode> toprocess=new HashSet<FlatNode>();
    toprocess.addAll(transset);
    Hashtable<FlatNode, Set<Object[]>> fntotuple=new Hashtable<FlatNode, Set<Object[]>>();
    Hashtable<FlatNode, FlatNode[]> fnmap=new Hashtable<FlatNode, FlatNode[]>();
    while(!toprocess.isEmpty()) {
      FlatNode fn=toprocess.iterator().next();
      toprocess.remove(fn);
      Set<Object[]> incomingtuples=new HashSet<Object[]>();

      for(int i=0;i<fn.numPrev();i++) {
	FlatNode fprev=fn.getPrev(i);
	if (nodeset.contains(fprev)||storeset.contains(fprev)) {
	  for(int j=0;j<fprev.numNext();j++) {
	    if (fprev.getNext(j)==fn) {
	      Object[] pair=new Object[2];
	      pair[0]=new Integer(j);pair[1]=fprev;
	      incomingtuples.add(pair);
	    }
	  }
	} else {
	  Set<Object[]> tuple=fntotuple.get(fprev);
	  if (tuple!=null)
	    incomingtuples.addAll(tuple);
	}
      }

      if (nodeset.contains(fn)||storeset.contains(fn)||fn.kind()==FKind.FlatAtomicExitNode) {
	//nodeset contains this node
	for(Iterator<Object[]> it=incomingtuples.iterator();it.hasNext();) {
	  Object[] pair=it.next();
	  int index=((Integer)pair[0]).intValue();
	  FlatNode node=(FlatNode)pair[1];
	  if (!fnmap.containsKey(node))
	    fnmap.put(node, new FlatNode[node.numNext()]);
	  fnmap.get(node)[index]=fn;
	}
	incomingtuples=new HashSet<Object[]>();
      }

      //add if we need to update
      if (!fntotuple.containsKey(fn)||
	  !fntotuple.get(fn).equals(incomingtuples)) {
	fntotuple.put(fn,incomingtuples);
	for(int i=0;i<fn.numNext();i++) {
	  if (transset.contains(fn.getNext(i)))
	    toprocess.add(fn.getNext(i));
	}
      }
    }
    return fnmap;
  }


  public Set<FlatNode> computeTransSet(LocalityBinding lb) {
    Set<FlatNode> transset=new HashSet();
    Set<FlatNode> tovisit=new HashSet();
    tovisit.addAll(state.getMethodFlat(lb.getMethod()).getNodeSet());
    while(!tovisit.isEmpty()) {
      FlatNode fn=tovisit.iterator().next();
      tovisit.remove(fn);
      if (locality.getAtomic(lb).get(fn).intValue()>0||fn.kind()==FKind.FlatAtomicExitNode)
	transset.add(fn);
    }
    return transset;
  }
}