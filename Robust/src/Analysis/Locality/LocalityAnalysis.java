package Analysis.Locality;

import Analysis.Liveness;
import java.util.*;
import Analysis.CallGraph.CallGraph;
import IR.SymbolTable;
import IR.State;
import IR.TypeUtil;
import IR.MethodDescriptor;
import IR.Flat.*;
import Analysis.Liveness;
import IR.ClassDescriptor;

public class LocalityAnalysis {
  State state;
  Set lbtovisit;
  Hashtable<LocalityBinding,LocalityBinding> discovered;
  Hashtable<LocalityBinding, Set<LocalityBinding>> dependence;
  Hashtable<LocalityBinding, Set<LocalityBinding>> calldep;
  Hashtable<LocalityBinding, Hashtable<FlatNode,Hashtable<TempDescriptor, Integer>>> temptab;
  Hashtable<LocalityBinding, Hashtable<FlatNode, Integer>> atomictab;
  Hashtable<LocalityBinding, Hashtable<FlatAtomicEnterNode, Set<TempDescriptor>>> tempstosave;
  Hashtable<ClassDescriptor, Set<LocalityBinding>> classtolb;
  Hashtable<MethodDescriptor, Set<LocalityBinding>> methodtolb;
  private LocalityBinding lbmain;
  private LocalityBinding lbrun;
  private LocalityBinding lbexecute;

  CallGraph callgraph;
  TypeUtil typeutil;
  public static final Integer LOCAL=new Integer(0);
  public static final Integer GLOBAL=new Integer(1);
  public static final Integer EITHER=new Integer(2);
  public static final Integer CONFLICT=new Integer(3);

  public static final Integer STMEITHER=new Integer(0);
  public static final Integer SCRATCH=new Integer(4);
  public static final Integer NORMAL=new Integer(8);
  public static final Integer STMCONFLICT=new Integer(12);

  public LocalityAnalysis(State state, CallGraph callgraph, TypeUtil typeutil) {
    this.typeutil=typeutil;
    this.state=state;
    this.discovered=new Hashtable<LocalityBinding,LocalityBinding>();
    this.dependence=new Hashtable<LocalityBinding, Set<LocalityBinding>>();
    this.calldep=new Hashtable<LocalityBinding, Set<LocalityBinding>>();
    this.temptab=new Hashtable<LocalityBinding, Hashtable<FlatNode,Hashtable<TempDescriptor, Integer>>>();
    this.atomictab=new Hashtable<LocalityBinding, Hashtable<FlatNode, Integer>>();
    this.lbtovisit=new HashSet();
    this.callgraph=callgraph;
    this.tempstosave=new Hashtable<LocalityBinding, Hashtable<FlatAtomicEnterNode, Set<TempDescriptor>>>();
    this.classtolb=new Hashtable<ClassDescriptor, Set<LocalityBinding>>();
    this.methodtolb=new Hashtable<MethodDescriptor, Set<LocalityBinding>>();
    doAnalysis();
  }

  public LocalityBinding getMain() {
    return lbmain;
  }

  /** This method returns the set of LocalityBindings that a given
   * flatcall could invoke */

  public LocalityBinding getBinding(LocalityBinding currlb, FlatCall fc) {
    boolean isatomic=getAtomic(currlb).get(fc).intValue()>0;
    Hashtable<TempDescriptor, Integer> currtable=getNodePreTempInfo(currlb,fc);
    MethodDescriptor md=fc.getMethod();

    boolean isnative=md.getModifiers().isNative();

    LocalityBinding lb=new LocalityBinding(md, isatomic);

    for(int i=0; i<fc.numArgs(); i++) {
      TempDescriptor arg=fc.getArg(i);
      lb.setGlobal(i,currtable.get(arg));
    }

    if (state.DSM&&fc.getThis()!=null) {
      Integer thistype=currtable.get(fc.getThis());
      if (thistype==null)
	thistype=EITHER;
      lb.setGlobalThis(thistype);
    } else if (state.SINGLETM&&fc.getThis()!=null) {
      Integer thistype=currtable.get(fc.getThis());
      if (thistype==null)
	thistype=STMEITHER;
      lb.setGlobalThis(thistype);
    }
    // else
         // lb.setGlobalThis(EITHER);//default value
    if (discovered.containsKey(lb))
      lb=discovered.get(lb);
    else throw new Error();
    return lb;
  }


  /** This method returns a set of LocalityBindings for the parameter class. */
  public Set<LocalityBinding> getClassBindings(ClassDescriptor cd) {
    return classtolb.get(cd);
  }

  /** This method returns a set of LocalityBindings for the parameter method. */

  public Set<LocalityBinding> getMethodBindings(MethodDescriptor md) {
    return methodtolb.get(md);
  }

  public Set<MethodDescriptor> getMethods() {
    return methodtolb.keySet();
  }

  /** This method returns a set of LocalityBindings.  A
   * LocalityBinding specifies a context a method can be invoked in.
   * It specifies whether the method is in a transaction and whether
   * its parameter objects are locals or globals.  */

  public Set<LocalityBinding> getLocalityBindings() {
    return discovered.keySet();
  }

  /** This method returns a hashtable for a given LocalityBinding
   * that tells the current local/global status of temps at the each
   * node in the flat representation. */

  public Hashtable<FlatNode, Hashtable<TempDescriptor, Integer>> getNodeTempInfo(LocalityBinding lb) {
    return temptab.get(lb);
  }

  /** This method returns a hashtable for a given LocalityBinding
   * that tells the current local/global status of temps at the
   * beginning of each node in the flat representation. */

  public Hashtable<TempDescriptor, Integer> getNodePreTempInfo(LocalityBinding lb, FlatNode fn) {
    Hashtable<TempDescriptor, Integer> currtable=new Hashtable<TempDescriptor, Integer>();
    Hashtable<FlatNode, Hashtable<TempDescriptor, Integer>> temptable=getNodeTempInfo(lb);

    for(int i=0; i<fn.numPrev(); i++) {
      FlatNode prevnode=fn.getPrev(i);
      Hashtable<TempDescriptor, Integer> prevtable=temptable.get(prevnode);
      for(Iterator<TempDescriptor> tempit=prevtable.keySet().iterator(); tempit.hasNext();) {
	TempDescriptor temp=tempit.next();
	Integer tmpint=prevtable.get(temp);
	Integer oldint=currtable.containsKey(temp) ? currtable.get(temp) : (state.DSM?EITHER:STMEITHER);
	Integer newint=state.DSM?merge(tmpint, oldint):mergestm(tmpint, oldint);
	currtable.put(temp, newint);
      }
    }
    return currtable;
  }

  /** This method returns an hashtable for a given LocalitBinding
   * that tells whether a node in the flat represenation is in a
   * transaction or not.  Integer values greater than 0 indicate
   * that the node is in a transaction and give the nesting depth.
   * The outermost AtomicEnterNode will have a value of 1 and the
   * outermost AtomicExitNode will have a value of 0. */

  public Hashtable<FlatNode, Integer> getAtomic(LocalityBinding lb) {
    return atomictab.get(lb);
  }

  /** This methods returns a hashtable for a given LocalityBinding
   * that tells which temps needs to be saved for each
   * AtomicEnterNode.  */

  public Hashtable<FlatAtomicEnterNode, Set<TempDescriptor>> getTemps(LocalityBinding lb) {
    return tempstosave.get(lb);
  }

  public Set<TempDescriptor> getTempSet(LocalityBinding lb) {
    HashSet<TempDescriptor> set=new HashSet<TempDescriptor>();
    Hashtable<FlatAtomicEnterNode, Set<TempDescriptor>> table=getTemps(lb);
    if (table!=null)
      for(Iterator<FlatAtomicEnterNode> faenit=table.keySet().iterator(); faenit.hasNext();) {
	FlatAtomicEnterNode faen=faenit.next();
	set.addAll(table.get(faen));
      }
    return set;
  }

  private void doAnalysis() {
    if (state.SINGLETM)
      computeLocalityBindingsSTM();
    else
      computeLocalityBindings();
    computeTempstoSave();
    cleanSets();
  }

  private void cleanSets() {
    HashSet<LocalityBinding> lbset=new HashSet<LocalityBinding>();
    Stack<LocalityBinding> lbstack=new Stack<LocalityBinding>();
    lbstack.add(lbmain);
    lbstack.add(lbrun);

    lbset.add(lbmain);
    lbset.add(lbrun);

    if(state.DSMTASK) {       // when Task.java is used
      lbstack.add(lbexecute);
      lbset.add(lbexecute);
    }
    while(!lbstack.isEmpty()) {
      LocalityBinding lb=lbstack.pop();
      if (calldep.containsKey(lb)) {
	Set<LocalityBinding> set=new HashSet<LocalityBinding>();
	set.addAll(calldep.get(lb));
	set.removeAll(lbset);
	lbstack.addAll(set);
	lbset.addAll(set);
      }
    }
    for(Iterator<LocalityBinding> lbit=discovered.keySet().iterator(); lbit.hasNext();) {
      LocalityBinding lb=lbit.next();
      if (!lbset.contains(lb)) {
	lbit.remove();
	classtolb.get(lb.getMethod().getClassDesc()).remove(lb);
	methodtolb.get(lb.getMethod()).remove(lb);
      }
    }
  }

  public MethodDescriptor getStart() {
    ClassDescriptor cd=typeutil.getClass(TypeUtil.ThreadClass);
    for(Iterator methodit=cd.getMethodTable().getSet("staticStart").iterator(); methodit
.hasNext();) {
      MethodDescriptor md=(MethodDescriptor) methodit.next();
      if (md.numParameters()!=1||!md.getModifiers().isStatic()||!md.getParamType(0).getSymbol().equals(TypeUtil.ThreadClass))
        continue;
      return md;
    }
    throw new Error("Can't find Thread.run");
  }
  
  
  private void computeLocalityBindingsSTM() {
    lbmain=new LocalityBinding(typeutil.getMain(), false);
    lbmain.setGlobalReturn(STMEITHER);
    lbmain.setGlobal(0, NORMAL);
    lbtovisit.add(lbmain);
    discovered.put(lbmain, lbmain);
    if (!classtolb.containsKey(lbmain.getMethod().getClassDesc()))
      classtolb.put(lbmain.getMethod().getClassDesc(), new HashSet<LocalityBinding>());
    classtolb.get(lbmain.getMethod().getClassDesc()).add(lbmain);

    if (!methodtolb.containsKey(lbmain.getMethod()))
      methodtolb.put(lbmain.getMethod(), new HashSet<LocalityBinding>());
    methodtolb.get(lbmain.getMethod()).add(lbmain);

    //Do this to force a virtual table number for the run method
    lbrun=new LocalityBinding(getStart(), false);
    lbrun.setGlobalReturn(STMEITHER);
    lbrun.setGlobal(0,NORMAL);
    lbtovisit.add(lbrun);
    discovered.put(lbrun, lbrun);
    if (!classtolb.containsKey(lbrun.getMethod().getClassDesc()))
      classtolb.put(lbrun.getMethod().getClassDesc(), new HashSet<LocalityBinding>());
    classtolb.get(lbrun.getMethod().getClassDesc()).add(lbrun);

    if (!methodtolb.containsKey(lbrun.getMethod()))
      methodtolb.put(lbrun.getMethod(), new HashSet<LocalityBinding>());
    methodtolb.get(lbrun.getMethod()).add(lbrun);

    while(!lbtovisit.isEmpty()) {
      LocalityBinding lb=(LocalityBinding) lbtovisit.iterator().next();
      lbtovisit.remove(lb);

      System.out.println("Analyzing "+lb);

      Integer returnglobal=lb.getGlobalReturn();
      MethodDescriptor md=lb.getMethod();
      Hashtable<FlatNode,Hashtable<TempDescriptor, Integer>> temptable=new Hashtable<FlatNode,Hashtable<TempDescriptor, Integer>>();
      Hashtable<FlatNode, Integer> atomictable=new Hashtable<FlatNode, Integer>();
      calldep.remove(lb);
      try {
	computeCallsFlagsSTM(md, lb, temptable, atomictable);
      } catch (Error e) {
	System.out.println("Error in "+md+" context "+lb);
	e.printStackTrace();
	System.exit(-1);
      }
      temptab.put(lb, temptable);
      atomictab.put(lb, atomictable);

      if (md.getReturnType()!=null&&md.getReturnType().isPtr()&&!returnglobal.equals(lb.getGlobalReturn())) {
	//return type is more precise now
	//rerun everything that call us
	lbtovisit.addAll(dependence.get(lb));
      }
    }
  }

  private static Integer mergestm(Integer a, Integer b) {
    if (a==null||a.equals(STMEITHER))
      return b;
    if (b==null||b.equals(STMEITHER))
      return a;
    if (a.equals(b))
      return a;
    return STMCONFLICT;
  }

  public void computeCallsFlagsSTM(MethodDescriptor md, LocalityBinding lb,  Hashtable<FlatNode, Hashtable<TempDescriptor, Integer>> temptable, Hashtable<FlatNode, Integer> atomictable) {
    FlatMethod fm=state.getMethodFlat(md);
    HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
    tovisit.add(fm.getNext(0));

    {
      // Build table for initial node
      Hashtable<TempDescriptor,Integer> table=new Hashtable<TempDescriptor,Integer>();
      temptable.put(fm, table);
      atomictable.put(fm, lb.isAtomic() ? 1 : 0);
      int offset=md.isStatic() ? 0 : 1;
      if (!md.isStatic()) {
	table.put(fm.getParameter(0), lb.getGlobalThis());
      }
      for(int i=offset; i<fm.numParameters(); i++) {
	TempDescriptor temp=fm.getParameter(i);
	Integer b=lb.isGlobal(i-offset);
	if (b!=null)
	table.put(temp,b);
      }
    }

    Hashtable<FlatNode, Set<TempDescriptor>> livemap=Liveness.computeLiveTemps(fm);
    
    while(!tovisit.isEmpty()) {
      FlatNode fn=tovisit.iterator().next();
      tovisit.remove(fn);
      Set<TempDescriptor> liveset=livemap.get(fn);
      Hashtable<TempDescriptor, Integer> currtable=new Hashtable<TempDescriptor, Integer>();
      int atomicstate=0;
      for(int i=0; i<fn.numPrev(); i++) {
	FlatNode prevnode=fn.getPrev(i);
	if (atomictable.containsKey(prevnode)) {
	  atomicstate=atomictable.get(prevnode).intValue();
	}
	if (!temptable.containsKey(prevnode))
	  continue;
	Hashtable<TempDescriptor, Integer> prevtable=temptable.get(prevnode);
	for(Iterator<TempDescriptor> tempit=prevtable.keySet().iterator(); tempit.hasNext();) {
	  TempDescriptor temp=tempit.next();
	  if (!liveset.contains(temp))
	    continue;
	  Integer tmpint=prevtable.get(temp);
	  Integer oldint=currtable.containsKey(temp) ? currtable.get(temp) : STMEITHER;
	  Integer newint=mergestm(tmpint, oldint);
	  currtable.put(temp, newint);
	}
      }
      atomictable.put(fn, atomicstate);

      // Process this node
      switch(fn.kind()) {
      case FKind.FlatAtomicEnterNode:
	processAtomicEnterNode((FlatAtomicEnterNode)fn, atomictable);
	if (!lb.isAtomic())
	  lb.setHasAtomic();
	break;

      case FKind.FlatAtomicExitNode:
	processAtomicExitNode((FlatAtomicExitNode)fn, atomictable);
	break;

      case FKind.FlatCall:
	processCallNodeSTM(lb, (FlatCall)fn, isAtomic(atomictable, fn), currtable, temptable.get(fn));
	break;

      case FKind.FlatNew:
	processNewSTM(lb, (FlatNew) fn, currtable);
	break;

      case FKind.FlatFieldNode:
	processFieldNodeSTM(lb, (FlatFieldNode) fn, currtable);
	break;

      case FKind.FlatSetFieldNode:
	processSetFieldNodeSTM(lb, (FlatSetFieldNode) fn, currtable);
	break;

      case FKind.FlatSetElementNode:
	processSetElementNodeSTM(lb, (FlatSetElementNode) fn, currtable);
	break;

      case FKind.FlatElementNode:
	processElementNodeSTM(lb, (FlatElementNode) fn, currtable);
	break;

      case FKind.FlatOpNode:
	processOpNodeSTM(lb, (FlatOpNode)fn, currtable);
	break;

      case FKind.FlatCastNode:
	processCastNodeSTM((FlatCastNode)fn, currtable);
	break;

      case FKind.FlatReturnNode:
	processReturnNodeSTM(lb, (FlatReturnNode)fn, currtable);
	break;

      case FKind.FlatLiteralNode:
	processLiteralNodeSTM((FlatLiteralNode)fn, currtable);
	break;

      case FKind.FlatMethod:
      case FKind.FlatOffsetNode:
      case FKind.FlatInstanceOfNode:
      case FKind.FlatCondBranch:
      case FKind.FlatBackEdge:
      case FKind.FlatNop:
      case FKind.FlatPrefetchNode:
      case FKind.FlatExit:
	//No action needed for these
	break;

      case FKind.FlatFlagActionNode:
      case FKind.FlatCheckNode:
      case FKind.FlatTagDeclaration:
	throw new Error("Incompatible with tasks!");

      default:
	throw new Error("In finding fn.kind()= " + fn.kind());
      }


      
      Hashtable<TempDescriptor,Integer> oldtable=temptable.get(fn);
      if (oldtable==null||!oldtable.equals(currtable)) {
	// Update table for this node
	temptable.put(fn, currtable);
	for(int i=0; i<fn.numNext(); i++) {
	  tovisit.add(fn.getNext(i));
	}
      }
    }
  }

  void processNewSTM(LocalityBinding lb, FlatNew fn, Hashtable<TempDescriptor, Integer> currtable) {
    if (fn.isScratch())
      currtable.put(fn.getDst(), SCRATCH);
    else
      currtable.put(fn.getDst(), NORMAL);
  }

  void processCallNodeSTM(LocalityBinding currlb, FlatCall fc, boolean isatomic, Hashtable<TempDescriptor, Integer> currtable, Hashtable<TempDescriptor, Integer> oldtable) {
    MethodDescriptor nodemd=fc.getMethod();
    Set methodset=null;
    Set runmethodset=null;

    if (nodemd.isStatic()||nodemd.getReturnType()==null) {
      methodset=new HashSet();
      methodset.add(nodemd);
    } else {
      methodset=callgraph.getMethods(nodemd, fc.getThis().getType());
      // Build start -> run link
      if (nodemd.getClassDesc().getSymbol().equals(TypeUtil.ThreadClass)&&
          nodemd.getSymbol().equals("start")&&!nodemd.getModifiers().isStatic()&&
          nodemd.numParameters()==0) {
	assert(nodemd.getModifiers().isNative());

	MethodDescriptor runmd=null;
	for(Iterator methodit=nodemd.getClassDesc().getMethodTable().getSet("staticStart").iterator(); methodit.hasNext();) {
	  MethodDescriptor md=(MethodDescriptor) methodit.next();
	  if (md.numParameters()!=1||!md.getModifiers().isStatic()||!md.getParamType(0).getSymbol().equals(TypeUtil.ThreadClass))
	    continue;
	  runmd=md;
	  break;
	}
	if (runmd!=null) {
	  runmethodset=callgraph.getMethods(runmd,fc.getThis().getType());
	  methodset.addAll(runmethodset);
	} else throw new Error("Can't find run method");
      }
    }

    Integer currreturnval=STMEITHER;     //Start off with the either value
    if (oldtable!=null&&fc.getReturnTemp()!=null&&
	oldtable.get(fc.getReturnTemp())!=null) {
      //ensure termination
      currreturnval=mergestm(currreturnval, oldtable.get(fc.getReturnTemp()));
    }

    for(Iterator methodit=methodset.iterator(); methodit.hasNext();) {
      MethodDescriptor md=(MethodDescriptor) methodit.next();

      boolean isnative=md.getModifiers().isNative();
      boolean isjoin = md.getClassDesc().getSymbol().equals(TypeUtil.ThreadClass)&&!nodemd.getModifiers().isStatic()&&nodemd.numParameters()==0&&md.getSymbol().equals("join");
      boolean isObjectgetType = md.getClassDesc().getSymbol().equals("Object") && md.getSymbol().equals("getType");
      boolean isObjecthashCode = md.getClassDesc().getSymbol().equals("Object") && md.getSymbol().equals("nativehashCode");

      LocalityBinding lb=new LocalityBinding(md, isatomic);
      if (isnative&&isatomic) {
	System.out.println("Don't call native methods in atomic blocks!"+currlb.getMethod());
      }

      if (runmethodset==null||!runmethodset.contains(md)) {
	for(int i=0; i<fc.numArgs(); i++) {
	  TempDescriptor arg=fc.getArg(i);
	  if (currtable.containsKey(arg))
	    lb.setGlobal(i,currtable.get(arg));
	}
	if (fc.getThis()!=null) {
	  Integer thistype=currtable.get(fc.getThis());
	  if (thistype==null)
	    thistype=STMEITHER;
	  
	  if(thistype.equals(STMCONFLICT))
	    throw new Error("Using type that can be either normal or scratch in context:\n"+currlb.getExplanation());
	  lb.setGlobalThis(thistype);
	}
      } else {
	Integer thistype=currtable.get(fc.getThis());
	if (!thistype.equals(NORMAL)&&!thistype.equals(STMEITHER)) {
	  throw new Error("Called start on possible scratch object"+thistype);
	}
	lb.setGlobal(0,currtable.get(fc.getThis()));
      }
      //lb is built
      if (!discovered.containsKey(lb)) {
	if (isnative) {
	  if (nodemd.getReturnType()!=null&&nodemd.getReturnType().isPtr())
	    lb.setGlobalReturn(NORMAL);
	} else
	  lb.setGlobalReturn(STMEITHER);

	lb.setParent(currlb);
	lbtovisit.add(lb);
	System.out.println("New lb:"+lb);
	discovered.put(lb, lb);
	if (!classtolb.containsKey(lb.getMethod().getClassDesc()))
	  classtolb.put(lb.getMethod().getClassDesc(), new HashSet<LocalityBinding>());
	classtolb.get(lb.getMethod().getClassDesc()).add(lb);
	if (!methodtolb.containsKey(lb.getMethod()))
	  methodtolb.put(lb.getMethod(), new HashSet<LocalityBinding>());
	methodtolb.get(lb.getMethod()).add(lb);
      } else
	lb=discovered.get(lb);
      Integer returnval=lb.getGlobalReturn();
      currreturnval=mergestm(returnval, currreturnval);
      if (!dependence.containsKey(lb))
	dependence.put(lb, new HashSet<LocalityBinding>());
      dependence.get(lb).add(currlb);

      if (!calldep.containsKey(currlb))
	calldep.put(currlb, new HashSet<LocalityBinding>());
      calldep.get(currlb).add(lb);
    }
    if (fc.getReturnTemp()!=null&&fc.getReturnTemp().getType().isPtr()) {
      currtable.put(fc.getReturnTemp(), currreturnval);
    }
  }

  void processFieldNodeSTM(LocalityBinding lb, FlatFieldNode ffn, Hashtable<TempDescriptor, Integer> currtable) {
    Integer type=currtable.get(ffn.getSrc());
    TempDescriptor dst=ffn.getDst();
    if (!ffn.getDst().getType().isPtr())
      return;

    if (type.equals(SCRATCH)) {
      currtable.put(dst,SCRATCH);
    } else if (type.equals(NORMAL)) {
      currtable.put(dst, NORMAL);
    } else if (type.equals(STMEITHER)) {
      currtable.put(dst, STMEITHER);
    } else if (type.equals(STMCONFLICT)) {
      throw new Error("Access to object that could be either normal or scratch in context:\n"+ffn+"  "+lb.getExplanation());
    }
  }

  //need to handle primitives
  void processSetFieldNodeSTM(LocalityBinding lb, FlatSetFieldNode fsfn, Hashtable<TempDescriptor, Integer> currtable) {
    Integer srctype=currtable.get(fsfn.getSrc());
    Integer dsttype=currtable.get(fsfn.getDst());
    if (!fsfn.getSrc().getType().isPtr())
      return;

    if (dsttype==null)
      System.out.println(fsfn);
    if (dsttype.equals(SCRATCH)) {
      if (!(srctype.equals(SCRATCH)||srctype.equals(STMEITHER)))
	throw new Error("Writing possible normal reference to scratch object in context: \n"+lb.getExplanation());
    } else if (dsttype.equals(NORMAL)) {
      //okay to store primitives in global object
      if (!(srctype.equals(NORMAL)||srctype.equals(STMEITHER)))
	throw new Error("Writing possible scratch reference to normal object in context:\n"+lb.getExplanation()+" for FlatFieldNode "+fsfn);
    } else if (dsttype.equals(STMEITHER)) {
      if (srctype.equals(STMCONFLICT))
	throw new Error("Using reference that could be scratch or normal in context:\n"+lb.getExplanation());
    } else if (dsttype.equals(STMCONFLICT)) {
      throw new Error("Access to object that could be either scratch or normal in context:\n"+lb.getExplanation());
    }
  }

  void processSetElementNodeSTM(LocalityBinding lb, FlatSetElementNode fsen, Hashtable<TempDescriptor, Integer> currtable) {
    Integer srctype=currtable.get(fsen.getSrc());
    Integer dsttype=currtable.get(fsen.getDst());
    if (!fsen.getSrc().getType().isPtr())
      return;

    if (dsttype.equals(SCRATCH)) {
      if (!(srctype.equals(SCRATCH)||srctype.equals(STMEITHER)))
	throw new Error("Writing possible normal reference to scratch object in context:\n"+lb.getExplanation()+fsen);
    } else if (dsttype.equals(NORMAL)) {
      if (!(srctype.equals(NORMAL)||srctype.equals(STMEITHER)))
	throw new Error("Writing possible scratch reference to normal object in context:\n"+lb.getExplanation());
    } else if (dsttype.equals(STMEITHER)) {
      if (srctype.equals(STMCONFLICT))
	throw new Error("Using reference that could be scratch or normal in context:\n"+lb.getExplanation());
    } else if (dsttype.equals(STMCONFLICT)) {
      throw new Error("Access to object that could be either normal or scratch in context:\n"+lb.getExplanation());
    }
  }

  void processOpNodeSTM(LocalityBinding lb, FlatOpNode fon, Hashtable<TempDescriptor, Integer> currtable) {
    /* Just propagate value */
    if (!fon.getLeft().getType().isPtr())
      return;

    Integer srcvalue=currtable.get(fon.getLeft());
    
    if (srcvalue==null) {
      System.out.println(fon);
      MethodDescriptor md=lb.getMethod();
      FlatMethod fm=state.getMethodFlat(md);
      System.out.println(fm.printMethod());
      throw new Error(fon.getLeft()+" is undefined!");
    }
    currtable.put(fon.getDest(), srcvalue);
  }
  
  void processCastNodeSTM(FlatCastNode fcn, Hashtable<TempDescriptor, Integer> currtable) {
    if (currtable.containsKey(fcn.getSrc()))
	currtable.put(fcn.getDst(), currtable.get(fcn.getSrc()));
  }

  void processReturnNodeSTM(LocalityBinding lb, FlatReturnNode frn, Hashtable<TempDescriptor, Integer> currtable) {
    if(frn.getReturnTemp()!=null&&frn.getReturnTemp().getType().isPtr()) {
      Integer returntype=currtable.get(frn.getReturnTemp());
      lb.setGlobalReturn(mergestm(returntype, lb.getGlobalReturn()));
    }
  }
  
   void processLiteralNodeSTM(FlatLiteralNode fln, Hashtable<TempDescriptor, Integer> currtable) {
    //null is either
     if (fln.getType().isNull())
       currtable.put(fln.getDst(), STMEITHER);
     else if (fln.getType().isPtr())
       currtable.put(fln.getDst(), NORMAL);
  }

  void processElementNodeSTM(LocalityBinding lb, FlatElementNode fen, Hashtable<TempDescriptor, Integer> currtable) {
    Integer type=currtable.get(fen.getSrc());
    TempDescriptor dst=fen.getDst();
    if (!fen.getDst().getType().isPtr())
      return;

    if (type==null) {
      System.out.println(fen +" in "+lb+" may access undefined variable");
      MethodDescriptor md=lb.getMethod();
      FlatMethod fm=state.getMethodFlat(md);
      System.out.println(fm.printMethod());
      System.exit(-1);
    } else if (type.equals(SCRATCH)) {
      currtable.put(dst,SCRATCH);
    } else if (type.equals(NORMAL)) {
      currtable.put(dst, NORMAL);
    } else if (type.equals(STMEITHER)) {
      currtable.put(dst, STMEITHER);
    } else if (type.equals(STMCONFLICT)) {
      throw new Error("Access to object that could be either global or local in context:\n"+lb.getExplanation());
    }
  }

  private void computeLocalityBindings() {
    lbmain=new LocalityBinding(typeutil.getMain(), false);
    lbmain.setGlobalReturn(EITHER);
    lbmain.setGlobal(0, LOCAL);
    lbtovisit.add(lbmain);
    discovered.put(lbmain, lbmain);
    if (!classtolb.containsKey(lbmain.getMethod().getClassDesc()))
      classtolb.put(lbmain.getMethod().getClassDesc(), new HashSet<LocalityBinding>());
    classtolb.get(lbmain.getMethod().getClassDesc()).add(lbmain);

    if (!methodtolb.containsKey(lbmain.getMethod()))
      methodtolb.put(lbmain.getMethod(), new HashSet<LocalityBinding>());
    methodtolb.get(lbmain.getMethod()).add(lbmain);

    //Do this to force a virtual table number for the run method
    lbrun=new LocalityBinding(typeutil.getRun(), false);
    lbrun.setGlobalReturn(EITHER);
    lbrun.setGlobalThis(GLOBAL);
    lbtovisit.add(lbrun);
    discovered.put(lbrun, lbrun);
    if (!classtolb.containsKey(lbrun.getMethod().getClassDesc()))
      classtolb.put(lbrun.getMethod().getClassDesc(), new HashSet<LocalityBinding>());
    classtolb.get(lbrun.getMethod().getClassDesc()).add(lbrun);

    if (!methodtolb.containsKey(lbrun.getMethod()))
      methodtolb.put(lbrun.getMethod(), new HashSet<LocalityBinding>());
    methodtolb.get(lbrun.getMethod()).add(lbrun);

    if(state.DSMTASK) {
      lbexecute = new LocalityBinding(typeutil.getExecute(), false);
      lbexecute.setGlobalReturn(EITHER);
      lbexecute.setGlobalThis(GLOBAL);
      lbtovisit.add(lbexecute);
      discovered.put(lbexecute, lbexecute);
      if (!classtolb.containsKey(lbexecute.getMethod().getClassDesc()))
        classtolb.put(lbexecute.getMethod().getClassDesc(), new HashSet<LocalityBinding>());
      classtolb.get(lbexecute.getMethod().getClassDesc()).add(lbexecute);

      if (!methodtolb.containsKey(lbexecute.getMethod()))
        methodtolb.put(lbexecute.getMethod(), new HashSet<LocalityBinding>());
      methodtolb.get(lbexecute.getMethod()).add(lbexecute);
    }

    while(!lbtovisit.isEmpty()) {
      LocalityBinding lb=(LocalityBinding) lbtovisit.iterator().next();
      lbtovisit.remove(lb);

      System.out.println("Analyzing "+lb);
      Integer returnglobal=lb.getGlobalReturn();
      MethodDescriptor md=lb.getMethod();
      Hashtable<FlatNode,Hashtable<TempDescriptor, Integer>> temptable=new Hashtable<FlatNode,Hashtable<TempDescriptor, Integer>>();
      Hashtable<FlatNode, Integer> atomictable=new Hashtable<FlatNode, Integer>();
      calldep.remove(lb);
      try {
	computeCallsFlags(md, lb, temptable, atomictable);
      } catch (Error e) {
	System.out.println("Error in "+md+" context "+lb);
	e.printStackTrace();
	System.exit(-1);
      }
      temptab.put(lb, temptable);
      atomictab.put(lb, atomictable);

      if (md.getReturnType()!=null&&!returnglobal.equals(lb.getGlobalReturn())) {
	//return type is more precise now
	//rerun everything that call us
	lbtovisit.addAll(dependence.get(lb));
      }
    }
  }




  public void computeCallsFlags(MethodDescriptor md, LocalityBinding lb, Hashtable<FlatNode, Hashtable<TempDescriptor, Integer>> temptable, Hashtable<FlatNode, Integer> atomictable) {
    FlatMethod fm=state.getMethodFlat(md);
    HashSet<FlatNode> tovisit=new HashSet<FlatNode>();
    tovisit.add(fm.getNext(0));
    {
      // Build table for initial node
      Hashtable<TempDescriptor,Integer> table=new Hashtable<TempDescriptor,Integer>();
      temptable.put(fm, table);
      atomictable.put(fm, lb.isAtomic() ? 1 : 0);
      int offset=md.isStatic() ? 0 : 1;
      if (!md.isStatic()) {
	table.put(fm.getParameter(0), lb.getGlobalThis());
      }
      for(int i=offset; i<fm.numParameters(); i++) {
	TempDescriptor temp=fm.getParameter(i);
	Integer b=lb.isGlobal(i-offset);
	table.put(temp,b);
      }
    }

    while(!tovisit.isEmpty()) {
      FlatNode fn=tovisit.iterator().next();
      tovisit.remove(fn);
      Hashtable<TempDescriptor, Integer> currtable=new Hashtable<TempDescriptor, Integer>();
      int atomicstate=0;
      for(int i=0; i<fn.numPrev(); i++) {
	FlatNode prevnode=fn.getPrev(i);
	if (atomictable.containsKey(prevnode)) {
	  atomicstate=atomictable.get(prevnode).intValue();
	}
	if (!temptable.containsKey(prevnode))
	  continue;
	Hashtable<TempDescriptor, Integer> prevtable=temptable.get(prevnode);
	for(Iterator<TempDescriptor> tempit=prevtable.keySet().iterator(); tempit.hasNext();) {
	  TempDescriptor temp=tempit.next();
	  Integer tmpint=prevtable.get(temp);
	  Integer oldint=currtable.containsKey(temp) ? currtable.get(temp) : EITHER;
	  Integer newint=merge(tmpint, oldint);
	  currtable.put(temp, newint);
	}
      }
      atomictable.put(fn, atomicstate);

      // Process this node
      switch(fn.kind()) {
      case FKind.FlatAtomicEnterNode:
	processAtomicEnterNode((FlatAtomicEnterNode)fn, atomictable);
	if (!lb.isAtomic())
	  lb.setHasAtomic();
	break;

      case FKind.FlatAtomicExitNode:
	processAtomicExitNode((FlatAtomicExitNode)fn, atomictable);
	break;

      case FKind.FlatCall:
	  processCallNode(lb, (FlatCall)fn, currtable, isAtomic(atomictable, fn), temptable.get(fn));
	break;

      case FKind.FlatFieldNode:
	processFieldNode(lb, (FlatFieldNode)fn, isAtomic(atomictable, fn), currtable);
	break;

      case FKind.FlatSetFieldNode:
	processSetFieldNode(lb, (FlatSetFieldNode)fn, isAtomic(atomictable,fn), currtable);
	break;

      case FKind.FlatNew:
	processNew(lb, (FlatNew)fn, isAtomic(atomictable, fn), currtable);
	break;

      case FKind.FlatOpNode:
	processOpNode((FlatOpNode)fn, currtable);
	break;

      case FKind.FlatCastNode:
	processCastNode((FlatCastNode)fn, currtable);
	break;

      case FKind.FlatLiteralNode:
	processLiteralNode((FlatLiteralNode)fn, currtable);
	break;

      case FKind.FlatReturnNode:
	processReturnNode(lb, (FlatReturnNode)fn, currtable);
	break;

      case FKind.FlatSetElementNode:
	processSetElementNode(lb, (FlatSetElementNode)fn, currtable, isAtomic(atomictable, fn));
	break;

      case FKind.FlatElementNode:
	processElementNode(lb, (FlatElementNode)fn, currtable, isAtomic(atomictable, fn));
	break;

      case FKind.FlatInstanceOfNode:
      case FKind.FlatCondBranch:
      case FKind.FlatBackEdge:
      case FKind.FlatNop:
      case FKind.FlatExit:
      case FKind.FlatPrefetchNode:
	//No action needed for these
	break;

      case FKind.FlatFlagActionNode:
      case FKind.FlatCheckNode:
      case FKind.FlatTagDeclaration:
	throw new Error("Incompatible with tasks!");

      case FKind.FlatMethod:
	break;

      case FKind.FlatOffsetNode:
	processOffsetNode((FlatOffsetNode)fn, currtable);
	break;

      default:
	throw new Error("In finding fn.kind()= " + fn.kind());
      }
      Hashtable<TempDescriptor,Integer> oldtable=temptable.get(fn);
      if (oldtable==null||!oldtable.equals(currtable)) {
	// Update table for this node
	temptable.put(fn, currtable);
	for(int i=0; i<fn.numNext(); i++) {
	  tovisit.add(fn.getNext(i));
	}
      }
    }
  }

  private static boolean isAtomic(Hashtable<FlatNode, Integer> atomictable, FlatNode fn) {
    return atomictable.get(fn).intValue()>0;
  }

  private static Integer merge(Integer a, Integer b) {
    if (a==null||a.equals(EITHER))
      return b;
    if (b==null||b.equals(EITHER))
      return a;
    if (a.equals(b))
      return a;
    return CONFLICT;
  }

    void processCallNode(LocalityBinding currlb, FlatCall fc, Hashtable<TempDescriptor, Integer> currtable, boolean isatomic, Hashtable<TempDescriptor,Integer> oldtable) {
    MethodDescriptor nodemd=fc.getMethod();
    Set methodset=null;
    Set runmethodset=null;
    Set executemethodset=null;

    if (nodemd.isStatic()||nodemd.getReturnType()==null) {
      methodset=new HashSet();
      methodset.add(nodemd);
    } else {
      methodset=callgraph.getMethods(nodemd, fc.getThis().getType());
      // Build start -> run link
      if (nodemd.getClassDesc().getSymbol().equals(TypeUtil.ThreadClass)&&
          nodemd.getSymbol().equals("start")&&!nodemd.getModifiers().isStatic()&&
          nodemd.numParameters()==1&&nodemd.getParamType(0).isInt()) {
      	assert(nodemd.getModifiers().isNative());

      	MethodDescriptor runmd=null;

        for(Iterator methodit=nodemd.getClassDesc().getMethodTable().getSet("run").iterator(); methodit.hasNext();) {
      	  MethodDescriptor md=(MethodDescriptor) methodit.next();
      
          if (md.numParameters()!=0||md.getModifiers().isStatic())
      	    continue;
      	  runmd=md;
      	  break;
	      }
      	if (runmd!=null) {
      	  runmethodset=callgraph.getMethods(runmd,fc.getThis().getType());
      	  methodset.addAll(runmethodset);
      	} else throw new Error("Can't find run method");
      }

      if(state.DSMTASK) {
        if (nodemd.getClassDesc().getSymbol().equals(TypeUtil.TaskClass) &&
          nodemd.getSymbol().equals("execution") && !nodemd.getModifiers().isStatic() &&
          nodemd.numParameters() == 0) {
      
          assert(nodemd.getModifiers().isNative());
          MethodDescriptor exemd = null;

          for(Iterator methodit=nodemd.getClassDesc().getMethodTable().getSet("execute").iterator(); methodit.hasNext();) {
            MethodDescriptor md = (MethodDescriptor) methodit.next();

            if (md.numParameters() != 0 || md.getModifiers().isStatic())
              continue;
            exemd = md;
            break;
          }

          if (exemd != null) {
            executemethodset = callgraph.getMethods(exemd, fc.getThis().getType());
            methodset.addAll(executemethodset);
          } else throw new Error("Can't find execute method");
        }
      }
    }

    Integer currreturnval=EITHER;     //Start off with the either value
    if (oldtable!=null&&fc.getReturnTemp()!=null&&
        oldtable.get(fc.getReturnTemp())!=null) {
	//ensure termination
	currreturnval=merge(currreturnval, oldtable.get(fc.getReturnTemp()));
    }

    for(Iterator methodit=methodset.iterator(); methodit.hasNext();) {
      MethodDescriptor md=(MethodDescriptor) methodit.next();

      boolean isnative=md.getModifiers().isNative();
      boolean isjoin = md.getClassDesc().getSymbol().equals(TypeUtil.ThreadClass)&&!nodemd.getModifiers().isStatic()&&nodemd.numParameters()==0&&md.getSymbol().equals("join");
      boolean isObjectgetType = md.getClassDesc().getSymbol().equals("Object") && md.getSymbol().equals("getType");
      boolean isObjecthashCode = md.getClassDesc().getSymbol().equals("Object") && md.getSymbol().equals("nativehashCode");

      LocalityBinding lb=new LocalityBinding(md, isatomic);
      if (isnative&&isatomic) {
	System.out.println("Don't call native methods in atomic blocks!"+currlb.getMethod());
      }

  if ((runmethodset==null||!runmethodset.contains(md)) &&( executemethodset == null || !executemethodset.contains(md))) {
	//Skip this part if it is a run method or execute method
	for(int i=0; i<fc.numArgs(); i++) {
	  TempDescriptor arg=fc.getArg(i);
	  if(isnative&&(currtable.get(arg).equals(GLOBAL)||
	                currtable.get(arg).equals(CONFLICT))&& !(nodemd.getSymbol().equals("rangePrefetch"))) {
	    throw new Error("Potential call to native method "+md+" with global parameter:\n"+currlb.getExplanation());
	  }
	  lb.setGlobal(i,currtable.get(arg));
	}
      }

      if (fc.getThis()!=null) {
	Integer thistype=currtable.get(fc.getThis());
	if (thistype==null)
	  thistype=EITHER;

	if(runmethodset!=null&&runmethodset.contains(md)&&thistype.equals(LOCAL) && executemethodset != null && executemethodset.contains(md))
	  throw new Error("Starting thread on local object not allowed in context:\n"+currlb.getExplanation());
	if(isjoin&&thistype.equals(LOCAL))
	  throw new Error("Joining thread on local object not allowed in context:\n"+currlb.getExplanation());
	if(thistype.equals(CONFLICT))
	  throw new Error("Using type that can be either local or global in context:\n"+currlb.getExplanation());
	if(runmethodset==null&&thistype.equals(GLOBAL)&&!isatomic && !isjoin && executemethodset == null) {
	  throw new Error("Using global object outside of transaction in context:\n"+currlb.getExplanation());
    }
	if (runmethodset==null&&isnative&&thistype.equals(GLOBAL) && !isjoin && executemethodset == null && !isObjectgetType && !isObjecthashCode)
	  throw new Error("Potential call to native method "+md+" on global objects:\n"+currlb.getExplanation());
	lb.setGlobalThis(thistype);
      }
      //lb is built
      if (!discovered.containsKey(lb)) {
	if (isnative)
	  lb.setGlobalReturn(LOCAL);
	else
	  lb.setGlobalReturn(EITHER);
	lb.setParent(currlb);
	lbtovisit.add(lb);
	discovered.put(lb, lb);
	if (!classtolb.containsKey(lb.getMethod().getClassDesc()))
	  classtolb.put(lb.getMethod().getClassDesc(), new HashSet<LocalityBinding>());
	classtolb.get(lb.getMethod().getClassDesc()).add(lb);
	if (!methodtolb.containsKey(lb.getMethod()))
	  methodtolb.put(lb.getMethod(), new HashSet<LocalityBinding>());
	methodtolb.get(lb.getMethod()).add(lb);
      } else
	lb=discovered.get(lb);
      Integer returnval=lb.getGlobalReturn();
      currreturnval=merge(returnval, currreturnval);
      if (!dependence.containsKey(lb))
	dependence.put(lb, new HashSet<LocalityBinding>());
      dependence.get(lb).add(currlb);

      if (!calldep.containsKey(currlb))
	calldep.put(currlb, new HashSet<LocalityBinding>());
      calldep.get(currlb).add(lb);
    }
    if (fc.getReturnTemp()!=null) {
      currtable.put(fc.getReturnTemp(), currreturnval);
    }
  }

  void processFieldNode(LocalityBinding lb, FlatFieldNode ffn, boolean transaction, Hashtable<TempDescriptor, Integer> currtable) {
    Integer type=currtable.get(ffn.getSrc());
    TempDescriptor dst=ffn.getDst();
    if (type.equals(LOCAL)) {
      if (ffn.getField().isGlobal())
	currtable.put(dst,GLOBAL);
      else
	currtable.put(dst,LOCAL);
    } else if (type.equals(GLOBAL)) {
      if (!transaction)
	throw new Error("Global access outside of a transaction in context:\n"+lb.getExplanation());
      if (ffn.getField().getType().isPrimitive()&&!ffn.getField().getType().isArray())
	currtable.put(dst, LOCAL);         // primitives are local
      else
	currtable.put(dst, GLOBAL);
    } else if (type.equals(EITHER)) {
      if (ffn.getField().getType().isPrimitive()&&!ffn.getField().getType().isArray())
	currtable.put(dst, LOCAL);         // primitives are local
      else if (ffn.getField().isGlobal())
	currtable.put(dst, GLOBAL);
      else
	currtable.put(dst, EITHER);
    } else if (type.equals(CONFLICT)) {
      throw new Error("Access to object that could be either global or local in context:\n"+lb.getExplanation());
    }
  }

  //need to handle primitives
  void processSetFieldNode(LocalityBinding lb, FlatSetFieldNode fsfn, boolean transaction, Hashtable<TempDescriptor, Integer> currtable) {
    Integer srctype=currtable.get(fsfn.getSrc());
    Integer dsttype=currtable.get(fsfn.getDst());

    if (dsttype.equals(LOCAL)) {
      if (fsfn.getField().isGlobal()) {
	if (!(srctype.equals(GLOBAL)||srctype.equals(EITHER)))
	  throw new Error("Writing possible local reference to global field in context: \n"+lb.getExplanation());
      } else {
	if (!(srctype.equals(LOCAL)||srctype.equals(EITHER))) {
	  throw new Error("Writing possible global reference to local object in context: \n"+lb.getExplanation());
    }
      }
    } else if (dsttype.equals(GLOBAL)) {
      if (!transaction)
	throw new Error("Global access outside of a transaction in context:\n"+lb.getExplanation());
      //okay to store primitives in global object
      if (srctype.equals(LOCAL) && fsfn.getField().getType().isPrimitive() && !fsfn.getField().getType().isArray())
	return;
      if (!(srctype.equals(GLOBAL)||srctype.equals(EITHER)))
	throw new Error("Writing possible local reference to global object in context:\n"+lb.getExplanation()+" for FlatFieldNode "+fsfn);
    } else if (dsttype.equals(EITHER)) {
      if (srctype.equals(CONFLICT))
	throw new Error("Using reference that could be local or global in context:\n"+lb.getExplanation());
    } else if (dsttype.equals(CONFLICT)) {
      throw new Error("Access to object that could be either global or local in context:\n"+lb.getExplanation());
    }
  }

  void processNew(LocalityBinding lb, FlatNew fn, boolean transaction, Hashtable<TempDescriptor, Integer> currtable) {
    if (fn.isGlobal()&&!transaction) {
      throw new Error("Allocating global object outside of transaction in context:"+lb.getExplanation());
    }
    if (fn.isGlobal())
      currtable.put(fn.getDst(), GLOBAL);
    else
      currtable.put(fn.getDst(), LOCAL);
  }

  void processOpNode(FlatOpNode fon, Hashtable<TempDescriptor, Integer> currtable) {
    /* Just propagate value */
    Integer srcvalue=currtable.get(fon.getLeft());

    if (srcvalue==null) {
      if (!fon.getLeft().getType().isPtr()) {
	srcvalue=LOCAL;
      } else
	throw new Error(fon.getLeft()+" is undefined!");
    }
    currtable.put(fon.getDest(), srcvalue);
  }

  void processCastNode(FlatCastNode fcn, Hashtable<TempDescriptor, Integer> currtable) {
    currtable.put(fcn.getDst(), currtable.get(fcn.getSrc()));
  }

  void processLiteralNode(FlatLiteralNode fln, Hashtable<TempDescriptor, Integer> currtable) {
    //null is either
    if (fln.getValue()==null)
      currtable.put(fln.getDst(), EITHER);
    else
      currtable.put(fln.getDst(), LOCAL);
  }

  void processOffsetNode(FlatOffsetNode fln, Hashtable<TempDescriptor, Integer> currtable) {
    currtable.put(fln.getDst(), LOCAL);
  }

  void processReturnNode(LocalityBinding lb, FlatReturnNode frn, Hashtable<TempDescriptor, Integer> currtable) {
    if(frn.getReturnTemp()!=null) {
      Integer returntype=currtable.get(frn.getReturnTemp());
      lb.setGlobalReturn(merge(returntype, lb.getGlobalReturn()));
    }
  }

  void processSetElementNode(LocalityBinding lb, FlatSetElementNode fsen, Hashtable<TempDescriptor, Integer> currtable, boolean isatomic) {
    Integer srctype=currtable.get(fsen.getSrc());
    Integer dsttype=currtable.get(fsen.getDst());

    if (dsttype.equals(LOCAL)) {
      if (!(srctype.equals(LOCAL)||srctype.equals(EITHER))) {
	throw new Error("Writing possible global reference to local object in context:\n"+lb.getExplanation()+fsen);
      }
    } else if (dsttype.equals(GLOBAL)) {
      if (srctype.equals(LOCAL) && fsen.getDst().getType().dereference().isPrimitive() && !fsen.getDst().getType().dereference().isArray())
	return;
      if (!(srctype.equals(GLOBAL)||srctype.equals(EITHER)))
	throw new Error("Writing possible local reference to global object in context:\n"+lb.getExplanation());
      if (!isatomic)
	throw new Error("Global access outside of a transaction in context:\n"+lb.getExplanation());
    } else if (dsttype.equals(EITHER)) {
      if (srctype.equals(CONFLICT))
	throw new Error("Using reference that could be local or global in context:\n"+lb.getExplanation());
    } else if (dsttype.equals(CONFLICT)) {
      throw new Error("Access to object that could be either global or local in context:\n"+lb.getExplanation());
    }
  }

  void processElementNode(LocalityBinding lb, FlatElementNode fen, Hashtable<TempDescriptor, Integer> currtable, boolean isatomic) {
    Integer type=currtable.get(fen.getSrc());
    TempDescriptor dst=fen.getDst();
    if (type.equals(LOCAL)) {
      currtable.put(dst,LOCAL);
    } else if (type.equals(GLOBAL)) {
      if (!isatomic)
	throw new Error("Global access outside of a transaction in context:\n"+lb.getExplanation());
      if(fen.getSrc().getType().dereference().isPrimitive()&&
         !fen.getSrc().getType().dereference().isArray())
	currtable.put(dst, LOCAL);
      else
	currtable.put(dst, GLOBAL);
    } else if (type.equals(EITHER)) {
      if(fen.getSrc().getType().dereference().isPrimitive()&&
         !fen.getSrc().getType().dereference().isArray())
	currtable.put(dst, LOCAL);
      else
	currtable.put(dst, EITHER);
    } else if (type.equals(CONFLICT)) {
      throw new Error("Access to object that could be either global or local in context:\n"+lb.getExplanation());
    }
  }

  void processAtomicEnterNode(FlatAtomicEnterNode fen, Hashtable<FlatNode, Integer> atomictable) {
    int atomic=atomictable.get(fen).intValue();
    atomictable.put(fen, new Integer(atomic+1));
  }

  void processAtomicExitNode(FlatAtomicExitNode fen, Hashtable<FlatNode, Integer> atomictable) {
    int atomic=atomictable.get(fen).intValue();
    atomictable.put(fen, new Integer(atomic-1));
  }
    
  private void computeTempstoSave() {
    for(Iterator<LocalityBinding> lbit=getLocalityBindings().iterator(); lbit.hasNext();) {
      LocalityBinding lb=lbit.next();
      computeTempstoSave(lb);
    }
  }

  /* Need to checkpoint all temps that could be read from along any
   * path that are either:
     1) Written to by any assignment inside the transaction
     2) Read from a global temp.

     Generate tempstosave map from
     localitybinding->flatatomicenternode->Set<TempDescriptors>
   */

  private void computeTempstoSave(LocalityBinding lb) {
    if (lb.isAtomic())
      return;
    Hashtable<FlatNode, Integer> atomictab=getAtomic(lb);
    Hashtable<FlatNode, Hashtable<TempDescriptor, Integer>> temptab=getNodeTempInfo(lb);
    MethodDescriptor md=lb.getMethod();
    FlatMethod fm=state.getMethodFlat(md);
    Hashtable<FlatNode, Set<TempDescriptor>> nodetotemps=Liveness.computeLiveTemps(fm);
    Hashtable<FlatAtomicEnterNode, Set<TempDescriptor>> nodetosavetemps=new Hashtable<FlatAtomicEnterNode, Set<TempDescriptor>>();
    tempstosave.put(lb, nodetosavetemps);
    Hashtable<FlatNode, FlatAtomicEnterNode> nodemap=new Hashtable<FlatNode, FlatAtomicEnterNode>();
    HashSet<FlatNode> toprocess=new HashSet<FlatNode>();
    HashSet<FlatNode> discovered=new HashSet<FlatNode>();
    toprocess.add(fm);
    discovered.add(fm);
    while(!toprocess.isEmpty()) {
      FlatNode fn=toprocess.iterator().next();
      toprocess.remove(fn);
      boolean isatomic=atomictab.get(fn).intValue()>0;
      if (isatomic&&
          atomictab.get(fn.getPrev(0)).intValue()==0) {
	assert(fn.getPrev(0).kind()==FKind.FlatAtomicEnterNode);
	nodemap.put(fn, (FlatAtomicEnterNode)fn);
	nodetosavetemps.put((FlatAtomicEnterNode)fn, new HashSet<TempDescriptor>());
      } else if (isatomic) {
	FlatAtomicEnterNode atomicnode=nodemap.get(fn);
	Set<TempDescriptor> livetemps=nodetotemps.get(atomicnode);
	List<TempDescriptor> reads=Arrays.asList(fn.readsTemps());
	List<TempDescriptor> writes=Arrays.asList(fn.writesTemps());

	for(Iterator<TempDescriptor> tempit=livetemps.iterator(); tempit.hasNext();) {
	  TempDescriptor tmp=tempit.next();
	  if (writes.contains(tmp)) {
	    nodetosavetemps.get(atomicnode).add(tmp);
	  } else if (state.DSM) {
	    if (reads.contains(tmp)&&temptab.get(fn).get(tmp)==GLOBAL) {
	      nodetosavetemps.get(atomicnode).add(tmp);
	    } 
	  } else if (state.SINGLETM) {
	    if (reads.contains(tmp)&&tmp.getType().isPtr()&&temptab.get(fn).get(tmp)==NORMAL) {
	      nodetosavetemps.get(atomicnode).add(tmp);
	    } 
	  }
	}
      }
      for(int i=0; i<fn.numNext(); i++) {
	FlatNode fnnext=fn.getNext(i);
	if (!discovered.contains(fnnext)) {
	  discovered.add(fnnext);
	  toprocess.add(fnnext);
	  if(isatomic) {
	    nodemap.put(fnnext, nodemap.get(fn));
	  }
	}
      }
    }
  }
}
