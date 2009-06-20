package IR.Flat;
import java.util.Hashtable;
import java.util.Set;
import java.util.Iterator;
import IR.ClassDescriptor;
import IR.Operation;
import IR.State;
import IR.TypeUtil;
import IR.MethodDescriptor;

public class Inliner {
  public static boolean inline(FlatCall fc, TypeUtil typeutil, State state) {
    MethodDescriptor md=fc.getMethod();
    /* Do we need to do virtual dispatch? */
    if (md.isStatic()||md.getReturnType()==null||singleCall(typeutil, fc.getThis().getType().getClassDesc(),md)) {
      //just reuse temps...makes problem with inlining recursion
      TempMap clonemap=new TempMap();
      Hashtable<FlatNode, FlatNode> flatmap=new Hashtable<FlatNode, FlatNode>();
      TempDescriptor rettmp=fc.getReturnTemp();
      FlatNode aftercallnode=fc.getNext(0);
      aftercallnode.removePrev(fc);

      FlatMethod fm=state.getMethodFlat(md);
      //Clone nodes
      Set<FlatNode> nodeset=fm.getNodeSet();
      nodeset.remove(fm);

      //Build the clones
      for(Iterator<FlatNode> fnit=nodeset.iterator();fnit.hasNext();) {
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
	  flatmap.put(fn,clone);
	}
      }
      //Build the move chain
      FlatNode first=new FlatNop();;
      FlatNode last=first;
      {
	int i=0;
	if (fc.getThis()!=null) {
	  FlatOpNode fon=new FlatOpNode(fm.getParameter(i++), fc.getThis(), null, new Operation(Operation.ASSIGN));
	  last.addNext(fon);
	  last=fon;
	}
	for(int j=0;j<fc.numArgs();i++,j++) {
	  FlatOpNode fon=new FlatOpNode(fm.getParameter(i), fc.getArg(j), null, new Operation(Operation.ASSIGN));
	  last.addNext(fon);
	  last=fon;
	}
      }

      //Add the edges
      for(Iterator<FlatNode> fnit=nodeset.iterator();fnit.hasNext();) {
	FlatNode fn=fnit.next();
	FlatNode fnclone=flatmap.get(fn);

	if (fn.kind()!=FKind.FlatReturnNode) {
	  //don't build old edges out of a flat return node
	  for(int i=0;i<fn.numNext();i++) {
	    FlatNode fnnext=fn.getNext(i);
	    FlatNode fnnextclone=flatmap.get(fnnext);
	    fnclone.addNext(fnnextclone);
	  }
	} else {
	  fnclone.addNext(aftercallnode);
	}
      }

      //Add edges to beginning of move chain
      for(int i=0;i<fc.numPrev();i++) {
	FlatNode fnprev=fc.getPrev(i);
	for(int j=0;j<fnprev.numNext();j++) {
	  if (fnprev.getNext(j)==fc) {
	    fnprev.setNewNext(j, first);
	    break;
	  }
	}
      }

      //Add in the edge from move chain to callee
      last.addNext(flatmap.get(fm.getNext(0)));
      return true;
    } else return false;
  }

  private static boolean singleCall(TypeUtil typeutil, ClassDescriptor thiscd, MethodDescriptor md) {
    Set subclasses=typeutil.getSubClasses(thiscd);
    if (subclasses==null)
      return true;
    for(Iterator classit=subclasses.iterator(); classit.hasNext();) {
      ClassDescriptor cd=(ClassDescriptor)classit.next();
      Set possiblematches=cd.getMethodTable().getSet(md.getSymbol());
      for(Iterator matchit=possiblematches.iterator(); matchit.hasNext();) {
        MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
        if (md.matches(matchmd))
          return false;
      }
    }
    return true;
  }
}
