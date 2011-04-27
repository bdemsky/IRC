package IR.Flat;
import java.util.Hashtable;
import java.util.Set;
import java.util.HashSet;
import java.util.Stack;
import java.util.Iterator;
import IR.ClassDescriptor;
import IR.Operation;
import IR.State;
import IR.TypeUtil;
import IR.MethodDescriptor;

public class Inliner {
  public static void inlineAtomic(State state, TypeUtil typeutil, FlatMethod fm, int depth) {
    Stack<FlatNode> toprocess=new Stack<FlatNode>();
    HashSet<FlatNode> visited=new HashSet<FlatNode>();
    Hashtable<FlatNode, Integer> atomictable=new Hashtable<FlatNode, Integer>();
    HashSet<FlatNode> atomicset=new HashSet<FlatNode>();

    toprocess.push(fm);
    visited.add(fm);
    atomictable.put(fm, new Integer(0));
    while(!toprocess.isEmpty()) {
      FlatNode fn=toprocess.pop();
      int atomicval=atomictable.get(fn).intValue();
      if (fn.kind()==FKind.FlatAtomicEnterNode)
	atomicval++;
      else if(fn.kind()==FKind.FlatAtomicExitNode)
	atomicval--;
      for(int i=0; i<fn.numNext(); i++) {
	FlatNode fnext=fn.getNext(i);
	if (!visited.contains(fnext)) {
	  atomictable.put(fnext, new Integer(atomicval));
	  if (atomicval>0)
	    atomicset.add(fnext);
	  visited.add(fnext);
	  toprocess.push(fnext);
	}
      }
    }
    //make depth 0 be depth infinity
    if (depth==0)
      depth=10000000;
    if (atomicset.isEmpty())
      return;
    System.out.println("Inlining methods into "+fm.getMethod());
    recursive(state, typeutil, atomicset, depth, new Stack<MethodDescriptor>());
  }


  public static void recursive(State state, TypeUtil typeutil, Set<FlatNode> fnset, int depth, Stack<MethodDescriptor> toexclude) {
    for(Iterator<FlatNode> fnit=fnset.iterator(); fnit.hasNext(); ) {
      FlatNode fn=fnit.next();
      if (fn.kind()==FKind.FlatCall) {
	FlatCall fc=(FlatCall)fn;
	MethodDescriptor md=fc.getMethod();

	if (toexclude.contains(md))
	  continue;

	Set<FlatNode> inlinefnset=inline(fc, typeutil, state);
	if (inlinefnset==null)
	  continue;

	toexclude.push(md);
	if (depth>1)
	  recursive(state, typeutil, inlinefnset, depth-1, toexclude);
	toexclude.pop();
      }
    }
  }

  public static Set<FlatNode> inline(FlatCall fc, TypeUtil typeutil, State state) {
    MethodDescriptor md=fc.getMethod();
    if (md.getModifiers().isNative())
      return null;

    /* Do we need to do virtual dispatch? */
    if (md.isStatic()||md.getReturnType()==null||singleCall(typeutil, fc.getThis().getType().getClassDesc(),md)) {
      //just reuse temps...makes problem with inlining recursion
      TempMap clonemap=new TempMap();
      Hashtable<FlatNode, FlatNode> flatmap=new Hashtable<FlatNode, FlatNode>();
      TempDescriptor rettmp=fc.getReturnTemp();
      FlatNode aftercallnode=fc.getNext(0);
      aftercallnode.removePrev(fc);
      System.out.println("Inlining: "+md);

      FlatMethod fm=state.getMethodFlat(md);
      //Clone nodes
      Set<FlatNode> nodeset=fm.getNodeSet();
      nodeset.remove(fm);

      HashSet<FlatNode> newnodes=new HashSet<FlatNode>();

      //Build the clones
      for(Iterator<FlatNode> fnit=nodeset.iterator(); fnit.hasNext(); ) {
	FlatNode fn=fnit.next();
	if (fn.kind()==FKind.FlatReturnNode) {
	  //Convert FlatReturn node into move
	  TempDescriptor rtmp=((FlatReturnNode)fn).getReturnTemp();
	  if (rtmp!=null) {
	    FlatOpNode fon=new FlatOpNode(rettmp, rtmp, null, new Operation(Operation.ASSIGN));
	    flatmap.put(fn, fon);
	  } else {
	    flatmap.put(fn, aftercallnode);
	  }
	} else {
	  FlatNode clone=fn.clone(clonemap);
	  newnodes.add(clone);
	  flatmap.put(fn,clone);
	}
      }
      //Build the move chain
      FlatNode first=new FlatNop();;
      newnodes.add(first);
      FlatNode last=first;
      {
	int i=0;
	if (fc.getThis()!=null) {
	  FlatOpNode fon=new FlatOpNode(fm.getParameter(i++), fc.getThis(), null, new Operation(Operation.ASSIGN));
	  newnodes.add(fon);
	  last.addNext(fon);
	  last=fon;
	}
	for(int j=0; j<fc.numArgs(); i++,j++) {
	  FlatOpNode fon=new FlatOpNode(fm.getParameter(i), fc.getArg(j), null, new Operation(Operation.ASSIGN));
	  newnodes.add(fon);
	  last.addNext(fon);
	  last=fon;
	}
      }

      //Add the edges
      for(Iterator<FlatNode> fnit=nodeset.iterator(); fnit.hasNext(); ) {
	FlatNode fn=fnit.next();
	FlatNode fnclone=flatmap.get(fn);

	if (fn.kind()!=FKind.FlatReturnNode) {
	  //don't build old edges out of a flat return node
	  for(int i=0; i<fn.numNext(); i++) {
	    FlatNode fnnext=fn.getNext(i);
	    FlatNode fnnextclone=flatmap.get(fnnext);
	    fnclone.setNewNext(i, fnnextclone);
	  }
	} else {
	  if (fnclone!=aftercallnode)
	    fnclone.addNext(aftercallnode);
	}
      }

      //Add edges to beginning of move chain
      for(int i=0; i<fc.numPrev(); i++) {
	FlatNode fnprev=fc.getPrev(i);
	for(int j=0; j<fnprev.numNext(); j++) {
	  if (fnprev.getNext(j)==fc) {
	    //doing setnewnext to avoid changing the node we are
	    //iterating over
	    fnprev.setNewNext(j, first);
	    break;
	  }
	}
      }

      //Add in the edge from move chain to callee
      last.addNext(flatmap.get(fm.getNext(0)));
      return newnodes;
    } else return null;
  }

  private static boolean singleCall(TypeUtil typeutil, ClassDescriptor thiscd, MethodDescriptor md) {
    Set subclasses=typeutil.getSubClasses(thiscd);
    if (subclasses==null)
      return true;
    for(Iterator classit=subclasses.iterator(); classit.hasNext(); ) {
      ClassDescriptor cd=(ClassDescriptor)classit.next();
      Set possiblematches=cd.getMethodTable().getSet(md.getSymbol());
      for(Iterator matchit=possiblematches.iterator(); matchit.hasNext(); ) {
	MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
	if (md.matches(matchmd))
	  return false;
      }
    }
    return true;
  }
}
