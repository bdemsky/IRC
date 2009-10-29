package Analysis.Locality;

public class BranchAnalysis {
  LocalityAnalysis locality;
  State state;
  public BranchAnalysis(Locality locality, LocalityAnalysis lb, Set<FlatNode> nodeset, State state) {
    this.locality=locality;
    this.state=state;
    doAnalysis(lb, nodeset);
  }

  Hashtable<Set<FlatNode>, Vector<FlatNode>> table=new Hashtable<Set<FlatNode>, Vector<FlatNode>>();
  Hashtable<FlatNode, FlatNode[]> fnmap;
  Hashtable<FlatNode, Set<FlatNode>> groupmap;

  public int jumpValue(FlatNode fn, int i) {
    FlatNode next=fnmap.get(fn)[i];
    Set<FlatNode> group=groupmap.get(fn);
    if (group==null)
      return -1;
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

  public void doAnalysis(LocalityAnalysis lb, Set<FlatNode> nodeset) {
    Set<FlatNode> transset=computeTransSet(lb);
    fnmap=computeMap(transset, nodeset);
    groupmap=new Hashtable<FlatNode, Set<FlatNode>>();

    for(Iterator<FlatNode> fnit=transset.iterator();fnit.hasNext();) {
      FlatNode fn=fnit.next();
      if (fn.numNext()>1) {
	FlatNode[] children=fnmap.get(fn);
	if (!groupmap.containsKey(fn)) {
	  groupmap.put(fn, new HashSet<FlatNode>());
	  groupmap.get(fn).add(fn);
	}
	for(int i=0;i<children.length;i++) {
	  FlatNode child=children[i];
	  if (child.numNext()>1)
	    mergegroups(fn, child, groupmap);
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

  public Hashtable<FlatNode, FlatNode[]> computeMap(Set<FlatNode> transset, Set<FlatNode> nodeset) {
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
	if (nodeset.contains(fprev)) {
	  for(int j=0;j<fprev.numNext();j++) {
	    if (fprev.getNext(j)==fn) {
	      Object[] pair=new Object[2];
	      pair[0]=new Integer(j);pair[1]=fn;
	      incomingtuples.add(pair);
	    }
	  }
	} else {
	  Set<Object[]> tuple=fntotuple.get(fprev);
	  incomingtuples.addAll(tuple);
	}
      }

      if (nodeset.contains(fn)) {
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
	tntotuple.put(fn,incomingtuples);
	for(int i=0;i<fn.numNext();i++) {
	  if (transset.contains(fn.getNext(i)))
	    toprocess.add(fn.getNext(i));
	}
      }
    }
    return fnmap;
  }


  public Set<FlatNode> computeTransSet(LocalityAnalysis lb) {
    Set<FlatNode> transset=new HashSet();
    Set<FlatNode> tovisit=new HashSet();
    tovisit.addAll(state.getMethodFlat(lb.getMethod()).getNodeSet());
    while(!tovisit.isEmpty()) {
      FlatNode fn=tovisit.iterator().next();
      tovisit.remove(fn);
      if (locality.getAtomic(lb).get(fn).intValue()>0)
	transset.add(fn);
    }
    return transset;
  }
}