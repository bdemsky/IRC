package IR.Flat;
import IR.Tree.Modifiers;
import IR.Tree.FlagExpressionNode;
import IR.Tree.DNFFlag;
import IR.Tree.DNFFlagAtom;
import IR.Tree.TagExpressionList;
import IR.Tree.OffsetNode;
import IR.*;

import java.util.*;
import java.io.*;

import Util.Relation;
import Analysis.TaskStateAnalysis.FlagState;
import Analysis.TaskStateAnalysis.FlagComparator;
import Analysis.TaskStateAnalysis.OptionalTaskDescriptor;
import Analysis.TaskStateAnalysis.Predicate;
import Analysis.TaskStateAnalysis.SafetyAnalysis;
import Analysis.TaskStateAnalysis.TaskIndex;
import Analysis.Locality.LocalityAnalysis;
import Analysis.Locality.LocalityBinding;
import Analysis.Locality.DiscoverConflicts;
import Analysis.Locality.DCWrapper;
import Analysis.Locality.DelayComputation;
import Analysis.Locality.BranchAnalysis;
import Analysis.CallGraph.CallGraph;
import Analysis.Prefetch.*;
import Analysis.Loops.WriteBarrier;
import Analysis.Loops.GlobalFieldType;
import Analysis.Locality.TypeAnalysis;
import Util.CodePrinter;

public class BuildCodeTran extends BuildCode {
  String oidstr="___nextobject___";
  LocalityAnalysis locality;
  Hashtable<LocalityBinding, TempDescriptor> reverttable;
  Hashtable<LocalityBinding, Hashtable<TempDescriptor, TempDescriptor>> backuptable;
  PrefetchAnalysis pa;
  WriteBarrier wb;
  DiscoverConflicts dc;
  DiscoverConflicts recorddc;
  DCWrapper delaycomp;
  LocalityBinding currlb;


  public BuildCodeTran(State st, Hashtable temptovar, TypeUtil typeutil, SafetyAnalysis sa, PrefetchAnalysis pa) {
    this(st, temptovar, typeutil, null, sa, pa);
  }

  public BuildCodeTran(State st, Hashtable temptovar, TypeUtil typeutil, LocalityAnalysis locality, PrefetchAnalysis pa) {
    this(st, temptovar, typeutil, locality, null, pa);
  }

  public BuildCodeTran(State st, Hashtable temptovar, TypeUtil typeutil, LocalityAnalysis locality, SafetyAnalysis sa, PrefetchAnalysis pa) {
    super(st, temptovar, typeutil, sa);
    this.sa=sa;
    if (state.SINGLETM)
      oidstr="___objlocation___";
    if (locality!=null) {
      this.locality=locality;
      this.reverttable=new Hashtable<LocalityBinding, TempDescriptor>();
      this.backuptable=new Hashtable<LocalityBinding, Hashtable<TempDescriptor, TempDescriptor>>();
      this.wb=new WriteBarrier(locality, st);
    }
    if (state.SINGLETM&&state.DCOPTS) {
      TypeAnalysis typeanalysis=new TypeAnalysis(locality, st, typeutil,callgraph);
      GlobalFieldType gft=new GlobalFieldType(callgraph, st, typeutil.getMain());
      this.dc=new DiscoverConflicts(locality, st, typeanalysis, gft);
      dc.doAnalysis();
    }
    if (state.DELAYCOMP) {
      TypeAnalysis typeanalysis=new TypeAnalysis(locality, st, typeutil,callgraph);
      GlobalFieldType gft=new GlobalFieldType(callgraph, st, typeutil.getMain());
      delaycomp=new DCWrapper(locality, st, typeanalysis, gft);
      dc=delaycomp.getConflicts();
      recorddc=new DiscoverConflicts(locality, st, typeanalysis, delaycomp.getCannotDelayMap(), true, true, null);
      recorddc.doAnalysis();
    }
  }

  /** The buildCode method outputs C code for all the methods.  The Flat
   * versions of the methods must already be generated and stored in
   * the State object. */

  PrintWriter outsandbox=null;

  protected void buildCodeSetup() {
    try {
      if (state.SANDBOX) {
	outsandbox=new CodePrinter(new FileOutputStream(PREFIX+"sandboxdefs.c"), true);
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }

  protected void additionalIncludesMethodsHeader(PrintWriter outmethodheader) {
    if (state.DSM)
      outmethodheader.println("#include \"dstm.h\"");
    if (state.SANDBOX) {
      outmethodheader.println("#include \"sandbox.h\"");
    }
    if (state.SINGLETM) {
      outmethodheader.println("#include \"tm.h\"");
      outmethodheader.println("#include \"delaycomp.h\"");
      outmethodheader.println("#include \"inlinestm.h\"");
    }
    if (state.ABORTREADERS) {
      outmethodheader.println("#include \"abortreaders.h\"");
      outmethodheader.println("#include <setjmp.h>");
    }

  }


  protected void outputMainMethod(PrintWriter outmethod) {
    outmethod.println("int main(int argc, const char *argv[]) {");
    outmethod.println("  int i;");
    outputStaticBlocks(outmethod);
    outputClassObjects(outmethod);
    additionalCodeAtTopOfMain(outmethod);

    if (state.DSM) {
      if (state.DSMRECOVERYSTATS) {
	outmethod.println("#ifdef RECOVERYSTATS \n");
	outmethod.println("handle();\n");
	outmethod.println("#endif\n");
      } else {
	outmethod.println("#if defined(TRANSSTATS) || defined(RECOVERYSTATS) \n");
	outmethod.println("handle();\n");
	outmethod.println("#endif\n");
      }
    }
    outmethod.println("initializethreads();");

    if (state.DSM) {
      outmethod.println("if (dstmStartup(argv[1])) {");
      if (GENERATEPRECISEGC) {
	outmethod.println("  struct ArrayObject * stringarray=allocate_newarray(NULL, STRINGARRAYTYPE, argc-2);");
      } else {
	outmethod.println("  struct ArrayObject * stringarray=allocate_newarray(STRINGARRAYTYPE, argc-2);");
      }
    } else {
      if (GENERATEPRECISEGC) {
	outmethod.println("  struct ArrayObject * stringarray=allocate_newarray(NULL, STRINGARRAYTYPE, argc-1);");
      } else {
	outmethod.println("  struct ArrayObject * stringarray=allocate_newarray(STRINGARRAYTYPE, argc-1);");
      }
    }
    if (state.DSM) {
      outmethod.println("  for(i=2;i<argc;i++) {");
    } else
      outmethod.println("  for(i=1;i<argc;i++) {");
    outmethod.println("    int length=strlen(argv[i]);");
    if (GENERATEPRECISEGC) {
      outmethod.println("    struct ___String___ *newstring=NewString(NULL, argv[i], length);");
    } else {
      outmethod.println("    struct ___String___ *newstring=NewString(argv[i], length);");
    }
    if (state.DSM)
      outmethod.println("    ((void **)(((char *)& stringarray->___length___)+sizeof(int)))[i-2]=newstring;");
    else
      outmethod.println("    ((void **)(((char *)& stringarray->___length___)+sizeof(int)))[i-1]=newstring;");
    outmethod.println("  }");

    MethodDescriptor md=typeutil.getMain();
    ClassDescriptor cd=typeutil.getMainClass();

    outmethod.println("   {");
    if (GENERATEPRECISEGC) {
      outmethod.print("       struct "+cd.getSafeSymbol()+locality.getMain().getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params __parameterlist__={");
      outmethod.println("1, NULL,"+"stringarray};");
      outmethod.println("     "+cd.getSafeSymbol()+locality.getMain().getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(& __parameterlist__);");
    } else {
      outmethod.println("     "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(stringarray);");
    }
    outmethod.println("   }");

    if (state.DSM) {
      outmethod.println("}");
    }

    outmethod.println("pthread_mutex_lock(&gclistlock);");
    outmethod.println("threadcount--;");
    outmethod.println("pthread_cond_signal(&gccond);");
    outmethod.println("pthread_mutex_unlock(&gclistlock);");
    
    outmethod.println("#if defined(TRANSSTATS) \n");
    outmethod.println("printf(\"******  Transaction Stats   ******\\n\");");
    outmethod.println("printf(\"numTransCommit= %d\\n\", numTransCommit);");
    outmethod.println("printf(\"numTransAbort= %d\\n\", numTransAbort);");
    outmethod.println("printf(\"nSoftAbort= %d\\n\", nSoftAbort);");
    if (state.DSM) {
      outmethod.println("printf(\"nchashSearch= %d\\n\", nchashSearch);");
      outmethod.println("printf(\"nmhashSearch= %d\\n\", nmhashSearch);");
      outmethod.println("printf(\"nprehashSearch= %d\\n\", nprehashSearch);");
      outmethod.println("printf(\"ndirtyCacheObj= %d\\n\", ndirtyCacheObj);");
      outmethod.println("printf(\"nRemoteReadSend= %d\\n\", nRemoteSend);");
      outmethod.println("printf(\"bytesSent= %d\\n\", bytesSent);");
      outmethod.println("printf(\"bytesRecv= %d\\n\", bytesRecv);");
      outmethod.println("printf(\"totalObjSize= %d\\n\", totalObjSize);");
      outmethod.println("printf(\"sendRemoteReq= %d\\n\", sendRemoteReq);");
      outmethod.println("printf(\"getResponse= %d\\n\", getResponse);");
    } else if (state.SINGLETM) {
      outmethod.println("printf(\"nSoftAbortAbort= %d\\n\", nSoftAbortAbort);");
      outmethod.println("printf(\"nSoftAbortCommit= %d\\n\", nSoftAbortCommit);");
      outmethod.println("#ifdef STMSTATS\n");
      outmethod.println("for(i=0; i<TOTALNUMCLASSANDARRAY; i++) {\n");
      outmethod.println("  printf(\"typesCausingAbort[%2d] numaccess= %5d numabort= %3d\\n\", i, typesCausingAbort[i].numaccess, typesCausingAbort[i].numabort);\n");
      outmethod.println("}\n");
      outmethod.println("#endif\n");
      outmethod.println("fflush(stdout);");
    }
    outmethod.println("#endif\n");
    
    
    if (state.EVENTMONITOR) {
      outmethod.println("dumpdata();");
    }
    
    if (state.SINGLETM)
      outmethod.println("pthread_exit(NULL);");
    additionalCodeAtBottomOfMain(outmethod);
    outmethod.println("}");
  }

  protected void additionalIncludesMethodsImplementation(PrintWriter outmethod) {
    if (state.SANDBOX) {
      outmethod.println("#include \"sandboxdefs.c\"");
    }
    if (state.DSM) {
      outmethod.println("#include \"addPrefetchEnhance.h\"");
      outmethod.println("#include \"localobjects.h\"");
    }
  }

  protected void generateMethods(PrintWriter outmethod) {
    for(Iterator<LocalityBinding> lbit=locality.getLocalityBindings().iterator(); lbit.hasNext(); ) {
      LocalityBinding lb=lbit.next();
      MethodDescriptor md=lb.getMethod();
      FlatMethod fm=state.getMethodFlat(md);
      wb.analyze(lb);
      if (!md.getModifiers().isNative()) {
	generateFlatMethod(fm, lb, outmethod);
      }
    }
  }

  protected void additionalIncludesStructsHeader(PrintWriter outstructs) {
    LocalityBinding lbrun=new LocalityBinding(typeutil.getRun(), false);
    if (state.DSM) {
      lbrun.setGlobalThis(LocalityAnalysis.GLOBAL);
    } else if (state.SINGLETM)   {
      lbrun.setGlobalThis(LocalityAnalysis.NORMAL);
    }
    outstructs.println("#define RUNMETHOD "+virtualcalls.getLocalityNumber(lbrun));

    if (state.DSMTASK) {
      LocalityBinding lbexecute = new LocalityBinding(typeutil.getExecute(), false);
      if(state.DSM)
	lbexecute.setGlobalThis(LocalityAnalysis.GLOBAL);
      else if( state.SINGLETM)
	lbexecute.setGlobalThis(LocalityAnalysis.NORMAL);
      outstructs.println("#define EXECUTEMETHOD " + virtualcalls.getLocalityNumber(lbexecute));
    }
  }

  protected void printExtraArrayFields(PrintWriter outclassdefs) {
    if (state.STMARRAY) {
      outclassdefs.println("  int lowindex;");
      outclassdefs.println("  int highindex;");
    }
    if (state.DUALVIEW) {
      outclassdefs.println("  int arrayversion;");
    }
  }

  /** The buildVirtualTables method outputs the virtual dispatch
   * tables for methods. */

  protected void buildVirtualTables(PrintWriter outvirtual) {
    Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
    while(classit.hasNext()) {
      ClassDescriptor cd=(ClassDescriptor)classit.next();
      if (virtualcalls.getMethodCount(cd)>maxcount)
	maxcount=virtualcalls.getMethodCount(cd);
    }

    LocalityBinding[][] lbvirtualtable=null;
    lbvirtualtable=new LocalityBinding[state.numClasses()+state.numArrays()][maxcount];

    /* Fill in virtual table */
    classit=state.getClassSymbolTable().getDescriptorsIterator();
    while(classit.hasNext()) {
      ClassDescriptor cd=(ClassDescriptor)classit.next();
      if(cd.isInterface()) {
	continue;
      }
      fillinRow(cd, lbvirtualtable, cd.getId());
    }

    ClassDescriptor objectcd=typeutil.getClass(TypeUtil.ObjectClass);
    Iterator arrayit=state.getArrayIterator();
    while(arrayit.hasNext()) {
      TypeDescriptor td=(TypeDescriptor)arrayit.next();
      int id=state.getArrayNumber(td);
      fillinRow(objectcd, lbvirtualtable, id+state.numClasses());
    }

    outvirtual.print("void * virtualtable[]={");
    boolean needcomma=false;
    for(int i=0; i<state.numClasses()+state.numArrays(); i++) {
      for(int j=0; j<maxcount; j++) {
	if (needcomma)
	  outvirtual.print(", ");
	if (lbvirtualtable[i][j]!=null) {
	  LocalityBinding lb=lbvirtualtable[i][j];
	  MethodDescriptor md=lb.getMethod();
	  outvirtual.print("& "+md.getClassDesc().getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor());
	} else {
	  outvirtual.print("0");
	}
	needcomma=true;
      }
      outvirtual.println("");
    }
    outvirtual.println("};");
    outvirtual.close();
  }

  protected void fillinRow(ClassDescriptor cd, LocalityBinding[][] virtualtable, int rownum) {
    /* Get inherited methods */
    if (cd.getSuperDesc()!=null)
      fillinRow(cd.getSuperDesc(), virtualtable, rownum);
    /* Override them with our methods */
    if (locality.getClassBindings(cd)!=null)
      for(Iterator<LocalityBinding> lbit=locality.getClassBindings(cd).iterator(); lbit.hasNext(); ) {
	LocalityBinding lb=lbit.next();
	MethodDescriptor md=lb.getMethod();
	//Is the method static or a constructor
	if (md.isStatic()||md.getReturnType()==null)
	  continue;
	int methodnum=virtualcalls.getLocalityNumber(lb);
	virtualtable[rownum][methodnum]=lb;
      }
  }

  /** Generate array that contains the sizes of class objects.  The
   * object allocation functions in the runtime use this
   * information. */

  protected void generateSizeArrayExtensions(PrintWriter outclassdefs) {
    outclassdefs.print("#ifdef TRANSSTATS \n");
    outclassdefs.print("extern int numTransAbort;\n");
    outclassdefs.print("extern int numTransCommit;\n");
    outclassdefs.print("extern int nSoftAbort;\n");
    if (state.DSM) {
      outclassdefs.print("extern int nchashSearch;\n");
      outclassdefs.print("extern int nmhashSearch;\n");
      outclassdefs.print("extern int nprehashSearch;\n");
      outclassdefs.print("extern int ndirtyCacheObj;\n");
      outclassdefs.print("extern int nRemoteSend;\n");
      outclassdefs.print("extern int sendRemoteReq;\n");
      outclassdefs.print("extern int getResponse;\n");
      outclassdefs.print("extern int bytesSent;\n");
      outclassdefs.print("extern int bytesRecv;\n");
      outclassdefs.print("extern int totalObjSize;\n");
      outclassdefs.print("extern void handle();\n");
    } else if (state.SINGLETM) {
      outclassdefs.println("extern int nSoftAbortAbort;");
      outclassdefs.println("extern int nSoftAbortCommit;");
      outclassdefs.println("#ifdef STMSTATS\n");
      outclassdefs.println("extern objtypestat_t typesCausingAbort[];");
      outclassdefs.println("#endif\n");
    }
    outclassdefs.print("#endif\n");

    outclassdefs.print("int numprefetchsites = " + pa.prefetchsiteid + ";\n");
  }

  /** Constructs params and temp objects for each method or task.
   * These objects tell the compiler which temps need to be
   * allocated.  */

  protected void generateTempStructs(FlatMethod fm, LocalityBinding lb) {
    MethodDescriptor md=fm.getMethod();
    TaskDescriptor task=fm.getTask();
    Set<TempDescriptor> saveset=lb!=null ? locality.getTempSet(lb) : null;
    ParamsObject objectparams=md!=null ? new ParamsObject(md,tag++) : new ParamsObject(task, tag++);
    if (lb!=null) {
      paramstable.put(lb, objectparams);
      backuptable.put(lb, new Hashtable<TempDescriptor, TempDescriptor>());
    } else if (md!=null)
      paramstable.put(md, objectparams);
    else
      paramstable.put(task, objectparams);

    for(int i=0; i<fm.numParameters(); i++) {
      TempDescriptor temp=fm.getParameter(i);
      TypeDescriptor type=temp.getType();
      if (type.isPtr()&&((GENERATEPRECISEGC) || (this.state.MULTICOREGC)))
	objectparams.addPtr(temp);
      else
	objectparams.addPrim(temp);
      if(lb!=null&&saveset.contains(temp)) {
	backuptable.get(lb).put(temp, temp.createNew());
      }
    }

    for(int i=0; i<fm.numTags(); i++) {
      TempDescriptor temp=fm.getTag(i);
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC))
	objectparams.addPtr(temp);
      else
	objectparams.addPrim(temp);
    }

    TempObject objecttemps=md!=null ? new TempObject(objectparams,md,tag++) : new TempObject(objectparams, task, tag++);
    if (lb!=null)
      tempstable.put(lb, objecttemps);
    else if (md!=null)
      tempstable.put(md, objecttemps);
    else
      tempstable.put(task, objecttemps);

    for(Iterator nodeit=fm.getNodeSet().iterator(); nodeit.hasNext(); ) {
      FlatNode fn=(FlatNode)nodeit.next();
      TempDescriptor[] writes=fn.writesTemps();
      for(int i=0; i<writes.length; i++) {
	TempDescriptor temp=writes[i];
	TypeDescriptor type=temp.getType();
	if (type.isPtr()&&((GENERATEPRECISEGC) || (this.state.MULTICOREGC)))
	  objecttemps.addPtr(temp);
	else
	  objecttemps.addPrim(temp);
	if(lb!=null&&saveset.contains(temp)&&
	   !backuptable.get(lb).containsKey(temp))
	  backuptable.get(lb).put(temp, temp.createNew());
      }
    }

    /* Create backup temps */
    if (lb!=null) {
      for(Iterator<TempDescriptor> tmpit=backuptable.get(lb).values().iterator(); tmpit.hasNext(); ) {
	TempDescriptor tmp=tmpit.next();
	TypeDescriptor type=tmp.getType();
	if (type.isPtr()&&((GENERATEPRECISEGC) || (this.state.MULTICOREGC)))
	  objecttemps.addPtr(tmp);
	else
	  objecttemps.addPrim(tmp);
      }
      /* Create temp to hold revert table */
      if (state.DSM&&(lb.getHasAtomic()||lb.isAtomic())) {
	TempDescriptor reverttmp=new TempDescriptor("revertlist", typeutil.getClass(TypeUtil.ObjectClass));
	if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC))
	  objecttemps.addPtr(reverttmp);
	else
	  objecttemps.addPrim(reverttmp);
	reverttable.put(lb, reverttmp);
      }
    }
  }

  /** This method outputs the following information about classes
   * and arrays:
   * (1) For classes, what are the locations of pointers.
   * (2) For arrays, does the array contain pointers or primitives.
   * (3) For classes, does the class contain flags.
   */

  protected void generateLayoutStructs(PrintWriter output) {
    Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
    while(it.hasNext()) {
      ClassDescriptor cn=(ClassDescriptor)it.next();
      output.println("unsigned INTPTR "+cn.getSafeSymbol()+"_pointers[]={");
      Iterator allit=cn.getFieldTable().getAllDescriptorsIterator();
      int count=0;
      while(allit.hasNext()) {
	FieldDescriptor fd=(FieldDescriptor)allit.next();
	TypeDescriptor type=fd.getType();
	if (state.DSM&&fd.isGlobal())         //Don't GC the global objects for now
	  continue;
	if (type.isPtr())
	  count++;
      }
      output.print(count);
      allit=cn.getFieldTable().getAllDescriptorsIterator();
      while(allit.hasNext()) {
	FieldDescriptor fd=(FieldDescriptor)allit.next();
	TypeDescriptor type=fd.getType();
	if (state.DSM&&fd.isGlobal())         //Don't GC the global objects for now
	  continue;
	if (type.isPtr()) {
	  output.println(",");
	  output.print("((unsigned INTPTR)&(((struct "+cn.getSafeSymbol() +" *)0)->"+
	               fd.getSafeSymbol()+"))");
	}
      }
      output.println("};");
    }
    output.println("unsigned INTPTR * pointerarray[]={");
    boolean needcomma=false;
    for(int i=0; i<state.numClasses(); i++) {
      ClassDescriptor cn=cdarray[i];
      if (needcomma)
	output.println(",");
      needcomma=true;
      if(cn != null) {
	output.print(cn.getSafeSymbol()+"_pointers");
      } else {
	output.print("NULL");
      }
    }

    for(int i=0; i<state.numArrays(); i++) {
      if (needcomma)
	output.println(", ");
      TypeDescriptor tdelement=arraytable[i].dereference();
      if (tdelement.isArray()||tdelement.isClass())
	output.print("((unsigned INTPTR *)1)");
      else
	output.print("0");
      needcomma=true;
    }

    output.println("};");
    needcomma=false;
    output.println("int hasflags[]={");
    for(int i=0; i<state.numClasses(); i++) {
      ClassDescriptor cn=cdarray[i];
      if (needcomma)
	output.println(", ");
      needcomma=true;
      if ((cn != null) && (cn.hasFlags()))
	output.print("1");
      else
	output.print("0");
    }
    output.println("};");
  }

  
  protected void generateCallStructsMethods(ClassDescriptor cn, PrintWriter output, PrintWriter headersout) {
    /* Cycle through LocalityBindings */
    HashSet<MethodDescriptor> nativemethods=new HashSet<MethodDescriptor>();
    Set<LocalityBinding> lbset=locality.getClassBindings(cn);
    if (lbset!=null) {
      for(Iterator<LocalityBinding> lbit=lbset.iterator(); lbit.hasNext(); ) {
	LocalityBinding lb=lbit.next();
	MethodDescriptor md=lb.getMethod();
	if (md.getModifiers().isNative()) {
	  //make sure we only print a native method once
	  if (nativemethods.contains(md)) {
	    FlatMethod fm=state.getMethodFlat(md);
	    generateTempStructs(fm, lb);
	    continue;
	  } else
	    nativemethods.add(md);
	}
	generateMethod(cn, md, lb, headersout, output);
      }
    }
    for(Iterator methodit=cn.getMethods(); methodit.hasNext(); ) {
      MethodDescriptor md=(MethodDescriptor)methodit.next();
      if (md.getModifiers().isNative()&&!nativemethods.contains(md)) {
	//Need to build param structure for library code
	FlatMethod fm=state.getMethodFlat(md);
	generateTempStructs(fm, null);
	generateMethodParam(cn, md, null, output);
      }
    }
  }


  protected void generateMethodParam(ClassDescriptor cn, MethodDescriptor md, LocalityBinding lb, PrintWriter output) {
    /* Output parameter structure */
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      ParamsObject objectparams=(ParamsObject) paramstable.get(lb!=null ? lb : md);
      if ((state.DSM||state.SINGLETM)&&lb!=null)
	output.println("struct "+cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params {");
      else
	output.println("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params {");
      output.println("  int size;");
      output.println("  void * next;");
      for(int i=0; i<objectparams.numPointers(); i++) {
	TempDescriptor temp=objectparams.getPointer(i);
	if(state.MGC && temp.getType().isClass() && temp.getType().getClassDesc().isEnum()) {
	  output.println("  int " + temp.getSafeSymbol() + ";");
	} else {
	  output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
	}
      }
      output.println("};\n");
    }
  }

  protected void generateMethod(ClassDescriptor cn, MethodDescriptor md, LocalityBinding lb, PrintWriter headersout, PrintWriter output) {
    FlatMethod fm=state.getMethodFlat(md);
    generateTempStructs(fm, lb);

    ParamsObject objectparams=(ParamsObject) paramstable.get(lb);
    TempObject objecttemps=(TempObject) tempstable.get(lb);

    generateMethodParam(cn, md, lb, output);

    /* Output temp structure */
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      output.println("struct "+cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_locals {");
      output.println("  int size;");
      output.println("  void * next;");
      for(int i=0; i<objecttemps.numPointers(); i++) {
	TempDescriptor temp=objecttemps.getPointer(i);
	if (temp.getType().isNull())
	  output.println("  void * "+temp.getSafeSymbol()+";");
	else
	  output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
      }
      output.println("};\n");
    }

    /********* Output method declaration ***********/
    headersout.println("#define D"+cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+" 1");

    /* First the return type */
    if (md.getReturnType()!=null) {
      if(state.MGC && md.getReturnType().isClass() && md.getReturnType().getClassDesc().isEnum()) {
	headersout.println("  int ");
      } else if (md.getReturnType().isClass()||md.getReturnType().isArray())
	headersout.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
      else
	headersout.print(md.getReturnType().getSafeSymbol()+" ");
    } else
      //catch the constructor case
      headersout.print("void ");

    /* Next the method name */
    headersout.print(cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");

    boolean printcomma=false;
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      headersout.print("struct "+cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * "+paramsprefix);
      printcomma=true;
    }

    /*  Output parameter list*/
    for(int i=0; i<objectparams.numPrimitives(); i++) {
      TempDescriptor temp=objectparams.getPrimitive(i);
      if (printcomma)
	headersout.print(", ");
      printcomma=true;
      if(state.MGC && temp.getType().isClass() && temp.getType().getClassDesc().isEnum()) {
	headersout.print("int " + temp.getSafeSymbol());
      } else if (temp.getType().isClass()||temp.getType().isArray())
	headersout.print("struct " + temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol());
      else
	headersout.print(temp.getType().getSafeSymbol()+" "+temp.getSafeSymbol());
    }
    headersout.println(");\n");
  }

  /***** Generate code for FlatMethod fm. *****/

  Hashtable<FlatAtomicEnterNode, AtomicRecord> atomicmethodmap;
  static int atomicmethodcount=0;


  BranchAnalysis branchanalysis;
  protected void generateFlatMethod(FlatMethod fm, LocalityBinding lb, PrintWriter output) {
    if (State.PRINTFLAT)
      System.out.println(fm.printMethod());
    MethodDescriptor md=fm.getMethod();
    TaskDescriptor task=fm.getTask();
    ClassDescriptor cn=md!=null ? md.getClassDesc() : null;
    ParamsObject objectparams=(ParamsObject)paramstable.get(lb!=null ? lb : md!=null ? md : task);

    HashSet<AtomicRecord> arset=null;
    branchanalysis=null;

    if (state.DELAYCOMP&&!lb.isAtomic()&&lb.getHasAtomic()) {
      //create map
      if (atomicmethodmap==null)
	atomicmethodmap=new Hashtable<FlatAtomicEnterNode, AtomicRecord>();

      //fix these so we get right strings for local variables
      localsprefixaddr=localsprefix;
      localsprefixderef=localsprefix+"->";
      arset=new HashSet<AtomicRecord>();

      //build branchanalysis
      branchanalysis=new BranchAnalysis(locality, lb, delaycomp.getNotReady(lb), delaycomp.livecode(lb), state);

      //Generate commit methods here
      for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator(); fnit.hasNext(); ) {
	FlatNode fn=fnit.next();
	if (fn.kind()==FKind.FlatAtomicEnterNode&&
	    locality.getAtomic(lb).get(fn.getPrev(0)).intValue()==0&&
	    delaycomp.needsFission(lb, (FlatAtomicEnterNode) fn)) {
	  //We have an atomic enter
	  FlatAtomicEnterNode faen=(FlatAtomicEnterNode) fn;
	  Set<FlatNode> exitset=faen.getExits();
	  //generate header
	  String methodname=md.getSymbol()+(atomicmethodcount++);
	  AtomicRecord ar=new AtomicRecord();
	  ar.name=methodname;
	  arset.add(ar);

	  atomicmethodmap.put(faen, ar);

	  //build data structure declaration
	  output.println("struct atomicprimitives_"+methodname+" {");

	  Set<FlatNode> recordset=delaycomp.livecode(lb);
	  Set<TempDescriptor> liveinto=delaycomp.liveinto(lb, faen, recordset);
	  Set<TempDescriptor> liveout=delaycomp.liveout(lb, faen);
	  Set<TempDescriptor> liveoutvirtualread=delaycomp.liveoutvirtualread(lb, faen);
	  ar.livein=liveinto;
	  ar.reallivein=new HashSet(liveinto);
	  ar.liveout=liveout;
	  ar.liveoutvirtualread=liveoutvirtualread;


	  for(Iterator<TempDescriptor> it=liveinto.iterator(); it.hasNext(); ) {
	    TempDescriptor tmp=it.next();
	    //remove the pointers
	    if (tmp.getType().isPtr()) {
	      it.remove();
	    } else {
	      //let's print it here
	      output.println(tmp.getType().getSafeSymbol()+" "+tmp.getSafeSymbol()+";");
	    }
	  }
	  for(Iterator<TempDescriptor> it=liveout.iterator(); it.hasNext(); ) {
	    TempDescriptor tmp=it.next();
	    //remove the pointers
	    if (tmp.getType().isPtr()) {
	      it.remove();
	    } else if (!liveinto.contains(tmp)) {
	      //let's print it here
	      output.println(tmp.getType().getSafeSymbol()+" "+tmp.getSafeSymbol()+";");
	    }
	  }
	  output.println("};");

	  //print out method name
	  output.println("void "+methodname+"(struct "+ cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * "+paramsprefix+", struct "+ cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_locals *"+localsprefix+", struct atomicprimitives_"+methodname+" * primitives) {");
	  //build code for commit method

	  //first define local primitives
	  Set<TempDescriptor> alltemps=delaycomp.alltemps(lb, faen, recordset);
	  for(Iterator<TempDescriptor> tmpit=alltemps.iterator(); tmpit.hasNext(); ) {
	    TempDescriptor tmp=tmpit.next();
	    if (!tmp.getType().isPtr()) {
	      if (liveinto.contains(tmp)||liveoutvirtualread.contains(tmp)) {
		//read from live into set
		output.println(tmp.getType().getSafeSymbol()+" "+tmp.getSafeSymbol()+"=primitives->"+tmp.getSafeSymbol()+";");
	      } else {
		//just define
		output.println(tmp.getType().getSafeSymbol()+" "+tmp.getSafeSymbol()+";");
	      }
	    }
	  }
	  //turn off write barrier generation
	  wb.turnoff();
	  state.SINGLETM=false;
	  generateCode(faen, fm, lb, exitset, output, false);
	  state.SINGLETM=true;
	  //turn on write barrier generation
	  wb.turnon();
	  output.println("}\n\n");
	}
      }
    }
    //redefine these back to normal

    localsprefixaddr="&"+localsprefix;
    localsprefixderef=localsprefix+".";

    generateHeader(fm, lb, md!=null ? md : task,output);
    TempObject objecttemp=(TempObject) tempstable.get(lb!=null ? lb : md!=null ? md : task);

    if (state.DELAYCOMP&&!lb.isAtomic()&&lb.getHasAtomic()) {
      for(Iterator<AtomicRecord> arit=arset.iterator(); arit.hasNext(); ) {
	AtomicRecord ar=arit.next();
	output.println("struct atomicprimitives_"+ar.name+" primitives_"+ar.name+";");
      }
    }

    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      if (md!=null&&(state.DSM||state.SINGLETM))
	output.print("   struct "+cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_locals "+localsprefix+"={");
      else if (md!=null&&!(state.DSM||state.SINGLETM))
	output.print("   struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_locals "+localsprefix+"={");
      else
	output.print("   struct "+task.getSafeSymbol()+"_locals "+localsprefix+"={");
      output.print(objecttemp.numPointers()+",");
      output.print(paramsprefix);
      for(int j=0; j<objecttemp.numPointers(); j++)
	output.print(", NULL");
      output.println("};");
    }

    for(int i=0; i<objecttemp.numPrimitives(); i++) {
      TempDescriptor td=objecttemp.getPrimitive(i);
      TypeDescriptor type=td.getType();
      if (type.isNull() && !type.isArray())
	output.println("   void * "+td.getSafeSymbol()+";");
      else if (state.MGC && type.isClass() && type.getClassDesc().isEnum()) {
	output.println("   int " + td.getSafeSymbol() + ";");
      } else if (type.isClass()||type.isArray())
	output.println("   struct "+type.getSafeSymbol()+" * "+td.getSafeSymbol()+";");
      else
	output.println("   "+type.getSafeSymbol()+" "+td.getSafeSymbol()+";");
    }



    additionalCodeAtTopFlatMethodBody(output, fm);



    /* Check to see if we need to do a GC if this is a
     * multi-threaded program...*/

    if (((state.THREAD||state.DSM||state.SINGLETM)&&GENERATEPRECISEGC)) {
      //Don't bother if we aren't in recursive methods...The loops case will catch it
      if (callgraph.getAllMethods(md).contains(md)) {
	if (state.DSM&&lb.isAtomic())
	  output.println("if (needtocollect) checkcollect2("+localsprefixaddr+");");
	else {
	  output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
	}
      }
    }
    generateCode(fm.getNext(0), fm, lb, null, output, true);
    output.println("}\n\n");
  }



  protected void generateCode(FlatNode first,
                              FlatMethod fm,
                              LocalityBinding lb,
                              Set<FlatNode> stopset,
                              PrintWriter output,
                              boolean firstpass) {

    /* Assign labels to FlatNode's if necessary.*/

    Hashtable<FlatNode, Integer> nodetolabel;
    currlb=lb;
    if (state.DELAYCOMP&&!firstpass)
      nodetolabel=dcassignLabels(first, stopset);
    else
      nodetolabel=assignLabels(first, stopset);

    Set<FlatNode> storeset=null;
    HashSet<FlatNode> genset=null;
    HashSet<FlatNode> refset=null;
    Set<FlatNode> unionset=null;

    if (state.DELAYCOMP&&!lb.isAtomic()&&lb.getHasAtomic()) {
      storeset=delaycomp.livecode(lb);
      genset=new HashSet<FlatNode>();
      if (state.STMARRAY&&!state.DUALVIEW) {
	refset=new HashSet<FlatNode>();
	refset.addAll(delaycomp.getDeref(lb));
	refset.removeAll(delaycomp.getCannotDelay(lb));
	refset.removeAll(delaycomp.getOther(lb));
      }
      if (firstpass) {
	genset.addAll(delaycomp.getCannotDelay(lb));
	genset.addAll(delaycomp.getOther(lb));
      } else {
	genset.addAll(delaycomp.getNotReady(lb));
	if (state.STMARRAY&&!state.DUALVIEW) {
	  genset.removeAll(refset);
	}
      }
      unionset=new HashSet<FlatNode>();
      unionset.addAll(storeset);
      unionset.addAll(genset);
      if (state.STMARRAY&&!state.DUALVIEW)
	unionset.addAll(refset);
    }

    /* Do the actual code generation */
    FlatNode current_node=null;
    HashSet tovisit=new HashSet();
    HashSet visited=new HashSet();
    if (!firstpass)
      tovisit.add(first.getNext(0));
    else
      tovisit.add(first);
    while(current_node!=null||!tovisit.isEmpty()) {
      if (current_node==null) {
	current_node=(FlatNode)tovisit.iterator().next();
	tovisit.remove(current_node);
      } else if (tovisit.contains(current_node)) {
	tovisit.remove(current_node);
      }
      visited.add(current_node);
      if (nodetolabel.containsKey(current_node)) {
	output.println("L"+nodetolabel.get(current_node)+":");
      }
      if (state.INSTRUCTIONFAILURE) {
	if (state.THREAD||state.DSM||state.SINGLETM) {
	  output.println("if ((++instructioncount)>failurecount) {instructioncount=0;injectinstructionfailure();}");
	} else
	  output.println("if ((--instructioncount)==0) injectinstructionfailure();");
      }
      if (current_node.numNext()==0||stopset!=null&&stopset.contains(current_node)) {
	output.print("   ");
	if (!state.DELAYCOMP||firstpass) {
	  generateFlatNode(fm, current_node, output);
	} else {
	  //store primitive variables in out set
	  AtomicRecord ar=atomicmethodmap.get((FlatAtomicEnterNode)first);
	  Set<TempDescriptor> liveout=ar.liveout;
	  for(Iterator<TempDescriptor> tmpit=liveout.iterator(); tmpit.hasNext(); ) {
	    TempDescriptor tmp=tmpit.next();
	    output.println("primitives->"+tmp.getSafeSymbol()+"="+tmp.getSafeSymbol()+";");
	  }
	}

	if (current_node.kind()!=FKind.FlatReturnNode) {
	  if(state.MGC) {
	    // TODO add version for normal Java later
	    if((fm.getMethod() != null) && (fm.getMethod().isStaticBlock())) {
	      // a static block, check if it has been executed
	      output.println("  global_defs_p->" + fm.getMethod().getClassDesc().getSafeSymbol()+"static_block_exe_flag = 1;");
	      output.println("");
	    }
	  }
	  output.println("   return;");
	}
	current_node=null;
      } else if(current_node.numNext()==1) {
	FlatNode nextnode;
	if (state.DELAYCOMP) {
	  boolean specialprimitive=false;
	  //skip literals...no need to add extra overhead
	  if (storeset!=null&&storeset.contains(current_node)&&current_node.kind()==FKind.FlatLiteralNode) {
	    TypeDescriptor typedesc=((FlatLiteralNode)current_node).getType();
	    if (!typedesc.isClass()&&!typedesc.isArray()) {
	      specialprimitive=true;
	    }
	  }

	  if (genset==null||genset.contains(current_node)||specialprimitive) {
	    generateFlatNode(fm, current_node, output);
	  }
	  if (state.STMARRAY&&!state.DUALVIEW&&refset!=null&&refset.contains(current_node)) {
	    //need to acquire lock
	    handleArrayDeref(fm, lb, current_node, output, firstpass);
	  }
	  if (storeset!=null&&storeset.contains(current_node)&&!specialprimitive) {
	    TempDescriptor wrtmp=current_node.writesTemps()[0];
	    if (firstpass) {
	      //need to store value written by previous node
	      if (wrtmp.getType().isPtr()) {
		//only lock the objects that may actually need locking
		if (recorddc.getNeedTrans(lb, current_node)&&
		    (!state.STMARRAY||state.DUALVIEW||!wrtmp.getType().isArray()||
		     wrtmp.getType().getSymbol().equals(TypeUtil.ObjectClass))) {
		  output.println("STOREPTR("+generateTemp(fm, wrtmp)+");/* "+current_node.nodeid+" */");
		} else {
		  output.println("STOREPTRNOLOCK("+generateTemp(fm, wrtmp)+");/* "+current_node.nodeid+" */");
		}
	      } else {
		output.println("STORE"+wrtmp.getType().getSafeDescriptor()+"("+generateTemp(fm, wrtmp)+");/* "+current_node.nodeid+" */");
	      }
	    } else {
	      //need to read value read by previous node
	      if (wrtmp.getType().isPtr()) {
		output.println("RESTOREPTR("+generateTemp(fm, wrtmp)+");/* "+current_node.nodeid+" */");
	      } else {
		output.println("RESTORE"+wrtmp.getType().getSafeDescriptor()+"("+generateTemp(fm, wrtmp)+"); /* "+current_node.nodeid+" */");
	      }
	    }
	  }
	  nextnode=current_node.getNext(0);
	} else {
	  output.print("   ");
	  generateFlatNode(fm, current_node, output);
	  nextnode=current_node.getNext(0);
	}
	if (visited.contains(nextnode)) {
	  output.println("goto L"+nodetolabel.get(nextnode)+";");
	  current_node=null;
	} else
	  current_node=nextnode;
      } else if (current_node.numNext()==2) {
	/* Branch */
	if (state.DELAYCOMP) {
	  boolean computeside=false;
	  if (firstpass) {
	    //need to record which way it should go
	    if (genset==null||genset.contains(current_node)) {
	      if (storeset!=null&&storeset.contains(current_node)) {
		//need to store which way branch goes
		generateStoreFlatCondBranch(fm, lb, (FlatCondBranch)current_node, "L"+nodetolabel.get(current_node.getNext(1)), output);
	      } else
		generateFlatCondBranch(fm, (FlatCondBranch)current_node, "L"+nodetolabel.get(current_node.getNext(1)), output);
	    } else {
	      //which side to execute
	      computeside=true;
	    }
	  } else {
	    if (genset.contains(current_node)) {
	      generateFlatCondBranch(fm, (FlatCondBranch)current_node, "L"+nodetolabel.get(current_node.getNext(1)), output);
	    } else if (storeset.contains(current_node)) {
	      //need to do branch
	      branchanalysis.generateGroupCode(current_node, output, nodetolabel);
	    } else {
	      //which side to execute
	      computeside=true;
	    }
	  }
	  if (computeside) {
	    Set<FlatNode> leftset=DelayComputation.getNext(current_node, 0, unionset, lb,locality, true);
	    int branch=0;
	    if (leftset.size()==0)
	      branch=1;
	    if (visited.contains(current_node.getNext(branch))) {
	      //already visited -- build jump
	      output.println("goto L"+nodetolabel.get(current_node.getNext(branch))+";");
	      current_node=null;
	    } else {
	      current_node=current_node.getNext(branch);
	    }
	  } else {
	    if (!visited.contains(current_node.getNext(1)))
	      tovisit.add(current_node.getNext(1));
	    if (visited.contains(current_node.getNext(0))) {
	      output.println("goto L"+nodetolabel.get(current_node.getNext(0))+";");
	      current_node=null;
	    } else
	      current_node=current_node.getNext(0);
	  }
	} else {
	  output.print("   ");
	  generateFlatCondBranch(fm, (FlatCondBranch)current_node, "L"+nodetolabel.get(current_node.getNext(1)), output);
	  if (!visited.contains(current_node.getNext(1)))
	    tovisit.add(current_node.getNext(1));
	  if (visited.contains(current_node.getNext(0))) {
	    output.println("goto L"+nodetolabel.get(current_node.getNext(0))+";");
	    current_node=null;
	  } else
	    current_node=current_node.getNext(0);
	}
      } else throw new Error();
    }
  }

  protected void handleArrayDeref(FlatMethod fm, LocalityBinding lb, FlatNode fn, PrintWriter output, boolean firstpass) {
    if (fn.kind()==FKind.FlatSetElementNode) {
      FlatSetElementNode fsen=(FlatSetElementNode) fn;
      String dst=generateTemp(fm, fsen.getDst());
      String src=generateTemp(fm, fsen.getSrc());
      String index=generateTemp(fm, fsen.getIndex());
      TypeDescriptor elementtype=fsen.getDst().getType().dereference();
      String type="";
      if (elementtype.isArray()||elementtype.isClass())
	type="void *";
      else
	type=elementtype.getSafeSymbol()+" ";
      if (firstpass) {
	output.println("STOREARRAY("+dst+","+index+","+type+")");
      } else {
	output.println("{");
	output.println("  struct ArrayObject *array;");
	output.println("  int index;");
	output.println("  RESTOREARRAY(array,index);");
	output.println("  (("+type+"*)(((char *)&array->___length___)+sizeof(int)))[index]="+src+";");
	output.println("}");
      }
    } else if (fn.kind()==FKind.FlatElementNode) {
      FlatElementNode fen=(FlatElementNode) fn;
      String src=generateTemp(fm, fen.getSrc());
      String index=generateTemp(fm, fen.getIndex());
      TypeDescriptor elementtype=fen.getSrc().getType().dereference();
      String dst=generateTemp(fm, fen.getDst());
      String type="";
      if (elementtype.isArray()||elementtype.isClass())
	type="void *";
      else
	type=elementtype.getSafeSymbol()+" ";
      if (firstpass) {
	output.println("STOREARRAY("+src+","+index+","+type+")");
      } else {
	output.println("{");
	output.println("  struct ArrayObject *array;");
	output.println("  int index;");
	output.println("  RESTOREARRAY(array,index);");
	output.println("  "+dst+"=(("+type+"*)(((char *)&array->___length___)+sizeof(int)))[index];");
	output.println("}");
      }
    }
  }
  /** Special label assignment for delaycomputation */
  protected Hashtable<FlatNode, Integer> dcassignLabels(FlatNode first, Set<FlatNode> lastset) {
    HashSet tovisit=new HashSet();
    HashSet visited=new HashSet();
    int labelindex=0;
    Hashtable<FlatNode, Integer> nodetolabel=new Hashtable<FlatNode, Integer>();

    //Label targets of branches
    Set<FlatNode> targets=branchanalysis.getTargets();
    for(Iterator<FlatNode> it=targets.iterator(); it.hasNext(); ) {
      nodetolabel.put(it.next(), new Integer(labelindex++));
    }


    tovisit.add(first);
    /*Assign labels first.  A node needs a label if the previous
     * node has two exits or this node is a join point. */

    while(!tovisit.isEmpty()) {
      FlatNode fn=(FlatNode)tovisit.iterator().next();
      tovisit.remove(fn);
      visited.add(fn);


      if(lastset!=null&&lastset.contains(fn)) {
	// if last is not null and matches, don't go
	// any further for assigning labels
	continue;
      }

      for(int i=0; i<fn.numNext(); i++) {
	FlatNode nn=fn.getNext(i);

	if(i>0) {
	  //1) Edge >1 of node
	  nodetolabel.put(nn,new Integer(labelindex++));
	}
	if (!visited.contains(nn)&&!tovisit.contains(nn)) {
	  tovisit.add(nn);
	} else {
	  //2) Join point
	  nodetolabel.put(nn,new Integer(labelindex++));
	}
      }
    }
    return nodetolabel;

  }

  /** Generate text string that corresponds to the TempDescriptor td. */
  protected String generateTemp(FlatMethod fm, TempDescriptor td) {
    MethodDescriptor md=fm.getMethod();
    TaskDescriptor task=fm.getTask();
    TempObject objecttemps=(TempObject) tempstable.get(currlb!=null ? currlb : md!=null ? md : task);

    if (objecttemps.isLocalPrim(td)||objecttemps.isParamPrim(td)) {
      return td.getSafeSymbol();
    }

    if (objecttemps.isLocalPtr(td)) {
      return localsprefixderef+td.getSafeSymbol();
    }

    if (objecttemps.isParamPtr(td)) {
      return paramsprefix+"->"+td.getSafeSymbol();
    }

    throw new Error();
  }

  public void generateFlatBackEdge(FlatMethod fm, FlatBackEdge fn, PrintWriter output) {
    if (state.SINGLETM&&state.SANDBOX&&(locality.getAtomic(currlb).get(fn).intValue()>0)) {
      output.println("if (unlikely((--transaction_check_counter)<=0)) checkObjects();");
    }
    if(state.DSM&&state.SANDBOX&&(locality.getAtomic(currlb).get(fn).intValue()>0)) {
      output.println("if (unlikely((--transaction_check_counter)<=0)) checkObjects();");
    }
    if (((state.THREAD||state.DSM||state.SINGLETM)&&GENERATEPRECISEGC)) {
      if(state.DSM&&locality.getAtomic(currlb).get(fn).intValue()>0) {
	output.println("if (needtocollect) checkcollect2("+localsprefixaddr+");");
      } else {
	output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
      }
    } else
      output.println("/* nop */");
  }


  public void generateFlatPrefetchNode(FlatMethod fm, FlatPrefetchNode fpn, PrintWriter output) {
    if (state.PREFETCH) {
      Vector oids = new Vector();
      Vector fieldoffset = new Vector();
      Vector endoffset = new Vector();
      int tuplecount = 0;        //Keeps track of number of prefetch tuples that need to be generated
      for(Iterator it = fpn.hspp.iterator(); it.hasNext(); ) {
	PrefetchPair pp = (PrefetchPair) it.next();
	Integer statusbase = locality.getNodePreTempInfo(currlb,fpn).get(pp.base);
	/* Find prefetches that can generate oid */
	if(statusbase == LocalityAnalysis.GLOBAL) {
	  generateTransCode(fm, currlb, pp, oids, fieldoffset, endoffset, tuplecount, locality.getAtomic(currlb).get(fpn).intValue()>0, false);
	  tuplecount++;
	} else if (statusbase == LocalityAnalysis.LOCAL) {
	  generateTransCode(fm,currlb,pp,oids,fieldoffset,endoffset,tuplecount,false,true);
	} else {
	  continue;
	}
      }
      if (tuplecount==0)
	return;
      System.out.println("Adding prefetch "+fpn+ " to method:" +fm);
      output.println("{");
      output.println("/* prefetch */");
      output.println("/* prefetchid_" + fpn.siteid + " */");
      output.println("void * prefptr;");
      output.println("int tmpindex;");

      output.println("if((evalPrefetch["+fpn.siteid+"].operMode) || (evalPrefetch["+fpn.siteid+"].retrycount <= 0)) {");
      /*Create C code for oid array */
      output.print("   unsigned int oidarray_[] = {");
      boolean needcomma=false;
      for (Iterator it = oids.iterator(); it.hasNext(); ) {
	if (needcomma)
	  output.print(", ");
	output.print(it.next());
	needcomma=true;
      }
      output.println("};");

      /*Create C code for endoffset values */
      output.print("   unsigned short endoffsetarry_[] = {");
      needcomma=false;
      for (Iterator it = endoffset.iterator(); it.hasNext(); ) {
	if (needcomma)
	  output.print(", ");
	output.print(it.next());
	needcomma=true;
      }
      output.println("};");

      /*Create C code for Field Offset Values */
      output.print("   short fieldarry_[] = {");
      needcomma=false;
      for (Iterator it = fieldoffset.iterator(); it.hasNext(); ) {
	if (needcomma)
	  output.print(", ");
	output.print(it.next());
	needcomma=true;
      }
      output.println("};");
      /* make the prefetch call to Runtime */
      output.println("   if(!evalPrefetch["+fpn.siteid+"].operMode) {");
      output.println("     evalPrefetch["+fpn.siteid+"].retrycount = RETRYINTERVAL;");
      output.println("   }");
      output.println("   prefetch("+fpn.siteid+" ,"+tuplecount+", oidarray_, endoffsetarry_, fieldarry_);");
      output.println(" } else {");
      output.println("   evalPrefetch["+fpn.siteid+"].retrycount--;");
      output.println(" }");
      output.println("}");
    }
  }

  public void generateTransCode(FlatMethod fm, LocalityBinding lb,PrefetchPair pp, Vector oids, Vector fieldoffset, Vector endoffset, int tuplecount, boolean inside, boolean localbase) {
    short offsetcount = 0;
    int breakindex=0;
    if (inside) {
      breakindex=1;
    } else if (localbase) {
      for(; breakindex<pp.desc.size(); breakindex++) {
	Descriptor desc=pp.getDescAt(breakindex);
	if (desc instanceof FieldDescriptor) {
	  FieldDescriptor fd=(FieldDescriptor)desc;
	  if (fd.isGlobal()) {
	    break;
	  }
	}
      }
      breakindex++;
    }

    if (breakindex>pp.desc.size())     //all local
      return;

    TypeDescriptor lasttype=pp.base.getType();
    String basestr=generateTemp(fm, pp.base);
    String teststr="";
    boolean maybenull=fm.getMethod().isStatic()||
                       !pp.base.equals(fm.getParameter(0));

    for(int i=0; i<breakindex; i++) {
      String indexcheck="";

      Descriptor desc=pp.getDescAt(i);
      if (desc instanceof FieldDescriptor) {
	FieldDescriptor fd=(FieldDescriptor)desc;
	if (maybenull) {
	  if (!teststr.equals(""))
	    teststr+="&&";
	  teststr+="((prefptr="+basestr+")!=NULL)";
	  basestr="((struct "+lasttype.getSafeSymbol()+" *)prefptr)->"+
	           fd.getSafeSymbol();
	} else {
	  basestr=basestr+"->"+
	           fd.getSafeSymbol();
	  maybenull=true;
	}
	lasttype=fd.getType();
      } else {
	IndexDescriptor id=(IndexDescriptor)desc;
	indexcheck="((tmpindex=";
	for(int j=0; j<id.tddesc.size(); j++) {
	  indexcheck+=generateTemp(fm, id.getTempDescAt(j))+"+";
	}
	indexcheck+=id.offset+")>=0)&(tmpindex<((struct ArrayObject *)prefptr)->___length___)";

	if (!teststr.equals(""))
	  teststr+="&&";
	teststr+="((prefptr="+basestr+")!= NULL) &&"+indexcheck;
	basestr="((void **)(((char *) &(((struct ArrayObject *)prefptr)->___length___))+sizeof(int)))[tmpindex]";
	maybenull=true;
	lasttype=lasttype.dereference();
      }
    }

    String oid;
    if (teststr.equals("")) {
      oid="((unsigned int)"+basestr+")";
    } else {
      oid="((unsigned int)(("+teststr+")?"+basestr+":NULL))";
    }
    oids.add(oid);

    for(int i = breakindex; i < pp.desc.size(); i++) {
      String newfieldoffset;
      Object desc = pp.getDescAt(i);
      if(desc instanceof FieldDescriptor) {
	FieldDescriptor fd=(FieldDescriptor)desc;
	newfieldoffset = new String("(unsigned int)(&(((struct "+ lasttype.getSafeSymbol()+" *)0)->"+ fd.getSafeSymbol()+ "))");
	lasttype=fd.getType();
      } else {
	newfieldoffset = "";
	IndexDescriptor id=(IndexDescriptor)desc;
	for(int j = 0; j < id.tddesc.size(); j++) {
	  newfieldoffset += generateTemp(fm, id.getTempDescAt(j)) + "+";
	}
	newfieldoffset += id.offset.toString();
	lasttype=lasttype.dereference();
      }
      fieldoffset.add(newfieldoffset);
    }

    int base=(tuplecount>0) ? ((Short)endoffset.get(tuplecount-1)).intValue() : 0;
    base+=pp.desc.size()-breakindex;
    endoffset.add(new Short((short)base));
  }



  public void generateFlatGlobalConvNode(FlatMethod fm, FlatGlobalConvNode fgcn, PrintWriter output) {
    if (currlb!=fgcn.getLocality())
      return;
    /* Have to generate flat globalconv */
    if (fgcn.getMakePtr()) {
      if (state.DSM) {
	output.println("TRANSREAD("+generateTemp(fm, fgcn.getSrc())+", (unsigned int) "+generateTemp(fm, fgcn.getSrc())+");");
      } else {
	if ((dc==null)||!state.READSET&&dc.getNeedTrans(currlb, fgcn)||state.READSET&&dc.getNeedWriteTrans(currlb, fgcn)) {
	  //need to do translation
	  output.println("TRANSREAD("+generateTemp(fm, fgcn.getSrc())+", "+generateTemp(fm, fgcn.getSrc())+", (void *)("+localsprefixaddr+"));");
	} else if (state.READSET&&dc.getNeedTrans(currlb, fgcn)) {
	  if (state.HYBRID&&delaycomp.getConv(currlb).contains(fgcn)) {
	    output.println("TRANSREADRDFISSION("+generateTemp(fm, fgcn.getSrc())+", "+generateTemp(fm, fgcn.getSrc())+");");
	  } else
	    output.println("TRANSREADRD("+generateTemp(fm, fgcn.getSrc())+", "+generateTemp(fm, fgcn.getSrc())+");");
	}
      }
    } else {
      /* Need to convert to OID */
      if ((dc==null)||dc.getNeedSrcTrans(currlb,fgcn)) {
	if (fgcn.doConvert()||(delaycomp!=null&&delaycomp.needsFission(currlb, fgcn.getAtomicEnter())&&atomicmethodmap.get(fgcn.getAtomicEnter()).reallivein.contains(fgcn.getSrc()))) {
	  output.println(generateTemp(fm, fgcn.getSrc())+"=(void *)COMPOID("+generateTemp(fm, fgcn.getSrc())+");");
	} else {
	  output.println(generateTemp(fm, fgcn.getSrc())+"=NULL;");
	}
      }
    }
  }

  int sandboxcounter=0;
  public void generateFlatAtomicEnterNode(FlatMethod fm,  FlatAtomicEnterNode faen, PrintWriter output) {
    /* Check to see if we need to generate code for this atomic */
    if (locality==null) {
      if (GENERATEPRECISEGC) {
	output.println("if (pthread_mutex_trylock(&atomiclock)!=0) {");
	output.println("stopforgc((struct garbagelist *) &___locals___);");
	output.println("pthread_mutex_lock(&atomiclock);");
	output.println("restartaftergc();");
	output.println("}");
      } else {
	output.println("pthread_mutex_lock(&atomiclock);");
      }
      return;
    }

    if (locality.getAtomic(currlb).get(faen.getPrev(0)).intValue()>0)
      return;


    if (state.SANDBOX) {
      outsandbox.println("int atomiccounter"+sandboxcounter+"=LOW_CHECK_FREQUENCY;");
      output.println("counter_reset_pointer=&atomiccounter"+sandboxcounter+";");
    }

    if (state.DELAYCOMP&&delaycomp.needsFission(currlb, faen)) {
      AtomicRecord ar=atomicmethodmap.get(faen);
      //copy in
      for(Iterator<TempDescriptor> tmpit=ar.livein.iterator(); tmpit.hasNext(); ) {
	TempDescriptor tmp=tmpit.next();
	output.println("primitives_"+ar.name+"."+tmp.getSafeSymbol()+"="+tmp.getSafeSymbol()+";");
      }

      //copy outs that depend on path
      for(Iterator<TempDescriptor> tmpit=ar.liveoutvirtualread.iterator(); tmpit.hasNext(); ) {
	TempDescriptor tmp=tmpit.next();
	if (!ar.livein.contains(tmp))
	  output.println("primitives_"+ar.name+"."+tmp.getSafeSymbol()+"="+tmp.getSafeSymbol()+";");
      }
    }

    /* Backup the temps. */
    for(Iterator<TempDescriptor> tmpit=locality.getTemps(currlb).get(faen).iterator(); tmpit.hasNext(); ) {
      TempDescriptor tmp=tmpit.next();
      output.println(generateTemp(fm, backuptable.get(currlb).get(tmp))+"="+generateTemp(fm,tmp)+";");
    }

    output.println("goto transstart"+faen.getIdentifier()+";");

    /******* Print code to retry aborted transaction *******/
    output.println("transretry"+faen.getIdentifier()+":");

    /* Restore temps */
    for(Iterator<TempDescriptor> tmpit=locality.getTemps(currlb).get(faen).iterator(); tmpit.hasNext(); ) {
      TempDescriptor tmp=tmpit.next();
      output.println(generateTemp(fm, tmp)+"="+generateTemp(fm,backuptable.get(currlb).get(tmp))+";");
    }

    if (state.DSM) {
      /********* Need to revert local object store ********/
      String revertptr=generateTemp(fm, reverttable.get(currlb));

      output.println("while ("+revertptr+") {");
      output.println("struct ___Object___ * tmpptr;");
      output.println("tmpptr="+revertptr+"->"+nextobjstr+";");
      output.println("REVERT_OBJ("+revertptr+");");
      output.println(revertptr+"=tmpptr;");
      output.println("}");
    }
    /******* Tell the runtime to start the transaction *******/

    output.println("transstart"+faen.getIdentifier()+":");
    if (state.SANDBOX) {
      output.println("transaction_check_counter=*counter_reset_pointer;");
      sandboxcounter++;
    }
    output.println("transStart();");

    if (state.ABORTREADERS||state.SANDBOX) {
      if (state.SANDBOX)
	output.println("abortenabled=1;");
      output.println("if (_setjmp(aborttrans)) {");
      output.println("  goto transretry"+faen.getIdentifier()+"; }");
    }
  }

  public void generateFlatAtomicExitNode(FlatMethod fm, FlatAtomicExitNode faen, PrintWriter output) {
    /* Check to see if we need to generate code for this atomic */
    if (locality==null) {
      output.println("pthread_mutex_unlock(&atomiclock);");
      return;
    }
    if (locality.getAtomic(currlb).get(faen).intValue()>0)
      return;
    //store the revert list before we lose the transaction object

    if (state.DSM) {
      String revertptr=generateTemp(fm, reverttable.get(currlb));
      output.println(revertptr+"=revertlist;");
      output.println("if (transCommit()) {");
      output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
      output.println("goto transretry"+faen.getAtomicEnter().getIdentifier()+";");
      output.println("} else {");
      /* Need to commit local object store */
      output.println("while ("+revertptr+") {");
      output.println("struct ___Object___ * tmpptr;");
      output.println("tmpptr="+revertptr+"->"+nextobjstr+";");
      output.println("COMMIT_OBJ("+revertptr+");");
      output.println(revertptr+"=tmpptr;");
      output.println("}");
      output.println("}");
      return;
    }

    if (!state.DELAYCOMP) {
      //Normal STM stuff
      output.println("if (transCommit()) {");
      /* Transaction aborts if it returns true */
      output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
      output.println("goto transretry"+faen.getAtomicEnter().getIdentifier()+";");
      output.println("}");
    } else {
      if (delaycomp.optimizeTrans(currlb, faen.getAtomicEnter())&&(!state.STMARRAY||state.DUALVIEW)) {
	AtomicRecord ar=atomicmethodmap.get(faen.getAtomicEnter());
	output.println("LIGHTWEIGHTCOMMIT("+ar.name+", &primitives_"+ar.name+", &"+localsprefix+", "+paramsprefix+", transretry"+faen.getAtomicEnter().getIdentifier()+");");
	//copy out
	for(Iterator<TempDescriptor> tmpit=ar.liveout.iterator(); tmpit.hasNext(); ) {
	  TempDescriptor tmp=tmpit.next();
	  output.println(tmp.getSafeSymbol()+"=primitives_"+ar.name+"."+tmp.getSafeSymbol()+";");
	}
      } else if (delaycomp.needsFission(currlb, faen.getAtomicEnter())) {
	AtomicRecord ar=atomicmethodmap.get(faen.getAtomicEnter());
	//do call
	output.println("if (transCommit((void (*)(void *, void *, void *))&"+ar.name+", &primitives_"+ar.name+", &"+localsprefix+", "+paramsprefix+")) {");
	output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
	output.println("goto transretry"+faen.getAtomicEnter().getIdentifier()+";");
	output.println("}");
	//copy out
	output.println("else {");
	for(Iterator<TempDescriptor> tmpit=ar.liveout.iterator(); tmpit.hasNext(); ) {
	  TempDescriptor tmp=tmpit.next();
	  output.println(tmp.getSafeSymbol()+"=primitives_"+ar.name+"."+tmp.getSafeSymbol()+";");
	}
	output.println("}");
      } else {
	output.println("if (transCommit(NULL, NULL, NULL, NULL)) {");
	output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
	output.println("goto transretry"+faen.getAtomicEnter().getIdentifier()+";");
	output.println("}");
      }
    }
  }

  protected void generateFlatCall(FlatMethod fm, FlatCall fc, PrintWriter output) {
    MethodDescriptor md=fc.getMethod();
    ParamsObject objectparams=(ParamsObject)paramstable.get(currlb!=null ? locality.getBinding(currlb, fc) : md);
    ClassDescriptor cn=md.getClassDesc();

    // if the called method is a static block or a static method or a constructor
    // need to check if it can be invoked inside some static block
    if(state.MGC) {
      // TODO add version for normal Java later
      if((md.isStatic() || md.isStaticBlock() || md.isConstructor()) &&
         ((fm.getMethod().isStaticBlock()) || (fm.getMethod().isInvokedByStatic()))) {
	if(!md.isInvokedByStatic()) {
	  System.err.println("Error: a method that is invoked inside a static block is not tagged!");
	}
	// is a static block or is invoked in some static block
	ClassDescriptor cd = fm.getMethod().getClassDesc();
	if(cd == cn) {
	  // the same class, do nothing
	  // TODO may want to invoke static field initialization here
	} else {
	  if((cn.getNumStaticFields() != 0) || (cn.getNumStaticBlocks() != 0)) {
	    // need to check if the class' static fields have been initialized and/or
	    // its static blocks have been executed
	    output.println("#ifdef MGC_STATIC_INIT_CHECK");
	    output.println("if(global_defs_p->" + cn.getSafeSymbol()+"static_block_exe_flag == 0) {");
	    if(cn.getNumStaticBlocks() != 0) {
	      MethodDescriptor t_md = (MethodDescriptor)cn.getMethodTable().get("staticblocks");
	      output.println("  "+cn.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"();");
	    } else {
	      output.println("  global_defs_p->" + cn.getSafeSymbol()+"static_block_exe_flag = 1;");
	    }
	    output.println("}");
	    output.println("#endif // MGC_STATIC_INIT_CHECK");
	  }
	}
      }
      if((md.getSymbol().equals("MonitorEnter") || md.getSymbol().equals("MonitorExit")) && fc.getThis().getSymbol().equals("classobj")) {
	// call MonitorEnter/MonitorExit on a class obj
	output.println("       " + cn.getSafeSymbol()+md.getSafeSymbol()+"_"
	               +md.getSafeMethodDescriptor() + "((struct ___Object___*)(&global_defs_p->"
	               + fc.getThis().getType().getClassDesc().getSafeSymbol() +"classobj));");
	return;
      }
    }

    output.println("{");
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      if (currlb!=null) {
	LocalityBinding fclb=locality.getBinding(currlb, fc);
	output.print("       struct "+cn.getSafeSymbol()+fclb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params __parameterlist__={");
      } else
	output.print("       struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params __parameterlist__={");
      output.print(objectparams.numPointers());
      output.print(", "+localsprefixaddr);
      if (md.getThis()!=null) {
	output.print(", ");
	output.print("(struct "+md.getThis().getType().getSafeSymbol() +" *)"+ generateTemp(fm,fc.getThis()));
      }
      if (fc.getThis()!=null&&md.getThis()==null) {
	System.out.println("WARNING!!!!!!!!!!!!");
	System.out.println("Source code calls static method "+md+" on an object in "+fm.getMethod()+"!");
      }


      for(int i=0; i<fc.numArgs(); i++) {
	Descriptor var=md.getParameter(i);
	TempDescriptor paramtemp=(TempDescriptor)temptovar.get(var);
	if (objectparams.isParamPtr(paramtemp)) {
	  TempDescriptor targ=fc.getArg(i);
	  output.print(", ");
	  TypeDescriptor td=md.getParamType(i);
	  if (td.isTag())
	    output.print("(struct "+(new TypeDescriptor(typeutil.getClass(TypeUtil.TagClass))).getSafeSymbol()  +" *)"+generateTemp(fm, targ));
	  else
	    output.print("(struct "+md.getParamType(i).getSafeSymbol()  +" *)"+generateTemp(fm, targ));
	}
      }
      output.println("};");
    }
    output.print("       ");


    if (fc.getReturnTemp()!=null)
      output.print(generateTemp(fm,fc.getReturnTemp())+"=");

    /* Do we need to do virtual dispatch? */
    if (md.isStatic()||md.getReturnType()==null||singleCall(fc.getThis().getType().getClassDesc(),md)) {
      //no
      if (currlb!=null) {
	LocalityBinding fclb=locality.getBinding(currlb, fc);
	output.print(cn.getSafeSymbol()+fclb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor());
      } else {
	output.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor());
      }
    } else {
      //yes
      output.print("((");
      if (state.MGC && md.getReturnType().isClass() && md.getReturnType().getClassDesc().isEnum()) {
	output.print("int ");
      } else if (md.getReturnType().isClass()||md.getReturnType().isArray())
	output.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
      else
	output.print(md.getReturnType().getSafeSymbol()+" ");
      output.print("(*)(");

      boolean printcomma=false;
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	if (currlb!=null) {
	  LocalityBinding fclb=locality.getBinding(currlb, fc);
	  output.print("struct "+cn.getSafeSymbol()+fclb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * ");
	} else
	  output.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * ");
	printcomma=true;
      }

      for(int i=0; i<objectparams.numPrimitives(); i++) {
	TempDescriptor temp=objectparams.getPrimitive(i);
	if (printcomma)
	  output.print(", ");
	printcomma=true;
	if (state.MGC && temp.getType().isClass() && temp.getType().getClassDesc().isEnum()) {
	  output.print("int ");
	} else if (temp.getType().isClass()||temp.getType().isArray())
	  output.print("struct " + temp.getType().getSafeSymbol()+" * ");
	else
	  output.print(temp.getType().getSafeSymbol());
      }


      if (currlb!=null) {
	LocalityBinding fclb=locality.getBinding(currlb, fc);
	output.print("))virtualtable["+generateTemp(fm,fc.getThis())+"->type*"+maxcount+"+"+virtualcalls.getLocalityNumber(fclb)+"])");
      } else
	output.print("))virtualtable["+generateTemp(fm,fc.getThis())+"->type*"+maxcount+"+"+virtualcalls.getMethodNumber(md)+"])");
    }

    output.print("(");
    boolean needcomma=false;
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      output.print("&__parameterlist__");
      needcomma=true;
    }

    if (!GENERATEPRECISEGC && !this.state.MULTICOREGC) {
      if (fc.getThis()!=null) {
	TypeDescriptor ptd=null;
	if(md.getThis() != null) {
	  ptd = md.getThis().getType();
	} else {
	  ptd = fc.getThis().getType();
	}
	if (needcomma)
	  output.print(",");
	if(state.MGC && ptd.isClass() && ptd.getClassDesc().isEnum()) {
	  // do nothing
	} else if (ptd.isClass()&&!ptd.isArray())
	  output.print("(struct "+ptd.getSafeSymbol()+" *) ");
	output.print(generateTemp(fm,fc.getThis()));
	needcomma=true;
      }
    }

    for(int i=0; i<fc.numArgs(); i++) {
      Descriptor var=md.getParameter(i);
      TempDescriptor paramtemp=(TempDescriptor)temptovar.get(var);
      if (objectparams.isParamPrim(paramtemp)) {
	TempDescriptor targ=fc.getArg(i);
	if (needcomma)
	  output.print(", ");

	TypeDescriptor ptd=md.getParamType(i);
	if (state.MGC && ptd.isClass() && ptd.getClassDesc().isEnum()) {
	  // do nothing
	} else if (ptd.isClass()&&!ptd.isArray())
	  output.print("(struct "+ptd.getSafeSymbol()+" *) ");
	output.print(generateTemp(fm, targ));
	needcomma=true;
      }
    }
    output.println(");");
    output.println("   }");
  }


  protected void generateFlatFieldNode(FlatMethod fm, FlatFieldNode ffn, PrintWriter output) {
    if (state.SINGLETM) {
      //single machine transactional memory case
      String field=ffn.getField().getSafeSymbol();
      String src=generateTemp(fm, ffn.getSrc());
      String dst=generateTemp(fm, ffn.getDst());

      output.println(dst+"="+ src +"->"+field+ ";");
      if (ffn.getField().getType().isPtr()&&locality.getAtomic(currlb).get(ffn).intValue()>0&&
          locality.getNodePreTempInfo(currlb, ffn).get(ffn.getSrc())!=LocalityAnalysis.SCRATCH) {
	if ((dc==null)||(!state.READSET&&dc.getNeedTrans(currlb, ffn))||
	    (state.READSET&&dc.getNeedWriteTrans(currlb, ffn))) {
	  output.println("TRANSREAD("+dst+", "+dst+", (void *) (" + localsprefixaddr + "));");
	} else if (state.READSET&&dc.getNeedTrans(currlb, ffn)) {
	  if (state.HYBRID&&delaycomp.getConv(currlb).contains(ffn)) {
	    output.println("TRANSREADRDFISSION("+dst+", "+dst+");");
	  } else
	    output.println("TRANSREADRD("+dst+", "+dst+");");
	}
      }
    } else if (state.DSM) {
      Integer status=locality.getNodePreTempInfo(currlb,ffn).get(ffn.getSrc());
      if (status==LocalityAnalysis.GLOBAL) {
	String field=ffn.getField().getSafeSymbol();
	String src=generateTemp(fm, ffn.getSrc());
	String dst=generateTemp(fm, ffn.getDst());

	if (ffn.getField().getType().isPtr()) {
	  output.println(dst+"="+ src +"->"+field+ ";");
	  output.println("TRANSREAD("+dst+", (unsigned int) "+dst+");");
	} else {
	  output.println(dst+"="+ src+"->"+field+";");
	}
      } else if (status==LocalityAnalysis.LOCAL) {
	if (ffn.getField().getType().isPtr()&&
	    ffn.getField().isGlobal()) {
	  String field=ffn.getField().getSafeSymbol();
	  String src=generateTemp(fm, ffn.getSrc());
	  String dst=generateTemp(fm, ffn.getDst());
	  output.println(dst+"="+ src +"->"+field+ ";");
	  if (locality.getAtomic(currlb).get(ffn).intValue()>0)
	    output.println("TRANSREAD("+dst+", (unsigned int) "+dst+");");
	} else
	  output.println(generateTemp(fm, ffn.getDst())+"="+ generateTemp(fm,ffn.getSrc())+"->"+ ffn.getField().getSafeSymbol()+";");
      } else if (status==LocalityAnalysis.EITHER) {
	//Code is reading from a null pointer
	output.println("if ("+generateTemp(fm, ffn.getSrc())+") {");
	output.println("#ifndef RAW");
	output.println("printf(\"BIG ERROR\\n\");exit(-1);}");
	output.println("#endif");
	//This should throw a suitable null pointer error
	output.println(generateTemp(fm, ffn.getDst())+"="+ generateTemp(fm,ffn.getSrc())+"->"+ ffn.getField().getSafeSymbol()+";");
      } else
	throw new Error("Read from non-global/non-local in:"+currlb.getExplanation());
    }
  }

  protected void generateFlatSetFieldNode(FlatMethod fm, FlatSetFieldNode fsfn, PrintWriter output) {
    if (fsfn.getField().getSymbol().equals("length")&&fsfn.getDst().getType().isArray())
      throw new Error("Can't set array length");
    if (state.SINGLETM && locality.getAtomic(currlb).get(fsfn).intValue()>0) {
      //Single Machine Transaction Case
      boolean srcptr=fsfn.getSrc().getType().isPtr();
      String src=generateTemp(fm,fsfn.getSrc());
      String dst=generateTemp(fm,fsfn.getDst());
      output.println("//"+srcptr+" "+fsfn.getSrc().getType().isNull());
      if (srcptr&&!fsfn.getSrc().getType().isNull()) {
	output.println("{");
	if ((dc==null)||dc.getNeedSrcTrans(currlb, fsfn)&&
	    locality.getNodePreTempInfo(currlb, fsfn).get(fsfn.getSrc())!=LocalityAnalysis.SCRATCH) {
	  output.println("INTPTR srcoid=("+src+"!=NULL?((INTPTR)"+src+"->"+oidstr+"):0);");
	} else {
	  output.println("INTPTR srcoid=(INTPTR)"+src+";");
	}
      }
      if (wb.needBarrier(fsfn)&&
          locality.getNodePreTempInfo(currlb, fsfn).get(fsfn.getDst())!=LocalityAnalysis.SCRATCH) {
	if (state.EVENTMONITOR) {
	  output.println("if ("+dst+"->___objstatus___&DIRTY) EVLOGEVENTOBJ(EV_WRITE,"+dst+"->objuid)");
	}
	output.println("*((unsigned int *)&("+dst+"->___objstatus___))|=DIRTY;");
      }
      if (srcptr&!fsfn.getSrc().getType().isNull()) {
	output.println("*((unsigned INTPTR *)&("+dst+"->"+
	               fsfn.getField().getSafeSymbol()+"))=srcoid;");
	output.println("}");
      } else {
	output.println(dst+"->"+
	               fsfn.getField().getSafeSymbol()+"="+ src+";");
      }
    } else if (state.DSM && locality.getAtomic(currlb).get(fsfn).intValue()>0) {
      Integer statussrc=locality.getNodePreTempInfo(currlb,fsfn).get(fsfn.getSrc());
      Integer statusdst=locality.getNodeTempInfo(currlb).get(fsfn).get(fsfn.getDst());
      boolean srcglobal=statussrc==LocalityAnalysis.GLOBAL;

      String src=generateTemp(fm,fsfn.getSrc());
      String dst=generateTemp(fm,fsfn.getDst());
      if (srcglobal) {
	output.println("{");
	output.println("INTPTR srcoid=("+src+"!=NULL?((INTPTR)"+src+"->"+oidstr+"):0);");
      }
      if (statusdst.equals(LocalityAnalysis.GLOBAL)) {
	String glbdst=dst;
	//mark it dirty
	if (wb.needBarrier(fsfn))
	  output.println("*((unsigned int *)&("+dst+"->___localcopy___))|=DIRTY;");
	if (srcglobal) {
	  output.println("*((unsigned INTPTR *)&("+glbdst+"->"+
	                 fsfn.getField().getSafeSymbol()+"))=srcoid;");
	} else
	  output.println(glbdst+"->"+
	                 fsfn.getField().getSafeSymbol()+"="+ src+";");
      } else if (statusdst.equals(LocalityAnalysis.LOCAL)) {
	/** Check if we need to copy */
	output.println("if(!"+dst+"->"+localcopystr+") {");
	/* Link object into list */
	String revertptr=generateTemp(fm, reverttable.get(currlb));
	output.println(revertptr+"=revertlist;");
	if (GENERATEPRECISEGC || this.state.MULTICOREGC)
	  output.println("COPY_OBJ((struct garbagelist *)"+localsprefixaddr+",(struct ___Object___ *)"+dst+");");
	else
	  output.println("COPY_OBJ("+dst+");");
	output.println(dst+"->"+nextobjstr+"="+revertptr+";");
	output.println("revertlist=(struct ___Object___ *)"+dst+";");
	output.println("}");
	if (srcglobal)
	  output.println(dst+"->"+
	                 fsfn.getField().getSafeSymbol()+"=(void *) srcoid;");
	else
	  output.println(dst+"->"+
	                 fsfn.getField().getSafeSymbol()+"="+ src+";");
      } else if (statusdst.equals(LocalityAnalysis.EITHER)) {
	//writing to a null...bad
	output.println("if ("+dst+") {");
	output.println("printf(\"BIG ERROR 2\\n\");exit(-1);}");
	if (srcglobal)
	  output.println(dst+"->"+
	                 fsfn.getField().getSafeSymbol()+"=(void *) srcoid;");
	else
	  output.println(dst+"->"+
	                 fsfn.getField().getSafeSymbol()+"="+ src+";");
      }
      if (srcglobal) {
	output.println("}");
      }
    }
  }

  protected void generateFlatElementNode(FlatMethod fm, FlatElementNode fen, PrintWriter output) {
    TypeDescriptor elementtype=fen.getSrc().getType().dereference();
    String type="";

    if (state.MGC && elementtype.isClass() && elementtype.getClassDesc().isEnum()) {
      type="int ";
    } else if (elementtype.isArray()||elementtype.isClass())
      type="void *";
    else
      type=elementtype.getSafeSymbol()+" ";

    if (this.state.ARRAYBOUNDARYCHECK && fen.needsBoundsCheck()) {
      output.println("if (unlikely(((unsigned int)"+generateTemp(fm, fen.getIndex())+") >= "+generateTemp(fm,fen.getSrc()) + "->___length___))");
      output.println("failedboundschk();");
    }
    if (state.SINGLETM) {
      //Single machine transaction case
      String dst=generateTemp(fm, fen.getDst());
      if ((!state.STMARRAY)||(!wb.needBarrier(fen))||locality.getNodePreTempInfo(currlb, fen).get(fen.getSrc())==LocalityAnalysis.SCRATCH||locality.getAtomic(currlb).get(fen).intValue()==0||(state.READSET&&!dc.getNeedGet(currlb, fen))) {
	output.println(dst +"=(("+ type+"*)(((char *) &("+ generateTemp(fm,fen.getSrc())+"->___length___))+sizeof(int)))["+generateTemp(fm, fen.getIndex())+"];");
      } else {
	output.println("STMGETARRAY("+dst+", "+ generateTemp(fm,fen.getSrc())+", "+generateTemp(fm, fen.getIndex())+", "+type+");");
      }

      if (elementtype.isPtr()&&locality.getAtomic(currlb).get(fen).intValue()>0&&
          locality.getNodePreTempInfo(currlb, fen).get(fen.getSrc())!=LocalityAnalysis.SCRATCH) {
	if ((dc==null)||!state.READSET&&dc.getNeedTrans(currlb, fen)||state.READSET&&dc.getNeedWriteTrans(currlb, fen)) {
	  output.println("TRANSREAD("+dst+", "+dst+", (void *)(" + localsprefixaddr+"));");
	} else if (state.READSET&&dc.getNeedTrans(currlb, fen)) {
	  if (state.HYBRID&&delaycomp.getConv(currlb).contains(fen)) {
	    output.println("TRANSREADRDFISSION("+dst+", "+dst+");");
	  } else
	    output.println("TRANSREADRD("+dst+", "+dst+");");
	}
      }
    } else if (state.DSM) {
      Integer status=locality.getNodePreTempInfo(currlb,fen).get(fen.getSrc());
      if (status==LocalityAnalysis.GLOBAL) {
	String dst=generateTemp(fm, fen.getDst());
	if (elementtype.isPtr()) {
	  output.println(dst +"=(("+ type+"*)(((char *) &("+ generateTemp(fm,fen.getSrc())+"->___length___))+sizeof(int)))["+generateTemp(fm, fen.getIndex())+"];");
	  output.println("TRANSREAD("+dst+", "+dst+");");
	} else {
	  output.println(dst +"=(("+ type+"*)(((char *) &("+ generateTemp(fm,fen.getSrc())+"->___length___))+sizeof(int)))["+generateTemp(fm, fen.getIndex())+"];");
	}
      } else if (status==LocalityAnalysis.LOCAL) {
	output.println(generateTemp(fm, fen.getDst())+"=(("+ type+"*)(((char *) &("+ generateTemp(fm,fen.getSrc())+"->___length___))+sizeof(int)))["+generateTemp(fm, fen.getIndex())+"];");
      } else if (status==LocalityAnalysis.EITHER) {
	//Code is reading from a null pointer
	output.println("if ("+generateTemp(fm, fen.getSrc())+") {");
	output.println("#ifndef RAW");
	output.println("printf(\"BIG ERROR\\n\");exit(-1);}");
	output.println("#endif");
	//This should throw a suitable null pointer error
	output.println(generateTemp(fm, fen.getDst())+"=(("+ type+"*)(((char *) &("+ generateTemp(fm,fen.getSrc())+"->___length___))+sizeof(int)))["+generateTemp(fm, fen.getIndex())+"];");
      } else
	throw new Error("Read from non-global/non-local in:"+currlb.getExplanation());
    }
  }

  protected void generateFlatSetElementNode(FlatMethod fm, FlatSetElementNode fsen, PrintWriter output) {
    //TODO: need dynamic check to make sure this assignment is actually legal
    //Because Object[] could actually be something more specific...ie. Integer[]

    TypeDescriptor elementtype=fsen.getDst().getType().dereference();
    String type="";

    if (state.MGC && elementtype.isClass() && elementtype.getClassDesc().isEnum()) {
      type="int ";
    } else if (elementtype.isArray()||elementtype.isClass() || (state.MGC && elementtype.isNull()))
      type="void *";
    else
      type=elementtype.getSafeSymbol()+" ";

    if (this.state.ARRAYBOUNDARYCHECK && fsen.needsBoundsCheck()) {
      output.println("if (unlikely(((unsigned int)"+generateTemp(fm, fsen.getIndex())+") >= "+generateTemp(fm,fsen.getDst()) + "->___length___))");
      output.println("failedboundschk();");
    }

    if (state.SINGLETM && locality.getAtomic(currlb).get(fsen).intValue()>0) {
      //Transaction set element case
      if (wb.needBarrier(fsen)&&
          locality.getNodePreTempInfo(currlb, fsen).get(fsen.getDst())!=LocalityAnalysis.SCRATCH) {
	output.println("*((unsigned int *)&("+generateTemp(fm,fsen.getDst())+"->___objstatus___))|=DIRTY;");
      }
      if (fsen.getSrc().getType().isPtr()&&!fsen.getSrc().getType().isNull()) {
	output.println("{");
	String src=generateTemp(fm, fsen.getSrc());
	if ((dc==null)||dc.getNeedSrcTrans(currlb, fsen)&&
	    locality.getNodePreTempInfo(currlb, fsen).get(fsen.getSrc())!=LocalityAnalysis.SCRATCH) {
	  output.println("INTPTR srcoid=("+src+"!=NULL?((INTPTR)"+src+"->"+oidstr+"):0);");
	} else {
	  output.println("INTPTR srcoid=(INTPTR)"+src+";");
	}
	if (state.STMARRAY&&locality.getNodePreTempInfo(currlb, fsen).get(fsen.getDst())!=LocalityAnalysis.SCRATCH&&wb.needBarrier(fsen)&&locality.getAtomic(currlb).get(fsen).intValue()>0) {
	  output.println("STMSETARRAY("+generateTemp(fm, fsen.getDst())+", "+generateTemp(fm, fsen.getIndex())+", srcoid, INTPTR);");
	} else {
	  output.println("((INTPTR*)(((char *) &("+ generateTemp(fm,fsen.getDst())+"->___length___))+sizeof(int)))["+generateTemp(fm, fsen.getIndex())+"]=srcoid;");
	}
	output.println("}");
      } else {
	if (state.STMARRAY&&locality.getNodePreTempInfo(currlb, fsen).get(fsen.getDst())!=LocalityAnalysis.SCRATCH&&wb.needBarrier(fsen)&&locality.getAtomic(currlb).get(fsen).intValue()>0) {
	  output.println("STMSETARRAY("+generateTemp(fm, fsen.getDst())+", "+generateTemp(fm, fsen.getIndex())+", "+ generateTemp(fm, fsen.getSrc()) +", "+type+");");
	} else {
	  output.println("(("+type +"*)(((char *) &("+ generateTemp(fm,fsen.getDst())+"->___length___))+sizeof(int)))["+generateTemp(fm, fsen.getIndex())+"]="+generateTemp(fm,fsen.getSrc())+";");
	}
      }
    } else if (state.DSM && locality.getAtomic(currlb).get(fsen).intValue()>0) {
      Integer statussrc=locality.getNodePreTempInfo(currlb,fsen).get(fsen.getSrc());
      Integer statusdst=locality.getNodePreTempInfo(currlb,fsen).get(fsen.getDst());
      boolean srcglobal=statussrc==LocalityAnalysis.GLOBAL;
      boolean dstglobal=statusdst==LocalityAnalysis.GLOBAL;
      boolean dstlocal=(statusdst==LocalityAnalysis.LOCAL)||(statusdst==LocalityAnalysis.EITHER);

      if (dstglobal) {
	if (wb.needBarrier(fsen))
	  output.println("*((unsigned int *)&("+generateTemp(fm,fsen.getDst())+"->___localcopy___))|=DIRTY;");
      } else if (dstlocal) {
	/** Check if we need to copy */
	String dst=generateTemp(fm, fsen.getDst());
	output.println("if(!"+dst+"->"+localcopystr+") {");
	/* Link object into list */
	String revertptr=generateTemp(fm, reverttable.get(currlb));
	output.println(revertptr+"=revertlist;");
	if ((GENERATEPRECISEGC) || this.state.MULTICOREGC)
	  output.println("COPY_OBJ((struct garbagelist *)"+localsprefixaddr+",(struct ___Object___ *)"+dst+");");
	else
	  output.println("COPY_OBJ("+dst+");");
	output.println(dst+"->"+nextobjstr+"="+revertptr+";");
	output.println("revertlist=(struct ___Object___ *)"+dst+";");
	output.println("}");
      } else {
	System.out.println("Node: "+fsen);
	System.out.println(currlb);
	System.out.println("statusdst="+statusdst);
	System.out.println(fm.printMethod());
	throw new Error("Unknown array type");
      }
      if (srcglobal) {
	output.println("{");
	String src=generateTemp(fm, fsen.getSrc());
	output.println("INTPTR srcoid=("+src+"!=NULL?((INTPTR)"+src+"->"+oidstr+"):0);");
	output.println("((INTPTR*)(((char *) &("+ generateTemp(fm,fsen.getDst())+"->___length___))+sizeof(int)))["+generateTemp(fm, fsen.getIndex())+"]=srcoid;");
	output.println("}");
      } else {
	output.println("(("+type +"*)(((char *) &("+ generateTemp(fm,fsen.getDst())+"->___length___))+sizeof(int)))["+generateTemp(fm, fsen.getIndex())+"]="+generateTemp(fm,fsen.getSrc())+";");
      }
    } else {
      if (state.FASTCHECK) {
	String dst=generateTemp(fm, fsen.getDst());
	output.println("if(!"+dst+"->"+localcopystr+") {");
	/* Link object into list */
	if (GENERATEPRECISEGC || this.state.MULTICOREGC)
	  output.println("COPY_OBJ((struct garbagelist *)"+localsprefixaddr+",(struct ___Object___ *)"+dst+");");
	else
	  output.println("COPY_OBJ("+dst+");");
	output.println(dst+"->"+nextobjstr+"="+fcrevert+";");
	output.println(fcrevert+"=(struct ___Object___ *)"+dst+";");
	output.println("}");
      }
      output.println("(("+type +"*)(((char *) &("+ generateTemp(fm,fsen.getDst())+"->___length___))+sizeof(int)))["+generateTemp(fm, fsen.getIndex())+"]="+generateTemp(fm,fsen.getSrc())+";");
    }
  }


  protected void generateFlatNew(FlatMethod fm, FlatNew fn, PrintWriter output) {
    if (state.DSM && locality.getAtomic(currlb).get(fn).intValue()>0&&!fn.isGlobal()) {
      //Stash pointer in case of GC
      String revertptr=generateTemp(fm, reverttable.get(currlb));
      output.println(revertptr+"=revertlist;");
    }
    if (state.SINGLETM) {
      if (fn.getType().isArray()) {
	int arrayid=state.getArrayNumber(fn.getType())+state.numClasses();
	if (locality.getAtomic(currlb).get(fn).intValue()>0) {
	  //inside transaction
	  output.println(generateTemp(fm,fn.getDst())+"=allocate_newarraytrans("+localsprefixaddr+", "+arrayid+", "+generateTemp(fm, fn.getSize())+");");
	} else {
	  //outside transaction
	  output.println(generateTemp(fm,fn.getDst())+"=allocate_newarray("+localsprefixaddr+", "+arrayid+", "+generateTemp(fm, fn.getSize())+");");
	}
      } else {
	if (locality.getAtomic(currlb).get(fn).intValue()>0) {
	  //inside transaction
	  output.println(generateTemp(fm,fn.getDst())+"=allocate_newtrans("+localsprefixaddr+", "+fn.getType().getClassDesc().getId()+");");
	} else {
	  //outside transaction
	  output.println(generateTemp(fm,fn.getDst())+"=allocate_new("+localsprefixaddr+", "+fn.getType().getClassDesc().getId()+");");
	}
      }
    } else if (fn.getType().isArray()) {
      int arrayid=state.getArrayNumber(fn.getType())+state.numClasses();
      if (fn.isGlobal()) {
	output.println(generateTemp(fm,fn.getDst())+"=allocate_newarrayglobal("+arrayid+", "+generateTemp(fm, fn.getSize())+");");
      } else if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	output.println(generateTemp(fm,fn.getDst())+"=allocate_newarray("+localsprefixaddr+", "+arrayid+", "+generateTemp(fm, fn.getSize())+");");
      } else {
	output.println(generateTemp(fm,fn.getDst())+"=allocate_newarray("+arrayid+", "+generateTemp(fm, fn.getSize())+");");
      }
    } else {
      if (fn.isGlobal()) {
	output.println(generateTemp(fm,fn.getDst())+"=allocate_newglobal("+fn.getType().getClassDesc().getId()+");");
      } else if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	output.println(generateTemp(fm,fn.getDst())+"=allocate_new("+localsprefixaddr+", "+fn.getType().getClassDesc().getId()+");");
      } else {
	output.println(generateTemp(fm,fn.getDst())+"=allocate_new("+fn.getType().getClassDesc().getId()+");");
      }
    }
    if (state.DSM && locality.getAtomic(currlb).get(fn).intValue()>0&&!fn.isGlobal()) {
      String revertptr=generateTemp(fm, reverttable.get(currlb));
      String dst=generateTemp(fm,fn.getDst());
      output.println(dst+"->___localcopy___=(struct ___Object___*)1;");
      output.println(dst+"->"+nextobjstr+"="+revertptr+";");
      output.println("revertlist=(struct ___Object___ *)"+dst+";");
    }
    if (state.FASTCHECK) {
      String dst=generateTemp(fm,fn.getDst());
      output.println(dst+"->___localcopy___=(struct ___Object___*)1;");
      output.println(dst+"->"+nextobjstr+"="+fcrevert+";");
      output.println(fcrevert+"=(struct ___Object___ *)"+dst+";");
    }
  }

  protected void generateFlatOpNode(FlatMethod fm, FlatOpNode fon, PrintWriter output) {
    if (fon.getRight()!=null) {
      if (fon.getOp().getOp()==Operation.URIGHTSHIFT) {
	if (fon.getLeft().getType().isLong())
	  output.println(generateTemp(fm, fon.getDest())+" = ((unsigned long long)"+generateTemp(fm, fon.getLeft())+")>>"+generateTemp(fm,fon.getRight())+";");
	else
	  output.println(generateTemp(fm, fon.getDest())+" = ((unsigned int)"+generateTemp(fm, fon.getLeft())+")>>"+generateTemp(fm,fon.getRight())+";");

      } else if (dc!=null) {
	output.print(generateTemp(fm, fon.getDest())+" = (");
	if (fon.getLeft().getType().isPtr()&&(fon.getOp().getOp()==Operation.EQUAL||fon.getOp().getOp()==Operation.NOTEQUAL))
	  output.print("(void *)");
	if (dc.getNeedLeftSrcTrans(currlb, fon))
	  output.print("("+generateTemp(fm, fon.getLeft())+"!=NULL?"+generateTemp(fm, fon.getLeft())+"->"+oidstr+":NULL)");
	else
	  output.print(generateTemp(fm, fon.getLeft()));
	output.print(")"+fon.getOp().toString()+"(");
	if (fon.getRight().getType().isPtr()&&(fon.getOp().getOp()==Operation.EQUAL||fon.getOp().getOp()==Operation.NOTEQUAL))
	  output.print("(void *)");
	if (dc.getNeedRightSrcTrans(currlb, fon))
	  output.println("("+generateTemp(fm, fon.getRight())+"!=NULL?"+generateTemp(fm, fon.getRight())+"->"+oidstr+":NULL));");
	else
	  output.println(generateTemp(fm,fon.getRight())+");");
      } else
	output.println(generateTemp(fm, fon.getDest())+" = "+generateTemp(fm, fon.getLeft())+fon.getOp().toString()+generateTemp(fm,fon.getRight())+";");
    } else if (fon.getOp().getOp()==Operation.ASSIGN)
      output.println(generateTemp(fm, fon.getDest())+" = "+generateTemp(fm, fon.getLeft())+";");
    else if (fon.getOp().getOp()==Operation.UNARYPLUS)
      output.println(generateTemp(fm, fon.getDest())+" = "+generateTemp(fm, fon.getLeft())+";");
    else if (fon.getOp().getOp()==Operation.UNARYMINUS)
      output.println(generateTemp(fm, fon.getDest())+" = -"+generateTemp(fm, fon.getLeft())+";");
    else if (fon.getOp().getOp()==Operation.LOGIC_NOT)
      output.println(generateTemp(fm, fon.getDest())+" = !"+generateTemp(fm, fon.getLeft())+";");
    else if (fon.getOp().getOp()==Operation.COMP)
      output.println(generateTemp(fm, fon.getDest())+" = ~"+generateTemp(fm, fon.getLeft())+";");
    else if (fon.getOp().getOp()==Operation.ISAVAILABLE) {
      output.println(generateTemp(fm, fon.getDest())+" = "+generateTemp(fm, fon.getLeft())+"->fses==NULL;");
    } else
      output.println(generateTemp(fm, fon.getDest())+fon.getOp().toString()+generateTemp(fm, fon.getLeft())+";");
  }

  protected void generateFlatLiteralNode(FlatMethod fm, FlatLiteralNode fln, PrintWriter output) {
    if (fln.getValue()==null)
      output.println(generateTemp(fm, fln.getDst())+"=0;");
    else if (fln.getType().getSymbol().equals(TypeUtil.StringClass)) {
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	if (state.DSM && locality.getAtomic(currlb).get(fln).intValue()>0) {
	  //Stash pointer in case of GC
	  String revertptr=generateTemp(fm, reverttable.get(currlb));
	  output.println(revertptr+"=revertlist;");
	}
	output.println(generateTemp(fm, fln.getDst())+"=NewString("+localsprefixaddr+", \""+FlatLiteralNode.escapeString((String)fln.getValue())+"\","+((String)fln.getValue()).length()+");");
	if (state.DSM && locality.getAtomic(currlb).get(fln).intValue()>0) {
	  //Stash pointer in case of GC
	  String revertptr=generateTemp(fm, reverttable.get(currlb));
	  output.println("revertlist="+revertptr+";");
	}
      } else {
	output.println(generateTemp(fm, fln.getDst())+"=NewString(\""+FlatLiteralNode.escapeString((String)fln.getValue())+"\","+((String)fln.getValue()).length()+");");
      }
    } else if (fln.getType().isBoolean()) {
      if (((Boolean)fln.getValue()).booleanValue())
	output.println(generateTemp(fm, fln.getDst())+"=1;");
      else
	output.println(generateTemp(fm, fln.getDst())+"=0;");
    } else if (fln.getType().isChar()) {
      String st=FlatLiteralNode.escapeString(fln.getValue().toString());
      output.println(generateTemp(fm, fln.getDst())+"='"+st+"';");
    } else if (fln.getType().isLong()) {
      output.println(generateTemp(fm, fln.getDst())+"="+fln.getValue()+"LL;");
    } else
      output.println(generateTemp(fm, fln.getDst())+"="+fln.getValue()+";");
  }

  protected void generateStoreFlatCondBranch(FlatMethod fm, LocalityBinding lb, FlatCondBranch fcb, String label, PrintWriter output) {
    int left=-1;
    int right=-1;
    //only record if this group has more than one exit
    if (branchanalysis.numJumps(fcb)>1) {
      left=branchanalysis.jumpValue(fcb, 0);
      right=branchanalysis.jumpValue(fcb, 1);
    }
    output.println("if (!"+generateTemp(fm, fcb.getTest())+") {");
    if (right!=-1)
      output.println("STOREBRANCH("+right+");");
    output.println("goto "+label+";");
    output.println("}");
    if (left!=-1)
      output.println("STOREBRANCH("+left+");");
  }

  /** This method generates header information for the method or
   * task referenced by the Descriptor des. */
  protected void generateHeader(FlatMethod fm, LocalityBinding lb, Descriptor des, PrintWriter output) {
    /* Print header */
    ParamsObject objectparams=(ParamsObject)paramstable.get(lb!=null ? lb : des);
    MethodDescriptor md=null;
    TaskDescriptor task=null;
    if (des instanceof MethodDescriptor)
      md=(MethodDescriptor) des;
    else
      task=(TaskDescriptor) des;

    ClassDescriptor cn=md!=null ? md.getClassDesc() : null;

    if (md!=null&&md.getReturnType()!=null) {
      if (state.MGC && md.getReturnType().isClass() && md.getReturnType().getClassDesc().isEnum()) {
	output.print("int ");
      } else if (md.getReturnType().isClass()||md.getReturnType().isArray())
	output.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
      else
	output.print(md.getReturnType().getSafeSymbol()+" ");
    } else
      //catch the constructor case
      output.print("void ");
    if (md!=null) {
      output.print(cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");
    } else
      output.print(task.getSafeSymbol()+"(");

    boolean printcomma=false;
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      if (md!=null) {
	output.print("struct "+cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * "+paramsprefix);
      } else
	output.print("struct "+task.getSafeSymbol()+"_params * "+paramsprefix);
      printcomma=true;
    }

    if (md!=null) {
      /* Method */
      for(int i=0; i<objectparams.numPrimitives(); i++) {
	TempDescriptor temp=objectparams.getPrimitive(i);
	if (printcomma)
	  output.print(", ");
	printcomma=true;
	if(state.MGC && temp.getType().isClass() && temp.getType().getClassDesc().isEnum()) {
	  output.print("int " + temp.getSafeSymbol());
	} else if (temp.getType().isClass()||temp.getType().isArray())
	  output.print("struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol());
	else
	  output.print(temp.getType().getSafeSymbol()+" "+temp.getSafeSymbol());
      }
      output.println(") {");
    } else if (!GENERATEPRECISEGC && !this.state.MULTICOREGC) {
      /* Imprecise Task */
      output.println("void * parameterarray[]) {");
      /* Unpack variables */
      for(int i=0; i<objectparams.numPrimitives(); i++) {
	TempDescriptor temp=objectparams.getPrimitive(i);
	if(state.MGC && temp.getType().isClass() && temp.getType().getClassDesc().isEnum()) {
	  output.print("int " + temp.getSafeSymbol() + "=parameterarray["+i+"];");
	} else {
	  output.println("struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+"=parameterarray["+i+"];");
	}
      }
      for(int i=0; i<fm.numTags(); i++) {
	TempDescriptor temp=fm.getTag(i);
	int offset=i+objectparams.numPrimitives();
	output.println("struct ___TagDescriptor___ * "+temp.getSafeSymbol()+"=parameterarray["+offset+"];");
      }

      if ((objectparams.numPrimitives()+fm.numTags())>maxtaskparams)
	maxtaskparams=objectparams.numPrimitives()+fm.numTags();
    } else output.println(") {");
  }
}






