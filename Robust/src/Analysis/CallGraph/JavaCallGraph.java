package Analysis.CallGraph;
import IR.State;
import IR.Flat.FlatMethod;
import IR.Flat.FlatNode;
import IR.Flat.FlatCall;
import IR.Flat.FKind;
import IR.Descriptor;
import IR.ClassDescriptor;
import IR.MethodDescriptor;
import IR.TaskDescriptor;
import IR.TypeDescriptor;
import IR.TypeUtil;
import java.util.*;
import java.io.*;

public class JavaCallGraph extends CallGraph {
  TypeUtil tu;
  HashSet discovered;

  public JavaCallGraph(State state, TypeUtil tu) {
    this.state=state;
    mapVirtual2ImplementationSet = new Hashtable();
    mapCaller2CalleeSet          = new Hashtable();
    mapCallee2CallerSet          = new Hashtable();
    discovered=new HashSet();
    this.tu=tu;
    buildVirtualMap();
    buildGraph();
  }

  public boolean isCallable(MethodDescriptor md) {
    return discovered.contains(md);
  }

  //Work our way down from main
  private void buildGraph() {
    MethodDescriptor main=tu.getMain();
    HashSet tovisit=new HashSet();
    tovisit.add(main);
    discovered.add(main);
    Iterator it=state.getClassSymbolTable().getDescriptorsIterator();

    while(it.hasNext()) {
      ClassDescriptor cn=(ClassDescriptor)it.next();
      Iterator methodit=cn.getMethods();
      //Iterator through methods
      while(methodit.hasNext()) {
	MethodDescriptor md=(MethodDescriptor)methodit.next();
	if (md.isStaticBlock()) {
	  tovisit.add(md);
	  discovered.add(md);
	}
      }
    }


    while(!tovisit.isEmpty()) {
      MethodDescriptor md=(MethodDescriptor)tovisit.iterator().next();
      tovisit.remove(md);
      FlatMethod fm=state.getMethodFlat(md);
      if (fm==null)
	continue;
      analyzeMethod(md, fm);
      for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator();fnit.hasNext();) {
	FlatNode fn=fnit.next();
	if (fn.kind()==FKind.FlatCall) {
	  FlatCall fcall=(FlatCall)fn;
	  Set callees=fcall.getThis()==null?getMethods(fcall.getMethod()):getMethods(fcall.getMethod(),fcall.getThis().getType());

	  if (fcall.getThis()!=null) {
	    MethodDescriptor methodd=fcall.getMethod();

	    if (methodd.getClassDesc()==tu.getClass(TypeUtil.ThreadClass)&&
		methodd.getSymbol().equals("start")&&methodd.numParameters()==0&&!methodd.getModifiers().isStatic()) {
	      //Have call to start
	      HashSet ns=new HashSet();
	      ns.addAll(callees);
	      ns.addAll(getMethods(tu.getRun(), fcall.getThis().getType()));
	      ns.addAll(getMethods(tu.getStaticStart(), fcall.getThis().getType()));
	      callees=ns;
	    }
	  }

	  for(Iterator mdit=callees.iterator();mdit.hasNext();) {
	    MethodDescriptor callee=(MethodDescriptor)mdit.next();
	    if (!discovered.contains(callee)) {
	      discovered.add(callee);
	      tovisit.add(callee);
	    }
	  }
	}
      }
    }
  }
}
