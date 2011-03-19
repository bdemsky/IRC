package Analysis.OoOJava;
import Util.Pair;
import java.util.*;
import IR.Flat.*;
import IR.*;
import Analysis.Liveness;
import Analysis.CallGraph.CallGraph;

public class Accessible {
  //Try to compute InAccessible
  HashMap<FlatNode, Set<TempDescriptor>> inAccessible;
  State state;
  CallGraph callGraph;
  RBlockRelationAnalysis taskAnalysis;
  Liveness liveness;
  Stack<Pair<FlatNode,MethodDescriptor>> toprocess=new Stack<Pair<FlatNode, MethodDescriptor>>();
  HashMap<MethodDescriptor, Set<Pair<FlatCall, MethodDescriptor>>> methodmap=new HashMap<MethodDescriptor, Set<Pair<FlatCall, MethodDescriptor>>>();

  public Accessible(State state, CallGraph callGraph, RBlockRelationAnalysis taskAnalysis, Liveness liveness) {
    inAccessible=new HashMap<FlatNode, Set<TempDescriptor>>();
    this.state=state;
    this.callGraph=callGraph;
    this.taskAnalysis=taskAnalysis;
    this.liveness=liveness;
  }

  public void computeFixPoint() {
    nextNode:
    while(!toprocess.isEmpty()) {
      Pair<FlatNode, MethodDescriptor> fnpair=toprocess.pop();
      FlatNode fn=fnpair.getFirst();
      MethodDescriptor pairmd=fnpair.getSecond();
      HashSet<TempDescriptor> inAccessibleSet=new HashSet<TempDescriptor>();
      for(int i=0;i<fn.numPrev();i++) {
	Set<TempDescriptor> inAccess=inAccessible.get(fn.getPrev(i));
	if (inAccess!=null)
	  inAccessibleSet.addAll(inAccess);
      }

      switch(fn.kind()) {
      case FKind.FlatNew:
      case FKind.FlatFieldNode:
      case FKind.FlatElementNode:
      case FKind.FlatSetFieldNode:
      case FKind.FlatSetElementNode:
	{
	  TempDescriptor[] rdtmps=fn.readsTemps();
	  for(int i=0;i<rdtmps.length;i++) {
	    inAccessibleSet.remove(rdtmps[i]);
	  }
	  TempDescriptor[] wrtmps=fn.writesTemps();
	  for(int i=0;i<wrtmps.length;i++) {
	    inAccessibleSet.remove(wrtmps[i]);
	  }
	}
	break;
      case FKind.FlatCastNode:
      case FKind.FlatOpNode:
	{
	  TempDescriptor[] rdtmps=fn.readsTemps();
	  TempDescriptor[] wrtmps=fn.writesTemps();
	  if (inAccessibleSet.contains(rdtmps[0]))
	    inAccessibleSet.add(wrtmps[0]);
	}
	break;
      case FKind.FlatReturnNode:
	{
	  FlatReturnNode fr=(FlatReturnNode)fn;
	  if (fr.getReturnTemp()!=null&&inAccessibleSet.contains(fr.getReturnTemp())) {
	    //Need to inform callers
	    Set<Pair<FlatCall, MethodDescriptor>> callset=methodmap.get(pairmd);
	    for(Pair<FlatCall, MethodDescriptor> fcallpair:callset) {
	      FlatCall fcall=fcallpair.getFirst();
	      Set<TempDescriptor> inAccess=inAccessible.get(fcall);
	      if (fcall.getReturnTemp()!=null&&!inAccess.contains(fcall.getReturnTemp())) {
		inAccess.add(fcall.getReturnTemp());
		toprocess.add(new Pair<FlatNode, MethodDescriptor>(fcall, fcallpair.getSecond()));
	      }
	    }
	  }
	}
	continue nextNode;
      case FKind.FlatSESEEnterNode:
      case FKind.FlatSESEExitNode:
	continue nextNode;
      case FKind.FlatCall: {
	FlatCall fcall=(FlatCall)fn;
	MethodDescriptor calledmethod=fcall.getMethod();
	Set methodsthatcouldbecalled=fcall.getThis()==null ? callGraph.getMethods(calledmethod) :
	  callGraph.getMethods(calledmethod, fcall.getThis().getType());	
	for(Object o:methodsthatcouldbecalled) {
	  MethodDescriptor md=(MethodDescriptor)o;
	  FlatMethod fm=state.getMethodFlat(md);
	  HashSet<TempDescriptor> tmpinaccess=new HashSet<TempDescriptor>();
	  for(int i=0;i<fm.numParameters();i++) {
	    TempDescriptor fmtmp=fm.getParameter(i);
	    TempDescriptor tmpcall=fcall.getArgMatchingParamIndex(fm, i);
	    if (inAccessibleSet.contains(tmpcall)) {
	      tmpinaccess.add(fmtmp);
	    }
	  }
	  if (!tmpinaccess.isEmpty()&&(!inAccessible.containsKey(fm)||!inAccessible.get(fm).containsAll(tmpinaccess))) {
	    for(int i=0;i<fm.numNext();i++)
	      toprocess.add(new Pair<FlatNode, MethodDescriptor>(fm.getNext(i),md));
	    inAccessible.get(fm).addAll(tmpinaccess);
	  }
	}
	//be sure not to wipe out return value or other inaccessible temps
	inAccessibleSet.addAll(inAccessible.get(fcall));
      }
	break;
      default:
      }
      if (!inAccessibleSet.isEmpty()&&(!inAccessible.containsKey(fn)||!inAccessible.get(fn).equals(inAccessibleSet))) {
	inAccessible.put(fn, inAccessibleSet);
	for(int i=0;i<fn.numNext();i++)
	  toprocess.add(new Pair<FlatNode, MethodDescriptor>(fn.getNext(i),pairmd));
      }
    }
  }

  public void doAnalysis() {
    for(FlatSESEEnterNode sese: taskAnalysis.getAllSESEs()) {
      FlatSESEExitNode seseexit=sese.getFlatExit();
      HashSet<TempDescriptor> liveout=new HashSet<TempDescriptor>(liveness.getLiveOutTemps(sese.getfmEnclosing(), seseexit));
      for(Iterator<TempDescriptor> tmpit=liveout.iterator();tmpit.hasNext();) {
	TempDescriptor tmp=tmpit.next();
	if (!tmp.getType().isPtr())
	  tmpit.remove();
      }
      inAccessible.put(seseexit, liveout);
      for(int i=0;i<seseexit.numNext();i++)
	toprocess.add(new Pair<FlatNode, MethodDescriptor>(seseexit.getNext(i),sese.getmdEnclosing()));
    }
    
    Set<MethodDescriptor> methodSet=taskAnalysis.getMethodsWithSESEs();
    Set<MethodDescriptor> canCallSESE=new HashSet<MethodDescriptor>(methodSet);
    Stack<MethodDescriptor> methodStack=new Stack<MethodDescriptor>();
    methodStack.addAll(methodSet);
    //Set up exits of SESEs
    while(!methodStack.isEmpty()) {
      MethodDescriptor md=methodStack.pop();
      Set callers=callGraph.getCallerSet(md);
      for(Object o:callers) {
	MethodDescriptor callermd=(MethodDescriptor)o;
	if (!canCallSESE.contains(callermd)) {
	  //new method descriptor
	  canCallSESE.add(callermd);
	  methodStack.add(callermd);
	}
      }
    }

    //Set up exits of methods
    for(MethodDescriptor md:canCallSESE) {
      FlatMethod fm=state.getMethodFlat(md);
      for(FlatNode fn:fm.getNodeSet()) {
	if (fn.kind()==FKind.FlatCall) {
	  FlatCall fcall=(FlatCall)fn;
	  MethodDescriptor calledmethod=fcall.getMethod();
	  Set methodsthatcouldbecalled=fcall.getThis()==null ? callGraph.getMethods(calledmethod) :
	    callGraph.getMethods(calledmethod, fcall.getThis().getType());
	  boolean specialcall=false;
	  for(Object o:methodsthatcouldbecalled) {
	    MethodDescriptor callermd=(MethodDescriptor)o;
	    if (canCallSESE.contains(callermd)) {
	      //TODO: NEED TO BUILD MAP FROM MD -> CALLS
	      specialcall=true;
	      if (!methodmap.containsKey(callermd))
		methodmap.put(callermd, new HashSet<Pair<FlatCall, MethodDescriptor>>());
	      methodmap.get(callermd).add(new Pair<FlatCall, MethodDescriptor>(fcall,md));
	    }
	  }
	  if (specialcall) {
	    Set<TempDescriptor> liveout=new HashSet<TempDescriptor>(liveness.getLiveOutTemps(fm, fcall));
	    TempDescriptor returntmp=fcall.getReturnTemp();
	    liveout.remove(returntmp);
	    for(Iterator<TempDescriptor> tmpit=liveout.iterator();tmpit.hasNext();) {
	      TempDescriptor tmp=tmpit.next();
	      if (!tmp.getType().isPtr())
		tmpit.remove();
	    }
	    inAccessible.put(fcall, liveout);
	    for(int i=0;i<fcall.numNext();i++)
	      toprocess.add(new Pair<FlatNode, MethodDescriptor>(fcall.getNext(i),md));
	  }
	}
      }
    }
    computeFixPoint();
  }
}