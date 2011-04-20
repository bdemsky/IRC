package IR.Tree;
import IR.*;
import IR.Tree.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;
import Util.Pair;

public class JavaBuilder {
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

  /* Maps class/interfaces to all instantiated classes that extend or
   * implement those classes or interfaces */

  HashMap<ClassDescriptor, Set<ClassDescriptor>> implementationMap=new HashMap<ClassDescriptor, Set<ClassDescriptor>>();

  /* Maps methods to the methods they call */
  
  HashMap<MethodDescriptor, Set<MethodDescriptor>> callMap=new HashMap<MethodDescriptor, Set<MethodDescriptor>>();

  /* Invocation map */
  HashMap<ClassDescriptor, Set<Pair<MethodDescriptor, MethodDescriptor>>> invocationMap=new HashMap<ClassDescriptor, Set<Pair<MethodDescriptor, MethodDescriptor>>>();
  

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
    ClassDescriptor mainClass=sc.getClass(null, state.main, SemanticCheck.INIT);
    MethodDescriptor mainMethod=tu.getMain();
    toprocess.push(mainMethod);
    computeFixPoint();
  }

  void checkMethod(MethodDescriptor md) {
    try {
      sc.checkMethodBody(md.getClassDesc(), md);
    } catch( Error e ) {
      System.out.println( "Error in "+md );
      throw e;
    }
  }
  
  void initClassDesc(ClassDescriptor cd) {
    if (classStatus.get(cd)==null) {
      classStatus.put(cd, CDINIT);
      //TODO...LOOK FOR STATIC INITIALIZERS
    }
  }
  
  void computeFixPoint() {
    while(!toprocess.isEmpty()) {
      MethodDescriptor md=toprocess.pop();
      checkMethod(md);
      initClassDesc(md.getClassDesc());
      bf.flattenMethod(md.getClassDesc(), md);
      processFlatMethod(md);
    }
  }
  
  void processCall(MethodDescriptor md, FlatCall fcall) {
    MethodDescriptor callmd=fcall.getMethod();

    //First handle easy cases...
    if (callmd.isStatic()||callmd.isConstructor()) {
      if (!discovered.contains(callmd)) {
	discovered.add(callmd);
	toprocess.push(callmd);
      }
      callMap.get(md).add(callmd);
      return;
    }

    //Otherwise, handle virtual dispatch...
    ClassDescriptor cn=callmd.getClassDesc();
    Set<ClassDescriptor> impSet=implementationMap.get(cn);

    if (!invocationMap.containsKey(cn))
      invocationMap.put(cn, new HashSet<Pair<MethodDescriptor,MethodDescriptor>>());
    invocationMap.get(cn).add(new Pair<MethodDescriptor, MethodDescriptor>(md, callmd));

    for(ClassDescriptor cdactual:impSet) {
      searchimp:
      while(cdactual!=null) {
	Set possiblematches=cdactual.getMethodTable().getSetFromSameScope(callmd.getSymbol());

	for(Iterator matchit=possiblematches.iterator(); matchit.hasNext();) {
	  MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
	  if (callmd.matches(matchmd)) {
	    //Found the method that will be called
	    if (!discovered.contains(matchmd)) {
	      discovered.add(matchmd);
	      toprocess.push(matchmd);
	    }
	    callMap.get(md).add(matchmd);
	    
	    break searchimp;
	  }
	}

	//Didn't find method...look in super class
	cdactual=cdactual.getSuperDesc();
      }
    }
  }

  void processNew(FlatNew fnew) {
    TypeDescriptor tdnew=fnew.getType();
    if (!tdnew.isClass())
      return;
    ClassDescriptor cdnew=tdnew.getClassDesc();
    Stack<ClassDescriptor> tovisit=new Stack<ClassDescriptor>();
    tovisit.add(cdnew);
    
    while(!tovisit.isEmpty()) {
      ClassDescriptor cdcurr=tovisit.pop();
      if (!implementationMap.containsKey(cdcurr))
	implementationMap.put(cdcurr, new HashSet<ClassDescriptor>());
      if (implementationMap.get(cdcurr).add(cdnew)) {
	//new implementation...see if it affects implementationmap
	if (invocationMap.containsKey(cdcurr)) {
	  for(Pair<MethodDescriptor, MethodDescriptor> mdpair:invocationMap.get(cdcurr)) {
	    MethodDescriptor md=mdpair.getFirst();
	    MethodDescriptor callmd=mdpair.getSecond();
	    ClassDescriptor cdactual=cdnew;
	    
	    searchimp:
	    while(cdactual!=null) {
	      Set possiblematches=cdactual.getMethodTable().getSetFromSameScope(callmd.getSymbol());
	      for(Iterator matchit=possiblematches.iterator(); matchit.hasNext();) {
		MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
		if (callmd.matches(matchmd)) {
		  //Found the method that will be called
		  if (!discovered.contains(matchmd)) {
		    discovered.add(matchmd);
		    toprocess.push(matchmd);
		  }
		  callMap.get(md).add(matchmd);
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
      for(Iterator interit=cdcurr.getSuperInterfaces();interit.hasNext();) {
	ClassDescriptor cdinter=(ClassDescriptor) interit.next();
	tovisit.push(cdinter);
      }
    }
  }

  void processFlatMethod(MethodDescriptor md) {
    if (!callMap.containsKey(md))
      callMap.put(md, new HashSet<MethodDescriptor>());
    
    FlatMethod fm=state.getMethodFlat(md);
    for(FlatNode fn:fm.getNodeSet()) {
      switch(fn.kind()) {
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

  public static ParseNode readSourceFile(State state, String sourcefile) {
    try {
      Reader fr= new BufferedReader(new FileReader(sourcefile));
      Lex.Lexer l = new Lex.Lexer(fr);
      java_cup.runtime.lr_parser g;
      g = new Parse.Parser(l);
      ParseNode p=null;
      try {
	p=(ParseNode) g./*debug_*/parse().value;
      } catch (Exception e) {
	System.err.println("Error parsing file:"+sourcefile);
	e.printStackTrace();
	System.exit(-1);
      }
      state.addParseNode(p);
      if (l.numErrors()!=0) {
	System.out.println("Error parsing "+sourcefile);
	System.exit(l.numErrors());
      }
      state.lines+=l.line_num;
      return p;

    } catch (Exception e) {
      throw new Error(e);
    }
  }

  public void loadClass(BuildIR bir, String sourcefile) {
    try {
      ParseNode pn=readSourceFile(state, sourcefile);
      bir.buildtree(pn, null,sourcefile);
    } catch (Exception e) {
      System.out.println("Error in sourcefile:"+sourcefile);
      e.printStackTrace();
      System.exit(-1);
    } catch (Error e) {
      System.out.println("Error in sourcefile:"+sourcefile);
      e.printStackTrace();
      System.exit(-1);
    }
  }
}