package Analysis.Loops;
import IR.Flat.*;
import IR.Operation;
import java.util.HashSet;
import java.util.Iterator;

public class DeadCode {
  public DeadCode() {
  }
  public void optimize(FlatMethod fm) {
    UseDef ud=new UseDef(fm);
    HashSet useful=new HashSet();
    boolean changed=true;
    while(changed) {
      changed=false;
      nextfn:
      for(Iterator<FlatNode> it=fm.getNodeSet().iterator();it.hasNext();) {
	FlatNode fn=it.next();
	switch(fn.kind()) {
	case FKind.FlatCall:
	case FKind.FlatFieldNode:
	case FKind.FlatSetFieldNode:
	case FKind.FlatNew:
	case FKind.FlatCastNode:
	case FKind.FlatReturnNode:
	case FKind.FlatCondBranch:
	case FKind.FlatSetElementNode:
	case FKind.FlatElementNode:
	case FKind.FlatFlagActionNode:
	case FKind.FlatCheckNode:
	case FKind.FlatBackEdge:
	case FKind.FlatExit:
	case FKind.FlatTagDeclaration:
	case FKind.FlatMethod:
	case FKind.FlatAtomicEnterNode:
	case FKind.FlatAtomicExitNode:
	case FKind.FlatPrefetchNode:
	case FKind.FlatSESEEnterNode:
	case FKind.FlatSESEExitNode:
	  if (!useful.contains(fn)) {
	    useful.add(fn);
	    changed=true;
	  }	  
	  break;
	case FKind.FlatOpNode:
	  FlatOpNode fon=(FlatOpNode)fn;
	  if (fon.getOp().getOp()==Operation.DIV||
	      fon.getOp().getOp()==Operation.MOD) {
	    if (!useful.contains(fn)) {
	      useful.add(fn);
	      changed=true;
	    }
	    break;
	  }
	default:
	  TempDescriptor[] writes=fn.writesTemps();
	  if (!useful.contains(fn))
	    for(int i=0;i<writes.length;i++) {
	      for(Iterator<FlatNode> uit=ud.useMap(fn,writes[i]).iterator();uit.hasNext();) {
		FlatNode ufn=uit.next();
		if (useful.contains(ufn)) {
		  //we are useful
		  useful.add(fn);
		  changed=true;
		  continue nextfn;
		}
	      }
	    }
	}
      }
    }
    //get rid of useless nodes
    for(Iterator<FlatNode> it=fm.getNodeSet().iterator();it.hasNext();) {
      FlatNode fn=it.next();
      if (!useful.contains(fn)) {
	//We have a useless node
	FlatNode fnnext=fn.getNext(0);

	for(int i=0;i<fn.numPrev();i++) {
	  FlatNode nprev=fn.getPrev(i);
	      
	  for(int j=0;j<nprev.numNext();j++) {
	    if (nprev.getNext(j)==fn) {
	      nprev.setnext(j, fnnext);
	      fnnext.addPrev(nprev);
	    }
	  }
	}
	//fix up prev edge of fnnext
	fnnext.removePrev(fn);
      }
    }
  }
}