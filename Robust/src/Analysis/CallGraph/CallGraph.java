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

public class CallGraph {
  protected State state;

  // MethodDescriptor maps to HashSet<MethodDescriptor>
  protected Hashtable mapVirtual2ImplementationSet;

  // MethodDescriptor or TaskDescriptor maps to HashSet<MethodDescriptor>
  protected Hashtable mapCaller2CalleeSet;

  // MethodDescriptor maps to HashSet<MethodDescriptor or TaskDescriptor>
  protected Hashtable mapCallee2CallerSet;

  protected CallGraph() {}

  protected TypeUtil typeUtil;

  public CallGraph(State state, TypeUtil typeUtil) {
    this.state=state;
    this.typeUtil=typeUtil;

    mapVirtual2ImplementationSet = new Hashtable();
    mapCaller2CalleeSet          = new Hashtable();
    mapCallee2CallerSet          = new Hashtable();
    buildVirtualMap();
    buildGraph();
  }

  // this method returns the set of Descriptors
  // (MethodDescriptors and/or TaskDescriptors)
  //  that call the given method
  public Set getCallerSet(MethodDescriptor md) {
    Set s = (Set) mapCallee2CallerSet.get(md);
    
    if( s == null ) {
      return new HashSet();
    }
    return s;
  }

  // this method returns the set of MethodDescriptors that
  // are called by the given method or task
  public Set getCalleeSet(Descriptor d) {
    assert(d instanceof MethodDescriptor) ||
    (d instanceof TaskDescriptor);

    Set s = (Set) mapCaller2CalleeSet.get(d);

    if( s == null ) {
      return new HashSet();
    }
    return s;
  }

  // build a mapping of virtual methods to all
  // possible implementations of that method
  protected void buildVirtualMap() {
    //Iterator through classes
    Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
    while(it.hasNext()) {
      ClassDescriptor cn=(ClassDescriptor)it.next();
      Iterator methodit=cn.getMethods();
      //Iterator through methods
      while(methodit.hasNext()) {
	MethodDescriptor md=(MethodDescriptor)methodit.next();
	if (md.isStatic()||md.getReturnType()==null)
	  continue;
	Stack<ClassDescriptor> possInterfaces=new Stack<ClassDescriptor>();
	ClassDescriptor tmpcd=cn;
	while(tmpcd!=null) {
	  for(Iterator supit=tmpcd.getSuperInterfaces();supit.hasNext();) {
	    possInterfaces.add((ClassDescriptor)supit.next());
	  }
	  tmpcd=tmpcd.getSuperDesc();
	}
	while(!possInterfaces.isEmpty()) {
	  ClassDescriptor IFdesc=possInterfaces.pop();
	  for(Iterator supit=IFdesc.getSuperInterfaces();supit.hasNext();) {
	    possInterfaces.add((ClassDescriptor)supit.next());
	  }
	  Set possiblematches=IFdesc.getMethodTable().getSet(md.getSymbol());
	  boolean foundmatch=false;
	  for(Iterator matchit=possiblematches.iterator(); matchit.hasNext();) {
	    MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
	    if (md.matches(matchmd)) {
	      if (!mapVirtual2ImplementationSet.containsKey(matchmd))
		mapVirtual2ImplementationSet.put(matchmd,new HashSet());
	      ((HashSet)mapVirtual2ImplementationSet.get(matchmd)).add(md);
	      break;
	    }
	  }
	}
	

	ClassDescriptor superdesc=cn.getSuperDesc();
	if (superdesc!=null) {
	  Set possiblematches=superdesc.getMethodTable().getSet(md.getSymbol());
	  boolean foundmatch=false;
	  for(Iterator matchit=possiblematches.iterator(); matchit.hasNext();) {
	    MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
	    if (md.matches(matchmd)) {
	      if (!mapVirtual2ImplementationSet.containsKey(matchmd))
		mapVirtual2ImplementationSet.put(matchmd,new HashSet());
	      ((HashSet)mapVirtual2ImplementationSet.get(matchmd)).add(md);
	      break;
	    }
	  }
	}
      }
    }
  }

  public Set getMethods(MethodDescriptor md, TypeDescriptor type) {
    return getMethods(md);
  }

  /** Given a call to MethodDescriptor, lists the methods which
      could actually be called due to virtual dispatch. */
  public Set getMethods(MethodDescriptor md) {
    HashSet ns=new HashSet();
    ns.add(md);
    Set s=(Set)mapVirtual2ImplementationSet.get(md);
    if (s!=null)
      for(Iterator it=s.iterator(); it.hasNext();) {
	MethodDescriptor md2=(MethodDescriptor)it.next();
	ns.addAll(getMethods(md2));
      }
    return ns;
  }

  /** Given a call to MethodDescriptor, lists the methods which
      could actually be call by that method. */
  public Set getMethodCalls(TaskDescriptor td) {
    return getMethodCalls( (Descriptor) td);
  }

  public Set getMethodCalls(MethodDescriptor md) {
    return getMethodCalls( (Descriptor) md);
  }

  public Set getMethodCalls(Descriptor d) {
    assert d instanceof MethodDescriptor ||
    d instanceof TaskDescriptor;

    HashSet ns=new HashSet();
    ns.add(d);
    return getMoreMethodCalls(ns, d);
  }

  private Set getMoreMethodCalls(HashSet found, Descriptor d) {
    HashSet ns=new HashSet();
    ns.add(d);
    found.add(d);
    Set s=(Set)mapCaller2CalleeSet.get(d);
    if (s!=null)
      for(Iterator it=s.iterator(); it.hasNext();) {
	MethodDescriptor md=(MethodDescriptor)it.next();
	if( !found.contains(md) ) {
	  ns.addAll(getMoreMethodCalls(found, md));
	}
      }
    return ns;
  }

  public boolean isCallable(MethodDescriptor md) {
    return true;
  }

  /** Returns all methods transitively callable from d */

  public Set getAllMethods(Descriptor d) {
    HashSet tovisit=new HashSet();
    tovisit.add(d);
    HashSet callable=new HashSet();
    while(!tovisit.isEmpty()) {
      Descriptor md=(Descriptor)tovisit.iterator().next();
      tovisit.remove(md);
      Set s=(Set)mapCaller2CalleeSet.get(md);

      if (s!=null) {
	for(Iterator it=s.iterator(); it.hasNext();) {
	  MethodDescriptor md2=(MethodDescriptor)it.next();
	  if( !callable.contains(md2) ) {
	    callable.add(md2);
	    tovisit.add(md2);
	  }
	}
      }
    }
    return callable;
  }
  
  // Returns a set of methods containing SESEs and located at the first   
  // in transitive call chain starting from d 
  public Set getFirstReachableMethodContainingSESE(Descriptor d,
      Set<MethodDescriptor> methodsContainingSESEs) {
    HashSet tovisit = new HashSet();
    tovisit.add(d);
    HashSet callable = new HashSet();
    while (!tovisit.isEmpty()) {
      Descriptor md = (Descriptor) tovisit.iterator().next();
      tovisit.remove(md);
      Set s = (Set) mapCaller2CalleeSet.get(md);

      if (s != null) {
        for (Iterator it = s.iterator(); it.hasNext();) {
          MethodDescriptor md2 = (MethodDescriptor) it.next();
          if (!callable.contains(md2)) {
            callable.add(md2);
            if (!methodsContainingSESEs.contains(md2)) {
              // if current method has sese, do not need to go down
              tovisit.add(md2);
            }
          }
        }
      }
    }
//    callable.retainAll(methodsContainingSESEs);
    return callable;
  }
  

  private void buildGraph() {
    Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
    while(it.hasNext()) {
      ClassDescriptor cn=(ClassDescriptor)it.next();
      Iterator methodit=cn.getMethods();
      //Iterator through methods
      while(methodit.hasNext()) {
	MethodDescriptor md=(MethodDescriptor)methodit.next();
	analyzeMethod( (Object)md, state.getMethodFlat(md) );
      }
    }
    it=state.getTaskSymbolTable().getDescriptorsIterator();
    while(it.hasNext()) {
      TaskDescriptor td=(TaskDescriptor)it.next();
      analyzeMethod( (Object)td, state.getMethodFlat(td) );
    }
  }

  protected void analyzeMethod(Object caller, FlatMethod fm) {
    HashSet toexplore=new HashSet();
    toexplore.add(fm);
    HashSet explored=new HashSet();
    //look at all the nodes in the flat representation
    while(!toexplore.isEmpty()) {
      FlatNode fn=(FlatNode)(toexplore.iterator()).next();
      toexplore.remove(fn);
      explored.add(fn);
      for(int i=0; i<fn.numNext(); i++) {
	FlatNode fnnext=fn.getNext(i);
	if (!explored.contains(fnnext))
	  toexplore.add(fnnext);
      }
      if (fn.kind()==FKind.FlatCall) {
	FlatCall fc=(FlatCall)fn;
	MethodDescriptor calledmethod=fc.getMethod();
	Set methodsthatcouldbecalled=fc.getThis()==null ? getMethods(calledmethod) :
	                              getMethods(calledmethod, fc.getThis().getType());
	
	// add caller -> callee maps
	if( !mapCaller2CalleeSet.containsKey(caller) ) {
	  mapCaller2CalleeSet.put(caller, new HashSet() );
	}
	((HashSet)mapCaller2CalleeSet.get(caller)).addAll(methodsthatcouldbecalled);

	// add callee -> caller maps
	Iterator calleeItr = methodsthatcouldbecalled.iterator();
	while( calleeItr.hasNext() ) {
	  MethodDescriptor callee = (MethodDescriptor) calleeItr.next();
	  if( !mapCallee2CallerSet.containsKey(callee) ) {
	    mapCallee2CallerSet.put(callee, new HashSet() );
	  }
	  ((HashSet)mapCallee2CallerSet.get(callee)).add(caller);
	}
      }
    }
  }


  public void writeVirtual2ImplemToDot(String graphName)  throws java.io.IOException {
    HashSet labeledInDot = new HashSet();

    BufferedWriter bw = new BufferedWriter(new FileWriter(graphName+".dot") );
    bw.write("digraph "+graphName+" {\n");
    Iterator mapItr =  mapVirtual2ImplementationSet.entrySet().iterator();
    while( mapItr.hasNext() ) {
      Map.Entry me        = (Map.Entry)mapItr.next();
      MethodDescriptor virtual   = (MethodDescriptor) me.getKey();
      HashSet implemSet = (HashSet)          me.getValue();

      if( !labeledInDot.contains(virtual) ) {
	labeledInDot.add(virtual);
	bw.write("  "+virtual.getNum()+"[label=\""+virtual+"\"];\n");
      }

      Iterator implemItr = implemSet.iterator();
      while( implemItr.hasNext() ) {
	Descriptor implem = (Descriptor) implemItr.next();

	if( !labeledInDot.contains(implem) ) {
	  labeledInDot.add(implem);
	  bw.write("  "+implem.getNum()+"[label=\""+implem+"\"];\n");
	}

	bw.write("  "+virtual.getNum()+"->"+implem.getNum()+";\n");
      }
    }
    bw.write("}\n");
    bw.close();
  }


  public void writeCaller2CalleesToDot(String graphName)  throws java.io.IOException {
    // write out the call graph (should be equivalent) by
    // using the callers mapping
    HashSet labeledInDot = new HashSet();
    BufferedWriter bw = new BufferedWriter(new FileWriter(graphName+"byCallers.dot") );
    bw.write("digraph "+graphName+"byCallers {\n");
    Iterator mapItr = mapCaller2CalleeSet.entrySet().iterator();
    while( mapItr.hasNext() ) {
      Map.Entry me      = (Map.Entry)mapItr.next();
      Descriptor caller = (Descriptor) me.getKey();
      HashSet calleeSet = (HashSet)    me.getValue();

      if( !labeledInDot.contains(caller) ) {
	labeledInDot.add(caller);
	bw.write("  "+caller.getNum()+"[label=\"" +caller+"\"];\n");
      }

      Iterator calleeItr = calleeSet.iterator();
      while( calleeItr.hasNext() ) {
	MethodDescriptor callee = (MethodDescriptor) calleeItr.next();

	if( !labeledInDot.contains(callee) ) {
	  labeledInDot.add(callee);
	  bw.write("  "+callee.getNum()+"[label=\""+callee+"\"];\n");
	}

	bw.write("  "+caller.getNum()+"->"+callee.getNum()+";\n");
      }
    }
    bw.write("}\n");
    bw.close();
  }


  public void writeCallee2CallersToDot(String graphName)  throws java.io.IOException {
    // each task or method only needs to be labeled once
    // in a dot file
    HashSet labeledInDot = new HashSet();

    // write out the call graph using the callees mapping
    BufferedWriter bw = new BufferedWriter(new FileWriter(graphName+"byCallees.dot") );
    bw.write("digraph "+graphName+"byCallees {\n");
    Iterator mapItr = mapCallee2CallerSet.entrySet().iterator();
    while( mapItr.hasNext() ) {
      Map.Entry me        = (Map.Entry)mapItr.next();
      MethodDescriptor callee    = (MethodDescriptor) me.getKey();
      HashSet callerSet = (HashSet)          me.getValue();

      if( !labeledInDot.contains(callee) ) {
	labeledInDot.add(callee);
	bw.write("  "+callee.getNum()+"[label=\""+callee+"\"];\n");
      }

      Iterator callerItr = callerSet.iterator();
      while( callerItr.hasNext() ) {
	Descriptor caller = (Descriptor) callerItr.next();

	if( !labeledInDot.contains(caller) ) {
	  labeledInDot.add(caller);
	  bw.write("  "+caller.getNum()+"[label=\""+caller+"\"];\n");
	}

	bw.write("  "+caller.getNum()+"->"+callee.getNum()+";\n");
      }
    }
    bw.write("}\n");
    bw.close();
  }
}
