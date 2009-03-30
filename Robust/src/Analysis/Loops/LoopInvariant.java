package Analysis.Loops;

import IR.Flat.*;

public class LoopInvariant {
  public LoopInvariant(TypeUtil typeutil) {
    this.typeutil=typeutil;
  }
  LoopFinder loops;
  DomTree posttree;
  Hashtable<Loops, Set<FlatNode>> table;
  Set<FlatNode> hoisted;
  UseDef usedef;
  TypeUtil typeutil;

  public void analyze(FlatMethod fm) {
    loops=new LoopFinder(fm);
    Loops root=loops.getRootLoop(fm);
    table=new Hashtable<Loops, Set<FlatNode>>();
    hoisted=new HashSet<FlatNode>();
    usedef=new UseDef(fm);
    posttree=new DomTree(fm,true);
    recurse(root);
  }

  public void recurse(Loops parent) {
    processLoop(parent, parent.nestedLoops().size()==0);
    for(Iterator lpit=parent.nestedLoops().iterator();lpit.hasNext();) {
      Loops child=(Loops)lpit.next();
      recurse(child);
    }
  }

  public void processLoop(Loops l, boolean isLeaf) {
    boolean changed=true;
    boolean unsafe=false;
    Set elements=l.loopIncElements();
    Set toprocess=l.loopIncElements();
    toprocess.removeAll(hoisted);

    HashSet<FieldDescriptor> fields=new HashSet<FieldDescriptor>();
    HashSet<TypeDescriptor> types=new HashSet<TypeDescriptor>();

    if (!isLeaf) {
      unsafe=true; 
    } else {
      /* Check whether it is safe to reuse values. */
      for(Iterator elit=elements.iterator();elit.hasNext();) {
	FlatNode fn=elit.next();
	if (fn.kind()==FKind.FlatAtomicEnterNode||
	    fn.kind()==FKind.FlatAtomicExitNode||
	    fn.kind()==FKind.Call) {
	  unsafe=true;
qq	  break;
	} else if (fn.kind()==FKind.FlatSetFieldNode) {
	  FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
	  fields.add(fsfn.getField());
	} else if (fn.kind()==FKind.FlatSetElementNode) {
	  FlatSetElementNode fsen=(FlatSetElementNode)fn;
	  types.add(fsen.getDst().getType());
	}
      }   
    }
    
    HashSet dominatorset=computeAlways(l);

    /* Compute loop invariants */
    table.put(l, new HashSet<FlatNode>());
    while(changed) {
      changed=false;
      nextfn:
      for(Iterator tpit=toprocess.iterator();tpit.hasNext();) {
	FlatNode fn=(FlatNode)tpit.next();
	switch(fn.kind()) {
	case FKind.FlatOpNode:
	  int op=((FlatOpNode)fn).getOperation();
	  if (op==Operation.DIV||op==Operation.MID||
	      checkNode(fn,elements)) {
	    continue nextfn;
	  }
	  break;

	case FKind.FlatInstanceOfNode:
	  if (checkNode(fn,elements)) {
	    continue nextfn;
	  }
	  break;

	case FKind.FlatElementNode:
	  if (unsafe||!dominatorset.contains(fn)||
	      checkNode(fn,elements))
	    continue nextfn;
	  TypeDescriptor td=((FlatElementNode)fn).getSrc().getType();
	  for(Iterator<TypeDescriptor> tpit=types.iterator();tpit.hasNext();) {
	    TypeDescriptor td2=tpit.next();
	    if (typeutil.isSuperorType(td,td2)||
		typeutil.isSuperorType(td2,td)) {
	      continue nextfn;
	    }
	  }
	  break;

	case FKind.FlatFieldNode:
	  if (unsafe||!dominatorset.contains(fn)||
	      fields.contains(((FlatFieldNode)fn).getField())||
	      checkNode(fn,elements)) {
	    continue nextfn;
	  }
	  break;

	default:
	  continue nextfn;
	}
	//mark to hoist
	hoisted.add(fn);
	table.get(l).add(fn);
      }
    }
  }

  public HashSet computeAlways(Loops l) {
    /* Compute nodes that are always executed in loop */
    HashSet dominatorset=null;
    if (!unsafe) {
      /* Compute nodes that definitely get executed in a loop */
      Set entrances=l.loopEntraces();
      assert entrances.size()==1;
      FlatNode entrance=(FlatNode)entrances.iterator().next();
      boolean first=true;
      for (int i=0;i<entrances.numPrev();i++) {
	FlatNode incoming=entrence.getPrev(i);
	if (elements.contains(incoming)) {
	  HashSet domset=new HashSet();
	  domset.add(incoming);
	  FlatNode tmp=incoming;
	  while(tmp!=entrance) {
	    tmp=domtree.idom(tmp);
	    domset.add(tmp);
	  }
	}
	if (first) {
	  dominatorset=domset;
	  first=false;
	} else {
	  for(Iterator it=dominatorset.iterator();it.hasNext();) {
	    FlatNode fn=(FlatNode)it.next();
	    if (!domset.containsKey(fn))
	      it.remove();
	  }
	}
      }
    }
    return dominatorset;
  }

  public boolean checkNode(FlatNode fn, Set elements) {
    //Can hoist if all variables are loop invariant
    TempDescriptor[]uses=fn.readsTemps();
    for(int i=0;i<uses.length;i++) {
      TempDescriptor t=uses[i];
      Set<FlatNode> defset=usedef.defMap(fn, t);
      for(Iterator<FlatNode> defit=defset.iterator();defit.hasNext();) {
	FlatNode def=defit.next();
	if (elements.contains(def)&&defset.size()>1)
	  return true;
	if (elements.contains(def)&&!hoisted.contains(def))
	  return true;
      }
    }
    return false;
  }
}