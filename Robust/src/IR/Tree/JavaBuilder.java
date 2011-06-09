package IR.Tree;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;
import Util.Pair;
import Analysis.CallGraph.CallGraph;

public class JavaBuilder implements CallGraph {
  State state;
  HashSet<Descriptor> checkedDesc=new HashSet<Descriptor>();
  HashMap<ClassDescriptor, Integer> classStatus=new HashMap<ClassDescriptor, Integer>();
  public final int CDNONE=0;
  public final int CDINIT=1;
  public final int CDINSTANTIATED=2;
  BuildIR bir;
  TypeUtil tu;
  SemanticCheck sc;
  BuildFlat bf;
  Stack<MethodDescriptor> toprocess=new Stack<MethodDescriptor>();
  HashSet<MethodDescriptor> discovered=new HashSet<MethodDescriptor>();
  HashMap<MethodDescriptor, Set<MethodDescriptor>> canCall=new HashMap<MethodDescriptor, Set<MethodDescriptor>>();
  MethodDescriptor mainMethod;

  /* Maps class/interfaces to all instantiated classes that extend or
   * implement those classes or interfaces */

  HashMap<ClassDescriptor, Set<ClassDescriptor>> implementationMap=new HashMap<ClassDescriptor, Set<ClassDescriptor>>();

  /* Maps methods to the methods they call */

  HashMap<MethodDescriptor, Set<MethodDescriptor>> callMap=new HashMap<MethodDescriptor, Set<MethodDescriptor>>();

  HashMap<MethodDescriptor, Set<MethodDescriptor>> revCallMap=new HashMap<MethodDescriptor, Set<MethodDescriptor>>();

  /* Invocation map */
  HashMap<ClassDescriptor, Set<Pair<MethodDescriptor, MethodDescriptor>>> invocationMap=new HashMap<ClassDescriptor, Set<Pair<MethodDescriptor, MethodDescriptor>>>();

  public Set getAllMethods(Descriptor d) {
    HashSet tovisit=new HashSet();
    tovisit.add(d);
    HashSet callable=new HashSet();
    while(!tovisit.isEmpty()) {
      Descriptor md=(Descriptor)tovisit.iterator().next();
      tovisit.remove(md);
      Set s=getCalleeSet(md);

      if (s!=null) {
        for(Iterator it=s.iterator(); it.hasNext(); ) {
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

  public Set getMethods(MethodDescriptor md, TypeDescriptor type) {
    if (canCall.containsKey(md))
      return canCall.get(md);
    else
      return new HashSet();
  }

  public Set getMethods(MethodDescriptor md) {
    return getMethods(md, null);
  }

  public Set getMethodCalls(Descriptor d) {
    Set set=getAllMethods(d);
    set.add(d);
    return set;
  }

  /* Returns whether there is a reachable call to this method descriptor...Not whether the implementation is called */

  public boolean isCalled(MethodDescriptor md) {
    return canCall.containsKey(md);
  }

  public boolean isCallable(MethodDescriptor md) {
    return !getCallerSet(md).isEmpty()||md==mainMethod;
  }

  public Set getCalleeSet(Descriptor d) {
    Set calleeset=callMap.get((MethodDescriptor)d);
    if (calleeset==null)
      return new HashSet();
    else
      return calleeset;
  }

  public Set getCallerSet(MethodDescriptor md) {
    Set callerset=revCallMap.get(md);
    if (callerset==null)
      return new HashSet();
    else
      return callerset;
  }

  public Set getFirstReachableMethodContainingSESE(Descriptor d,
                                                   Set<MethodDescriptor> methodsContainingSESEs) {
    throw new Error("");
  }

  public boolean hasLayout(ClassDescriptor cd) {
    return sc.hasLayout(cd);
  }

  public JavaBuilder(State state) {
    this.state=state;
    bir=new BuildIR(state);
    tu=new TypeUtil(state, bir);
    sc=new SemanticCheck(state, tu, false);
    bf=new BuildFlat(state,tu);
  }

  public TypeUtil getTypeUtil() {
    return tu;
  }

  public BuildFlat getBuildFlat() {
    return bf;
  }

  public void build() {
    //Initialize Strings to keep runtime happy
    ClassDescriptor stringClass=sc.getClass(null, TypeUtil.StringClass, SemanticCheck.INIT);
    instantiateClass(stringClass);

    ClassDescriptor mainClass=sc.getClass(null, state.main, SemanticCheck.INIT);
    mainMethod=tu.getMain();

    canCall.put(mainMethod, new HashSet<MethodDescriptor>());
    canCall.get(mainMethod).add(mainMethod);

    toprocess.push(mainMethod);
    computeFixPoint();
    tu.createFullTable();
  }

  void checkMethod(MethodDescriptor md) {
    try {
      sc.checkMethodBody(md.getClassDesc(), md);
    } catch( Error e ) {
      System.out.println("Error in "+md);
      throw e;
    }
  }

  public boolean isInit(ClassDescriptor cd) {
    return classStatus.get(cd)!=null&&classStatus.get(cd)>=CDINIT;
  }

  void initClassDesc(ClassDescriptor cd, int init) {
    if (classStatus.get(cd)==null||classStatus.get(cd)<init) {
      if (classStatus.get(cd)==null) {
        MethodDescriptor mdstaticinit = (MethodDescriptor)cd.getMethodTable().get("staticblocks");
        if (mdstaticinit!=null) {
          discovered.add(mdstaticinit);
          toprocess.push(mdstaticinit);
        }
      }
      classStatus.put(cd, init);
    }
  }

  void computeFixPoint() {
    while(!toprocess.isEmpty()) {
      MethodDescriptor md=toprocess.pop();
      checkMethod(md);
      initClassDesc(md.getClassDesc(), CDINIT);
      bf.flattenMethod(md.getClassDesc(), md);
      processFlatMethod(md);
    }

    //make sure every called method descriptor has a flat method
    for(MethodDescriptor callmd : canCall.keySet())
      bf.addJustFlatMethod(callmd);
  }

  void processCall(MethodDescriptor md, FlatCall fcall) {
    MethodDescriptor callmd=fcall.getMethod();
    //make sure we have a FlatMethod for the base method...
    if (!canCall.containsKey(callmd))
      canCall.put(callmd, new HashSet<MethodDescriptor>());

    //First handle easy cases...
    if (callmd.isStatic()||callmd.isConstructor()) {
      if (!discovered.contains(callmd)) {
        discovered.add(callmd);
        toprocess.push(callmd);
      }
      if (!revCallMap.containsKey(callmd))
        revCallMap.put(callmd, new HashSet<MethodDescriptor>());
      revCallMap.get(callmd).add(md);
      callMap.get(md).add(callmd);
      canCall.get(callmd).add(callmd);
      return;
    }

    //Otherwise, handle virtual dispatch...
    ClassDescriptor cn=callmd.getClassDesc();
    Set<ClassDescriptor> impSet=implementationMap.get(cn);

    if (!invocationMap.containsKey(cn))
      invocationMap.put(cn, new HashSet<Pair<MethodDescriptor,MethodDescriptor>>());
    invocationMap.get(cn).add(new Pair<MethodDescriptor, MethodDescriptor>(md, callmd));

    if (impSet!=null) {
      for(ClassDescriptor cdactual : impSet) {
        searchimp :
        while(cdactual!=null) {
          Set possiblematches=cdactual.getMethodTable().getSetFromSameScope(callmd.getSymbol());

          for(Iterator matchit=possiblematches.iterator(); matchit.hasNext(); ) {
            MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
            if (callmd.matches(matchmd)) {
              //Found the method that will be called
              if (!discovered.contains(matchmd)) {
                discovered.add(matchmd);
                toprocess.push(matchmd);
              }

              if (!revCallMap.containsKey(matchmd))
                revCallMap.put(matchmd, new HashSet<MethodDescriptor>());
              revCallMap.get(matchmd).add(md);

              callMap.get(md).add(matchmd);
              canCall.get(callmd).add(matchmd);
              break searchimp;
            }
          }

          //Didn't find method...look in super class
          cdactual=cdactual.getSuperDesc();
        }
      }
    }
  }

  void processNew(FlatNew fnew) {
    TypeDescriptor tdnew=fnew.getType();
    if (!tdnew.isClass())
      return;
    ClassDescriptor cdnew=tdnew.getClassDesc();
    //Make sure class is fully initialized
    sc.checkClass(cdnew, SemanticCheck.INIT);
    instantiateClass(cdnew);
  }

  void instantiateClass(ClassDescriptor cdnew) {
    if (classStatus.containsKey(cdnew)&&classStatus.get(cdnew)==CDINSTANTIATED)
      return;
    initClassDesc(cdnew, CDINSTANTIATED);

    Stack<ClassDescriptor> tovisit=new Stack<ClassDescriptor>();
    tovisit.add(cdnew);

    while(!tovisit.isEmpty()) {
      ClassDescriptor cdcurr=tovisit.pop();
      if (!implementationMap.containsKey(cdcurr))
        implementationMap.put(cdcurr, new HashSet<ClassDescriptor>());
      if (implementationMap.get(cdcurr).add(cdnew)) {
        //new implementation...see if it affects implementationmap
        if (invocationMap.containsKey(cdcurr)) {
          for(Pair<MethodDescriptor, MethodDescriptor> mdpair : invocationMap.get(cdcurr)) {
            MethodDescriptor md=mdpair.getFirst();
            MethodDescriptor callmd=mdpair.getSecond();
            ClassDescriptor cdactual=cdnew;

searchimp:
            while(cdactual!=null) {
              Set possiblematches=cdactual.getMethodTable().getSetFromSameScope(callmd.getSymbol());
              for(Iterator matchit=possiblematches.iterator(); matchit.hasNext(); ) {
                MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
                if (callmd.matches(matchmd)) {
                  //Found the method that will be called
                  if (!discovered.contains(matchmd)) {
                    discovered.add(matchmd);
                    toprocess.push(matchmd);
                  }
                  if (!revCallMap.containsKey(matchmd))
                    revCallMap.put(matchmd, new HashSet<MethodDescriptor>());
                  revCallMap.get(matchmd).add(md);
                  callMap.get(md).add(matchmd);
                  canCall.get(callmd).add(matchmd);
                  break searchimp;
                }
              }

              //Didn't find method...look in super class
              cdactual=cdactual.getSuperDesc();
            }
          }
        }
      }
      if (cdcurr.getSuperDesc()!=null)
        tovisit.push(cdcurr.getSuperDesc());
      for(Iterator interit=cdcurr.getSuperInterfaces(); interit.hasNext(); ) {
        ClassDescriptor cdinter=(ClassDescriptor) interit.next();
        tovisit.push(cdinter);
      }
    }
  }

  void processFlatMethod(MethodDescriptor md) {
    if (!callMap.containsKey(md))
      callMap.put(md, new HashSet<MethodDescriptor>());

    FlatMethod fm=state.getMethodFlat(md);
    for(FlatNode fn: fm.getNodeSet()) {
      switch(fn.kind()) {
      case FKind.FlatFieldNode: {
	FieldDescriptor fd=((FlatFieldNode)fn).getField();
	if (fd.isStatic()) {
	  ClassDescriptor cd=fd.getClassDescriptor();
	  initClassDesc(cd, CDINIT);
	}
	break;
      }

      case FKind.FlatSetFieldNode: {
	FieldDescriptor fd=((FlatSetFieldNode)fn).getField();
	if (fd.isStatic()) {
	  ClassDescriptor cd=fd.getClassDescriptor();
	  initClassDesc(cd, CDINIT);
	}
	break;
      }

      case FKind.FlatCall: {
	FlatCall fcall=(FlatCall)fn;
	processCall(md, fcall);
	break;
      }

      case FKind.FlatNew: {
        FlatNew fnew=(FlatNew)fn;
        processNew(fnew);
        break;
      }
      }
    }
  }
}