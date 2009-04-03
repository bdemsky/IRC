package Analysis.Loops;

import IR.Flat.*;
import IR.FieldDescriptor;
import IR.TypeDescriptor;
import IR.TypeUtil;
import IR.Operation;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.Hashtable;

public class LoopInvariant {
  public LoopInvariant(TypeUtil typeutil) {
    this.typeutil=typeutil;
  }
  LoopFinder loops;
  DomTree posttree;
  Hashtable<FlatNode, Vector<FlatNode>> table;
  Set<FlatNode> hoisted;
  UseDef usedef;
  TypeUtil typeutil;
  Set tounroll;
  Loops root;

  public void analyze(FlatMethod fm) {
    loops=new LoopFinder(fm);
    root=loops.getRootloop(fm);
    table=new Hashtable<FlatNode, Vector<FlatNode>>();
    hoisted=new HashSet<FlatNode>();
    usedef=new UseDef(fm);
    posttree=new DomTree(fm,true);
    tounroll=new HashSet();
    recurse(root);
  }

  public void recurse(Loops parent) {
    for(Iterator lpit=parent.nestedLoops().iterator();lpit.hasNext();) {
      Loops child=(Loops)lpit.next();
      processLoop(child, child.nestedLoops().size()==0);
      recurse(child);
    }
  }

  public void processLoop(Loops l, boolean isLeaf) {
    boolean changed=true;
    boolean unsafe=false;
    Set elements=l.loopIncElements();
    Set toprocess=l.loopIncElements();
    toprocess.removeAll(hoisted);
    Set entrances=l.loopEntrances();
    assert entrances.size()==1;
    FlatNode entrance=(FlatNode)entrances.iterator().next();

    HashSet<FieldDescriptor> fields=new HashSet<FieldDescriptor>();
    HashSet<TypeDescriptor> types=new HashSet<TypeDescriptor>();
    
    if (!isLeaf) {
      unsafe=true; 
    } else {
      /* Check whether it is safe to reuse values. */
      for(Iterator elit=elements.iterator();elit.hasNext();) {
	FlatNode fn=(FlatNode)elit.next();
	if (fn.kind()==FKind.FlatAtomicEnterNode||
	    fn.kind()==FKind.FlatAtomicExitNode||
	    fn.kind()==FKind.FlatCall) {
	  unsafe=true;
	  break;
	} else if (fn.kind()==FKind.FlatSetFieldNode) {
	  FlatSetFieldNode fsfn=(FlatSetFieldNode)fn;
	  fields.add(fsfn.getField());
	} else if (fn.kind()==FKind.FlatSetElementNode) {
	  FlatSetElementNode fsen=(FlatSetElementNode)fn;
	  types.add(fsen.getDst().getType());
	}
      }   
    }
    
    HashSet dominatorset=unsafe?null:computeAlways(l);

    /* Compute loop invariants */
    table.put(entrance, new Vector<FlatNode>());
    while(changed) {
      changed=false;
      nextfn:
      for(Iterator tpit=toprocess.iterator();tpit.hasNext();) {
	FlatNode fn=(FlatNode)tpit.next();
	switch(fn.kind()) {
	case FKind.FlatOpNode:
	  int op=((FlatOpNode)fn).getOp().getOp();
	  if (op==Operation.DIV||op==Operation.MOD||
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
	  if (unsafe||dominatorset==null||
	      !dominatorset.contains(fn)||
	      checkNode(fn,elements))
	    continue nextfn;
	  TypeDescriptor td=((FlatElementNode)fn).getSrc().getType();
	  for(Iterator<TypeDescriptor> tdit=types.iterator();tdit.hasNext();) {
	    TypeDescriptor td2=tdit.next();
	    if (typeutil.isSuperorType(td,td2)||
		typeutil.isSuperorType(td2,td)) {
	      continue nextfn;
	    }
	  }
	  if (isLeaf)
	    tounroll.add(l);
	  break;

	case FKind.FlatFieldNode:
	  if (unsafe||dominatorset==null||
	      !dominatorset.contains(fn)||
	      fields.contains(((FlatFieldNode)fn).getField())||
	      checkNode(fn,elements)) {
	    continue nextfn;
	  }
	  if (isLeaf)
	    tounroll.add(l);
	  break;

	default:
	  continue nextfn;
	}
	//mark to hoist
	hoisted.add(fn);
	table.get(entrance).add(fn);
      }
    }
  }

  public HashSet computeAlways(Loops l) {
    /* Compute nodes that are always executed in loop */
    HashSet dominatorset=null;
    /* Compute nodes that definitely get executed in a loop */
    Set elements=l.loopIncElements();
    Set entrances=l.loopEntrances();
    assert entrances.size()==1;
    FlatNode entrance=(FlatNode)entrances.iterator().next();
    boolean first=true;
    for (int i=0;i<entrance.numPrev();i++) {
      FlatNode incoming=entrance.getPrev(i);
      if (elements.contains(incoming)) {
	HashSet domset=new HashSet();
	domset.add(incoming);
	FlatNode tmp=incoming;
	while(tmp!=entrance) {
	  tmp=posttree.idom(tmp);
	  domset.add(tmp);
	}
	if (first) {
	  dominatorset=domset;
	  first=false;
	} else {
	  for(Iterator it=dominatorset.iterator();it.hasNext();) {
	    FlatNode fn=(FlatNode)it.next();
	    if (!domset.contains(fn))
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