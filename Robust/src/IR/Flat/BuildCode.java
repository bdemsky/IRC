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

public class BuildCode {
  State state;
  Hashtable temptovar;
  Hashtable paramstable;
  Hashtable tempstable;
  Hashtable fieldorder;
  Hashtable flagorder;
  int tag=0;
  String localsprefix="___locals___";
  String localsprefixaddr="&"+localsprefix;
  String localsprefixderef=localsprefix+".";
  String fcrevert="___fcrevert___";
  String paramsprefix="___params___";
  String oidstr="___nextobject___";
  String nextobjstr="___nextobject___";
  String localcopystr="___localcopy___";
  public static boolean GENERATEPRECISEGC=false;
  public static String PREFIX="";
  public static String arraytype="ArrayObject";
  public static int flagcount = 0;
  Virtual virtualcalls;
  TypeUtil typeutil;
  protected int maxtaskparams=0;
  private int maxcount=0;
  ClassDescriptor[] cdarray;
  TypeDescriptor[] arraytable;
  LocalityAnalysis locality;
  Hashtable<LocalityBinding, TempDescriptor> reverttable;
  Hashtable<LocalityBinding, Hashtable<TempDescriptor, TempDescriptor>> backuptable;
  SafetyAnalysis sa;
  PrefetchAnalysis pa;
  WriteBarrier wb;
  DiscoverConflicts dc;
  DiscoverConflicts recorddc;
  DCWrapper delaycomp;
  CallGraph callgraph;


  public BuildCode(State st, Hashtable temptovar, TypeUtil typeutil, SafetyAnalysis sa, PrefetchAnalysis pa) {
    this(st, temptovar, typeutil, null, sa, pa);
  }

  public BuildCode(State st, Hashtable temptovar, TypeUtil typeutil, LocalityAnalysis locality, PrefetchAnalysis pa) {
    this(st, temptovar, typeutil, locality, null, pa);
  }

  public BuildCode(State st, Hashtable temptovar, TypeUtil typeutil, LocalityAnalysis locality, SafetyAnalysis sa, PrefetchAnalysis pa ) {
    this.sa=sa;
    this.pa=pa;
    state=st;
    callgraph=new CallGraph(state);
    if (state.SINGLETM)
      oidstr="___objlocation___";
    this.temptovar=temptovar;
    paramstable=new Hashtable();
    tempstable=new Hashtable();
    fieldorder=new Hashtable();
    flagorder=new Hashtable();
    this.typeutil=typeutil;
    virtualcalls=new Virtual(state,locality);
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
      //TypeAnalysis typeanalysis=new TypeAnalysis(locality, st, typeutil,callgraph);
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

  public void buildCode() {
    /* Create output streams to write to */
    PrintWriter outclassdefs=null;
    PrintWriter outstructs=null;
    PrintWriter outrepairstructs=null;
    PrintWriter outmethodheader=null;
    PrintWriter outmethod=null;
    PrintWriter outvirtual=null;
    PrintWriter outtask=null;
    PrintWriter outtaskdefs=null;
    PrintWriter outoptionalarrays=null;
    PrintWriter optionalheaders=null;
    PrintWriter outglobaldefs=null;

    try {
      if (state.SANDBOX) {
	outsandbox=new CodePrinter(new FileOutputStream(PREFIX+"sandboxdefs.c"), true);
      }
      outstructs=new CodePrinter(new FileOutputStream(PREFIX+"structdefs.h"), true);
      outmethodheader=new CodePrinter(new FileOutputStream(PREFIX+"methodheaders.h"), true);
      outclassdefs=new CodePrinter(new FileOutputStream(PREFIX+"classdefs.h"), true);
      if(state.MGC) {
        // TODO add version for normal Java later
      outglobaldefs=new CodePrinter(new FileOutputStream(PREFIX+"globaldefs.h"), true);
      }
      outmethod=new CodePrinter(new FileOutputStream(PREFIX+"methods.c"), true);
      outvirtual=new CodePrinter(new FileOutputStream(PREFIX+"virtualtable.h"), true);
      if (state.TASK) {
	outtask=new CodePrinter(new FileOutputStream(PREFIX+"task.h"), true);
	outtaskdefs=new CodePrinter(new FileOutputStream(PREFIX+"taskdefs.c"), true);
	if (state.OPTIONAL) {
	  outoptionalarrays=new CodePrinter(new FileOutputStream(PREFIX+"optionalarrays.c"), true);
	  optionalheaders=new CodePrinter(new FileOutputStream(PREFIX+"optionalstruct.h"), true);
	}
      }
      if (state.structfile!=null) {
	outrepairstructs=new CodePrinter(new FileOutputStream(PREFIX+state.structfile+".struct"), true);
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }

    /* Build the virtual dispatch tables */
    buildVirtualTables(outvirtual);

    /* Tag the methods that are invoked by static blocks */
    tagMethodInvokedByStaticBlock();
    
    /* Output includes */
    outmethodheader.println("#ifndef METHODHEADERS_H");
    outmethodheader.println("#define METHODHEADERS_H");
    outmethodheader.println("#include \"structdefs.h\"");
    if (state.DSM)
      outmethodheader.println("#include \"dstm.h\"");
    if (state.SANDBOX) {
      outmethodheader.println("#include \"sandbox.h\"");
    }
    if (state.EVENTMONITOR) {
      outmethodheader.println("#include \"monitor.h\"");
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


    additionalIncludesMethodsHeader( outmethodheader );


    /* Output Structures */
    outputStructs(outstructs);

    // Output the C class declarations
    // These could mutually reference each other
    
    if(state.MGC) {
      // TODO add version for normal Java later
    outglobaldefs.println("#ifndef __GLOBALDEF_H_");
    outglobaldefs.println("#define __GLOBALDEF_H_");
    outglobaldefs.println("");
    outglobaldefs.println("struct global_defs_t {");
    }
    
    outclassdefs.println("#ifndef __CLASSDEF_H_");
    outclassdefs.println("#define __CLASSDEF_H_");
    outputClassDeclarations(outclassdefs, outglobaldefs);

    // Output function prototypes and structures for parameters
    Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
    while(it.hasNext()) {
      ClassDescriptor cn=(ClassDescriptor)it.next();
      generateCallStructs(cn, outclassdefs, outstructs, outmethodheader, outglobaldefs);
    }
    outclassdefs.println("#endif");
    outclassdefs.close();
    if(state.MGC) {
      // TODO add version for normal Java later
    outglobaldefs.println("};");
    outglobaldefs.println("");
    outglobaldefs.println("extern struct global_defs_t * global_defs_p;");
    outglobaldefs.println("#endif");
    outglobaldefs.flush();
    outglobaldefs.close();
    }

    if (state.TASK) {
      /* Map flags to integers */
      /* The runtime keeps track of flags using these integers */
      it=state.getClassSymbolTable().getDescriptorsIterator();
      while(it.hasNext()) {
	ClassDescriptor cn=(ClassDescriptor)it.next();
	mapFlags(cn);
      }
      /* Generate Tasks */
      generateTaskStructs(outstructs, outmethodheader);

      /* Outputs generic task structures if this is a task
         program */
      outputTaskTypes(outtask);
    }


    // an opportunity for subclasses to do extra
    // initialization
    preCodeGenInitialization();


    /* Build the actual methods */
    outputMethods(outmethod);

    
    // opportunity for subclasses to gen extra code
    additionalCodeGen( outmethodheader,
                       outstructs,
                       outmethod );


    if (state.TASK) {
      /* Output code for tasks */
      outputTaskCode(outtaskdefs, outmethod);
      outtaskdefs.close();
      /* Record maximum number of task parameters */
      outstructs.println("#define MAXTASKPARAMS "+maxtaskparams);
    } else if (state.main!=null) {
      /* Generate main method */
      outputMainMethod(outmethod);
    }

    /* Generate information for task with optional parameters */
    if (state.TASK&&state.OPTIONAL) {
      generateOptionalArrays(outoptionalarrays, optionalheaders, state.getAnalysisResult(), state.getOptionalTaskDescriptors());
      outoptionalarrays.close();
    }
    
    /* Output structure definitions for repair tool */
    if (state.structfile!=null) {
      buildRepairStructs(outrepairstructs);
      outrepairstructs.close();
    }
    
    /* Close files */
    outmethodheader.println("#endif");
    outmethodheader.close();
    outmethod.close();
    outstructs.println("#endif");
    outstructs.close();



    postCodeGenCleanUp();
  }


  
  /* This method goes though the call graph and tag those methods that are 
   * invoked inside static blocks
   */
  protected void tagMethodInvokedByStaticBlock() {
    if(state.MGC) {
      Iterator it_sclasses = this.state.getSClassSymbolTable().getDescriptorsIterator();
      MethodDescriptor current_md=null;
      HashSet tovisit=new HashSet();
      HashSet visited=new HashSet();

      while(it_sclasses.hasNext()) {
        ClassDescriptor cd = (ClassDescriptor)it_sclasses.next();
        MethodDescriptor md = (MethodDescriptor)cd.getMethodTable().get("staticblocks");
        if(md != null) {
          tovisit.add(md);
        }
      }

      while(!tovisit.isEmpty()) {
        current_md=(MethodDescriptor)tovisit.iterator().next();
        tovisit.remove(current_md);
        visited.add(current_md);
        Iterator it_callee = this.callgraph.getCalleeSet(current_md).iterator();
        while(it_callee.hasNext()) {
          Descriptor d = (Descriptor)it_callee.next();
          if(d instanceof MethodDescriptor) {
            if(!visited.contains(d)) {
              ((MethodDescriptor)d).setIsInvokedByStatic(true);
              tovisit.add(d);
            }
          }
        }
      }
    } // TODO for normal Java version
  }
  
  /* This code generates code for each static block and static field 
   * initialization.*/
  protected void outputStaticBlocks(PrintWriter outmethod) {
    //  execute all the static blocks and all the static field initializations
    // TODO
  }
  
  /* This code generates code to create a Class object for each class for 
   * getClass() method.
   * */
  protected void outputClassObjects(PrintWriter outmethod) {
    // for each class, initialize its Class object
    // TODO
  }

  /* This code just generates the main C method for java programs.
   * The main C method packs up the arguments into a string array
   * and passes it to the java main method. */

  protected void outputMainMethod(PrintWriter outmethod) {
    outmethod.println("int main(int argc, const char *argv[]) {");
    outmethod.println("  int i;");
    
    outputStaticBlocks(outmethod);
    outputClassObjects(outmethod);


    additionalCodeAtTopOfMain( outmethod );


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
    
    if (state.THREAD||state.DSM||state.SINGLETM) {
      outmethod.println("initializethreads();");
    }
    if (state.DSM) {
      outmethod.println("if (dstmStartup(argv[1])) {");
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	outmethod.println("  struct ArrayObject * stringarray=allocate_newarray(NULL, STRINGARRAYTYPE, argc-2);");
      } else {
	outmethod.println("  struct ArrayObject * stringarray=allocate_newarray(STRINGARRAYTYPE, argc-2);");
      }
    } else {
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
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
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
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
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      if (state.DSM||state.SINGLETM) {
	outmethod.print("       struct "+cd.getSafeSymbol()+locality.getMain().getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params __parameterlist__={");
      } else
	outmethod.print("       struct "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params __parameterlist__={");
    outmethod.println("1, NULL,"+"stringarray};");
      if (state.DSM||state.SINGLETM)
	outmethod.println("     "+cd.getSafeSymbol()+locality.getMain().getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(& __parameterlist__);");
      else
	outmethod.println("     "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(& __parameterlist__);");
    } else {
      if (state.DSM||state.SINGLETM)
	outmethod.println("     "+cd.getSafeSymbol()+locality.getMain().getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(stringarray);");
      else
	outmethod.println("     "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(stringarray);");
    }
    outmethod.println("   }");

    if (state.DSM) {
      outmethod.println("}");
    }

    if (state.THREAD||state.DSM||state.SINGLETM) {
      outmethod.println("pthread_mutex_lock(&gclistlock);");
      outmethod.println("threadcount--;");
      outmethod.println("pthread_cond_signal(&gccond);");
      outmethod.println("pthread_mutex_unlock(&gclistlock);");
    }

    if (state.DSM||state.SINGLETM) {
      //outmethod.println("#if defined(TRANSSTATS) || defined(RECOVERYSTATS) \n");
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
    }

    if (state.EVENTMONITOR) {
      outmethod.println("dumpdata();");
    }

    if (state.THREAD||state.SINGLETM)
      outmethod.println("pthread_exit(NULL);");


    additionalCodeAtBottomOfMain( outmethod );


    outmethod.println("}");
  }

  /* This method outputs code for each task. */

  private void outputTaskCode(PrintWriter outtaskdefs, PrintWriter outmethod) {
    /* Compile task based program */
    outtaskdefs.println("#include \"task.h\"");
    outtaskdefs.println("#include \"methodheaders.h\"");
    Iterator taskit=state.getTaskSymbolTable().getDescriptorsIterator();
    while(taskit.hasNext()) {
      TaskDescriptor td=(TaskDescriptor)taskit.next();
      FlatMethod fm=state.getMethodFlat(td);
      generateFlatMethod(fm, null, outmethod);
      generateTaskDescriptor(outtaskdefs, fm, td);
    }

    //Output task descriptors
    taskit=state.getTaskSymbolTable().getDescriptorsIterator();
    outtaskdefs.println("struct taskdescriptor * taskarray[]= {");
    boolean first=true;
    while(taskit.hasNext()) {
      TaskDescriptor td=(TaskDescriptor)taskit.next();
      if (first)
	first=false;
      else
	outtaskdefs.println(",");
      outtaskdefs.print("&task_"+td.getSafeSymbol());
    }
    outtaskdefs.println("};");

    outtaskdefs.println("int numtasks="+state.getTaskSymbolTable().getValueSet().size()+";");
  }


  /* This method outputs most of the methods.c file.  This includes
   * some standard includes and then an array with the sizes of
   * objets and array that stores supertype and then the code for
   * the Java methods.. */
  protected void outputMethods(PrintWriter outmethod) {
    outmethod.println("#include \"methodheaders.h\"");
    outmethod.println("#include \"virtualtable.h\"");
    outmethod.println("#include \"runtime.h\"");

    // always include: compiler directives will leave out
    // instrumentation when option is not set
    outmethod.println("#include \"coreprof/coreprof.h\"");

    if (state.SANDBOX) {
      outmethod.println("#include \"sandboxdefs.c\"");
    }
    if (state.DSM) {
      outmethod.println("#include \"addPrefetchEnhance.h\"");
      outmethod.println("#include \"localobjects.h\"");
    }
    if (state.FASTCHECK) {
      outmethod.println("#include \"localobjects.h\"");
    }
    if(state.MULTICORE) {
      if(state.TASK) {
        outmethod.println("#include \"task.h\"");
      }
	  outmethod.println("#include \"multicoreruntime.h\"");
	  outmethod.println("#include \"runtime_arch.h\"");
    }
    if (state.THREAD||state.DSM||state.SINGLETM) {
      outmethod.println("#include <thread.h>");
    }
    if(state.MGC) {
      outmethod.println("#include \"thread.h\"");
    } 
    if (state.main!=null) {
      outmethod.println("#include <string.h>");
    }
    if (state.CONSCHECK) {
      outmethod.println("#include \"checkers.h\"");
    }


    additionalIncludesMethodsImplementation( outmethod );


    if(state.MGC) {
      // TODO add version for normal Java later
    outmethod.println("struct global_defs_t * global_defs_p;");
    }
    //Store the sizes of classes & array elements
    generateSizeArray(outmethod);

    //Store table of supertypes
    generateSuperTypeTable(outmethod);
    
    // Store table of classnames
    /*if(state.MGC) {
      // TODO add code for normal Java later
      generateClassNameTable(outmethod);
    }*/

    //Store the layout of classes
    generateLayoutStructs(outmethod);


    additionalCodeAtTopMethodsImplementation( outmethod );


    /* Generate code for methods */
    if (state.DSM||state.SINGLETM) {
      for(Iterator<LocalityBinding> lbit=locality.getLocalityBindings().iterator(); lbit.hasNext();) {
	LocalityBinding lb=lbit.next();
	MethodDescriptor md=lb.getMethod();
	FlatMethod fm=state.getMethodFlat(md);
	wb.analyze(lb);
	if (!md.getModifiers().isNative()) {
	  generateFlatMethod(fm, lb, outmethod);
	}
      }
    } else {
      Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
      while(classit.hasNext()) {
	ClassDescriptor cn=(ClassDescriptor)classit.next();
	Iterator methodit=cn.getMethods();
	while(methodit.hasNext()) {
	  /* Classify parameters */
	  MethodDescriptor md=(MethodDescriptor)methodit.next();
	  FlatMethod fm=state.getMethodFlat(md);
	  if (!md.getModifiers().isNative()) {
	    generateFlatMethod(fm, null, outmethod);
	  }
	}
      }
    }
  }

  protected void outputStructs(PrintWriter outstructs) {
    outstructs.println("#ifndef STRUCTDEFS_H");
    outstructs.println("#define STRUCTDEFS_H");
    outstructs.println("#include \"classdefs.h\"");
    outstructs.println("#ifndef INTPTR");
    outstructs.println("#ifdef BIT64");
    outstructs.println("#define INTPTR long");
    outstructs.println("#else");
    outstructs.println("#define INTPTR int");
    outstructs.println("#endif");
    outstructs.println("#endif");


    additionalIncludesStructsHeader( outstructs );


    /* Output #defines that the runtime uses to determine type
     * numbers for various objects it needs */
    outstructs.println("#define MAXCOUNT "+maxcount);
    if (state.DSM||state.SINGLETM) {
      LocalityBinding lbrun=new LocalityBinding(typeutil.getRun(), false);
      if (state.DSM) {
	lbrun.setGlobalThis(LocalityAnalysis.GLOBAL);
      }
      else if (state.SINGLETM) {
	lbrun.setGlobalThis(LocalityAnalysis.NORMAL);
      }
      outstructs.println("#define RUNMETHOD "+virtualcalls.getLocalityNumber(lbrun));
    }

    if (state.DSMTASK) {
      LocalityBinding lbexecute = new LocalityBinding(typeutil.getExecute(), false);
      if(state.DSM)
        lbexecute.setGlobalThis(LocalityAnalysis.GLOBAL);
      else if( state.SINGLETM)
        lbexecute.setGlobalThis(LocalityAnalysis.NORMAL);
      outstructs.println("#define EXECUTEMETHOD " + virtualcalls.getLocalityNumber(lbexecute));
    }

    outstructs.println("#define STRINGARRAYTYPE "+
                       (state.getArrayNumber(
                          (new TypeDescriptor(typeutil.getClass(TypeUtil.StringClass))).makeArray(state))+state.numClasses()));

    outstructs.println("#define OBJECTARRAYTYPE "+
                       (state.getArrayNumber(
                          (new TypeDescriptor(typeutil.getClass(TypeUtil.ObjectClass))).makeArray(state))+state.numClasses()));


    outstructs.println("#define STRINGTYPE "+typeutil.getClass(TypeUtil.StringClass).getId());
    outstructs.println("#define CHARARRAYTYPE "+
                       (state.getArrayNumber((new TypeDescriptor(TypeDescriptor.CHAR)).makeArray(state))+state.numClasses()));

    outstructs.println("#define BYTEARRAYTYPE "+
                       (state.getArrayNumber((new TypeDescriptor(TypeDescriptor.BYTE)).makeArray(state))+state.numClasses()));

    outstructs.println("#define BYTEARRAYARRAYTYPE "+
                       (state.getArrayNumber((new TypeDescriptor(TypeDescriptor.BYTE)).makeArray(state).makeArray(state))+state.numClasses()));

    outstructs.println("#define NUMCLASSES "+state.numClasses());
    int totalClassSize = state.numClasses() + state.numArrays();
    outstructs.println("#define TOTALNUMCLASSANDARRAY "+ totalClassSize);
    if (state.TASK) {
      outstructs.println("#define STARTUPTYPE "+typeutil.getClass(TypeUtil.StartupClass).getId());
      outstructs.println("#define TAGTYPE "+typeutil.getClass(TypeUtil.TagClass).getId());
      outstructs.println("#define TAGARRAYTYPE "+
                         (state.getArrayNumber(new TypeDescriptor(typeutil.getClass(TypeUtil.TagClass)).makeArray(state))+state.numClasses()));
    }
  }

  protected void outputClassDeclarations(PrintWriter outclassdefs, PrintWriter outglobaldefs) {
    if (state.THREAD||state.DSM||state.SINGLETM)
      outclassdefs.println("#include <pthread.h>");
    outclassdefs.println("#ifndef INTPTR");
    outclassdefs.println("#ifdef BIT64");
    outclassdefs.println("#define INTPTR long");
    outclassdefs.println("#else");
    outclassdefs.println("#define INTPTR int");
    outclassdefs.println("#endif");
    outclassdefs.println("#endif");
    if(state.OPTIONAL)
      outclassdefs.println("#include \"optionalstruct.h\"");
    outclassdefs.println("struct "+arraytype+";");
    /* Start by declaring all structs */
    Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
    while(it.hasNext()) {
      ClassDescriptor cn=(ClassDescriptor)it.next();
      outclassdefs.println("struct "+cn.getSafeSymbol()+";");
      
      if(state.MGC) {
        // TODO add version for normal Java later
      if((cn.getNumStaticFields() != 0) || (cn.getNumStaticBlocks() != 0)) {
        // this class has static fields/blocks, need to add a global flag to 
        // indicate if its static fields have been initialized and/or if its
        // static blocks have been executed
        outglobaldefs.println("  int "+cn.getSafeSymbol()+"static_block_exe_flag;");
      }
      
      // for each class, create a global object
      outglobaldefs.println("  struct ___Object___ "+cn.getSafeSymbol()+"classobj;");
      }
    }
    outclassdefs.println("");
    //Print out definition for array type
    outclassdefs.println("struct "+arraytype+" {");
    outclassdefs.println("  int type;");

    
    additionalClassObjectFields( outclassdefs );


    if (state.EVENTMONITOR) {
      outclassdefs.println("  int objuid;");
    }
    if (state.THREAD) {
      outclassdefs.println("  pthread_t tid;");
      outclassdefs.println("  void * lockentry;");
      outclassdefs.println("  int lockcount;");
    }
    if(state.MGC) {
      outclassdefs.println("  int mutex;");  
      outclassdefs.println("  int objlock;");
      if(state.MULTICOREGC) {
        outclassdefs.println("  int marked;");
      }
    } 
    if (state.TASK) {
      outclassdefs.println("  int flag;");
      if(!state.MULTICORE) {
	outclassdefs.println("  void * flagptr;");
      } else {
        outclassdefs.println("  int version;");
        outclassdefs.println("  int * lock;");  // lock entry for this obj
        outclassdefs.println("  int mutex;");  
        outclassdefs.println("  int lockcount;");
        if(state.MULTICOREGC) {
          outclassdefs.println("  int marked;");
        }
      }
      if(state.OPTIONAL) {
	outclassdefs.println("  int numfses;");
	outclassdefs.println("  int * fses;");
      }
    }

    printClassStruct(typeutil.getClass(TypeUtil.ObjectClass), outclassdefs, outglobaldefs);

    if (state.STMARRAY) {
      outclassdefs.println("  int lowindex;");
      outclassdefs.println("  int highindex;");
    }
    if (state.ARRAYPAD)
      outclassdefs.println("  int paddingforarray;");
    if (state.DUALVIEW) {
      outclassdefs.println("  int arrayversion;");
    }

    outclassdefs.println("  int ___length___;");
    outclassdefs.println("};\n");
    
    if(state.MGC) {
      // TODO add version for normal Java later
    outclassdefs.println("");
    //Print out definition for Class type 
    outclassdefs.println("struct Class {");
    outclassdefs.println("  int type;");


    additionalClassObjectFields( outclassdefs );


    if (state.EVENTMONITOR) {
      outclassdefs.println("  int objuid;");
    }
    if (state.THREAD) {
      outclassdefs.println("  pthread_t tid;");
      outclassdefs.println("  void * lockentry;");
      outclassdefs.println("  int lockcount;");
    }
    if(state.MGC) {
      outclassdefs.println("  int mutex;");  
      outclassdefs.println("  int objlock;");
      if(state.MULTICOREGC) {
        outclassdefs.println("  int marked;");
      }
    } 
    if (state.TASK) {
      outclassdefs.println("  int flag;");
      if(!state.MULTICORE) {
        outclassdefs.println("  void * flagptr;");
      } else {
        outclassdefs.println("  int version;");
        outclassdefs.println("  int * lock;");  // lock entry for this obj
        outclassdefs.println("  int mutex;");  
        outclassdefs.println("  int lockcount;");
        if(state.MULTICOREGC) {
          outclassdefs.println("  int marked;");
        }
      }
      if(state.OPTIONAL) {
        outclassdefs.println("  int numfses;");
        outclassdefs.println("  int * fses;");
      }
    }
    printClassStruct(typeutil.getClass(TypeUtil.ObjectClass), outclassdefs, outglobaldefs);
    outclassdefs.println("};\n");
    }
    
    outclassdefs.println("");
    outclassdefs.println("extern int classsize[];");
    outclassdefs.println("extern int hasflags[];");
    outclassdefs.println("extern unsigned INTPTR * pointerarray[];");
    outclassdefs.println("extern int supertypes[];");
    /*if(state.MGC) {
      // TODO add version for normal Java later
    outclassdefs.println("extern char * classname[];");
    }*/
    outclassdefs.println("");
  }

  /** Prints out definitions for generic task structures */

  private void outputTaskTypes(PrintWriter outtask) {
    outtask.println("#ifndef _TASK_H");
    outtask.println("#define _TASK_H");
    outtask.println("struct parameterdescriptor {");
    outtask.println("int type;");
    outtask.println("int numberterms;");
    outtask.println("int *intarray;");
    outtask.println("void * queue;");
    outtask.println("int numbertags;");
    outtask.println("int *tagarray;");
    outtask.println("};");

    outtask.println("struct taskdescriptor {");
    outtask.println("void * taskptr;");
    outtask.println("int numParameters;");
    outtask.println("  int numTotal;");
    outtask.println("struct parameterdescriptor **descriptorarray;");
    outtask.println("char * name;");
    outtask.println("};");
    outtask.println("extern struct taskdescriptor * taskarray[];");
    outtask.println("extern numtasks;");
    outtask.println("#endif");
  }


  private void buildRepairStructs(PrintWriter outrepairstructs) {
    Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
    while(classit.hasNext()) {
      ClassDescriptor cn=(ClassDescriptor)classit.next();
      outrepairstructs.println("structure "+cn.getSymbol()+" {");
      outrepairstructs.println("  int __type__;");
      if (state.TASK) {
	outrepairstructs.println("  int __flag__;");
	if(!state.MULTICORE) {
	  outrepairstructs.println("  int __flagptr__;");
	}
      }
      printRepairStruct(cn, outrepairstructs);
      outrepairstructs.println("}\n");
    }

    for(int i=0; i<state.numArrays(); i++) {
      TypeDescriptor tdarray=arraytable[i];
      TypeDescriptor tdelement=tdarray.dereference();
      outrepairstructs.println("structure "+arraytype+"_"+state.getArrayNumber(tdarray)+" {");
      outrepairstructs.println("  int __type__;");
      printRepairStruct(typeutil.getClass(TypeUtil.ObjectClass), outrepairstructs);
      outrepairstructs.println("  int length;");
      /*
         // Need to add support to repair tool for this
         if (tdelement.isClass()||tdelement.isArray())
          outrepairstructs.println("  "+tdelement.getRepairSymbol()+" * elem[this.length];");
         else
          outrepairstructs.println("  "+tdelement.getRepairSymbol()+" elem[this.length];");
       */
      outrepairstructs.println("}\n");
    }
  }

  private void printRepairStruct(ClassDescriptor cn, PrintWriter output) {
    ClassDescriptor sp=cn.getSuperDesc();
    if (sp!=null)
      printRepairStruct(sp, output);

    Vector fields=(Vector)fieldorder.get(cn);

    for(int i=0; i<fields.size(); i++) {
      FieldDescriptor fd=(FieldDescriptor)fields.get(i);
      if (fd.getType().isArray()) {
	output.println("  "+arraytype+"_"+ state.getArrayNumber(fd.getType()) +" * "+fd.getSymbol()+";");
      } else if (fd.getType().isClass())
	output.println("  "+fd.getType().getRepairSymbol()+" * "+fd.getSymbol()+";");
      else if (fd.getType().isFloat())
	output.println("  int "+fd.getSymbol()+"; /* really float */");
      else
	output.println("  "+fd.getType().getRepairSymbol()+" "+fd.getSymbol()+";");
    }
  }

  /** This method outputs TaskDescriptor information */
  private void generateTaskDescriptor(PrintWriter output, FlatMethod fm, TaskDescriptor task) {
    for (int i=0; i<task.numParameters(); i++) {
      VarDescriptor param_var=task.getParameter(i);
      TypeDescriptor param_type=task.getParamType(i);
      FlagExpressionNode param_flag=task.getFlag(param_var);
      TagExpressionList param_tag=task.getTag(param_var);

      int dnfterms;
      if (param_flag==null) {
	output.println("int parameterdnf_"+i+"_"+task.getSafeSymbol()+"[]={");
	output.println("0x0, 0x0 };");
	dnfterms=1;
      } else {
	DNFFlag dflag=param_flag.getDNF();
	dnfterms=dflag.size();

	Hashtable flags=(Hashtable)flagorder.get(param_type.getClassDesc());
	output.println("int parameterdnf_"+i+"_"+task.getSafeSymbol()+"[]={");
	for(int j=0; j<dflag.size(); j++) {
	  if (j!=0)
	    output.println(",");
	  Vector term=dflag.get(j);
	  int andmask=0;
	  int checkmask=0;
	  for(int k=0; k<term.size(); k++) {
	    DNFFlagAtom dfa=(DNFFlagAtom)term.get(k);
	    FlagDescriptor fd=dfa.getFlag();
	    boolean negated=dfa.getNegated();
	    int flagid=1<<((Integer)flags.get(fd)).intValue();
	    andmask|=flagid;
	    if (!negated)
	      checkmask|=flagid;
	  }
	  output.print("0x"+Integer.toHexString(andmask)+", 0x"+Integer.toHexString(checkmask));
	}
	output.println("};");
      }

      output.println("int parametertag_"+i+"_"+task.getSafeSymbol()+"[]={");
      //BUG...added next line to fix, test with any task program
      if (param_tag!=null)
	for(int j=0; j<param_tag.numTags(); j++) {
	  if (j!=0)
	    output.println(",");
	  /* for each tag we need */
	  /* which slot it is */
	  /* what type it is */
	  TagVarDescriptor tvd=(TagVarDescriptor)task.getParameterTable().get(param_tag.getName(j));
	  TempDescriptor tmp=param_tag.getTemp(j);
	  int slot=fm.getTagInt(tmp);
	  output.println(slot+", "+state.getTagId(tvd.getTag()));
	}
      output.println("};");

      output.println("struct parameterdescriptor parameter_"+i+"_"+task.getSafeSymbol()+"={");
      output.println("/* type */"+param_type.getClassDesc().getId()+",");
      output.println("/* number of DNF terms */"+dnfterms+",");
      output.println("parameterdnf_"+i+"_"+task.getSafeSymbol()+",");
      output.println("0,");
      //BUG, added next line to fix and else statement...test
      //with any task program
      if (param_tag!=null)
	output.println("/* number of tags */"+param_tag.numTags()+",");
      else
	output.println("/* number of tags */ 0,");
      output.println("parametertag_"+i+"_"+task.getSafeSymbol());
      output.println("};");
    }


    output.println("struct parameterdescriptor * parameterdescriptors_"+task.getSafeSymbol()+"[] = {");
    for (int i=0; i<task.numParameters(); i++) {
      if (i!=0)
	output.println(",");
      output.print("&parameter_"+i+"_"+task.getSafeSymbol());
    }
    output.println("};");

    output.println("struct taskdescriptor task_"+task.getSafeSymbol()+"={");
    output.println("&"+task.getSafeSymbol()+",");
    output.println("/* number of parameters */" +task.numParameters() + ",");
    int numtotal=task.numParameters()+fm.numTags();
    output.println("/* number total parameters */" +numtotal + ",");
    output.println("parameterdescriptors_"+task.getSafeSymbol()+",");
    output.println("\""+task.getSymbol()+"\"");
    output.println("};");
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
    MethodDescriptor[][] virtualtable=null;
    LocalityBinding[][] lbvirtualtable=null;
    if (state.DSM||state.SINGLETM)
      lbvirtualtable=new LocalityBinding[state.numClasses()+state.numArrays()][maxcount];
    else
      virtualtable=new MethodDescriptor[state.numClasses()+state.numArrays()][maxcount];

    /* Fill in virtual table */
    classit=state.getClassSymbolTable().getDescriptorsIterator();
    while(classit.hasNext()) {
      ClassDescriptor cd=(ClassDescriptor)classit.next();
      if(cd.isInterface()) {
        continue;
      }
      if (state.DSM||state.SINGLETM)
	fillinRow(cd, lbvirtualtable, cd.getId());
      else
	fillinRow(cd, virtualtable, cd.getId());
    }

    ClassDescriptor objectcd=typeutil.getClass(TypeUtil.ObjectClass);
    Iterator arrayit=state.getArrayIterator();
    while(arrayit.hasNext()) {
      TypeDescriptor td=(TypeDescriptor)arrayit.next();
      int id=state.getArrayNumber(td);
      if (state.DSM||state.SINGLETM)
	fillinRow(objectcd, lbvirtualtable, id+state.numClasses());
      else
	fillinRow(objectcd, virtualtable, id+state.numClasses());
    }

    outvirtual.print("void * virtualtable[]={");
    boolean needcomma=false;
    for(int i=0; i<state.numClasses()+state.numArrays(); i++) {
      for(int j=0; j<maxcount; j++) {
	if (needcomma)
	  outvirtual.print(", ");
	if ((state.DSM||state.SINGLETM)&&lbvirtualtable[i][j]!=null) {
	  LocalityBinding lb=lbvirtualtable[i][j];
	  MethodDescriptor md=lb.getMethod();
	  outvirtual.print("& "+md.getClassDesc().getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor());
	} else if (!(state.DSM||state.SINGLETM)&&virtualtable[i][j]!=null) {
	  MethodDescriptor md=virtualtable[i][j];
	  outvirtual.print("& "+md.getClassDesc().getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor());
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

  private void fillinRow(ClassDescriptor cd, MethodDescriptor[][] virtualtable, int rownum) {
    /* Get inherited methods */
    if (cd.getSuperDesc()!=null)
      fillinRow(cd.getSuperDesc(), virtualtable, rownum);
    if(state.MGC) {
      // TODO add version for normal Java later
      Iterator it_sifs = cd.getSuperInterfaces();
      while(it_sifs.hasNext()) {
        ClassDescriptor superif = (ClassDescriptor)it_sifs.next();
        fillinRow(superif, virtualtable, rownum);
      }
    }
    /* Override them with our methods */
    for(Iterator it=cd.getMethods(); it.hasNext();) {
      MethodDescriptor md=(MethodDescriptor)it.next();
      if (md.isStatic()||md.getReturnType()==null)
	continue;
      int methodnum=virtualcalls.getMethodNumber(md);
      virtualtable[rownum][methodnum]=md;
    }
  }

  private void fillinRow(ClassDescriptor cd, LocalityBinding[][] virtualtable, int rownum) {
    /* Get inherited methods */
    if (cd.getSuperDesc()!=null)
      fillinRow(cd.getSuperDesc(), virtualtable, rownum);
    /* Override them with our methods */
    if (locality.getClassBindings(cd)!=null)
      for(Iterator<LocalityBinding> lbit=locality.getClassBindings(cd).iterator(); lbit.hasNext();) {
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

  private void generateSizeArray(PrintWriter outclassdefs) {
    outclassdefs.print("extern struct prefetchCountStats * evalPrefetch;\n");
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


    Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
    cdarray=new ClassDescriptor[state.numClasses()];
    cdarray[0] = null;
    while(it.hasNext()) {
      ClassDescriptor cd=(ClassDescriptor)it.next();
      if(!cd.isInterface()) {
        cdarray[cd.getId()]=cd;
      }
    }

    arraytable=new TypeDescriptor[state.numArrays()];

    Iterator arrayit=state.getArrayIterator();
    while(arrayit.hasNext()) {
      TypeDescriptor td=(TypeDescriptor)arrayit.next();
      int id=state.getArrayNumber(td);
      arraytable[id]=td;
    }



    /* Print out types */
    outclassdefs.println("/* ");
    for(int i=0; i<state.numClasses(); i++) {
      ClassDescriptor cd=cdarray[i];
      if(cd == null) {
        outclassdefs.println("NULL " + i);
      } else {
        outclassdefs.println(cd +"  "+i);
      }
    }

    for(int i=0; i<state.numArrays(); i++) {
      TypeDescriptor arraytd=arraytable[i];
      outclassdefs.println(arraytd.toPrettyString() +"  "+(i+state.numClasses()));
    }

    outclassdefs.println("*/");


    outclassdefs.print("int classsize[]={");

    boolean needcomma=false;
    for(int i=0; i<state.numClasses(); i++) {
      if (needcomma)
	outclassdefs.print(", ");
      if(i>0) {
        outclassdefs.print("sizeof(struct "+cdarray[i].getSafeSymbol()+")");
      } else {
        outclassdefs.print("0");
      }
      needcomma=true;
    }


    for(int i=0; i<state.numArrays(); i++) {
      if (needcomma)
	outclassdefs.print(", ");
      TypeDescriptor tdelement=arraytable[i].dereference();
      if (tdelement.isArray()||tdelement.isClass())
	outclassdefs.print("sizeof(void *)");
      else
	outclassdefs.print("sizeof("+tdelement.getSafeSymbol()+")");
      needcomma=true;
    }

    outclassdefs.println("};");

    ClassDescriptor objectclass=typeutil.getClass(TypeUtil.ObjectClass);
    needcomma=false;
    outclassdefs.print("int typearray[]={");
    for(int i=0; i<state.numClasses(); i++) {
      ClassDescriptor cd=cdarray[i];
      ClassDescriptor supercd=i>0?cd.getSuperDesc():null;
      if (needcomma)
	outclassdefs.print(", ");
      if (supercd==null)
	outclassdefs.print("-1");
      else
	outclassdefs.print(supercd.getId());
      needcomma=true;
    }

    for(int i=0; i<state.numArrays(); i++) {
      TypeDescriptor arraytd=arraytable[i];
      ClassDescriptor arraycd=arraytd.getClassDesc();
      if (arraycd==null) {
	if (needcomma)
	  outclassdefs.print(", ");
	outclassdefs.print(objectclass.getId());
	needcomma=true;
	continue;
      }
      ClassDescriptor cd=arraycd.getSuperDesc();
      int type=-1;
      while(cd!=null) {
	TypeDescriptor supertd=new TypeDescriptor(cd);
	supertd.setArrayCount(arraytd.getArrayCount());
	type=state.getArrayNumber(supertd);
	if (type!=-1) {
	  type+=state.numClasses();
	  break;
	}
	cd=cd.getSuperDesc();
      }
      if (needcomma)
	outclassdefs.print(", ");
      outclassdefs.print(type);
      needcomma=true;
    }

    outclassdefs.println("};");

    needcomma=false;


    outclassdefs.print("int typearray2[]={");
    for(int i=0; i<state.numArrays(); i++) {
      TypeDescriptor arraytd=arraytable[i];
      ClassDescriptor arraycd=arraytd.getClassDesc();
      if (arraycd==null) {
	if (needcomma)
	  outclassdefs.print(", ");
	outclassdefs.print("-1");
	needcomma=true;
	continue;
      }
      ClassDescriptor cd=arraycd.getSuperDesc();
      int level=arraytd.getArrayCount()-1;
      int type=-1;
      for(; level>0; level--) {
	TypeDescriptor supertd=new TypeDescriptor(objectclass);
	supertd.setArrayCount(level);
	type=state.getArrayNumber(supertd);
	if (type!=-1) {
	  type+=state.numClasses();
	  break;
	}
      }
      if (needcomma)
	outclassdefs.print(", ");
      outclassdefs.print(type);
      needcomma=true;
    }

    outclassdefs.println("};");
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

    for(Iterator nodeit=fm.getNodeSet().iterator(); nodeit.hasNext();) {
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
      for(Iterator<TempDescriptor> tmpit=backuptable.get(lb).values().iterator(); tmpit.hasNext();) {
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

  private void generateLayoutStructs(PrintWriter output) {
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
	  output.print("((unsigned INTPTR)&(((struct "+cn.getSafeSymbol() +" *)0)->"+fd.getSafeSymbol()+"))");
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

  /** Print out table to give us supertypes */
  private void generateSuperTypeTable(PrintWriter output) {
    output.println("int supertypes[]={");
    boolean needcomma=false;
    for(int i=0; i<state.numClasses(); i++) {
      ClassDescriptor cn=cdarray[i];
      if (needcomma)
	output.println(",");
      needcomma=true;
      if ((cn != null) && (cn.getSuperDesc()!=null)) {
	ClassDescriptor cdsuper=cn.getSuperDesc();
	output.print(cdsuper.getId());
      } else
	output.print("-1");
    }
    output.println("};");
  }
  
  /** Print out table to give us classnames */
  /*private void generateClassNameTable(PrintWriter output) {
    output.println("char * classname[]={");
    boolean needcomma=false;
    for(int i=0; i<state.numClasses(); i++) {
      ClassDescriptor cn=cdarray[i];
      if (needcomma)
    output.println(",");
      needcomma=true;
      if ((cn != null) && (cn.getSuperDesc()!=null)) {
    output.print("\"" + cn.getSymbol() + "\"");
      } else
    output.print("\"\"");
    }
    output.println("};");
  }*/

  /** Force consistent field ordering between inherited classes. */

  private void printClassStruct(ClassDescriptor cn, PrintWriter classdefout, PrintWriter globaldefout) {

    ClassDescriptor sp=cn.getSuperDesc();
    if (sp!=null)
      printClassStruct(sp, classdefout, globaldefout);

    if (!fieldorder.containsKey(cn)) {
      Vector fields=new Vector();
      fieldorder.put(cn,fields);
      Vector fieldvec=cn.getFieldVec();
      for(int i=0;i<fieldvec.size();i++) {
	FieldDescriptor fd=(FieldDescriptor)fieldvec.get(i);
	if ((sp==null||!sp.getFieldTable().contains(fd.getSymbol())))
	  fields.add(fd);
      }
    }
    Vector fields=(Vector)fieldorder.get(cn);

    for(int i=0; i<fields.size(); i++) {
      FieldDescriptor fd=(FieldDescriptor)fields.get(i);
      if (state.MGC && fd.getType().isClass()
          && fd.getType().getClassDesc().isEnum()) {
        classdefout.println("  int " + fd.getSafeSymbol() + ";");
      } else if (fd.getType().isClass()||fd.getType().isArray()) {
        if ((state.MGC) && (fd.isStatic())) {
          // TODO add version for normal Java later
          // static field
          if(fd.isVolatile()) {
            globaldefout.println("  volatile struct "+fd.getType().getSafeSymbol()+ " * "+cn.getSafeSymbol()+fd.getSafeSymbol()+";");
          } else {
            globaldefout.println("  struct "+fd.getType().getSafeSymbol()+ " * "+cn.getSafeSymbol()+fd.getSafeSymbol()+";");
          }
          classdefout.println("  struct "+fd.getType().getSafeSymbol()+" ** "+fd.getSafeSymbol()+";");
        } else if ((state.MGC) && (fd.isVolatile())) {
          // TODO add version for normal Java later
          // static field
          globaldefout.println("  volatile struct "+fd.getType().getSafeSymbol()+ " * "+cn.getSafeSymbol()+fd.getSafeSymbol()+";");
          classdefout.println("  struct"+fd.getType().getSafeSymbol()+" ** "+fd.getSafeSymbol()+";");
        } else {
	classdefout.println("  struct "+fd.getType().getSafeSymbol()+" * "+fd.getSafeSymbol()+";");
        }
      } else if ((state.MGC) && (fd.isStatic())) {
        // TODO add version for normal Java later
        // static field
        if(fd.isVolatile()) {
          globaldefout.println("  volatile "+fd.getType().getSafeSymbol()+ " "+cn.getSafeSymbol()+fd.getSafeSymbol()+";");
        } else {
          globaldefout.println("  "+fd.getType().getSafeSymbol()+ " "+cn.getSafeSymbol()+fd.getSafeSymbol()+";");
        }
        classdefout.println("  "+fd.getType().getSafeSymbol()+" * "+fd.getSafeSymbol()+";");
      } else if ((state.MGC) && (fd.isVolatile())) {
        // TODO add version for normal Java later
        // static field
        globaldefout.println("  volatile "+fd.getType().getSafeSymbol()+ " "+cn.getSafeSymbol()+fd.getSafeSymbol()+";");
        classdefout.println("  "+fd.getType().getSafeSymbol()+" * "+fd.getSafeSymbol()+";");
      } else
	classdefout.println("  "+fd.getType().getSafeSymbol()+" "+fd.getSafeSymbol()+";");
    }
  }


  /* Map flags to integers consistently between inherited
   * classes. */

  protected void mapFlags(ClassDescriptor cn) {
    ClassDescriptor sp=cn.getSuperDesc();
    if (sp!=null)
      mapFlags(sp);
    int max=0;
    if (!flagorder.containsKey(cn)) {
      Hashtable flags=new Hashtable();
      flagorder.put(cn,flags);
      if (sp!=null) {
	Hashtable superflags=(Hashtable)flagorder.get(sp);
	Iterator superflagit=superflags.keySet().iterator();
	while(superflagit.hasNext()) {
	  FlagDescriptor fd=(FlagDescriptor)superflagit.next();
	  Integer number=(Integer)superflags.get(fd);
	  flags.put(fd, number);
	  if ((number.intValue()+1)>max)
	    max=number.intValue()+1;
	}
      }

      Iterator flagit=cn.getFlags();
      while(flagit.hasNext()) {
	FlagDescriptor fd=(FlagDescriptor)flagit.next();
	if (sp==null||!sp.getFlagTable().contains(fd.getSymbol()))
	  flags.put(fd, new Integer(max++));
      }
    }
  }


  /** This function outputs (1) structures that parameters are
   * passed in (when PRECISE GC is enabled) and (2) function
   * prototypes for the methods */

  protected void generateCallStructs(ClassDescriptor cn, PrintWriter classdefout, PrintWriter output, PrintWriter headersout, PrintWriter globaldefout) {
    /* Output class structure */
    classdefout.println("struct "+cn.getSafeSymbol()+" {");
    classdefout.println("  int type;");


    additionalClassObjectFields( classdefout );


    if (state.EVENTMONITOR) {
      classdefout.println("  int objuid;");
    }
    if (state.THREAD) {
      classdefout.println("  pthread_t tid;");
      classdefout.println("  void * lockentry;");
      classdefout.println("  int lockcount;");
    }
    if(state.MGC) {
      classdefout.println("  int mutex;");  
      classdefout.println("  int objlock;");
      if(state.MULTICOREGC) {
        classdefout.println("  int marked;");
      }
    } 
    if (state.TASK) {
      classdefout.println("  int flag;");
      if((!state.MULTICORE) || (cn.getSymbol().equals("TagDescriptor"))) {
	classdefout.println("  void * flagptr;");
      } else if (state.MULTICORE) {
	classdefout.println("  int version;");
    classdefout.println("  int * lock;");  // lock entry for this obj
    classdefout.println("  int mutex;");  
    classdefout.println("  int lockcount;");
    if(state.MULTICOREGC) {
      classdefout.println("  int marked;");
    }
      }
      if (state.OPTIONAL) {
	classdefout.println("  int numfses;");
	classdefout.println("  int * fses;");
      }
    }
    printClassStruct(cn, classdefout, globaldefout);
    classdefout.println("};\n");

    if (state.DSM||state.SINGLETM) {
      /* Cycle through LocalityBindings */
      HashSet<MethodDescriptor> nativemethods=new HashSet<MethodDescriptor>();
      Set<LocalityBinding> lbset=locality.getClassBindings(cn);
      if (lbset!=null) {
	for(Iterator<LocalityBinding> lbit=lbset.iterator(); lbit.hasNext();) {
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
      for(Iterator methodit=cn.getMethods(); methodit.hasNext();) {
	MethodDescriptor md=(MethodDescriptor)methodit.next();
	if (md.getModifiers().isNative()&&!nativemethods.contains(md)) {
	  //Need to build param structure for library code
	  FlatMethod fm=state.getMethodFlat(md);
	  generateTempStructs(fm, null);
	  generateMethodParam(cn, md, null, output);
	}
      }

    } else
      for(Iterator methodit=cn.getMethods(); methodit.hasNext();) {
	MethodDescriptor md=(MethodDescriptor)methodit.next();
	generateMethod(cn, md, null, headersout, output);
      }
  }

  private void generateMethodParam(ClassDescriptor cn, MethodDescriptor md, LocalityBinding lb, PrintWriter output) {
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
	output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
      }
      output.println("};\n");
    }
  }

  private void generateMethod(ClassDescriptor cn, MethodDescriptor md, LocalityBinding lb, PrintWriter headersout, PrintWriter output) {
    FlatMethod fm=state.getMethodFlat(md);
    generateTempStructs(fm, lb);

    ParamsObject objectparams=(ParamsObject) paramstable.get(lb!=null ? lb : md);
    TempObject objecttemps=(TempObject) tempstable.get(lb!=null ? lb : md);

    generateMethodParam(cn, md, lb, output);

    /* Output temp structure */
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      if (state.DSM||state.SINGLETM)
	output.println("struct "+cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_locals {");
      else
	output.println("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_locals {");
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
    if (state.DSM||state.SINGLETM) {
      headersout.println("#define D"+cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+" 1");
    } else {
      headersout.println("#define D"+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+" 1");
    }
    /* First the return type */
    if (md.getReturnType()!=null) {
      if (md.getReturnType().isClass()||md.getReturnType().isArray())
	headersout.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
      else
	headersout.print(md.getReturnType().getSafeSymbol()+" ");
    } else
      //catch the constructor case
      headersout.print("void ");

    /* Next the method name */
    if (state.DSM||state.SINGLETM) {
      headersout.print(cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");
    } else {
      headersout.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");
    }
    boolean printcomma=false;
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      if (state.DSM||state.SINGLETM) {
	headersout.print("struct "+cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * "+paramsprefix);
      } else
	headersout.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * "+paramsprefix);
      printcomma=true;
    }

    /*  Output parameter list*/
    for(int i=0; i<objectparams.numPrimitives(); i++) {
      TempDescriptor temp=objectparams.getPrimitive(i);
      if (printcomma)
	headersout.print(", ");
      printcomma=true;
      if (temp.getType().isClass()||temp.getType().isArray())
	headersout.print("struct " + temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol());
      else
	headersout.print(temp.getType().getSafeSymbol()+" "+temp.getSafeSymbol());
    }
    headersout.println(");\n");
  }


  /** This function outputs (1) structures that parameters are
   * passed in (when PRECISE GC is enabled) and (2) function
   * prototypes for the tasks */

  private void generateTaskStructs(PrintWriter output, PrintWriter headersout) {
    /* Cycle through tasks */
    Iterator taskit=state.getTaskSymbolTable().getDescriptorsIterator();

    while(taskit.hasNext()) {
      /* Classify parameters */
      TaskDescriptor task=(TaskDescriptor)taskit.next();
      FlatMethod fm=state.getMethodFlat(task);
      generateTempStructs(fm, null);

      ParamsObject objectparams=(ParamsObject) paramstable.get(task);
      TempObject objecttemps=(TempObject) tempstable.get(task);

      /* Output parameter structure */
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	output.println("struct "+task.getSafeSymbol()+"_params {");
	output.println("  int size;");
	output.println("  void * next;");
	for(int i=0; i<objectparams.numPointers(); i++) {
	  TempDescriptor temp=objectparams.getPointer(i);
	  output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
	}

	output.println("};\n");
	if ((objectparams.numPointers()+fm.numTags())>maxtaskparams) {
	  maxtaskparams=objectparams.numPointers()+fm.numTags();
	}
      }

      /* Output temp structure */
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	output.println("struct "+task.getSafeSymbol()+"_locals {");
	output.println("  int size;");
	output.println("  void * next;");
	for(int i=0; i<objecttemps.numPointers(); i++) {
	  TempDescriptor temp=objecttemps.getPointer(i);
	  if (temp.getType().isNull())
	    output.println("  void * "+temp.getSafeSymbol()+";");
	  else if(temp.getType().isTag())
	    output.println("  struct "+
	                   (new TypeDescriptor(typeutil.getClass(TypeUtil.TagClass))).getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
	  else
	    output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
	}
	output.println("};\n");
      }

      /* Output task declaration */
      headersout.print("void " + task.getSafeSymbol()+"(");

      boolean printcomma=false;
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	headersout.print("struct "+task.getSafeSymbol()+"_params * "+paramsprefix);
      } else
	headersout.print("void * parameterarray[]");
      headersout.println(");\n");
    }
  }

  /***** Generate code for FlatMethod fm. *****/

  Hashtable<FlatAtomicEnterNode, AtomicRecord> atomicmethodmap;
  static int atomicmethodcount=0;


  BranchAnalysis branchanalysis;
  private void generateFlatMethod(FlatMethod fm, LocalityBinding lb, PrintWriter output) {
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
      for(Iterator<FlatNode> fnit=fm.getNodeSet().iterator();fnit.hasNext();) {
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


	  for(Iterator<TempDescriptor> it=liveinto.iterator(); it.hasNext();) {
	    TempDescriptor tmp=it.next();
	    //remove the pointers
	    if (tmp.getType().isPtr()) {
	      it.remove();
	    } else {
	      //let's print it here
	      output.println(tmp.getType().getSafeSymbol()+" "+tmp.getSafeSymbol()+";");
	    }
	  }
	  for(Iterator<TempDescriptor> it=liveout.iterator(); it.hasNext();) {
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
	  for(Iterator<TempDescriptor> tmpit=alltemps.iterator();tmpit.hasNext();) {
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
      for(Iterator<AtomicRecord> arit=arset.iterator();arit.hasNext();) {
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
      if (type.isNull())
	output.println("   void * "+td.getSafeSymbol()+";");
      else if (state.MGC && type.isClass() && type.getClassDesc().isEnum()) {
        output.println("   int " + td.getSafeSymbol() + ";");
      } else if (type.isClass()||type.isArray())
	output.println("   struct "+type.getSafeSymbol()+" * "+td.getSafeSymbol()+";");
      else
	output.println("   "+type.getSafeSymbol()+" "+td.getSafeSymbol()+";");
    }



    additionalCodeAtTopFlatMethodBody( output, fm );
    


    /* Check to see if we need to do a GC if this is a
     * multi-threaded program...*/

    if (((state.OOOJAVA||state.THREAD||state.DSM||state.SINGLETM)&&GENERATEPRECISEGC) 
        || this.state.MULTICOREGC) {
      //Don't bother if we aren't in recursive methods...The loops case will catch it
      if (callgraph.getAllMethods(md).contains(md)) {
	if (state.DSM&&lb.isAtomic())
	  output.println("if (needtocollect) checkcollect2("+localsprefixaddr+");");
	else if (this.state.MULTICOREGC) {
	  output.println("if(gcflag) gc("+localsprefixaddr+");");
	} else {
	  output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
	}
      }
    }
    
    if(state.MGC) {
      // TODO add version for normal Java later
    if(fm.getMethod().isStaticBlock()) {
      // a static block, check if it has been executed
      output.println("  if(global_defs_p->" + cn.getSafeSymbol()+"static_block_exe_flag != 0) {");
      output.println("    return;");
      output.println("  }");
      output.println("");
    }
    if((!fm.getMethod().isStaticBlock()) && (fm.getMethod().getReturnType() == null) && (cn != null)){
      // is a constructor, check and output initialization of the static fields
      // here does not initialize the static fields of the class, instead it 
      // redirect the corresponding fields in the object to the global_defs_p
      Vector fields=(Vector)fieldorder.get(cn);

      for(int i=0; i<fields.size(); i++) {
        FieldDescriptor fd=(FieldDescriptor)fields.get(i);
        if(fd.isStatic()) {
          // static field
          output.println(generateTemp(fm,fm.getParameter(0),lb)+"->"+fd.getSafeSymbol()+"=&(global_defs_p->"+cn.getSafeSymbol()+fd.getSafeSymbol()+");");
        }
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
	  generateFlatNode(fm, lb, current_node, output);
	} else {
	  //store primitive variables in out set
	  AtomicRecord ar=atomicmethodmap.get((FlatAtomicEnterNode)first);
	  Set<TempDescriptor> liveout=ar.liveout;
	  for(Iterator<TempDescriptor> tmpit=liveout.iterator();tmpit.hasNext();) {
	    TempDescriptor tmp=tmpit.next();
	    output.println("primitives->"+tmp.getSafeSymbol()+"="+tmp.getSafeSymbol()+";");
	  }
	}
	if (state.OOOJAVA && stopset!=null) {
	  assert first.getPrev( 0 ) instanceof FlatSESEEnterNode;
	  assert current_node       instanceof FlatSESEExitNode;
	  FlatSESEEnterNode fsen = (FlatSESEEnterNode) first.getPrev( 0 );
	  FlatSESEExitNode  fsxn = (FlatSESEExitNode)  current_node;
	  assert fsen.getFlatExit().equals( fsxn );
	  assert fsxn.getFlatEnter().equals( fsen );
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
	if (state.OOOJAVA && 
	    current_node.kind()==FKind.FlatSESEEnterNode
	   ) {
	  FlatSESEEnterNode fsen = (FlatSESEEnterNode)current_node;
	  generateFlatNode(fm, lb, current_node, output);
	  nextnode=fsen.getFlatExit().getNext(0);
	} else if (state.DELAYCOMP) {
	  boolean specialprimitive=false;
	  //skip literals...no need to add extra overhead
	  if (storeset!=null&&storeset.contains(current_node)&&current_node.kind()==FKind.FlatLiteralNode) {
	    TypeDescriptor typedesc=((FlatLiteralNode)current_node).getType();
	    if (!typedesc.isClass()&&!typedesc.isArray()) {
	      specialprimitive=true;
	    }
	  }

	  if (genset==null||genset.contains(current_node)||specialprimitive)
	    generateFlatNode(fm, lb, current_node, output);
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
		  output.println("STOREPTR("+generateTemp(fm, wrtmp,lb)+");/* "+current_node.nodeid+" */");
		} else {
		  output.println("STOREPTRNOLOCK("+generateTemp(fm, wrtmp,lb)+");/* "+current_node.nodeid+" */");
		}
	      } else {
		output.println("STORE"+wrtmp.getType().getSafeDescriptor()+"("+generateTemp(fm, wrtmp, lb)+");/* "+current_node.nodeid+" */");
	      }
	    } else {
	      //need to read value read by previous node
	      if (wrtmp.getType().isPtr()) {
		output.println("RESTOREPTR("+generateTemp(fm, wrtmp,lb)+");/* "+current_node.nodeid+" */");
	      } else {
		output.println("RESTORE"+wrtmp.getType().getSafeDescriptor()+"("+generateTemp(fm, wrtmp, lb)+"); /* "+current_node.nodeid+" */");		
	      }
	    }
	  }
	  nextnode=current_node.getNext(0);
	} else {
	  output.print("   ");
	  generateFlatNode(fm, lb, current_node, output);
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
		generateFlatCondBranch(fm, lb, (FlatCondBranch)current_node, "L"+nodetolabel.get(current_node.getNext(1)), output);
	    } else {
	      //which side to execute
	      computeside=true;
	    }
	  } else {
	    if (genset.contains(current_node)) {
	      generateFlatCondBranch(fm, lb, (FlatCondBranch)current_node, "L"+nodetolabel.get(current_node.getNext(1)), output);	      
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
	  generateFlatCondBranch(fm, lb, (FlatCondBranch)current_node, "L"+nodetolabel.get(current_node.getNext(1)), output);
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
      String dst=generateTemp(fm, fsen.getDst(), lb);
      String src=generateTemp(fm, fsen.getSrc(), lb);
      String index=generateTemp(fm, fsen.getIndex(), lb);      
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
      String src=generateTemp(fm, fen.getSrc(), lb);
      String index=generateTemp(fm, fen.getIndex(), lb);
      TypeDescriptor elementtype=fen.getSrc().getType().dereference();
      String dst=generateTemp(fm, fen.getDst(), lb);
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
    for(Iterator<FlatNode> it=targets.iterator();it.hasNext();) {
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

  protected Hashtable<FlatNode, Integer> assignLabels(FlatNode first) {
    return assignLabels(first, null);
  }

  protected Hashtable<FlatNode, Integer> assignLabels(FlatNode first, Set<FlatNode> lastset) {
    HashSet tovisit=new HashSet();
    HashSet visited=new HashSet();
    int labelindex=0;
    Hashtable<FlatNode, Integer> nodetolabel=new Hashtable<FlatNode, Integer>();
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
  protected String generateTemp(FlatMethod fm, TempDescriptor td, LocalityBinding lb) {
    MethodDescriptor md=fm.getMethod();
    TaskDescriptor task=fm.getTask();
    TempObject objecttemps=(TempObject) tempstable.get(lb!=null ? lb : md!=null ? md : task);

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



  protected void generateFlatNode(FlatMethod fm, LocalityBinding lb, FlatNode fn, PrintWriter output) {


    additionalCodePreNode( fm, lb, fn, output );


    switch(fn.kind()) {
    case FKind.FlatAtomicEnterNode:
      generateFlatAtomicEnterNode(fm, lb, (FlatAtomicEnterNode) fn, output);
      break;

    case FKind.FlatAtomicExitNode:
      generateFlatAtomicExitNode(fm, lb, (FlatAtomicExitNode) fn, output);
      break;

    case FKind.FlatInstanceOfNode:
      generateFlatInstanceOfNode(fm, lb, (FlatInstanceOfNode)fn, output);
      break;

    case FKind.FlatSESEEnterNode:
      generateFlatSESEEnterNode(fm, lb, (FlatSESEEnterNode)fn, output);
      break;

    case FKind.FlatSESEExitNode:
      generateFlatSESEExitNode(fm, lb, (FlatSESEExitNode)fn, output);
      break;
      
    case FKind.FlatWriteDynamicVarNode:
      generateFlatWriteDynamicVarNode(fm, lb, (FlatWriteDynamicVarNode)fn, output);
      break;

    case FKind.FlatGlobalConvNode:
      generateFlatGlobalConvNode(fm, lb, (FlatGlobalConvNode) fn, output);
      break;

    case FKind.FlatTagDeclaration:
      generateFlatTagDeclaration(fm, lb, (FlatTagDeclaration) fn,output);
      break;

    case FKind.FlatCall:
      generateFlatCall(fm, lb, (FlatCall) fn,output);
      break;

    case FKind.FlatFieldNode:
      generateFlatFieldNode(fm, lb, (FlatFieldNode) fn,output);
      break;

    case FKind.FlatElementNode:
      generateFlatElementNode(fm, lb, (FlatElementNode) fn,output);
      break;

    case FKind.FlatSetElementNode:
      generateFlatSetElementNode(fm, lb, (FlatSetElementNode) fn,output);
      break;

    case FKind.FlatSetFieldNode:
      generateFlatSetFieldNode(fm, lb, (FlatSetFieldNode) fn,output);
      break;

    case FKind.FlatNew:
      generateFlatNew(fm, lb, (FlatNew) fn,output);
      break;

    case FKind.FlatOpNode:
      generateFlatOpNode(fm, lb, (FlatOpNode) fn,output);
      break;

    case FKind.FlatCastNode:
      generateFlatCastNode(fm, lb, (FlatCastNode) fn,output);
      break;

    case FKind.FlatLiteralNode:
      generateFlatLiteralNode(fm, lb, (FlatLiteralNode) fn,output);
      break;

    case FKind.FlatReturnNode:
      generateFlatReturnNode(fm, lb, (FlatReturnNode) fn,output);
      break;

    case FKind.FlatNop:
      output.println("/* nop */");
      break;

    case FKind.FlatGenReachNode:
      // this node is just for generating a reach graph
      // in disjointness analysis at a particular program point
      break;

    case FKind.FlatExit:
      output.println("/* exit */");
      break;

    case FKind.FlatBackEdge:
      if (state.SINGLETM&&state.SANDBOX&&(locality.getAtomic(lb).get(fn).intValue()>0)) {
	output.println("if (unlikely((--transaction_check_counter)<=0)) checkObjects();");
      }
      if(state.DSM&&state.SANDBOX&&(locality.getAtomic(lb).get(fn).intValue()>0)) {
        output.println("if (unlikely((--transaction_check_counter)<=0)) checkObjects();");
      }
      if (((state.OOOJAVA||state.THREAD||state.DSM||state.SINGLETM)&&GENERATEPRECISEGC)
          || (this.state.MULTICOREGC)) {
	if(state.DSM&&locality.getAtomic(lb).get(fn).intValue()>0) {
	  output.println("if (needtocollect) checkcollect2("+localsprefixaddr+");");
	} else if(this.state.MULTICOREGC) {
	  output.println("if (gcflag) gc("+localsprefixaddr+");");
	} else {
	  output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
	}
      } else
	output.println("/* nop */");
      break;

    case FKind.FlatCheckNode:
      generateFlatCheckNode(fm, lb, (FlatCheckNode) fn, output);
      break;

    case FKind.FlatFlagActionNode:
      generateFlatFlagActionNode(fm, lb, (FlatFlagActionNode) fn, output);
      break;

    case FKind.FlatPrefetchNode:
      generateFlatPrefetchNode(fm,lb, (FlatPrefetchNode) fn, output);
      break;

    case FKind.FlatOffsetNode:
      generateFlatOffsetNode(fm, lb, (FlatOffsetNode)fn, output);
      break;

    default:
      throw new Error();
    }


    additionalCodePostNode( fm, lb, fn, output );
  }

  public void generateFlatOffsetNode(FlatMethod fm, LocalityBinding lb, FlatOffsetNode fofn, PrintWriter output) {
    output.println("/* FlatOffsetNode */");
    FieldDescriptor fd=fofn.getField();
    output.println(generateTemp(fm, fofn.getDst(),lb)+ " = (short)(int) (&((struct "+fofn.getClassType().getSafeSymbol() +" *)0)->"+ fd.getSafeSymbol()+");");
    output.println("/* offset */");
  }

  public void generateFlatPrefetchNode(FlatMethod fm, LocalityBinding lb, FlatPrefetchNode fpn, PrintWriter output) {
    if (state.PREFETCH) {
      Vector oids = new Vector();
      Vector fieldoffset = new Vector();
      Vector endoffset = new Vector();
      int tuplecount = 0;        //Keeps track of number of prefetch tuples that need to be generated
      for(Iterator it = fpn.hspp.iterator(); it.hasNext();) {
	PrefetchPair pp = (PrefetchPair) it.next();
	Integer statusbase = locality.getNodePreTempInfo(lb,fpn).get(pp.base);
	/* Find prefetches that can generate oid */
	if(statusbase == LocalityAnalysis.GLOBAL) {
	  generateTransCode(fm, lb, pp, oids, fieldoffset, endoffset, tuplecount, locality.getAtomic(lb).get(fpn).intValue()>0, false);
	  tuplecount++;
	} else if (statusbase == LocalityAnalysis.LOCAL) {
	  generateTransCode(fm,lb,pp,oids,fieldoffset,endoffset,tuplecount,false,true);
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
      for (Iterator it = oids.iterator(); it.hasNext();) {
	if (needcomma)
	  output.print(", ");
	output.print(it.next());
	needcomma=true;
      }
      output.println("};");

      /*Create C code for endoffset values */
      output.print("   unsigned short endoffsetarry_[] = {");
      needcomma=false;
      for (Iterator it = endoffset.iterator(); it.hasNext();) {
	if (needcomma)
	  output.print(", ");
	output.print(it.next());
	needcomma=true;
      }
      output.println("};");

      /*Create C code for Field Offset Values */
      output.print("   short fieldarry_[] = {");
      needcomma=false;
      for (Iterator it = fieldoffset.iterator(); it.hasNext();) {
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
    String basestr=generateTemp(fm, pp.base, lb);
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
	  basestr="((struct "+lasttype.getSafeSymbol()+" *)prefptr)->"+fd.getSafeSymbol();
	} else {
	  basestr=basestr+"->"+fd.getSafeSymbol();
	  maybenull=true;
	}
	lasttype=fd.getType();
      } else {
	IndexDescriptor id=(IndexDescriptor)desc;
	indexcheck="((tmpindex=";
	for(int j=0; j<id.tddesc.size(); j++) {
	  indexcheck+=generateTemp(fm, id.getTempDescAt(j), lb)+"+";
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
	  newfieldoffset += generateTemp(fm, id.getTempDescAt(j), lb) + "+";
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



  public void generateFlatGlobalConvNode(FlatMethod fm, LocalityBinding lb, FlatGlobalConvNode fgcn, PrintWriter output) {
    if (lb!=fgcn.getLocality())
      return;
    /* Have to generate flat globalconv */
    if (fgcn.getMakePtr()) {
      if (state.DSM) {
	//DEBUG: output.println("TRANSREAD("+generateTemp(fm, fgcn.getSrc(),lb)+", (unsigned int) "+generateTemp(fm, fgcn.getSrc(),lb)+",\" "+fm+":"+fgcn+"\");");
	   output.println("TRANSREAD("+generateTemp(fm, fgcn.getSrc(),lb)+", (unsigned int) "+generateTemp(fm, fgcn.getSrc(),lb)+");");
      } else {
	if ((dc==null)||!state.READSET&&dc.getNeedTrans(lb, fgcn)||state.READSET&&dc.getNeedWriteTrans(lb, fgcn)) {
	  //need to do translation
	  output.println("TRANSREAD("+generateTemp(fm, fgcn.getSrc(),lb)+", "+generateTemp(fm, fgcn.getSrc(),lb)+", (void *)("+localsprefixaddr+"));");
	} else if (state.READSET&&dc.getNeedTrans(lb, fgcn)) {
	  if (state.HYBRID&&delaycomp.getConv(lb).contains(fgcn)) {
	    output.println("TRANSREADRDFISSION("+generateTemp(fm, fgcn.getSrc(),lb)+", "+generateTemp(fm, fgcn.getSrc(),lb)+");");
	  } else
	    output.println("TRANSREADRD("+generateTemp(fm, fgcn.getSrc(),lb)+", "+generateTemp(fm, fgcn.getSrc(),lb)+");");
	}
      }
    } else {
      /* Need to convert to OID */
      if ((dc==null)||dc.getNeedSrcTrans(lb,fgcn)) {
	if (fgcn.doConvert()||(delaycomp!=null&&delaycomp.needsFission(lb, fgcn.getAtomicEnter())&&atomicmethodmap.get(fgcn.getAtomicEnter()).reallivein.contains(fgcn.getSrc()))) {
	  output.println(generateTemp(fm, fgcn.getSrc(),lb)+"=(void *)COMPOID("+generateTemp(fm, fgcn.getSrc(),lb)+");");
	} else {
	  output.println(generateTemp(fm, fgcn.getSrc(),lb)+"=NULL;");
	}
      }
    }
  }

  public void generateFlatInstanceOfNode(FlatMethod fm,  LocalityBinding lb, FlatInstanceOfNode fion, PrintWriter output) {
    int type;
    if (fion.getType().isArray()) {
      type=state.getArrayNumber(fion.getType())+state.numClasses();
    } else {
      type=fion.getType().getClassDesc().getId();
    }

    if (fion.getType().getSymbol().equals(TypeUtil.ObjectClass))
      output.println(generateTemp(fm, fion.getDst(), lb)+"=1;");
    else
      output.println(generateTemp(fm, fion.getDst(), lb)+"=instanceof("+generateTemp(fm,fion.getSrc(),lb)+","+type+");");
  }

  int sandboxcounter=0;
  public void generateFlatAtomicEnterNode(FlatMethod fm,  LocalityBinding lb, FlatAtomicEnterNode faen, PrintWriter output) {
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

    if (locality.getAtomic(lb).get(faen.getPrev(0)).intValue()>0)
      return;


    if (state.SANDBOX) {
      outsandbox.println("int atomiccounter"+sandboxcounter+"=LOW_CHECK_FREQUENCY;");
      output.println("counter_reset_pointer=&atomiccounter"+sandboxcounter+";");
    }

    if (state.DELAYCOMP&&delaycomp.needsFission(lb, faen)) {
      AtomicRecord ar=atomicmethodmap.get(faen);
      //copy in
      for(Iterator<TempDescriptor> tmpit=ar.livein.iterator();tmpit.hasNext();) {
	TempDescriptor tmp=tmpit.next();
	output.println("primitives_"+ar.name+"."+tmp.getSafeSymbol()+"="+tmp.getSafeSymbol()+";");
      }

      //copy outs that depend on path
      for(Iterator<TempDescriptor> tmpit=ar.liveoutvirtualread.iterator();tmpit.hasNext();) {
	TempDescriptor tmp=tmpit.next();
	if (!ar.livein.contains(tmp))
	  output.println("primitives_"+ar.name+"."+tmp.getSafeSymbol()+"="+tmp.getSafeSymbol()+";");
      }
    }

    /* Backup the temps. */
    for(Iterator<TempDescriptor> tmpit=locality.getTemps(lb).get(faen).iterator(); tmpit.hasNext();) {
      TempDescriptor tmp=tmpit.next();
      output.println(generateTemp(fm, backuptable.get(lb).get(tmp),lb)+"="+generateTemp(fm,tmp,lb)+";");
    }

    output.println("goto transstart"+faen.getIdentifier()+";");

    /******* Print code to retry aborted transaction *******/
    output.println("transretry"+faen.getIdentifier()+":");

    /* Restore temps */
    for(Iterator<TempDescriptor> tmpit=locality.getTemps(lb).get(faen).iterator(); tmpit.hasNext();) {
      TempDescriptor tmp=tmpit.next();
      output.println(generateTemp(fm, tmp,lb)+"="+generateTemp(fm,backuptable.get(lb).get(tmp),lb)+";");
    }

    if (state.DSM) {
      /********* Need to revert local object store ********/
      String revertptr=generateTemp(fm, reverttable.get(lb),lb);

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

  public void generateFlatAtomicExitNode(FlatMethod fm,  LocalityBinding lb, FlatAtomicExitNode faen, PrintWriter output) {
    /* Check to see if we need to generate code for this atomic */
    if (locality==null) {
      output.println("pthread_mutex_unlock(&atomiclock);");
      return;
    }
    if (locality.getAtomic(lb).get(faen).intValue()>0)
      return;
    //store the revert list before we lose the transaction object
    
    if (state.DSM) {
      String revertptr=generateTemp(fm, reverttable.get(lb),lb);
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
      if (delaycomp.optimizeTrans(lb, faen.getAtomicEnter())&&(!state.STMARRAY||state.DUALVIEW))  {
	AtomicRecord ar=atomicmethodmap.get(faen.getAtomicEnter());
	output.println("LIGHTWEIGHTCOMMIT("+ar.name+", &primitives_"+ar.name+", &"+localsprefix+", "+paramsprefix+", transretry"+faen.getAtomicEnter().getIdentifier()+");");
	//copy out
	for(Iterator<TempDescriptor> tmpit=ar.liveout.iterator();tmpit.hasNext();) {
	  TempDescriptor tmp=tmpit.next();
	  output.println(tmp.getSafeSymbol()+"=primitives_"+ar.name+"."+tmp.getSafeSymbol()+";");
	}
      } else if (delaycomp.needsFission(lb, faen.getAtomicEnter())) {
	AtomicRecord ar=atomicmethodmap.get(faen.getAtomicEnter());
	//do call
	output.println("if (transCommit((void (*)(void *, void *, void *))&"+ar.name+", &primitives_"+ar.name+", &"+localsprefix+", "+paramsprefix+")) {");
	output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
	output.println("goto transretry"+faen.getAtomicEnter().getIdentifier()+";");
	output.println("}");
	//copy out
	output.println("else {");
	for(Iterator<TempDescriptor> tmpit=ar.liveout.iterator();tmpit.hasNext();) {
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

  public void generateFlatSESEEnterNode( FlatMethod fm,  
					 LocalityBinding lb, 
					 FlatSESEEnterNode fsen, 
					 PrintWriter output) {
    // if OOOJAVA flag is off, okay that SESE nodes are in IR graph, 
    // just skip over them and code generates exactly the same    
  }

  public void generateFlatSESEExitNode( FlatMethod fm,
					LocalityBinding lb,
					FlatSESEExitNode fsexn,
					PrintWriter output) {
    // if OOOJAVA flag is off, okay that SESE nodes are in IR graph, 
    // just skip over them and code generates exactly the same 
  }
 
  public void generateFlatWriteDynamicVarNode( FlatMethod fm,  
					       LocalityBinding lb, 
					       FlatWriteDynamicVarNode fwdvn,
					       PrintWriter output
					     ) {
    if( !state.OOOJAVA ) {
      // should node should not be in an IR graph if the
      // OOOJAVA flag is not set
      throw new Error("Unexpected presence of FlatWriteDynamicVarNode");
    }
  }

  
  private void generateFlatCheckNode(FlatMethod fm,  LocalityBinding lb, FlatCheckNode fcn, PrintWriter output) {
    if (state.CONSCHECK) {
      String specname=fcn.getSpec();
      String varname="repairstate___";
      output.println("{");
      output.println("struct "+specname+"_state * "+varname+"=allocate"+specname+"_state();");

      TempDescriptor[] temps=fcn.getTemps();
      String[] vars=fcn.getVars();
      for(int i=0; i<temps.length; i++) {
	output.println(varname+"->"+vars[i]+"=(unsigned int)"+generateTemp(fm, temps[i],lb)+";");
      }

      output.println("if (doanalysis"+specname+"("+varname+")) {");
      output.println("free"+specname+"_state("+varname+");");
      output.println("} else {");
      output.println("/* Bad invariant */");
      output.println("free"+specname+"_state("+varname+");");
      output.println("abort_task();");
      output.println("}");
      output.println("}");
    }
  }

  private void generateFlatCall(FlatMethod fm, LocalityBinding lb, FlatCall fc, PrintWriter output) {

    MethodDescriptor md=fc.getMethod();
    ParamsObject objectparams=(ParamsObject)paramstable.get(lb!=null ? locality.getBinding(lb, fc) : md);
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
          if(cn.getNumStaticFields() != 0) {
            // TODO add static field initialization here
          }
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
      if (lb!=null) {
	LocalityBinding fclb=locality.getBinding(lb, fc);
	output.print("       struct "+cn.getSafeSymbol()+fclb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params __parameterlist__={");
      } else
	output.print("       struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params __parameterlist__={");
      output.print(objectparams.numPointers());
      output.print(", "+localsprefixaddr);
      if (md.getThis()!=null) {
	output.print(", ");
	output.print("(struct "+md.getThis().getType().getSafeSymbol() +" *)"+ generateTemp(fm,fc.getThis(),lb));
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
	    output.print("(struct "+(new TypeDescriptor(typeutil.getClass(TypeUtil.TagClass))).getSafeSymbol()  +" *)"+generateTemp(fm, targ,lb));
	  else
	    output.print("(struct "+md.getParamType(i).getSafeSymbol()  +" *)"+generateTemp(fm, targ,lb));
	}
      }
      output.println("};");
    }
    output.print("       ");


    if (fc.getReturnTemp()!=null)
      output.print(generateTemp(fm,fc.getReturnTemp(),lb)+"=");

    /* Do we need to do virtual dispatch? */
    if (md.isStatic()||md.getReturnType()==null||singleCall(fc.getThis().getType().getClassDesc(),md)) {
      //no
      if (lb!=null) {
	LocalityBinding fclb=locality.getBinding(lb, fc);
	output.print(cn.getSafeSymbol()+fclb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor());
      } else {
	output.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor());
      }
    } else {
      //yes
      output.print("((");
      if (md.getReturnType().isClass()||md.getReturnType().isArray())
	output.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
      else
	output.print(md.getReturnType().getSafeSymbol()+" ");
      output.print("(*)(");

      boolean printcomma=false;
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	if (lb!=null) {
	  LocalityBinding fclb=locality.getBinding(lb, fc);
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
	if (temp.getType().isClass()||temp.getType().isArray())
	  output.print("struct " + temp.getType().getSafeSymbol()+" * ");
	else
	  output.print(temp.getType().getSafeSymbol());
      }


      if (lb!=null) {
	LocalityBinding fclb=locality.getBinding(lb, fc);
	output.print("))virtualtable["+generateTemp(fm,fc.getThis(),lb)+"->type*"+maxcount+"+"+virtualcalls.getLocalityNumber(fclb)+"])");
      } else
	output.print("))virtualtable["+generateTemp(fm,fc.getThis(),lb)+"->type*"+maxcount+"+"+virtualcalls.getMethodNumber(md)+"])");
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
	if (ptd.isClass()&&!ptd.isArray())
	  output.print("(struct "+ptd.getSafeSymbol()+" *) ");
	output.print(generateTemp(fm,fc.getThis(),lb));
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
	if (ptd.isClass()&&!ptd.isArray())
	  output.print("(struct "+ptd.getSafeSymbol()+" *) ");
	output.print(generateTemp(fm, targ,lb));
	needcomma=true;
      }
    }
    output.println(");");
    output.println("   }");
  }

  private boolean singleCall(ClassDescriptor thiscd, MethodDescriptor md) {
    Set subclasses=typeutil.getSubClasses(thiscd);
    if (subclasses==null)
      return true;
    for(Iterator classit=subclasses.iterator(); classit.hasNext();) {
      ClassDescriptor cd=(ClassDescriptor)classit.next();
      Set possiblematches=cd.getMethodTable().getSetFromSameScope(md.getSymbol());
      for(Iterator matchit=possiblematches.iterator(); matchit.hasNext();) {
	MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
	if (md.matches(matchmd))
	  return false;
      }
    }
    return true;
  }

  private void generateFlatFieldNode(FlatMethod fm, LocalityBinding lb, FlatFieldNode ffn, PrintWriter output) {
    if (state.SINGLETM) {
      //single machine transactional memory case
      String field=ffn.getField().getSafeSymbol();
      String src=generateTemp(fm, ffn.getSrc(),lb);
      String dst=generateTemp(fm, ffn.getDst(),lb);

      output.println(dst+"="+ src +"->"+field+ ";");
      if (ffn.getField().getType().isPtr()&&locality.getAtomic(lb).get(ffn).intValue()>0&&
          locality.getNodePreTempInfo(lb, ffn).get(ffn.getSrc())!=LocalityAnalysis.SCRATCH) {
	if ((dc==null)||(!state.READSET&&dc.getNeedTrans(lb, ffn))||
	    (state.READSET&&dc.getNeedWriteTrans(lb, ffn))) {
	  output.println("TRANSREAD("+dst+", "+dst+", (void *) (" + localsprefixaddr + "));");
	} else if (state.READSET&&dc.getNeedTrans(lb, ffn)) {
	  if (state.HYBRID&&delaycomp.getConv(lb).contains(ffn)) {
	    output.println("TRANSREADRDFISSION("+dst+", "+dst+");");
	  } else
	    output.println("TRANSREADRD("+dst+", "+dst+");");
	}
      }
    } else if (state.DSM) {
      Integer status=locality.getNodePreTempInfo(lb,ffn).get(ffn.getSrc());
      if (status==LocalityAnalysis.GLOBAL) {
	String field=ffn.getField().getSafeSymbol();
	String src=generateTemp(fm, ffn.getSrc(),lb);
	String dst=generateTemp(fm, ffn.getDst(),lb);

	if (ffn.getField().getType().isPtr()) {

	  //TODO: Uncomment this when we have runtime support
	  //if (ffn.getSrc()==ffn.getDst()) {
	  //output.println("{");
	  //output.println("void * temp="+src+";");
	  //output.println("if (temp&0x1) {");
	  //output.println("temp=(void *) transRead(trans, (unsigned int) temp);");
	  //output.println(src+"->"+field+"="+temp+";");
	  //output.println("}");
	  //output.println(dst+"=temp;");
	  //output.println("}");
	  //} else {
	  output.println(dst+"="+ src +"->"+field+ ";");
	  //output.println("if ("+dst+"&0x1) {");
	  //DEBUG: output.println("TRANSREAD("+dst+", (unsigned int) "+dst+",\""+fm+":"+ffn+"\");");
      output.println("TRANSREAD("+dst+", (unsigned int) "+dst+");");
	  //output.println(src+"->"+field+"="+src+"->"+field+";");
	  //output.println("}");
	  //}
	} else {
	  output.println(dst+"="+ src+"->"+field+";");
	}
      } else if (status==LocalityAnalysis.LOCAL) {
	if (ffn.getField().getType().isPtr()&&
	    ffn.getField().isGlobal()) {
	  String field=ffn.getField().getSafeSymbol();
	  String src=generateTemp(fm, ffn.getSrc(),lb);
	  String dst=generateTemp(fm, ffn.getDst(),lb);
	  output.println(dst+"="+ src +"->"+field+ ";");
	  if (locality.getAtomic(lb).get(ffn).intValue()>0)
	    //DEBUG: output.println("TRANSREAD("+dst+", (unsigned int) "+dst+",\""+fm+":"+ffn+"\");");
	    output.println("TRANSREAD("+dst+", (unsigned int) "+dst+");");
	} else
	  output.println(generateTemp(fm, ffn.getDst(),lb)+"="+ generateTemp(fm,ffn.getSrc(),lb)+"->"+ ffn.getField().getSafeSymbol()+";");
      } else if (status==LocalityAnalysis.EITHER) {
	//Code is reading from a null pointer
	output.println("if ("+generateTemp(fm, ffn.getSrc(),lb)+") {");
	output.println("#ifndef RAW");
	output.println("printf(\"BIG ERROR\\n\");exit(-1);}");
	output.println("#endif");
	//This should throw a suitable null pointer error
	output.println(generateTemp(fm, ffn.getDst(),lb)+"="+ generateTemp(fm,ffn.getSrc(),lb)+"->"+ ffn.getField().getSafeSymbol()+";");
      } else
	throw new Error("Read from non-global/non-local in:"+lb.getExplanation());
    } else{
// DEBUG 	if(!ffn.getDst().getType().isPrimitive()){
// DEBUG  		output.println("within((void*)"+generateTemp(fm,ffn.getSrc(),lb)+"->"+ ffn.getField().getSafeSymbol()+");");
// DEBUG   	} 
      if(state.MGC) {
        // TODO add version for normal Java later
      if(ffn.getField().isStatic()) {
        // static field
        if((fm.getMethod().isStaticBlock()) || (fm.getMethod().isInvokedByStatic())) {
          // is a static block or is invoked in some static block
          ClassDescriptor cd = fm.getMethod().getClassDesc();
          ClassDescriptor cn = ffn.getSrc().getType().getClassDesc();
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
        // redirect to the global_defs_p structure
        if((ffn.getField().isStatic()) || (ffn.getSrc().getType().isStatic())) {
          // reference to the static field with Class name
          output.println(generateTemp(fm, ffn.getDst(),lb)+"=global_defs_p->"+ ffn.getSrc().getType().getClassDesc().getSafeSymbol()+ffn.getField().getSafeSymbol()+";");
        } else {
          output.println(generateTemp(fm, ffn.getDst(),lb)+"=*"+ generateTemp(fm,ffn.getSrc(),lb)+"->"+ ffn.getField().getSafeSymbol()+";");
        }
        //output.println(generateTemp(fm, ffn.getDst(),lb)+"=global_defs_p->"+ffn.getSrc().getType().getClassDesc().getSafeSymbol()+"->"+ ffn.getField().getSafeSymbol()+";");
      } else if (ffn.getField().isEnum()) {
          // an Enum value, directly replace the field access as int
          output.println(generateTemp(fm, ffn.getDst(), lb) + "=" + ffn.getField().enumValue() + ";");
	  } else {
        output.println(generateTemp(fm, ffn.getDst(),lb)+"="+ generateTemp(fm,ffn.getSrc(),lb)+"->"+ ffn.getField().getSafeSymbol()+";");
      } 
    } else {
        output.println(generateTemp(fm, ffn.getDst(),lb)+"="+ generateTemp(fm,ffn.getSrc(),lb)+"->"+ ffn.getField().getSafeSymbol()+";");
      }
    }
  }


  private void generateFlatSetFieldNode(FlatMethod fm, LocalityBinding lb, FlatSetFieldNode fsfn, PrintWriter output) {
    if (fsfn.getField().getSymbol().equals("length")&&fsfn.getDst().getType().isArray())
      throw new Error("Can't set array length");
    if (state.SINGLETM && locality.getAtomic(lb).get(fsfn).intValue()>0) {
      //Single Machine Transaction Case
      boolean srcptr=fsfn.getSrc().getType().isPtr();
      String src=generateTemp(fm,fsfn.getSrc(),lb);
      String dst=generateTemp(fm,fsfn.getDst(),lb);
      output.println("//"+srcptr+" "+fsfn.getSrc().getType().isNull());
      if (srcptr&&!fsfn.getSrc().getType().isNull()) {
	output.println("{");
	if ((dc==null)||dc.getNeedSrcTrans(lb, fsfn)&&
	    locality.getNodePreTempInfo(lb, fsfn).get(fsfn.getSrc())!=LocalityAnalysis.SCRATCH) {
	  output.println("INTPTR srcoid=("+src+"!=NULL?((INTPTR)"+src+"->"+oidstr+"):0);");
	} else {
	  output.println("INTPTR srcoid=(INTPTR)"+src+";");
	}
      }
      if (wb.needBarrier(fsfn)&&
          locality.getNodePreTempInfo(lb, fsfn).get(fsfn.getDst())!=LocalityAnalysis.SCRATCH) {
	if (state.EVENTMONITOR) {
	  output.println("if ("+dst+"->___objstatus___&DIRTY) EVLOGEVENTOBJ(EV_WRITE,"+dst+"->objuid)");
	}
	output.println("*((unsigned int *)&("+dst+"->___objstatus___))|=DIRTY;");
      }
      if (srcptr&!fsfn.getSrc().getType().isNull()) {
	output.println("*((unsigned INTPTR *)&("+dst+"->"+ fsfn.getField().getSafeSymbol()+"))=srcoid;");
	output.println("}");
      } else {
	output.println(dst+"->"+ fsfn.getField().getSafeSymbol()+"="+ src+";");
      }
    } else if (state.DSM && locality.getAtomic(lb).get(fsfn).intValue()>0) {
      Integer statussrc=locality.getNodePreTempInfo(lb,fsfn).get(fsfn.getSrc());
      Integer statusdst=locality.getNodeTempInfo(lb).get(fsfn).get(fsfn.getDst());
      boolean srcglobal=statussrc==LocalityAnalysis.GLOBAL;

      String src=generateTemp(fm,fsfn.getSrc(),lb);
      String dst=generateTemp(fm,fsfn.getDst(),lb);
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
	  output.println("*((unsigned INTPTR *)&("+glbdst+"->"+ fsfn.getField().getSafeSymbol()+"))=srcoid;");
	} else
	  output.println(glbdst+"->"+ fsfn.getField().getSafeSymbol()+"="+ src+";");
      } else if (statusdst.equals(LocalityAnalysis.LOCAL)) {
	/** Check if we need to copy */
	output.println("if(!"+dst+"->"+localcopystr+") {");
	/* Link object into list */
	String revertptr=generateTemp(fm, reverttable.get(lb),lb);
	output.println(revertptr+"=revertlist;");
	if (GENERATEPRECISEGC || this.state.MULTICOREGC)
	  output.println("COPY_OBJ((struct garbagelist *)"+localsprefixaddr+",(struct ___Object___ *)"+dst+");");
	else
	  output.println("COPY_OBJ("+dst+");");
	output.println(dst+"->"+nextobjstr+"="+revertptr+";");
	output.println("revertlist=(struct ___Object___ *)"+dst+";");
	output.println("}");
	if (srcglobal)
	  output.println(dst+"->"+ fsfn.getField().getSafeSymbol()+"=(void *) srcoid;");
	else
	  output.println(dst+"->"+ fsfn.getField().getSafeSymbol()+"="+ src+";");
      } else if (statusdst.equals(LocalityAnalysis.EITHER)) {
	//writing to a null...bad
	output.println("if ("+dst+") {");
	output.println("printf(\"BIG ERROR 2\\n\");exit(-1);}");
	if (srcglobal)
	  output.println(dst+"->"+ fsfn.getField().getSafeSymbol()+"=(void *) srcoid;");
	else
	  output.println(dst+"->"+ fsfn.getField().getSafeSymbol()+"="+ src+";");
      }
      if (srcglobal) {
	output.println("}");
      }
    } else {
      if (state.FASTCHECK) {
	String dst=generateTemp(fm, fsfn.getDst(),lb);
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
      
// DEBUG 	if(!fsfn.getField().getType().isPrimitive()){
// DEBUG		output.println("within((void*)"+generateTemp(fm,fsfn.getSrc(),lb)+");");
// DEBUG   } 
      if(state.MGC) {
        // TODO add version for normal Java later
      if(fsfn.getField().isStatic()) {
        // static field
        if((fm.getMethod().isStaticBlock()) || (fm.getMethod().isInvokedByStatic())) {
          // is a static block or is invoked in some static block
          ClassDescriptor cd = fm.getMethod().getClassDesc();
          ClassDescriptor cn = fsfn.getDst().getType().getClassDesc();
          if(cd == cn) {
            // the same class, do nothing
            // TODO may want to invoke static field initialization here
          } else {
            if((cn.getNumStaticFields() != 0) || (cn.getNumStaticBlocks() != 0)) {
              // need to check if the class' static fields have been initialized and/or
              // its static blocks have been executed
              output.println("#ifdef MGC_STATIC_INIT_CHECK");
              output.println("if(global_defs_p->" + cn.getSafeSymbol()+"static_block_exe_flag == 0) {");
              if(cn.getNumStaticFields() != 0) {
                // TODO add static field initialization here
              }
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
        // redirect to the global_defs_p structure
        if(fsfn.getDst().getType().isStatic()) {
          // reference to the static field with Class name
          output.println("global_defs_p->" + fsfn.getDst().getType().getClassDesc().getSafeSymbol() + fsfn.getField().getSafeSymbol()+"="+ generateTemp(fm,fsfn.getSrc(),lb)+";");
        } else {
          output.println("*"+generateTemp(fm, fsfn.getDst(),lb)+"->"+ fsfn.getField().getSafeSymbol()+"="+ generateTemp(fm,fsfn.getSrc(),lb)+";");
        }
      } else {
        output.println(generateTemp(fm, fsfn.getDst(),lb)+"->"+ fsfn.getField().getSafeSymbol()+"="+ generateTemp(fm,fsfn.getSrc(),lb)+";");
      } 
      } else {
        output.println(generateTemp(fm, fsfn.getDst(),lb)+"->"+ fsfn.getField().getSafeSymbol()+"="+ generateTemp(fm,fsfn.getSrc(),lb)+";");
      }
    }
  }

  private void generateFlatElementNode(FlatMethod fm, LocalityBinding lb, FlatElementNode fen, PrintWriter output) {
    TypeDescriptor elementtype=fen.getSrc().getType().dereference();
    String type="";

    if (elementtype.isArray()||elementtype.isClass())
      type="void *";
    else
      type=elementtype.getSafeSymbol()+" ";

    if (this.state.ARRAYBOUNDARYCHECK && fen.needsBoundsCheck()) {
      output.println("if (unlikely(((unsigned int)"+generateTemp(fm, fen.getIndex(),lb)+") >= "+generateTemp(fm,fen.getSrc(),lb) + "->___length___))");
      output.println("failedboundschk();");
    }
    if (state.SINGLETM) {
      //Single machine transaction case
      String dst=generateTemp(fm, fen.getDst(),lb);
      if ((!state.STMARRAY)||(!wb.needBarrier(fen))||locality.getNodePreTempInfo(lb, fen).get(fen.getSrc())==LocalityAnalysis.SCRATCH||locality.getAtomic(lb).get(fen).intValue()==0||(state.READSET&&!dc.getNeedGet(lb, fen))) {
	output.println(dst +"=(("+ type+"*)(((char *) &("+ generateTemp(fm,fen.getSrc(),lb)+"->___length___))+sizeof(int)))["+generateTemp(fm, fen.getIndex(),lb)+"];");
      } else {
	output.println("STMGETARRAY("+dst+", "+ generateTemp(fm,fen.getSrc(),lb)+", "+generateTemp(fm, fen.getIndex(),lb)+", "+type+");");
      }

      if (elementtype.isPtr()&&locality.getAtomic(lb).get(fen).intValue()>0&&
          locality.getNodePreTempInfo(lb, fen).get(fen.getSrc())!=LocalityAnalysis.SCRATCH) {
	if ((dc==null)||!state.READSET&&dc.getNeedTrans(lb, fen)||state.READSET&&dc.getNeedWriteTrans(lb, fen)) {
	  output.println("TRANSREAD("+dst+", "+dst+", (void *)(" + localsprefixaddr+"));");
	} else if (state.READSET&&dc.getNeedTrans(lb, fen)) {
	  if (state.HYBRID&&delaycomp.getConv(lb).contains(fen)) {
	    output.println("TRANSREADRDFISSION("+dst+", "+dst+");");
	  } else
	    output.println("TRANSREADRD("+dst+", "+dst+");");
	}
      }
    } else if (state.DSM) {
      Integer status=locality.getNodePreTempInfo(lb,fen).get(fen.getSrc());
      if (status==LocalityAnalysis.GLOBAL) {
	String dst=generateTemp(fm, fen.getDst(),lb);
	if (elementtype.isPtr()) {
	  output.println(dst +"=(("+ type+"*)(((char *) &("+ generateTemp(fm,fen.getSrc(),lb)+"->___length___))+sizeof(int)))["+generateTemp(fm, fen.getIndex(),lb)+"];");
	  //DEBUG: output.println("TRANSREAD("+dst+", "+dst+",\""+fm+":"+fen+"\");");
	  output.println("TRANSREAD("+dst+", "+dst+");");
	} else {
	  output.println(dst +"=(("+ type+"*)(((char *) &("+ generateTemp(fm,fen.getSrc(),lb)+"->___length___))+sizeof(int)))["+generateTemp(fm, fen.getIndex(),lb)+"];");
	}
      } else if (status==LocalityAnalysis.LOCAL) {
	output.println(generateTemp(fm, fen.getDst(),lb)+"=(("+ type+"*)(((char *) &("+ generateTemp(fm,fen.getSrc(),lb)+"->___length___))+sizeof(int)))["+generateTemp(fm, fen.getIndex(),lb)+"];");
      } else if (status==LocalityAnalysis.EITHER) {
	//Code is reading from a null pointer
	output.println("if ("+generateTemp(fm, fen.getSrc(),lb)+") {");
	output.println("#ifndef RAW");
	output.println("printf(\"BIG ERROR\\n\");exit(-1);}");
	output.println("#endif");
	//This should throw a suitable null pointer error
	output.println(generateTemp(fm, fen.getDst(),lb)+"=(("+ type+"*)(((char *) &("+ generateTemp(fm,fen.getSrc(),lb)+"->___length___))+sizeof(int)))["+generateTemp(fm, fen.getIndex(),lb)+"];");
      } else
	throw new Error("Read from non-global/non-local in:"+lb.getExplanation());
    } else {
// DEBUG output.println("within((void*)"+generateTemp(fm,fen.getSrc(),lb)+");");
        output.println(generateTemp(fm, fen.getDst(),lb)+"=(("+ type+"*)(((char *) &("+ generateTemp(fm,fen.getSrc(),lb)+"->___length___))+sizeof(int)))["+generateTemp(fm, fen.getIndex(),lb)+"];");
    }
  }

  private void generateFlatSetElementNode(FlatMethod fm, LocalityBinding lb, FlatSetElementNode fsen, PrintWriter output) {
    //TODO: need dynamic check to make sure this assignment is actually legal
    //Because Object[] could actually be something more specific...ie. Integer[]

    TypeDescriptor elementtype=fsen.getDst().getType().dereference();
    String type="";

    if (elementtype.isArray()||elementtype.isClass())
      type="void *";
    else
      type=elementtype.getSafeSymbol()+" ";

    if (this.state.ARRAYBOUNDARYCHECK && fsen.needsBoundsCheck()) {
      output.println("if (unlikely(((unsigned int)"+generateTemp(fm, fsen.getIndex(),lb)+") >= "+generateTemp(fm,fsen.getDst(),lb) + "->___length___))");
      output.println("failedboundschk();");
    }

    if (state.SINGLETM && locality.getAtomic(lb).get(fsen).intValue()>0) {
      //Transaction set element case
      if (wb.needBarrier(fsen)&&
          locality.getNodePreTempInfo(lb, fsen).get(fsen.getDst())!=LocalityAnalysis.SCRATCH) {
	output.println("*((unsigned int *)&("+generateTemp(fm,fsen.getDst(),lb)+"->___objstatus___))|=DIRTY;");
      }
      if (fsen.getSrc().getType().isPtr()&&!fsen.getSrc().getType().isNull()) {
	output.println("{");
	String src=generateTemp(fm, fsen.getSrc(), lb);
	if ((dc==null)||dc.getNeedSrcTrans(lb, fsen)&&
	    locality.getNodePreTempInfo(lb, fsen).get(fsen.getSrc())!=LocalityAnalysis.SCRATCH) {
	  output.println("INTPTR srcoid=("+src+"!=NULL?((INTPTR)"+src+"->"+oidstr+"):0);");
	} else {
	  output.println("INTPTR srcoid=(INTPTR)"+src+";");
	}
	if (state.STMARRAY&&locality.getNodePreTempInfo(lb, fsen).get(fsen.getDst())!=LocalityAnalysis.SCRATCH&&wb.needBarrier(fsen)&&locality.getAtomic(lb).get(fsen).intValue()>0) {
	  output.println("STMSETARRAY("+generateTemp(fm, fsen.getDst(),lb)+", "+generateTemp(fm, fsen.getIndex(),lb)+", srcoid, INTPTR);");
	} else {
	  output.println("((INTPTR*)(((char *) &("+ generateTemp(fm,fsen.getDst(),lb)+"->___length___))+sizeof(int)))["+generateTemp(fm, fsen.getIndex(),lb)+"]=srcoid;");
	}
	output.println("}");
      } else {
	if (state.STMARRAY&&locality.getNodePreTempInfo(lb, fsen).get(fsen.getDst())!=LocalityAnalysis.SCRATCH&&wb.needBarrier(fsen)&&locality.getAtomic(lb).get(fsen).intValue()>0) {
	  output.println("STMSETARRAY("+generateTemp(fm, fsen.getDst(),lb)+", "+generateTemp(fm, fsen.getIndex(),lb)+", "+ generateTemp(fm, fsen.getSrc(), lb) +", "+type+");");
	} else {
	  output.println("(("+type +"*)(((char *) &("+ generateTemp(fm,fsen.getDst(),lb)+"->___length___))+sizeof(int)))["+generateTemp(fm, fsen.getIndex(),lb)+"]="+generateTemp(fm,fsen.getSrc(),lb)+";");
	}
      }
    } else if (state.DSM && locality.getAtomic(lb).get(fsen).intValue()>0) {
      Integer statussrc=locality.getNodePreTempInfo(lb,fsen).get(fsen.getSrc());
      Integer statusdst=locality.getNodePreTempInfo(lb,fsen).get(fsen.getDst());
      boolean srcglobal=statussrc==LocalityAnalysis.GLOBAL;
      boolean dstglobal=statusdst==LocalityAnalysis.GLOBAL;
      boolean dstlocal=(statusdst==LocalityAnalysis.LOCAL)||(statusdst==LocalityAnalysis.EITHER);
      
      if (dstglobal) {
	if (wb.needBarrier(fsen))
	  output.println("*((unsigned int *)&("+generateTemp(fm,fsen.getDst(),lb)+"->___localcopy___))|=DIRTY;");
      } else if (dstlocal) {
	/** Check if we need to copy */
	String dst=generateTemp(fm, fsen.getDst(),lb);
	output.println("if(!"+dst+"->"+localcopystr+") {");
	/* Link object into list */
	String revertptr=generateTemp(fm, reverttable.get(lb),lb);
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
	System.out.println(lb);
	System.out.println("statusdst="+statusdst);
	System.out.println(fm.printMethod());
	throw new Error("Unknown array type");
      }
      if (srcglobal) {
	output.println("{");
	String src=generateTemp(fm, fsen.getSrc(), lb);
	output.println("INTPTR srcoid=("+src+"!=NULL?((INTPTR)"+src+"->"+oidstr+"):0);");
	output.println("((INTPTR*)(((char *) &("+ generateTemp(fm,fsen.getDst(),lb)+"->___length___))+sizeof(int)))["+generateTemp(fm, fsen.getIndex(),lb)+"]=srcoid;");
	output.println("}");
      } else {
	output.println("(("+type +"*)(((char *) &("+ generateTemp(fm,fsen.getDst(),lb)+"->___length___))+sizeof(int)))["+generateTemp(fm, fsen.getIndex(),lb)+"]="+generateTemp(fm,fsen.getSrc(),lb)+";");
      }
    } else {
      if (state.FASTCHECK) {
	String dst=generateTemp(fm, fsen.getDst(),lb);
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
// DEBUG      output.println("within((void*)"+generateTemp(fm,fsen.getDst(),lb)+");");
      output.println("(("+type +"*)(((char *) &("+ generateTemp(fm,fsen.getDst(),lb)+"->___length___))+sizeof(int)))["+generateTemp(fm, fsen.getIndex(),lb)+"]="+generateTemp(fm,fsen.getSrc(),lb)+";");
    }
  }


  protected void generateFlatNew(FlatMethod fm, LocalityBinding lb, FlatNew fn, PrintWriter output) {
    if (state.DSM && locality.getAtomic(lb).get(fn).intValue()>0&&!fn.isGlobal()) {
      //Stash pointer in case of GC
      String revertptr=generateTemp(fm, reverttable.get(lb),lb);
      output.println(revertptr+"=revertlist;");
    }
    if (state.SINGLETM) {
      if (fn.getType().isArray()) {
	int arrayid=state.getArrayNumber(fn.getType())+state.numClasses();
	if (locality.getAtomic(lb).get(fn).intValue()>0) {
	  //inside transaction
	  output.println(generateTemp(fm,fn.getDst(),lb)+"=allocate_newarraytrans("+localsprefixaddr+", "+arrayid+", "+generateTemp(fm, fn.getSize(),lb)+");");
	} else {
	  //outside transaction
	  output.println(generateTemp(fm,fn.getDst(),lb)+"=allocate_newarray("+localsprefixaddr+", "+arrayid+", "+generateTemp(fm, fn.getSize(),lb)+");");
	}
      } else {
	if (locality.getAtomic(lb).get(fn).intValue()>0) {
	  //inside transaction
	  output.println(generateTemp(fm,fn.getDst(),lb)+"=allocate_newtrans("+localsprefixaddr+", "+fn.getType().getClassDesc().getId()+");");
	} else {
	  //outside transaction
	  output.println(generateTemp(fm,fn.getDst(),lb)+"=allocate_new("+localsprefixaddr+", "+fn.getType().getClassDesc().getId()+");");
	}
      }
    } else if (fn.getType().isArray()) {
      int arrayid=state.getArrayNumber(fn.getType())+state.numClasses();
      if (fn.isGlobal()&&(state.DSM||state.SINGLETM)) {
	output.println(generateTemp(fm,fn.getDst(),lb)+"=allocate_newarrayglobal("+arrayid+", "+generateTemp(fm, fn.getSize(),lb)+");");
      } else if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
        output.println(generateTemp(fm,fn.getDst(),lb)+"=allocate_newarray("+localsprefixaddr+", "+arrayid+", "+generateTemp(fm, fn.getSize(),lb)+");");    		  
      } else {
	output.println(generateTemp(fm,fn.getDst(),lb)+"=allocate_newarray("+arrayid+", "+generateTemp(fm, fn.getSize(),lb)+");");
      }
    } else {
      if (fn.isGlobal()&&(state.DSM||state.SINGLETM)) {
	output.println(generateTemp(fm,fn.getDst(),lb)+"=allocate_newglobal("+fn.getType().getClassDesc().getId()+");");
      } else if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
        output.println(generateTemp(fm,fn.getDst(),lb)+"=allocate_new("+localsprefixaddr+", "+fn.getType().getClassDesc().getId()+");");        
      } else {
	output.println(generateTemp(fm,fn.getDst(),lb)+"=allocate_new("+fn.getType().getClassDesc().getId()+");");
      }
    }
    if (state.DSM && locality.getAtomic(lb).get(fn).intValue()>0&&!fn.isGlobal()) {
      String revertptr=generateTemp(fm, reverttable.get(lb),lb);
      String dst=generateTemp(fm,fn.getDst(),lb);
      output.println(dst+"->___localcopy___=(struct ___Object___*)1;");
      output.println(dst+"->"+nextobjstr+"="+revertptr+";");
      output.println("revertlist=(struct ___Object___ *)"+dst+";");
    }
    if (state.FASTCHECK) {
      String dst=generateTemp(fm,fn.getDst(),lb);
      output.println(dst+"->___localcopy___=(struct ___Object___*)1;");
      output.println(dst+"->"+nextobjstr+"="+fcrevert+";");
      output.println(fcrevert+"=(struct ___Object___ *)"+dst+";");
    }
  }

  private void generateFlatTagDeclaration(FlatMethod fm, LocalityBinding lb, FlatTagDeclaration fn, PrintWriter output) {
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      output.println(generateTemp(fm,fn.getDst(),lb)+"=allocate_tag("+localsprefixaddr+", "+state.getTagId(fn.getType())+");");
    } else {
      output.println(generateTemp(fm,fn.getDst(),lb)+"=allocate_tag("+state.getTagId(fn.getType())+");");
    }
  }

  private void generateFlatOpNode(FlatMethod fm, LocalityBinding lb, FlatOpNode fon, PrintWriter output) {
    if (fon.getRight()!=null) {
      if (fon.getOp().getOp()==Operation.URIGHTSHIFT) {
	if (fon.getLeft().getType().isLong())
	  output.println(generateTemp(fm, fon.getDest(),lb)+" = ((unsigned long long)"+generateTemp(fm, fon.getLeft(),lb)+")>>"+generateTemp(fm,fon.getRight(),lb)+";");
	else
	  output.println(generateTemp(fm, fon.getDest(),lb)+" = ((unsigned int)"+generateTemp(fm, fon.getLeft(),lb)+")>>"+generateTemp(fm,fon.getRight(),lb)+";");

      } else if (dc!=null) {
	output.print(generateTemp(fm, fon.getDest(),lb)+" = (");
	if (fon.getLeft().getType().isPtr()&&(fon.getOp().getOp()==Operation.EQUAL||fon.getOp().getOp()==Operation.NOTEQUAL))
	    output.print("(void *)");
	if (dc.getNeedLeftSrcTrans(lb, fon))
	  output.print("("+generateTemp(fm, fon.getLeft(),lb)+"!=NULL?"+generateTemp(fm, fon.getLeft(),lb)+"->"+oidstr+":NULL)");
	else
	  output.print(generateTemp(fm, fon.getLeft(),lb));
	output.print(")"+fon.getOp().toString()+"(");
	if (fon.getRight().getType().isPtr()&&(fon.getOp().getOp()==Operation.EQUAL||fon.getOp().getOp()==Operation.NOTEQUAL))
	    output.print("(void *)");
	if (dc.getNeedRightSrcTrans(lb, fon))
	  output.println("("+generateTemp(fm, fon.getRight(),lb)+"!=NULL?"+generateTemp(fm, fon.getRight(),lb)+"->"+oidstr+":NULL));");
	else
	  output.println(generateTemp(fm,fon.getRight(),lb)+");");
      } else
	output.println(generateTemp(fm, fon.getDest(),lb)+" = "+generateTemp(fm, fon.getLeft(),lb)+fon.getOp().toString()+generateTemp(fm,fon.getRight(),lb)+";");
    } else if (fon.getOp().getOp()==Operation.ASSIGN)
      output.println(generateTemp(fm, fon.getDest(),lb)+" = "+generateTemp(fm, fon.getLeft(),lb)+";");
    else if (fon.getOp().getOp()==Operation.UNARYPLUS)
      output.println(generateTemp(fm, fon.getDest(),lb)+" = "+generateTemp(fm, fon.getLeft(),lb)+";");
    else if (fon.getOp().getOp()==Operation.UNARYMINUS)
      output.println(generateTemp(fm, fon.getDest(),lb)+" = -"+generateTemp(fm, fon.getLeft(),lb)+";");
    else if (fon.getOp().getOp()==Operation.LOGIC_NOT)
      output.println(generateTemp(fm, fon.getDest(),lb)+" = !"+generateTemp(fm, fon.getLeft(),lb)+";");
    else if (fon.getOp().getOp()==Operation.COMP)
      output.println(generateTemp(fm, fon.getDest(),lb)+" = ~"+generateTemp(fm, fon.getLeft(),lb)+";");
    else if (fon.getOp().getOp()==Operation.ISAVAILABLE) {
      output.println(generateTemp(fm, fon.getDest(),lb)+" = "+generateTemp(fm, fon.getLeft(),lb)+"->fses==NULL;");
    } else
      output.println(generateTemp(fm, fon.getDest(),lb)+fon.getOp().toString()+generateTemp(fm, fon.getLeft(),lb)+";");
  }

  private void generateFlatCastNode(FlatMethod fm, LocalityBinding lb, FlatCastNode fcn, PrintWriter output) {
    /* TODO: Do type check here */
    if (fcn.getType().isArray()) {
      output.println(generateTemp(fm,fcn.getDst(),lb)+"=(struct ArrayObject *)"+generateTemp(fm,fcn.getSrc(),lb)+";");
    } else if (fcn.getType().isClass())
      output.println(generateTemp(fm,fcn.getDst(),lb)+"=(struct "+fcn.getType().getSafeSymbol()+" *)"+generateTemp(fm,fcn.getSrc(),lb)+";");
    else
      output.println(generateTemp(fm,fcn.getDst(),lb)+"=("+fcn.getType().getSafeSymbol()+")"+generateTemp(fm,fcn.getSrc(),lb)+";");
  }

  private void generateFlatLiteralNode(FlatMethod fm, LocalityBinding lb, FlatLiteralNode fln, PrintWriter output) {
    if (fln.getValue()==null)
      output.println(generateTemp(fm, fln.getDst(),lb)+"=0;");
    else if (fln.getType().getSymbol().equals(TypeUtil.StringClass)) {
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	if (state.DSM && locality.getAtomic(lb).get(fln).intValue()>0) {
	  //Stash pointer in case of GC
	  String revertptr=generateTemp(fm, reverttable.get(lb),lb);
	  output.println(revertptr+"=revertlist;");
	}
	output.println(generateTemp(fm, fln.getDst(),lb)+"=NewString("+localsprefixaddr+", \""+FlatLiteralNode.escapeString((String)fln.getValue())+"\","+((String)fln.getValue()).length()+");");
	if (state.DSM && locality.getAtomic(lb).get(fln).intValue()>0) {
	  //Stash pointer in case of GC
	  String revertptr=generateTemp(fm, reverttable.get(lb),lb);
	  output.println("revertlist="+revertptr+";");
	}
      } else {
	output.println(generateTemp(fm, fln.getDst(),lb)+"=NewString(\""+FlatLiteralNode.escapeString((String)fln.getValue())+"\","+((String)fln.getValue()).length()+");");
      }
    } else if (fln.getType().isBoolean()) {
      if (((Boolean)fln.getValue()).booleanValue())
	output.println(generateTemp(fm, fln.getDst(),lb)+"=1;");
      else
	output.println(generateTemp(fm, fln.getDst(),lb)+"=0;");
    } else if (fln.getType().isChar()) {
      String st=FlatLiteralNode.escapeString(fln.getValue().toString());
      output.println(generateTemp(fm, fln.getDst(),lb)+"='"+st+"';");
    } else if (fln.getType().isLong()) {
      output.println(generateTemp(fm, fln.getDst(),lb)+"="+fln.getValue()+"LL;");
    } else
      output.println(generateTemp(fm, fln.getDst(),lb)+"="+fln.getValue()+";");
  }

  protected void generateFlatReturnNode(FlatMethod fm, LocalityBinding lb, FlatReturnNode frn, PrintWriter output) {
    if(state.MGC) {
      // TODO add version for normal Java later
    if((fm.getMethod() != null) && (fm.getMethod().isStaticBlock())) {
      // a static block, check if it has been executed
      output.println("  global_defs_p->" + fm.getMethod().getClassDesc().getSafeSymbol()+"static_block_exe_flag = 1;");
      output.println("");
    }
    }
    if (frn.getReturnTemp()!=null) {
      if (frn.getReturnTemp().getType().isPtr())
	output.println("return (struct "+fm.getMethod().getReturnType().getSafeSymbol()+"*)"+generateTemp(fm, frn.getReturnTemp(), lb)+";");
      else
	output.println("return "+generateTemp(fm, frn.getReturnTemp(), lb)+";");
    } else {
      output.println("return;");
    }
  }

  protected void generateStoreFlatCondBranch(FlatMethod fm, LocalityBinding lb, FlatCondBranch fcb, String label, PrintWriter output) {
    int left=-1;
    int right=-1;
    //only record if this group has more than one exit
    if (branchanalysis.numJumps(fcb)>1) {
      left=branchanalysis.jumpValue(fcb, 0);
      right=branchanalysis.jumpValue(fcb, 1);
    }
    output.println("if (!"+generateTemp(fm, fcb.getTest(),lb)+") {");
    if (right!=-1)
      output.println("STOREBRANCH("+right+");");
    output.println("goto "+label+";");
    output.println("}");
    if (left!=-1)
      output.println("STOREBRANCH("+left+");");
  }

  protected void generateFlatCondBranch(FlatMethod fm, LocalityBinding lb, FlatCondBranch fcb, String label, PrintWriter output) {
    output.println("if (!"+generateTemp(fm, fcb.getTest(),lb)+") goto "+label+";");
  }

  /** This method generates header information for the method or
   * task referenced by the Descriptor des. */
  private void generateHeader(FlatMethod fm, LocalityBinding lb, Descriptor des, PrintWriter output) {
    generateHeader(fm, lb, des, output, false);
  }

  private void generateHeader(FlatMethod fm, LocalityBinding lb, Descriptor des, PrintWriter output, boolean addSESErecord) {
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
      if (md.getReturnType().isClass()||md.getReturnType().isArray())
	output.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
      else
	output.print(md.getReturnType().getSafeSymbol()+" ");
    } else
      //catch the constructor case
      output.print("void ");
    if (md!=null) {
      if (state.DSM||state.SINGLETM) {
	output.print(cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");
      } else
	output.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");
    } else
      output.print(task.getSafeSymbol()+"(");
    
    boolean printcomma=false;
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      if (md!=null) {
	if (state.DSM||state.SINGLETM) {
	  output.print("struct "+cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * "+paramsprefix);
	} else
	  output.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * "+paramsprefix);
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
	if (temp.getType().isClass()||temp.getType().isArray())
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
	output.println("struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+"=parameterarray["+i+"];");
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

  public void generateFlatFlagActionNode(FlatMethod fm, LocalityBinding lb, FlatFlagActionNode ffan, PrintWriter output) {
    output.println("/* FlatFlagActionNode */");


    /* Process tag changes */
    Relation tagsettable=new Relation();
    Relation tagcleartable=new Relation();

    Iterator tagsit=ffan.getTempTagPairs();
    while (tagsit.hasNext()) {
      TempTagPair ttp=(TempTagPair) tagsit.next();
      TempDescriptor objtmp=ttp.getTemp();
      TagDescriptor tag=ttp.getTag();
      TempDescriptor tagtmp=ttp.getTagTemp();
      boolean tagstatus=ffan.getTagChange(ttp);
      if (tagstatus) {
	tagsettable.put(objtmp, tagtmp);
      } else {
	tagcleartable.put(objtmp, tagtmp);
      }
    }


    Hashtable flagandtable=new Hashtable();
    Hashtable flagortable=new Hashtable();

    /* Process flag changes */
    Iterator flagsit=ffan.getTempFlagPairs();
    while(flagsit.hasNext()) {
      TempFlagPair tfp=(TempFlagPair)flagsit.next();
      TempDescriptor temp=tfp.getTemp();
      Hashtable flagtable=(Hashtable)flagorder.get(temp.getType().getClassDesc());
      FlagDescriptor flag=tfp.getFlag();
      if (flag==null) {
	//Newly allocate objects that don't set any flags case
	if (flagortable.containsKey(temp)) {
	  throw new Error();
	}
	int mask=0;
	flagortable.put(temp,new Integer(mask));
      } else {
	int flagid=1<<((Integer)flagtable.get(flag)).intValue();
	boolean flagstatus=ffan.getFlagChange(tfp);
	if (flagstatus) {
	  int mask=0;
	  if (flagortable.containsKey(temp)) {
	    mask=((Integer)flagortable.get(temp)).intValue();
	  }
	  mask|=flagid;
	  flagortable.put(temp,new Integer(mask));
	} else {
	  int mask=0xFFFFFFFF;
	  if (flagandtable.containsKey(temp)) {
	    mask=((Integer)flagandtable.get(temp)).intValue();
	  }
	  mask&=(0xFFFFFFFF^flagid);
	  flagandtable.put(temp,new Integer(mask));
	}
      }
    }


    HashSet flagtagset=new HashSet();
    flagtagset.addAll(flagortable.keySet());
    flagtagset.addAll(flagandtable.keySet());
    flagtagset.addAll(tagsettable.keySet());
    flagtagset.addAll(tagcleartable.keySet());

    Iterator ftit=flagtagset.iterator();
    while(ftit.hasNext()) {
      TempDescriptor temp=(TempDescriptor)ftit.next();


      Set tagtmps=tagcleartable.get(temp);
      if (tagtmps!=null) {
	Iterator tagit=tagtmps.iterator();
	while(tagit.hasNext()) {
	  TempDescriptor tagtmp=(TempDescriptor)tagit.next();
	  if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC))
	    output.println("tagclear("+localsprefixaddr+", (struct ___Object___ *)"+generateTemp(fm, temp,lb)+", "+generateTemp(fm,tagtmp,lb)+");");
	  else
	    output.println("tagclear((struct ___Object___ *)"+generateTemp(fm, temp,lb)+", "+generateTemp(fm,tagtmp,lb)+");");
	}
      }

      tagtmps=tagsettable.get(temp);
      if (tagtmps!=null) {
	Iterator tagit=tagtmps.iterator();
	while(tagit.hasNext()) {
	  TempDescriptor tagtmp=(TempDescriptor)tagit.next();
	  if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC))
	    output.println("tagset("+localsprefixaddr+", (struct ___Object___ *)"+generateTemp(fm, temp,lb)+", "+generateTemp(fm,tagtmp,lb)+");");
	  else
	    output.println("tagset((struct ___Object___ *)"+generateTemp(fm, temp, lb)+", "+generateTemp(fm,tagtmp, lb)+");");
	}
      }

      int ormask=0;
      int andmask=0xFFFFFFF;

      if (flagortable.containsKey(temp))
	ormask=((Integer)flagortable.get(temp)).intValue();
      if (flagandtable.containsKey(temp))
	andmask=((Integer)flagandtable.get(temp)).intValue();
      generateFlagOrAnd(ffan, fm, lb, temp, output, ormask, andmask);
      generateObjectDistribute(ffan, fm, lb, temp, output);
    }
  }

  protected void generateFlagOrAnd(FlatFlagActionNode ffan, FlatMethod fm, LocalityBinding lb, TempDescriptor temp,
                                   PrintWriter output, int ormask, int andmask) {
    if (ffan.getTaskType()==FlatFlagActionNode.NEWOBJECT) {
      output.println("flagorandinit("+generateTemp(fm, temp, lb)+", 0x"+Integer.toHexString(ormask)+", 0x"+Integer.toHexString(andmask)+");");
    } else {
      output.println("flagorand("+generateTemp(fm, temp, lb)+", 0x"+Integer.toHexString(ormask)+", 0x"+Integer.toHexString(andmask)+");");
    }
  }

  protected void generateObjectDistribute(FlatFlagActionNode ffan, FlatMethod fm, LocalityBinding lb, TempDescriptor temp, PrintWriter output) {
    output.println("enqueueObject("+generateTemp(fm, temp, lb)+");");
  }

  void generateOptionalHeader(PrintWriter headers) {

    //GENERATE HEADERS
    headers.println("#include \"task.h\"\n\n");
    headers.println("#ifndef _OPTIONAL_STRUCT_");
    headers.println("#define _OPTIONAL_STRUCT_");

    //STRUCT PREDICATEMEMBER
    headers.println("struct predicatemember{");
    headers.println("int type;");
    headers.println("int numdnfterms;");
    headers.println("int * flags;");
    headers.println("int numtags;");
    headers.println("int * tags;\n};\n\n");

    //STRUCT OPTIONALTASKDESCRIPTOR
    headers.println("struct optionaltaskdescriptor{");
    headers.println("struct taskdescriptor * task;");
    headers.println("int index;");
    headers.println("int numenterflags;");
    headers.println("int * enterflags;");
    headers.println("int numpredicatemembers;");
    headers.println("struct predicatemember ** predicatememberarray;");
    headers.println("};\n\n");

    //STRUCT TASKFAILURE
    headers.println("struct taskfailure {");
    headers.println("struct taskdescriptor * task;");
    headers.println("int index;");
    headers.println("int numoptionaltaskdescriptors;");
    headers.println("struct optionaltaskdescriptor ** optionaltaskdescriptorarray;\n};\n\n");

    //STRUCT FSANALYSISWRAPPER
    headers.println("struct fsanalysiswrapper{");
    headers.println("int  flags;");
    headers.println("int numtags;");
    headers.println("int * tags;");
    headers.println("int numtaskfailures;");
    headers.println("struct taskfailure ** taskfailurearray;");
    headers.println("int numoptionaltaskdescriptors;");
    headers.println("struct optionaltaskdescriptor ** optionaltaskdescriptorarray;\n};\n\n");

    //STRUCT CLASSANALYSISWRAPPER
    headers.println("struct classanalysiswrapper{");
    headers.println("int type;");
    headers.println("int numotd;");
    headers.println("struct optionaltaskdescriptor ** otdarray;");
    headers.println("int numfsanalysiswrappers;");
    headers.println("struct fsanalysiswrapper ** fsanalysiswrapperarray;\n};");

    headers.println("extern struct classanalysiswrapper * classanalysiswrapperarray[];");

    Iterator taskit=state.getTaskSymbolTable().getDescriptorsIterator();
    while(taskit.hasNext()) {
      TaskDescriptor td=(TaskDescriptor)taskit.next();
      headers.println("extern struct taskdescriptor task_"+td.getSafeSymbol()+";");
    }

  }

  //CHECK OVER THIS -- THERE COULD BE SOME ERRORS HERE
  int generateOptionalPredicate(Predicate predicate, OptionalTaskDescriptor otd, ClassDescriptor cdtemp, PrintWriter output) {
    int predicateindex = 0;
    //iterate through the classes concerned by the predicate
    Set c_vard = predicate.vardescriptors;
    Hashtable<TempDescriptor, Integer> slotnumber=new Hashtable<TempDescriptor, Integer>();
    int current_slot=0;

    for(Iterator vard_it = c_vard.iterator(); vard_it.hasNext();) {
      VarDescriptor vard = (VarDescriptor)vard_it.next();
      TypeDescriptor typed = vard.getType();

      //generate for flags
      HashSet fen_hashset = predicate.flags.get(vard.getSymbol());
      output.println("int predicateflags_"+predicateindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"[]={");
      int numberterms=0;
      if (fen_hashset!=null) {
	for (Iterator fen_it = fen_hashset.iterator(); fen_it.hasNext();) {
	  FlagExpressionNode fen = (FlagExpressionNode)fen_it.next();
	  if (fen!=null) {
	    DNFFlag dflag=fen.getDNF();
	    numberterms+=dflag.size();

	    Hashtable flags=(Hashtable)flagorder.get(typed.getClassDesc());

	    for(int j=0; j<dflag.size(); j++) {
	      if (j!=0)
		output.println(",");
	      Vector term=dflag.get(j);
	      int andmask=0;
	      int checkmask=0;
	      for(int k=0; k<term.size(); k++) {
		DNFFlagAtom dfa=(DNFFlagAtom)term.get(k);
		FlagDescriptor fd=dfa.getFlag();
		boolean negated=dfa.getNegated();
		int flagid=1<<((Integer)flags.get(fd)).intValue();
		andmask|=flagid;
		if (!negated)
		  checkmask|=flagid;
	      }
	      output.print("/*andmask*/0x"+Integer.toHexString(andmask)+", /*checkmask*/0x"+Integer.toHexString(checkmask));
	    }
	  }
	}
      }
      output.println("};\n");

      //generate for tags
      TagExpressionList tagel = predicate.tags.get(vard.getSymbol());
      output.println("int predicatetags_"+predicateindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"[]={");
      int numtags = 0;
      if (tagel!=null) {
	for(int j=0; j<tagel.numTags(); j++) {
	  if (j!=0)
	    output.println(",");
	  TempDescriptor tmp=tagel.getTemp(j);
	  if (!slotnumber.containsKey(tmp)) {
	    Integer slotint=new Integer(current_slot++);
	    slotnumber.put(tmp,slotint);
	  }
	  int slot=slotnumber.get(tmp).intValue();
	  output.println("/* slot */"+ slot+", /*tagid*/"+state.getTagId(tmp.getTag()));
	}
	numtags = tagel.numTags();
      }
      output.println("};");

      //store the result into a predicatemember struct
      output.println("struct predicatemember predicatemember_"+predicateindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"={");
      output.println("/*type*/"+typed.getClassDesc().getId()+",");
      output.println("/* number of dnf terms */"+numberterms+",");
      output.println("predicateflags_"+predicateindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+",");
      output.println("/* number of tag */"+numtags+",");
      output.println("predicatetags_"+predicateindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+",");
      output.println("};\n");
      predicateindex++;
    }


    //generate an array that stores the entire predicate
    output.println("struct predicatemember * predicatememberarray_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"[]={");
    for( int j = 0; j<predicateindex; j++) {
      if( j != predicateindex-1) output.println("&predicatemember_"+j+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+",");
      else output.println("&predicatemember_"+j+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol());
    }
    output.println("};\n");
    return predicateindex;
  }


  void generateOptionalArrays(PrintWriter output, PrintWriter headers, Hashtable<ClassDescriptor, Hashtable<FlagState, Set<OptionalTaskDescriptor>>> safeexecution, Hashtable optionaltaskdescriptors) {
    generateOptionalHeader(headers);
    //GENERATE STRUCTS
    output.println("#include \"optionalstruct.h\"\n\n");
    output.println("#include \"stdlib.h\"\n");

    HashSet processedcd = new HashSet();
    int maxotd=0;
    Enumeration e = safeexecution.keys();
    while (e.hasMoreElements()) {
      int numotd=0;
      //get the class
      ClassDescriptor cdtemp=(ClassDescriptor)e.nextElement();
      Hashtable flaginfo=(Hashtable)flagorder.get(cdtemp);       //will be used several times

      //Generate the struct of optionals
      Collection c_otd = ((Hashtable)optionaltaskdescriptors.get(cdtemp)).values();
      numotd = c_otd.size();
      if(maxotd<numotd) maxotd = numotd;
      if( !c_otd.isEmpty() ) {
	for(Iterator otd_it = c_otd.iterator(); otd_it.hasNext();) {
	  OptionalTaskDescriptor otd = (OptionalTaskDescriptor)otd_it.next();

	  //generate the int arrays for the predicate
	  Predicate predicate = otd.predicate;
	  int predicateindex = generateOptionalPredicate(predicate, otd, cdtemp, output);
	  TreeSet<Integer> fsset=new TreeSet<Integer>();
	  //iterate through possible FSes corresponding to
	  //the state when entering

	  for(Iterator fses = otd.enterflagstates.iterator(); fses.hasNext();) {
	    FlagState fs = (FlagState)fses.next();
	    int flagid=0;
	    for(Iterator flags = fs.getFlags(); flags.hasNext();) {
	      FlagDescriptor flagd = (FlagDescriptor)flags.next();
	      int id=1<<((Integer)flaginfo.get(flagd)).intValue();
	      flagid|=id;
	    }
	    fsset.add(new Integer(flagid));
	    //tag information not needed because tag
	    //changes are not tolerated.
	  }

	  output.println("int enterflag_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"[]={");
	  boolean needcomma=false;
	  for(Iterator<Integer> it=fsset.iterator(); it.hasNext();) {
	    if(needcomma)
	      output.print(", ");
	    output.println(it.next());
	  }

	  output.println("};\n");


	  //generate optionaltaskdescriptor that actually
	  //includes exit fses, predicate and the task
	  //concerned
	  output.println("struct optionaltaskdescriptor optionaltaskdescriptor_"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"={");
	  output.println("&task_"+otd.td.getSafeSymbol()+",");
	  output.println("/*index*/"+otd.getIndex()+",");
	  output.println("/*number of enter flags*/"+fsset.size()+",");
	  output.println("enterflag_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+",");
	  output.println("/*number of members */"+predicateindex+",");
	  output.println("predicatememberarray_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+",");
	  output.println("};\n");
	}
      } else
	continue;
      // if there are no optionals, there is no need to build the rest of the struct

      output.println("struct optionaltaskdescriptor * otdarray"+cdtemp.getSafeSymbol()+"[]={");
      c_otd = ((Hashtable)optionaltaskdescriptors.get(cdtemp)).values();
      if( !c_otd.isEmpty() ) {
	boolean needcomma=false;
	for(Iterator otd_it = c_otd.iterator(); otd_it.hasNext();) {
	  OptionalTaskDescriptor otd = (OptionalTaskDescriptor)otd_it.next();
	  if(needcomma)
	    output.println(",");
	  needcomma=true;
	  output.println("&optionaltaskdescriptor_"+otd.getuid()+"_"+cdtemp.getSafeSymbol());
	}
      }
      output.println("};\n");

      //get all the possible flagstates reachable by an object
      Hashtable hashtbtemp = safeexecution.get(cdtemp);
      int fscounter = 0;
      TreeSet fsts=new TreeSet(new FlagComparator(flaginfo));
      fsts.addAll(hashtbtemp.keySet());
      for(Iterator fsit=fsts.iterator(); fsit.hasNext();) {
	FlagState fs = (FlagState)fsit.next();
	fscounter++;

	//get the set of OptionalTaskDescriptors corresponding
	HashSet<OptionalTaskDescriptor> availabletasks = (HashSet<OptionalTaskDescriptor>)hashtbtemp.get(fs);
	//iterate through the OptionalTaskDescriptors and
	//store the pointers to the optionals struct (see on
	//top) into an array

	output.println("struct optionaltaskdescriptor * optionaltaskdescriptorarray_FS"+fscounter+"_"+cdtemp.getSafeSymbol()+"[] = {");
	for(Iterator<OptionalTaskDescriptor> mos = ordertd(availabletasks).iterator(); mos.hasNext();) {
	  OptionalTaskDescriptor mm = mos.next();
	  if(!mos.hasNext())
	    output.println("&optionaltaskdescriptor_"+mm.getuid()+"_"+cdtemp.getSafeSymbol());
	  else
	    output.println("&optionaltaskdescriptor_"+mm.getuid()+"_"+cdtemp.getSafeSymbol()+",");
	}

	output.println("};\n");

	//process flag information (what the flag after failure is) so we know what optionaltaskdescriptors to choose.

	int flagid=0;
	for(Iterator flags = fs.getFlags(); flags.hasNext();) {
	  FlagDescriptor flagd = (FlagDescriptor)flags.next();
	  int id=1<<((Integer)flaginfo.get(flagd)).intValue();
	  flagid|=id;
	}

	//process tag information

	int tagcounter = 0;
	boolean first = true;
	Enumeration tag_enum = fs.getTags();
	output.println("int tags_FS"+fscounter+"_"+cdtemp.getSafeSymbol()+"[]={");
	while(tag_enum.hasMoreElements()) {
	  tagcounter++;
	  TagDescriptor tagd = (TagDescriptor)tag_enum.nextElement();
	  if(first==true)
	    first = false;
	  else
	    output.println(", ");
	  output.println("/*tagid*/"+state.getTagId(tagd));
	}
	output.println("};");

	Set<TaskIndex> tiset=sa.getTaskIndex(fs);
	for(Iterator<TaskIndex> itti=tiset.iterator(); itti.hasNext();) {
	  TaskIndex ti=itti.next();
	  if (ti.isRuntime())
	    continue;

	  Set<OptionalTaskDescriptor> otdset=sa.getOptions(fs, ti);

	  output.print("struct optionaltaskdescriptor * optionaltaskfailure_FS"+fscounter+"_"+ti.getTask().getSafeSymbol()+"_"+ti.getIndex()+"_array[] = {");
	  boolean needcomma=false;
	  for(Iterator<OptionalTaskDescriptor> otdit=ordertd(otdset).iterator(); otdit.hasNext();) {
	    OptionalTaskDescriptor otd=otdit.next();
	    if(needcomma)
	      output.print(", ");
	    needcomma=true;
	    output.println("&optionaltaskdescriptor_"+otd.getuid()+"_"+cdtemp.getSafeSymbol());
	  }
	  output.println("};");

	  output.print("struct taskfailure taskfailure_FS"+fscounter+"_"+ti.getTask().getSafeSymbol()+"_"+ti.getIndex()+" = {");
	  output.print("&task_"+ti.getTask().getSafeSymbol()+", ");
	  output.print(ti.getIndex()+", ");
	  output.print(otdset.size()+", ");
	  output.print("optionaltaskfailure_FS"+fscounter+"_"+ti.getTask().getSafeSymbol()+"_"+ti.getIndex()+"_array");
	  output.println("};");
	}

	tiset=sa.getTaskIndex(fs);
	boolean needcomma=false;
	int runtimeti=0;
	output.println("struct taskfailure * taskfailurearray"+fscounter+"_"+cdtemp.getSafeSymbol()+"[]={");
	for(Iterator<TaskIndex> itti=tiset.iterator(); itti.hasNext();) {
	  TaskIndex ti=itti.next();
	  if (ti.isRuntime()) {
	    runtimeti++;
	    continue;
	  }
	  if (needcomma)
	    output.print(", ");
	  needcomma=true;
	  output.print("&taskfailure_FS"+fscounter+"_"+ti.getTask().getSafeSymbol()+"_"+ti.getIndex());
	}
	output.println("};\n");

	//Store the result in fsanalysiswrapper

	output.println("struct fsanalysiswrapper fsanalysiswrapper_FS"+fscounter+"_"+cdtemp.getSafeSymbol()+"={");
	output.println("/*flag*/"+flagid+",");
	output.println("/* number of tags*/"+tagcounter+",");
	output.println("tags_FS"+fscounter+"_"+cdtemp.getSafeSymbol()+",");
	output.println("/* numtask failures */"+(tiset.size()-runtimeti)+",");
	output.println("taskfailurearray"+fscounter+"_"+cdtemp.getSafeSymbol()+",");
	output.println("/* number of optionaltaskdescriptors */"+availabletasks.size()+",");
	output.println("optionaltaskdescriptorarray_FS"+fscounter+"_"+cdtemp.getSafeSymbol());
	output.println("};\n");

      }

      //Build the array of fsanalysiswrappers
      output.println("struct fsanalysiswrapper * fsanalysiswrapperarray_"+cdtemp.getSafeSymbol()+"[] = {");
      boolean needcomma=false;
      for(int i = 0; i<fscounter; i++) {
	if (needcomma) output.print(",");
	output.println("&fsanalysiswrapper_FS"+(i+1)+"_"+cdtemp.getSafeSymbol());
	needcomma=true;
      }
      output.println("};");

      //Build the classanalysiswrapper referring to the previous array
      output.println("struct classanalysiswrapper classanalysiswrapper_"+cdtemp.getSafeSymbol()+"={");
      output.println("/*type*/"+cdtemp.getId()+",");
      output.println("/*numotd*/"+numotd+",");
      output.println("otdarray"+cdtemp.getSafeSymbol()+",");
      output.println("/* number of fsanalysiswrappers */"+fscounter+",");
      output.println("fsanalysiswrapperarray_"+cdtemp.getSafeSymbol()+"};\n");
      processedcd.add(cdtemp);
    }

    //build an array containing every classes for which code has been build
    output.println("struct classanalysiswrapper * classanalysiswrapperarray[]={");
    for(int i=0; i<state.numClasses(); i++) {
      ClassDescriptor cn=cdarray[i];
      if (i>0)
	output.print(", ");
      if ((cn != null) && (processedcd.contains(cn)))
	output.print("&classanalysiswrapper_"+cn.getSafeSymbol());
      else
	output.print("NULL");
    }
    output.println("};");

    output.println("#define MAXOTD "+maxotd);
    headers.println("#endif");
  }

  public List<OptionalTaskDescriptor> ordertd(Set<OptionalTaskDescriptor> otdset) {
    Relation r=new Relation();
    for(Iterator<OptionalTaskDescriptor>otdit=otdset.iterator(); otdit.hasNext();) {
      OptionalTaskDescriptor otd=otdit.next();
      TaskIndex ti=new TaskIndex(otd.td, otd.getIndex());
      r.put(ti, otd);
    }

    LinkedList<OptionalTaskDescriptor> l=new LinkedList<OptionalTaskDescriptor>();
    for(Iterator it=r.keySet().iterator(); it.hasNext();) {
      Set s=r.get(it.next());
      for(Iterator it2=s.iterator(); it2.hasNext();) {
	OptionalTaskDescriptor otd=(OptionalTaskDescriptor)it2.next();
	l.add(otd);
      }
    }

    return l;
  }

  protected void outputTransCode(PrintWriter output) {
  }



  // override these methods in a subclass of BuildCode
  // to generate code for additional systems
  protected void additionalIncludesMethodsHeader( PrintWriter outmethodheader ) {}
  protected void preCodeGenInitialization() {}
  protected void postCodeGenCleanUp() {}
  protected void additionalCodeGen( PrintWriter outmethodheader,
                                    PrintWriter outstructs,
                                    PrintWriter outmethod ) {}
  protected void additionalCodeAtTopOfMain( PrintWriter outmethod ) {}
  protected void additionalCodeAtBottomOfMain( PrintWriter outmethod ) {}
  protected void additionalIncludesMethodsImplementation( PrintWriter outmethod ) {}
  protected void additionalIncludesStructsHeader( PrintWriter outstructs ) {}
  protected void additionalClassObjectFields( PrintWriter outclassdefs ) {}
  protected void additionalCodeAtTopMethodsImplementation( PrintWriter outmethod ) {}
  protected void additionalCodeAtTopFlatMethodBody( PrintWriter output, FlatMethod fm ) {}
  protected void additionalCodePreNode( FlatMethod fm, LocalityBinding lb, FlatNode fn, PrintWriter output ) {}
  protected void additionalCodePostNode( FlatMethod fm, LocalityBinding lb, FlatNode fn, PrintWriter output ) {}
}






