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
  public JavaCallGraph(State state, TypeUtil tu) {
    this.state=state;
    mapVirtual2ImplementationSet = new Hashtable();
    mapCaller2CalleeSet          = new Hashtable();
    mapCallee2CallerSet          = new Hashtable();
    this.tu=tu;
    buildVirtualMap();
    buildGraph();
  }

  //Work our way down from main
  private void buildGraph() {
    MethodDescriptor main=tu.getMain();
    HashSet tovisit=new HashSet();
    HashSet discovered=new HashSet();
    tovisit.add(main);
    discovered.add(main);
    while(!tovisit.isEmpty()) {
      MethodDescriptor md=(MethodDescriptor)tovisit.iterator().next();
      tovisit.remove(md);
      FlatMethod fm=state.getMethodFlat(main);
      analyzeMethod(md, fm);
      for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator();fnit.hasNext();) {
	FlatNode fn=fnit.next();
	if (fn.kind()==FKind.FlatCall) {
	  FlatCall fcall=(FlatCall)fn;
	  Set callees=getMethods(fcall.getMethod(),fcall.getThis().getType());
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
