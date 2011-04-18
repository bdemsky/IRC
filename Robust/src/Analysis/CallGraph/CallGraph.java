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


public interface CallGraph {
  public Set getAllMethods(Descriptor d);
  public Set getMethods(MethodDescriptor md, TypeDescriptor type);
  public Set getMethods(MethodDescriptor md);
  public Set getCallerSet(MethodDescriptor md);
  public Set getCalleeSet(Descriptor d);
  public boolean isCallable(MethodDescriptor md);
  public Set getMethodCalls(Descriptor d);
  public Set getFirstReachableMethodContainingSESE(Descriptor d, Set<MethodDescriptor> methodsContainingSESEs);
}