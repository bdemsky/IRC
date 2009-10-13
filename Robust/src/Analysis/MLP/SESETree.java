package Analysis.MLP;
import IR.*;
import IR.Flat.*;
import java.util.*;
import Analysis.CallGraph.*;

public class SESETree {
  State state;
  TypeUtil typeutil;
  CallGraph callgraph;
  SESENode root;
  Hashtable<MethodDescriptor, Set<SESENode>> toanalyze=new Hashtable<MethodDescriptor,Set<SESENode>>();
  Hashtable<MethodDescriptor, Set<SESENode>> discovered=new Hashtable<MethodDescriptor,Set<SESENode>>();
  Hashtable<FlatSESEEnterNode, SESENode> sesemap=new Hashtable<FlatSESEEnterNode, SESENode>();

  public SESETree(State state, TypeUtil typeutil, CallGraph callgraph) {
    this.state=state;
    this.typeutil=typeutil;
    this.callgraph=callgraph;
    root=new SESENode(null, true);
    doAnalysis();
  }

  public SESENode getRoot() {
    return root;
  }

  public void doAnalysis() {
    MethodDescriptor main=typeutil.getMain();
    add(toanalyze, main, root);
    add(discovered, main, root);
    
    while(!toanalyze.isEmpty()) {
      MethodDescriptor md=toanalyze.keySet().iterator().next();
      Set<SESENode> context=toanalyze.get(md);
      toanalyze.remove(md);
      FlatMethod fm=state.getMethodFlat(md);
      analyzeMethod(fm, context);
    }
  }

  public SESENode getSESE(FlatSESEEnterNode enter) {
    if (!sesemap.containsKey(enter)) {
      sesemap.put(enter, new SESENode(enter, false));
    }
    return sesemap.get(enter);
  }

  public Set<SESENode> getSESE(MethodDescriptor md) {
    return discovered.get(md);
  }

  public Hashtable<FlatNode, Stack<SESENode>> analyzeMethod(FlatMethod fm) {
    return analyzeMethod(fm, null);
  }

  private Hashtable<FlatNode, Stack<SESENode>> analyzeMethod(FlatMethod fm, Set<SESENode> context) {
    Hashtable<FlatNode, Stack<SESENode>> stacks=new Hashtable<FlatNode, Stack<SESENode>> ();
    stacks.put(fm, new Stack<SESENode>());
    HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
    HashSet<FlatNode> fndiscovered=new HashSet<FlatNode>();
    tovisit.add(fm);
    fndiscovered.add(fm);
    while(!tovisit.isEmpty()) {
      FlatNode fn=tovisit.iterator().next();
      tovisit.remove(fn);
      Stack<SESENode> instack=stacks.get(fm);
      switch(fn.kind()) {
      case FKind.FlatCall: {
	if (context==null)
	  break;
	FlatCall fc=(FlatCall)fn;
	//handle method call
	Set<SESENode> parents;
	if (instack.isEmpty()) {
	  parents=context;
	} else {
	  parents=new HashSet<SESENode>();
	  parents.add(instack.peek());
	}
	for(Iterator<SESENode> parentit=parents.iterator();parentit.hasNext();) {
	  SESENode parentsese=parentit.next();
	  for(Iterator<MethodDescriptor> calleeit=(fc.getThis()==null?callgraph.getMethods(fc.getMethod()):callgraph.getMethods(fc.getMethod(), fc.getThis().getType())).iterator(); calleeit.hasNext();) {
	    MethodDescriptor md=calleeit.next();
	    if (add(discovered,md, parentsese)) {
	      add(toanalyze, md, parentsese);
	    }
	  }
	}
	break;
      }
      case FKind.FlatSESEEnterNode: {
	FlatSESEEnterNode enter=(FlatSESEEnterNode)fn;

	if (context!=null) {
	  Set<SESENode> parents;
	  if (instack.isEmpty()) {
	    parents=context;
	  } else {
	    parents=new HashSet<SESENode>();
	    parents.add(instack.peek());
	  }
	  SESENode sese=getSESE(enter);
	  for(Iterator<SESENode> parentit=parents.iterator();parentit.hasNext();) {
	    SESENode parentsese=parentit.next();
	    parentsese.addChild(sese);
	  }
	}
	Stack<SESENode> copy=(Stack<SESENode>)instack.clone();
	copy.push(sese);
	instack=copy;
	break;
      }
      case FKind.FlatSESEExitNode: {
	FlatSESEExitNode exit=(FlatSESEExitNode)fn;
	Stack<SESENode> copy=(Stack<SESENode>)instack.clone();
	copy.pop();
	instack=copy;
	break;
      }
      }
      for(int i=0;i<fn.numNext();i++) {
	FlatNode fnext=fn.getNext(i);
	if (!fndiscovered.contains(fnext)) {
	  fndiscovered.add(fnext);
	  tovisit.add(fnext);
	  stacks.put(fnext, instack);
	}
      }
    }
    return stacks;
  }

  public static boolean add(Hashtable<MethodDescriptor, Set<SESENode>> discovered, MethodDescriptor md, SESENode sese) {
    if (!discovered.containsKey(md))
      discovered.put(md, new HashSet<SESENode>());
    if (discovered.get(md).contains(sese))
      return false;
    discovered.get(md).add(sese);
    return true;
  }


  class SESENode {
    boolean isRoot;
    HashSet<SESENode> children;
    HashSet<SESENode> parents;

    FlatSESEEnterNode node;
    SESENode(FlatSESEEnterNode node, boolean isRoot) {
      children=new HashSet<SESENode>();
      this.isRoot=isRoot;
      this.node=node;
    }

    public boolean isLeaf() {
      return children.isEmpty();
    }

    protected void addChild(SESENode child) {
      children.add(child);
      child.parents.add(this);
    }
    
    public Set<SESENode> getParents() {
      return parents;
    }

    public Set<SESENode> getChildren() {
      return children;
    }
  }
}