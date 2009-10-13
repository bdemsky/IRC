package Analysis.MLP;

public class MemoryStall {
  JavaCallGraph callgraph;
  State state;
  TypeUtil typeutil;
  SESETree sesetree;

  public MemoryStall(State state, TypeUtil typeutil, JavaCallGraph callgraph, SESETree sesetree) {
    this.state=state;
    this.typeutil=typeutil;
    this.callgraph=callgraph;
    this.sesetree=sesetree;
  }

  private boolean isOnlyLeaf(MethodDescriptor md) {
    Set<SESENode> seseset=sesetree.getSESE(md);
    for(Iterator<SESENode> seseit=seseset.iterator();seseit.hasNext();) {
      SESENode sese=seseit.next();
      if (!sese.isLeaf())
	return false;
    }
    return true;
  }

  HashSet toanalyze=new HashSet();

  public void doAnalysis() {
    MethodDescriptor main=typeutil.getMain();
    toanalyze.addAll(callGraph.getAllMethods(main));

    while(!toanalyze.isEmpty()) {
      MethodDescriptor md=(MethodDescriptor)toanalyze.iterator().next();
      toanalyze.remove(md);
      if (isOnlyLeaf(md))
	continue;
      analyzeMethod(md);
    }
  }
  
  private void analyzeMethod(MethodDescriptor md) {
    FlatMethod fm=state.getMethodFlat(md);
    Hashtable<FlatNode, Stack<SESENode>> nodetosese=sesetree.analyzeMethod(md);
    HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
    tovisit.add(fm);
    Hashtable<FlatNode, Set<TempDescriptor>> dirtytemps=new Hashtable<FlatNode, Set<TempDescriptor>>();

    while(!tovisit.isEmpty()) {
      FlatNode fn=tovisit.iterator().next();
      tovisit.remove(fn);
      HashSet<TempDescriptor> newset=new HashSet<TempDescriptor>();
      for(int i=0;i<fn.numPrev();i++) {
	if (dirtytemps.containsKey(fn.getPrev(i)))
	  newset.addAll(dirtytemps.get(fn.getPrev(i)));
      }
      
      switch(fn.kind()) {
      case FKind.FlatSESEExitNode: {
	newset=new HashSet<TempDescriptor>();
	break;
      }
      case FKind.FlatElementNode: {
	FlatElementNode fen=(FlatElementNode) fn;
	newset.remove(fen.getSrc());
	newset.remove(fen.getDst());
	break;
      }
      case FKind.FlatFieldNode: {
	FlatFieldNode ffn=(FlatFieldNode) fn;
	newset.remove(ffn.getSrc());
	newset.remove(ffn.getDst());
	break;
      }
      case FKind.FlatSetFieldNode: {
	FlatSetFieldNode fsfn=(FlatSetFieldNode) fn;
	newset.remove(fsfn.getSrc());
	newset.remove(fsfn.getDst());
	break;
      }
      case FKind.FlatSetElementNode: {
	FlatSetElementNode fsen=(FlatSetElementNode) fn;
	newset.remove(fsen.getSrc());
	newset.remove(fsen.getDst());
	break;
      }
      case FKind.FlatLiteralNode: {
	FlatLiteralNode fln=(FlatLiteralNode) fn;
	newset.remove(fln.getDst());
	break;
      }
      case FKind.FlatMethodNode: {
	
	break;
      }
      case FKind.FlatOpNode: {
	FlatOpNode fon=(FlatOpNode)fn;
	if (fon.getOp().getOp()==Operation.ASSIGN) {
	  if (newset.contains(getLeft()))
	    newset.add(getDest());
	  else if (!newset.contains(getLeft()))
	    newset.remove(getDest());
	  break;
	}
      }
      case FKind.FlatCastNode: {
	FlatCastNode fcn=(FlatOpNode)fn;
	if (newset.contains(getSrc()))
	  newset.add(getDst());
	else if (!newset.contains(getSrc()))
	  newset.remove(getDst());
	break;
      }
      case FKind.FlatNew: {
	FlatNew fnew=(FlatNew) fn;
	newset.remove(fnew.getDst());
	break;
      }
      case FKind.FlatReturnNode: {
	FlatReturnNode frn=(FlatReturnNode) fn;
	
	break;
      }
      case FKind.FlatCall: {
	FlatCall fc=(FlatCall)fn;
	
	break;
      }
      }
      
    }

    
    
  }

  private MethodDescriptor getBase(MethodDescriptor md) {
    ClassDescriptor cd=md.getClassDesc();
    while (cd.getSuperDesc()!=null) {
      cd=cd.getSuperDesc();
      Set methodset=cd.getMethodTable().getSet(md.getSymbol());
      MethodDescriptor mdtemp=null;
      for(Iterator mdit=methodset.iterator();mdit.hasNext();) {
	MethodDescriptor mdsuper=(MethodDescriptor) mdit.next();
	if (mdsuper.matches(md)) {
	  mdtemp=mdsuper;
	  break;
	}
      }
      if (mdtemp!=null)
	md=mdtemp;
      else
	return md;
    }
    return md;
  }

  class MethodContext {
    boolean[] parameters;
    boolean dirtytemp;
  }
  
}