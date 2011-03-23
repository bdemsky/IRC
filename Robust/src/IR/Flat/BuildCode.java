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
import Analysis.CallGraph.CallGraph;
import Analysis.Loops.GlobalFieldType;
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
  String nextobjstr="___nextobject___";
  String localcopystr="___localcopy___";
  public static boolean GENERATEPRECISEGC=false;
  public static String PREFIX="";
  public static String arraytype="ArrayObject";
  public static int flagcount = 0;
  Virtual virtualcalls;
  TypeUtil typeutil;
  protected int maxtaskparams=0;
  protected int maxcount=0;
  ClassDescriptor[] cdarray;
  ClassDescriptor[] ifarray;
  TypeDescriptor[] arraytable;
  SafetyAnalysis sa;
  CallGraph callgraph;
  Hashtable<String, ClassDescriptor> printedfieldstbl;
  int globaldefscount=0;
  boolean mgcstaticinit = false;

  public BuildCode(State st, Hashtable temptovar, TypeUtil typeutil) {
    this(st, temptovar, typeutil, null);
  }

  public BuildCode(State st, Hashtable temptovar, TypeUtil typeutil, SafetyAnalysis sa) {
    this.sa=sa;
    state=st;
    callgraph=new CallGraph(state);
    this.temptovar=temptovar;
    paramstable=new Hashtable();
    tempstable=new Hashtable();
    fieldorder=new Hashtable();
    flagorder=new Hashtable();
    this.typeutil=typeutil;
    checkMethods2Gen();
    virtualcalls=new Virtual(state, null);
    printedfieldstbl = new Hashtable<String, ClassDescriptor>();
  }

  /** The buildCode method outputs C code for all the methods.  The Flat
   * versions of the methods must already be generated and stored in
   * the State object. */


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
    PrintWriter outglobaldefsprim=null;

    try {
      buildCodeSetup(); //EXTENSION POINT
      outstructs=new CodePrinter(new FileOutputStream(PREFIX+"structdefs.h"), true);
      outmethodheader=new CodePrinter(new FileOutputStream(PREFIX+"methodheaders.h"), true);
      outclassdefs=new CodePrinter(new FileOutputStream(PREFIX+"classdefs.h"), true);
      outglobaldefs=new CodePrinter(new FileOutputStream(PREFIX+"globaldefs.h"), true);
      outglobaldefsprim=new CodePrinter(new FileOutputStream(PREFIX+"globaldefsprim.h"), true);
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

    /* Fix field safe symbols due to shadowing */
    FieldShadow.handleFieldShadow(state);

    /* Build the virtual dispatch tables */
    buildVirtualTables(outvirtual);

    /* Tag the methods that are invoked by static blocks */
    tagMethodInvokedByStaticBlock();

    /* Output includes */
    outmethodheader.println("#ifndef METHODHEADERS_H");
    outmethodheader.println("#define METHODHEADERS_H");
    outmethodheader.println("#include \"structdefs.h\"");

    if (state.EVENTMONITOR) {
      outmethodheader.println("#include \"monitor.h\"");
    }

    additionalIncludesMethodsHeader(outmethodheader);

    /* Output Structures */
    outputStructs(outstructs);

    // Output the C class declarations
    // These could mutually reference each other

    outglobaldefs.println("#ifndef __GLOBALDEF_H_");
    outglobaldefs.println("#define __GLOBALDEF_H_");
    outglobaldefs.println("");
    outglobaldefs.println("struct global_defs_t {");
    outglobaldefs.println("  int size;");
    outglobaldefs.println("  void * next;");
    outglobaldefsprim.println("#ifndef __GLOBALDEFPRIM_H_");
    outglobaldefsprim.println("#define __GLOBALDEFPRIM_H_");
    outglobaldefsprim.println("");
    outglobaldefsprim.println("struct global_defsprim_t {");

    outclassdefs.println("#ifndef __CLASSDEF_H_");
    outclassdefs.println("#define __CLASSDEF_H_");
    outputClassDeclarations(outclassdefs, outglobaldefs, outglobaldefsprim);

    // Output function prototypes and structures for parameters
    Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
    while(it.hasNext()) {
      ClassDescriptor cn=(ClassDescriptor)it.next();
      generateCallStructs(cn, outclassdefs, outstructs, outmethodheader, outglobaldefs, outglobaldefsprim);
    }
    outclassdefs.println("#include \"globaldefs.h\"");
    outclassdefs.println("#include \"globaldefsprim.h\"");
    outclassdefs.println("#endif");
    outclassdefs.close();
    
    outglobaldefs.println("};");
    outglobaldefs.println("");
    outglobaldefs.println("extern struct global_defs_t * global_defs_p;");
    outglobaldefs.println("#endif");
    outglobaldefs.flush();
    outglobaldefs.close();

    outglobaldefsprim.println("};");
    outglobaldefsprim.println("");
    outglobaldefsprim.println("extern struct global_defsprim_t * global_defsprim_p;");
    outglobaldefsprim.println("#endif");
    outglobaldefsprim.flush();
    outglobaldefsprim.close();

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
    additionalCodeGen(outmethodheader,
                      outstructs,
                      outmethod);


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

  /* This method goes though the call graph and check which methods are really
   * invoked and should be generated
   */
  protected void checkMethods2Gen() {
    MethodDescriptor md=(state.main==null)?null:typeutil.getMain();
    
    if(md != null) {
      // check the methods to be generated
      state.setGenAllMethods(false);
    } else {
      // generate all methods
      return;
    }
    this.state.addMethod2gen(md);
    
    Iterator it_classes = this.state.getClassSymbolTable().getDescriptorsIterator();
    while(it_classes.hasNext()) {
      ClassDescriptor cd = (ClassDescriptor)it_classes.next();
      Iterator it_methods = cd.getMethodTable().getDescriptorsIterator();
      while(it_methods.hasNext()) {
        md = (MethodDescriptor)it_methods.next();
        if(md.isStaticBlock() || md.getModifiers().isNative() || this.callgraph.getCallerSet(md).size() > 0
            || (cd.getSymbol().equals("Thread") && md.getSymbol().equals("staticStart"))) {
          this.state.addMethod2gen(md);
        }
      }
    }
  }


  /* This method goes though the call graph and tag those methods that are
   * invoked inside static blocks
   */
  protected void tagMethodInvokedByStaticBlock() {
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
  }

  /* This code generates code for each static block and static field
   * initialization.*/
  protected void outputStaticBlocks(PrintWriter outmethod) {
    //  execute all the static blocks and all the static field initializations
    // execute all the static blocks and all the static field initializations
    SymbolTable sctbl = this.state.getSClassSymbolTable();
    Iterator it_sclasses = sctbl.getDescriptorsIterator();
    if(it_sclasses.hasNext()) {
      while(it_sclasses.hasNext()) {
        ClassDescriptor t_cd = (ClassDescriptor)it_sclasses.next();
        MethodDescriptor t_md = (MethodDescriptor)t_cd.getMethodTable().get("staticblocks");
        if(t_md != null) {
          outmethod.println("   {");
          if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
            outmethod.print("       struct "+t_cd.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"_params __parameterlist__={");
            outmethod.println("0, NULL};");
            outmethod.println("     "+t_cd.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"(& __parameterlist__);");
          } else {
            outmethod.println("     "+t_cd.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"();");
          }
          outmethod.println("   }");
        }
      }
    }
  }

  /* This code generates code to create a Class object for each class for
   * getClass() method.
   * */
  protected void outputClassObjects(PrintWriter outmethod) {
    // for each class, initialize its Class object
    SymbolTable ctbl = this.state.getClassSymbolTable();
    for(Iterator it_classes = ctbl.getDescriptorsIterator();it_classes.hasNext();) {
      ClassDescriptor t_cd = (ClassDescriptor)it_classes.next();
      outmethod.println(" {");
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	outmethod.println("    struct garbagelist dummy={0,NULL};");
	outmethod.println("    global_defs_p->"+t_cd.getSafeSymbol()+"classobj = allocate_new(&dummy, " + typeutil.getClass(TypeUtil.ObjectClass).getId() + ");");
      } else 
	outmethod.println("    global_defs_p->"+t_cd.getSafeSymbol()+"classobj = allocate_new("+typeutil.getClass(TypeUtil.ObjectClass).getId() + ");");
      outmethod.println(" }");
    }
  }

  /* This code just generates the main C method for java programs.
   * The main C method packs up the arguments into a string array
   * and passes it to the java main method. */

  protected void outputMainMethod(PrintWriter outmethod) {
    outmethod.println("int main(int argc, const char *argv[]) {");
    outmethod.println("  int i;");
    outmethod.println("  global_defs_p=calloc(1, sizeof(struct global_defs_t));");
    outmethod.println("  global_defsprim_p=calloc(1, sizeof(struct global_defsprim_t));");
    if (GENERATEPRECISEGC) {
      outmethod.println("  global_defs_p->size="+globaldefscount+";");
      outmethod.println("  for(i=0;i<"+globaldefscount+";i++) {");
      outmethod.println("    ((struct garbagelist *)global_defs_p)->array[i]=NULL;");
      outmethod.println("  }");
    }
    outputStaticBlocks(outmethod);
    outputClassObjects(outmethod);


    additionalCodeAtTopOfMain(outmethod);

    if (state.THREAD) {
      outmethod.println("initializethreads();");
    }

    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      outmethod.println("  struct ArrayObject * stringarray=allocate_newarray(NULL, STRINGARRAYTYPE, argc-1);");
    } else {
      outmethod.println("  struct ArrayObject * stringarray=allocate_newarray(STRINGARRAYTYPE, argc-1);");
    }
    outmethod.println("  for(i=1;i<argc;i++) {");
    outmethod.println("    int length=strlen(argv[i]);");

    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      outmethod.println("    struct ___String___ *newstring=NewString(NULL, argv[i], length);");
    } else {
      outmethod.println("    struct ___String___ *newstring=NewString(argv[i], length);");
    }
    outmethod.println("    ((void **)(((char *)& stringarray->___length___)+sizeof(int)))[i-1]=newstring;");
    outmethod.println("  }");

    MethodDescriptor md=typeutil.getMain();
    ClassDescriptor cd=typeutil.getMainClass();

    outmethod.println("   {");
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      outmethod.print("       struct "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params __parameterlist__={");
      outmethod.println("1, NULL,"+"stringarray};");
      outmethod.println("     "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(& __parameterlist__);");
    } else {
      outmethod.println("     "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(stringarray);");
    }
    outmethod.println("   }");

    if (state.THREAD) {
      outmethod.println("pthread_mutex_lock(&gclistlock);");
      outmethod.println("threadcount--;");
      outmethod.println("pthread_cond_signal(&gccond);");
      outmethod.println("pthread_mutex_unlock(&gclistlock);");
    }

    if (state.EVENTMONITOR) {
      outmethod.println("dumpdata();");
    }

    if (state.THREAD)
      outmethod.println("pthread_exit(NULL);");


    additionalCodeAtBottomOfMain(outmethod);

    outmethod.println("}");
  }

  /* This method outputs code for each task. */

  protected void outputTaskCode(PrintWriter outtaskdefs, PrintWriter outmethod) {
    /* Compile task based program */
    outtaskdefs.println("#include \"task.h\"");
    outtaskdefs.println("#include \"methodheaders.h\"");
    Iterator taskit=state.getTaskSymbolTable().getDescriptorsIterator();
    while(taskit.hasNext()) {
      TaskDescriptor td=(TaskDescriptor)taskit.next();
      FlatMethod fm=state.getMethodFlat(td);
      generateFlatMethod(fm, outmethod);
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


    additionalIncludesMethodsImplementation(outmethod);

    outmethod.println("struct global_defs_t * global_defs_p;");
    outmethod.println("struct global_defsprim_t * global_defsprim_p;");
    //Store the sizes of classes & array elements
    generateSizeArray(outmethod);

    //Store table of supertypes
    generateSuperTypeTable(outmethod);

    //Store the layout of classes
    generateLayoutStructs(outmethod);


    additionalCodeAtTopMethodsImplementation(outmethod);

    generateMethods(outmethod);
  }

  protected void generateMethods(PrintWriter outmethod) {
    /* Generate code for methods */
    Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
    while(classit.hasNext()) {
      ClassDescriptor cn=(ClassDescriptor)classit.next();
      Iterator methodit=cn.getMethods();
      while(methodit.hasNext()) {
	/* Classify parameters */
	MethodDescriptor md=(MethodDescriptor)methodit.next();
    if(!this.state.genAllMethods) {
      boolean foundmatch = false;
      Set vec_md = this.state.getMethod2gen().getSet(md.getSymbol());
      for(Iterator matchit=vec_md.iterator(); matchit.hasNext();) {
        MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
        if (md.matches(matchmd)) {
          foundmatch=true;
          break;
        }
      }
      if(!foundmatch) {
        continue;
      }
    }
	FlatMethod fm=state.getMethodFlat(md);
	if (!md.getModifiers().isNative()) {
	  generateFlatMethod(fm, outmethod);
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


    additionalIncludesStructsHeader(outstructs);


    /* Output #defines that the runtime uses to determine type
     * numbers for various objects it needs */
    outstructs.println("#define MAXCOUNT "+maxcount);

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
    int totalClassSize = state.numClasses() + state.numArrays() + state.numInterfaces();
    outstructs.println("#define TOTALNUMCLASSANDARRAY "+ totalClassSize);
    if (state.TASK) {
      outstructs.println("#define STARTUPTYPE "+typeutil.getClass(TypeUtil.StartupClass).getId());
      outstructs.println("#define TAGTYPE "+typeutil.getClass(TypeUtil.TagClass).getId());
      outstructs.println("#define TAGARRAYTYPE "+
                         (state.getArrayNumber(new TypeDescriptor(typeutil.getClass(TypeUtil.TagClass)).makeArray(state))+state.numClasses()));
    }
  }

  protected void outputClassDeclarations(PrintWriter outclassdefs, PrintWriter outglobaldefs, PrintWriter outglobaldefsprim) {
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

      if((cn.getNumStaticFields() != 0) || (cn.getNumStaticBlocks() != 0)) {
	// this class has static fields/blocks, need to add a global flag to
	// indicate if its static fields have been initialized and/or if its
	// static blocks have been executed
	outglobaldefsprim.println("  int "+cn.getSafeSymbol()+"static_block_exe_flag;");
      }
      
      // for each class, create a global object
      outglobaldefs.println("  struct ___Object___ *"+cn.getSafeSymbol()+"classobj;");
      globaldefscount++;
    }
    outclassdefs.println("");
    //Print out definition for array type
    outclassdefs.println("struct "+arraytype+" {");
    outclassdefs.println("  int type;");


    additionalClassObjectFields(outclassdefs);


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
      outclassdefs.println("  volatile int notifycount;");
      outclassdefs.println("  volatile int objlock;");
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

    printClassStruct(typeutil.getClass(TypeUtil.ObjectClass), outclassdefs, outglobaldefs, outglobaldefsprim);
    printedfieldstbl.clear();
    printExtraArrayFields(outclassdefs);
    if (state.ARRAYPAD) {
      outclassdefs.println("  int paddingforarray;");
    }

    outclassdefs.println("  int ___length___;");
    outclassdefs.println("};\n");

    if(state.MGC) {
      // TODO add version for normal Java later
      outclassdefs.println("");
      //Print out definition for Class type
      outclassdefs.println("struct Class {");
      outclassdefs.println("  int type;");


      additionalClassObjectFields(outclassdefs);


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
	outclassdefs.println("  volatile int notifycount;");
	outclassdefs.println("  volatile int objlock;");
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
	  outclassdefs.println("  int * lock;"); // lock entry for this obj
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
      printClassStruct(typeutil.getClass(TypeUtil.ObjectClass), outclassdefs, outglobaldefs, outglobaldefsprim);
      printedfieldstbl.clear();
      outclassdefs.println("};\n");
    }

    outclassdefs.println("");
    outclassdefs.println("extern int classsize[];");
    outclassdefs.println("extern int hasflags[];");
    outclassdefs.println("extern unsigned INTPTR * pointerarray[];");
    outclassdefs.println("extern int* supertypes[];");
    outclassdefs.println("");
  }

  /** Prints out definitions for generic task structures */

  protected void outputTaskTypes(PrintWriter outtask) {
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


  protected void buildRepairStructs(PrintWriter outrepairstructs) {
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
      outrepairstructs.println("}\n");
    }
  }

  protected void printRepairStruct(ClassDescriptor cn, PrintWriter output) {
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
  protected void generateTaskDescriptor(PrintWriter output, FlatMethod fm, TaskDescriptor task) {
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
    virtualtable=new MethodDescriptor[state.numClasses()+state.numArrays()][maxcount];

    /* Fill in virtual table */
    classit=state.getClassSymbolTable().getDescriptorsIterator();
    while(classit.hasNext()) {
      ClassDescriptor cd=(ClassDescriptor)classit.next();
      if(cd.isInterface()) {
        continue;
      }        
      fillinRow(cd, virtualtable, cd.getId());
    }

    ClassDescriptor objectcd=typeutil.getClass(TypeUtil.ObjectClass);
    Iterator arrayit=state.getArrayIterator();
    while(arrayit.hasNext()) {
      TypeDescriptor td=(TypeDescriptor)arrayit.next();
      int id=state.getArrayNumber(td);
      fillinRow(objectcd, virtualtable, id+state.numClasses());
    }

    outvirtual.print("void * virtualtable[]={");
    boolean needcomma=false;
    for(int i=0; i<state.numClasses()+state.numArrays(); i++) {
      for(int j=0; j<maxcount; j++) {
	if (needcomma)
	  outvirtual.print(", ");
	if (virtualtable[i][j]!=null) {
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

  protected void fillinRow(ClassDescriptor cd, MethodDescriptor[][] virtualtable, int rownum) {
    /* Get inherited methods */
    Iterator it_sifs = cd.getSuperInterfaces();
    while(it_sifs.hasNext()) {
      ClassDescriptor superif = (ClassDescriptor)it_sifs.next();
      fillinRow(superif, virtualtable, rownum);
    }
    if (cd.getSuperDesc()!=null)
      fillinRow(cd.getSuperDesc(), virtualtable, rownum);
    /* Override them with our methods */
    for(Iterator it=cd.getMethods(); it.hasNext(); ) {
      MethodDescriptor md=(MethodDescriptor)it.next();
      if (md.isStatic()||md.getReturnType()==null)
	continue;
      boolean foundmatch = false;
      if(this.state.genAllMethods) {
        foundmatch = true;
      } else {
        Set vec_md = this.state.getMethod2gen().getSet(md.getSymbol());
        for(Iterator matchit=vec_md.iterator(); matchit.hasNext();) {
          MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
          if (md.matches(matchmd)) {
            foundmatch=true;
            break;
          }
        }
      }
      if(!foundmatch) {
        continue;
      }
      int methodnum = virtualcalls.getMethodNumber(md);
      virtualtable[rownum][methodnum]=md;
    }
  }

  /** Generate array that contains the sizes of class objects.  The
   * object allocation functions in the runtime use this
   * information. */

  protected void generateSizeArray(PrintWriter outclassdefs) {
    outclassdefs.print("extern struct prefetchCountStats * evalPrefetch;\n");
    generateSizeArrayExtensions(outclassdefs);

    Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
    cdarray=new ClassDescriptor[state.numClasses()];
    ifarray = new ClassDescriptor[state.numInterfaces()];
    cdarray[0] = null;
    int interfaceid = 0;
    while(it.hasNext()) {
      ClassDescriptor cd=(ClassDescriptor)it.next();
      if(cd.isInterface()) {
        ifarray[cd.getId()] = cd;
      } else {
        cdarray[cd.getId()] = cd;
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
    
    for(int i=0; i<state.numInterfaces(); i++) {
      ClassDescriptor ifcd = ifarray[i];
      outclassdefs.println(ifcd +"  "+(i+state.numClasses()+state.numArrays()));
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
      if (tdelement.isArray()||tdelement.isClass()||tdelement.isNull())
	outclassdefs.print("sizeof(void *)");
      else
	outclassdefs.print("sizeof("+tdelement.getSafeSymbol()+")");
      needcomma=true;
    }
    
    for(int i=0; i<state.numInterfaces(); i++) {
      if (needcomma)
        outclassdefs.print(", ");
      outclassdefs.print("sizeof(struct "+ifarray[i].getSafeSymbol()+")");
      needcomma=true;
    }

    outclassdefs.println("};");

    ClassDescriptor objectclass=typeutil.getClass(TypeUtil.ObjectClass);
    needcomma=false;
    outclassdefs.print("int typearray[]={");
    for(int i=0; i<state.numClasses(); i++) {
      ClassDescriptor cd=cdarray[i];
      ClassDescriptor supercd=i>0 ? cd.getSuperDesc() : null;
      if(supercd != null && supercd.isInterface()) {
        throw new Error("Super class can not be interfaces");
      }
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
    
    for(int i=0; i<state.numInterfaces(); i++) {
      ClassDescriptor cd=ifarray[i];
      ClassDescriptor supercd=cd.getSuperDesc();
      if(supercd != null && supercd.isInterface()) {
        throw new Error("Super class can not be interfaces");
      }
      if (needcomma)
    outclassdefs.print(", ");
      if (supercd==null)
    outclassdefs.print("-1");
      else
    outclassdefs.print(supercd.getId());
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

  protected void generateTempStructs(FlatMethod fm) {
    MethodDescriptor md=fm.getMethod();
    TaskDescriptor task=fm.getTask();
    ParamsObject objectparams=md!=null ? new ParamsObject(md,tag++) : new ParamsObject(task, tag++);
    if (md!=null)
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
    }

    for(int i=0; i<fm.numTags(); i++) {
      TempDescriptor temp=fm.getTag(i);
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC))
	objectparams.addPtr(temp);
      else
	objectparams.addPrim(temp);
    }

    TempObject objecttemps=md!=null ? new TempObject(objectparams,md,tag++) : new TempObject(objectparams, task, tag++);
    if (md!=null)
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
    if(fd.isStatic()) {
      continue;
    }
	TypeDescriptor type=fd.getType();
	if (type.isPtr())
	  count++;
      }
      output.print(count);
      allit=cn.getFieldTable().getAllDescriptorsIterator();
      while(allit.hasNext()) {
	FieldDescriptor fd=(FieldDescriptor)allit.next();
    if(fd.isStatic()) {
      continue;
    }
	TypeDescriptor type=fd.getType();
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
  
  private int checkarraysupertype(ClassDescriptor arraycd, TypeDescriptor arraytd) {
    int type=-1;
    
    TypeDescriptor supertd=new TypeDescriptor(arraycd);
    supertd.setArrayCount(arraytd.getArrayCount());
    type=state.getArrayNumber(supertd);
    if (type!=-1) {
      return type;
    }
    
    ClassDescriptor cd = arraycd.getSuperDesc();
    if(cd != null) {
      type = checkarraysupertype(cd, arraytd);
      if(type != -1) {
        return type;
      }
    }

    Iterator it_sifs = arraycd.getSuperInterfaces();
    while(it_sifs.hasNext()) {
      ClassDescriptor ifcd = (ClassDescriptor)it_sifs.next();
      type = checkarraysupertype(ifcd, arraytd);
      if(type != -1) {
        return type;
      }
    }
    
    return type;
  }


  /** Print out table to give us supertypes */
  protected void generateSuperTypeTable(PrintWriter output) {
    ClassDescriptor objectclass=typeutil.getClass(TypeUtil.ObjectClass);
    for(int i=0; i<state.numClasses(); i++) {
      ClassDescriptor cn=cdarray[i];
      if(cn == null) {
        continue;
      }
      output.print("int supertypes" + cn.getSafeSymbol() + "[] = {");
      boolean ncomma = false;
      int snum = 0;
      if((cn != null) && (cn.getSuperDesc() != null)) {
        snum++;
      }
      Iterator it_sifs = cn != null? cn.getSuperInterfaces() : null;
      while(it_sifs != null && it_sifs.hasNext()) {
        snum++;
        it_sifs.next();
      }
      output.print(snum);
      ncomma = true;
      if ((cn != null) && (cn.getSuperDesc()!=null)) {
        if(ncomma) {
          output.print(",");
        }
        ClassDescriptor cdsuper=cn.getSuperDesc();
        output.print(cdsuper.getId());
      } 
      it_sifs = cn != null? cn.getSuperInterfaces() : null;
      while(it_sifs != null && it_sifs.hasNext()) {
        if(ncomma) {
          output.print(",");
        }
        output.print(((ClassDescriptor)it_sifs.next()).getId()+state.numClasses()+state.numArrays());
      }
      
      output.println("};");
    }
    
    for(int i=0; i<state.numArrays(); i++) {
      TypeDescriptor arraytd=arraytable[i];
      ClassDescriptor arraycd=arraytd.getClassDesc();
      output.print("int supertypes___arraytype___" + (i+state.numClasses()) + "[] = {");
      boolean ncomma = false;
      int snum = 0;
      if (arraycd==null) {
        snum++;
        output.print(snum);
        output.print(", ");
        output.print(objectclass.getId());
        output.println("};");
        continue;
      }
      if((arraycd != null) && (arraycd.getSuperDesc() != null)) {
        snum++;
      }
      Iterator it_sifs = arraycd != null? arraycd.getSuperInterfaces() : null;
      while(it_sifs != null && it_sifs.hasNext()) {
        snum++;
        it_sifs.next();
      }
      output.print(snum);
      ncomma = true;
      if ((arraycd != null) && (arraycd.getSuperDesc()!=null)) {
        ClassDescriptor cd=arraycd.getSuperDesc();
        int type=-1;
        if(cd!=null) {
          type = checkarraysupertype(cd, arraytd);
          if(type != -1) {
            type += state.numClasses();
          }
        }
        if (ncomma)
          output.print(", ");
        output.print(type);
      } 
      it_sifs = arraycd != null? arraycd.getSuperInterfaces() : null;
      while(it_sifs != null && it_sifs.hasNext()) {
        ClassDescriptor ifcd = (ClassDescriptor)it_sifs.next();
        int type = checkarraysupertype(ifcd , arraytd);
        if(type != -1) {
          type += state.numClasses();
        }
        if (ncomma)
          output.print(", ");
        output.print(type);
      }
      output.println("};");
    }
    
    for(int i=0; i<state.numInterfaces(); i++) {
      ClassDescriptor cn=ifarray[i];
      if(cn == null) {
        continue;
      }
      output.print("int supertypes" + cn.getSafeSymbol() + "[] = {");
      boolean ncomma = false;
      int snum = 0;
      if((cn != null) && (cn.getSuperDesc() != null)) {
        snum++;
      }
      Iterator it_sifs = cn != null? cn.getSuperInterfaces() : null;
      while(it_sifs != null && it_sifs.hasNext()) {
        snum++;
        it_sifs.next();
      }
      output.print(snum);
      ncomma = true;
      if ((cn != null) && (cn.getSuperDesc()!=null)) {
        if(ncomma) {
          output.print(",");
        }
        ClassDescriptor cdsuper=cn.getSuperDesc();
        output.print(cdsuper.getId());
      } 
      it_sifs = cn != null? cn.getSuperInterfaces() : null;
      while(it_sifs != null && it_sifs.hasNext()) {
        if(ncomma) {
          output.print(",");
        }
        output.print(((ClassDescriptor)it_sifs.next()).getId()+state.numClasses()+state.numArrays());
      }
      
      output.println("};");
    }
    
    output.println("int* supertypes[]={");
    boolean needcomma=false;
    for(int i=0; i<state.numClasses(); i++) {
      ClassDescriptor cn=cdarray[i];
      if (needcomma)
	output.println(",");
      needcomma=true;
      if(cn != null) {
        output.print("supertypes" + cn.getSafeSymbol());
      } else {
        output.print(0);
      }
    }
    
    for(int i=0; i<state.numArrays(); i++) {
      if (needcomma)
        output.println(",");
      needcomma = true;
      output.print("supertypes___arraytype___" + (i+state.numClasses()));
    }
    
    for(int i=0; i<state.numInterfaces(); i++) {
      ClassDescriptor cn=ifarray[i];
      if (needcomma)
    output.println(",");
      needcomma=true;
      output.print("supertypes" + cn.getSafeSymbol());
    }
    output.println("};");
  }

  /** Force consistent field ordering between inherited classes. */

  protected void printClassStruct(ClassDescriptor cn, PrintWriter classdefout, PrintWriter globaldefout, PrintWriter globaldefprimout) {

    ClassDescriptor sp=cn.getSuperDesc();
    if (sp!=null)
      printClassStruct(sp, classdefout, /*globaldefout*/ null, null);

    SymbolTable sitbl = cn.getSuperInterfaceTable();
    Iterator it_sifs = sitbl.getDescriptorsIterator();
    while(it_sifs.hasNext()) {
      ClassDescriptor si = (ClassDescriptor)it_sifs.next();
      printClassStruct(si, classdefout, /*globaldefout*/ null, null);
    }

    if (!fieldorder.containsKey(cn)) {
      Vector fields=new Vector();
      fieldorder.put(cn,fields);

      Vector fieldvec=cn.getFieldVec();
      for(int i=0; i<fieldvec.size(); i++) {
	FieldDescriptor fd=(FieldDescriptor)fieldvec.get(i);
	if((sp != null) && sp.getFieldTable().contains(fd.getSymbol())) {
	  // a shadow field
	} else {
	  it_sifs = sitbl.getDescriptorsIterator();
	  boolean hasprinted = false;
	  while(it_sifs.hasNext()) {
	    ClassDescriptor si = (ClassDescriptor)it_sifs.next();
	    if(si.getFieldTable().contains(fd.getSymbol())) {
	      hasprinted = true;
	      break;
	    }
	  }
	  if(hasprinted) {
	    // this field has been defined in the super class
	  } else {
	    fields.add(fd);
	  }
	}
      }
    }
    //Vector fields=(Vector)fieldorder.get(cn);

    Vector fields = cn.getFieldVec();

    for(int i=0; i<fields.size(); i++) {
      FieldDescriptor fd=(FieldDescriptor)fields.get(i);
      String fstring = fd.getSafeSymbol();
      if(printedfieldstbl.containsKey(fstring)) {
        printedfieldstbl.put(fstring, cn);
        continue;
      } else {
        printedfieldstbl.put(fstring, cn);
      }
      if (fd.getType().isClass()
          && fd.getType().getClassDesc().isEnum()) {
	classdefout.println("  int " + fd.getSafeSymbol() + ";");
      } else if (fd.getType().isClass()||fd.getType().isArray()) {
	if (fd.isStatic()) {
	  // TODO add version for normal Java later
	  // static field
	  if(globaldefout != null) {
	    if(fd.isVolatile()) {
	      globaldefout.println("  volatile struct "+fd.getType().getSafeSymbol()+ " * "+fd.getSafeSymbol()+";");
	    } else {
	      globaldefout.println("  struct "+fd.getType().getSafeSymbol()+ " * "+fd.getSafeSymbol()+";");
	    }
	    globaldefscount++;
	  }
	} else if (fd.isVolatile()) {
	  //volatile field
	  classdefout.println("  volatile struct "+fd.getType().getSafeSymbol()+ " * "+fd.getSafeSymbol()+";");
	} else {
	  classdefout.println("  struct "+fd.getType().getSafeSymbol()+" * "+fd.getSafeSymbol()+";");
	}
      } else if (fd.isStatic()) {
	// TODO add version for normal Java later
	// static field
	if(globaldefout != null) {
	  if(fd.isVolatile()) {
	    globaldefprimout.println("  volatile "+fd.getType().getSafeSymbol()+ " "+fd.getSafeSymbol()+";");
	  } else {
	    globaldefprimout.println("  "+fd.getType().getSafeSymbol()+ " "+fd.getSafeSymbol()+";");
	  }
	}
      } else if (fd.isVolatile()) {
	//volatile field
	classdefout.println("  volatile "+fd.getType().getSafeSymbol()+ " "+fd.getSafeSymbol()+";");
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

  protected void generateCallStructs(ClassDescriptor cn, PrintWriter classdefout, PrintWriter output, PrintWriter headersout, PrintWriter globaldefout, PrintWriter globaldefprimout) {
    /* Output class structure */
    classdefout.println("struct "+cn.getSafeSymbol()+" {");
    classdefout.println("  int type;");


    additionalClassObjectFields(classdefout);


    if (state.EVENTMONITOR) {
      classdefout.println("  int objuid;");
    }
    if (state.THREAD) {
      classdefout.println("  pthread_t tid;");
      classdefout.println("  void * lockentry;");
      classdefout.println("  int lockcount;");
    }
    if (state.MGC) {
      classdefout.println("  int mutex;");
      classdefout.println("  volatile int notifycount;");
      classdefout.println("  volatile int objlock;");
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
	classdefout.println("  int * lock;"); // lock entry for this obj
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
    printClassStruct(cn, classdefout, globaldefout, globaldefprimout);
    printedfieldstbl.clear(); // = new Hashtable<String, ClassDescriptor>();
    classdefout.println("};\n");
    generateCallStructsMethods(cn, output, headersout);
  }


  protected void generateCallStructsMethods(ClassDescriptor cn, PrintWriter output, PrintWriter headersout) {
    for(Iterator methodit=cn.getMethods(); methodit.hasNext(); ) {
      MethodDescriptor md=(MethodDescriptor)methodit.next();
      boolean foundmatch = false;
      if(this.state.genAllMethods) {
        foundmatch = true;
      } else {
        Set vec_md = this.state.getMethod2gen().getSet(md.getSymbol());
        for(Iterator matchit=vec_md.iterator(); matchit.hasNext();) {
          MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
          if (md.matches(matchmd)) {
            foundmatch=true;
            break;
          }
        }
      }
      if(foundmatch) {
        generateMethod(cn, md, headersout, output);
      }
    }
  }

  protected void generateMethodParam(ClassDescriptor cn, MethodDescriptor md, PrintWriter output) {
    /* Output parameter structure */
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      if(md.isInvokedByStatic() && !md.isStaticBlock() && !md.getModifiers().isNative()) {
        // generate the staticinit version
        String mdstring = md.getSafeMethodDescriptor() + "staticinit";
        
        ParamsObject objectparams=(ParamsObject) paramstable.get(md);
        output.println("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+mdstring+"_params {");
        output.println("  int size;");
        output.println("  void * next;");
        for(int i=0; i<objectparams.numPointers(); i++) {
          TempDescriptor temp=objectparams.getPointer(i);
          if(temp.getType().isClass() && temp.getType().getClassDesc().isEnum()) {
            output.println("  int " + temp.getSafeSymbol() + ";");
          } else {
            output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
          }
        }
        output.println("};\n");
      }
      
      ParamsObject objectparams=(ParamsObject) paramstable.get(md);
      output.println("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params {");
      output.println("  int size;");
      output.println("  void * next;");
      for(int i=0; i<objectparams.numPointers(); i++) {
	TempDescriptor temp=objectparams.getPointer(i);
	if(temp.getType().isClass() && temp.getType().getClassDesc().isEnum()) {
	  output.println("  int " + temp.getSafeSymbol() + ";");
	} else {
	  output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
	}
      }
      output.println("};\n");
    }
  }

  protected void generateMethod(ClassDescriptor cn, MethodDescriptor md, PrintWriter headersout, PrintWriter output) {
    FlatMethod fm=state.getMethodFlat(md);
    generateTempStructs(fm);

    ParamsObject objectparams=(ParamsObject) paramstable.get(md);
    TempObject objecttemps=(TempObject) tempstable.get(md);
    
    boolean printcomma = false;
    
    generateMethodParam(cn, md, output);
    
    if(md.isInvokedByStatic() && !md.isStaticBlock() && !md.getModifiers().isNative()) {
      // generate the staticinit version
      String mdstring = md.getSafeMethodDescriptor() + "staticinit";

      /* Output temp structure */
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
        output.println("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+mdstring+"_locals {");
        output.println("  int size;");
        output.println("  void * next;");
        for(int i=0; i<objecttemps.numPointers(); i++) {
          TempDescriptor temp=objecttemps.getPointer(i);
          if (!temp.getType().isArray() && temp.getType().isNull())
            output.println("  void * "+temp.getSafeSymbol()+";");
          else
            output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
        }
        output.println("};\n");
      }
      
      headersout.println("#define D"+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+mdstring+" 1");
      /* First the return type */
      if (md.getReturnType()!=null) {
        if(md.getReturnType().isClass() && md.getReturnType().getClassDesc().isEnum()) {
          headersout.println("  int ");
        } else if (md.getReturnType().isClass()||md.getReturnType().isArray())
          headersout.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
        else
          headersout.print(md.getReturnType().getSafeSymbol()+" ");
      } else
        //catch the constructor case
        headersout.print("void ");

      /* Next the method name */
      headersout.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+mdstring+"(");
      printcomma=false;
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
        headersout.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+mdstring+"_params * "+paramsprefix);
        printcomma=true;
      }

      /*  Output parameter list*/
      for(int i=0; i<objectparams.numPrimitives(); i++) {
        TempDescriptor temp=objectparams.getPrimitive(i);
        if (printcomma)
          headersout.print(", ");
        printcomma=true;
        if(temp.getType().isClass() && temp.getType().getClassDesc().isEnum()) {
          headersout.print("int " + temp.getSafeSymbol());
        } else if (temp.getType().isClass()||temp.getType().isArray())
          headersout.print("struct " + temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol());
        else
          headersout.print(temp.getType().getSafeSymbol()+" "+temp.getSafeSymbol());
      }
      headersout.println(");\n");
    }

    /* Output temp structure */
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      output.println("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_locals {");
      output.println("  int size;");
      output.println("  void * next;");
      for(int i=0; i<objecttemps.numPointers(); i++) {
	TempDescriptor temp=objecttemps.getPointer(i);
	if (!temp.getType().isArray() && temp.getType().isNull())
	  output.println("  void * "+temp.getSafeSymbol()+";");
	else
	  output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
      }
      output.println("};\n");
    }

    /********* Output method declaration ***********/
    headersout.println("#define D"+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+" 1");
    /* First the return type */
    if (md.getReturnType()!=null) {
      if(md.getReturnType().isClass() && md.getReturnType().getClassDesc().isEnum()) {
	headersout.println("  int ");
      } else if (md.getReturnType().isClass()||md.getReturnType().isArray())
	headersout.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
      else
	headersout.print(md.getReturnType().getSafeSymbol()+" ");
    } else
      //catch the constructor case
      headersout.print("void ");

    /* Next the method name */
    headersout.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");
    printcomma=false;
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      headersout.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * "+paramsprefix);
      printcomma=true;
    }

    /*  Output parameter list*/
    for(int i=0; i<objectparams.numPrimitives(); i++) {
      TempDescriptor temp=objectparams.getPrimitive(i);
      if (printcomma)
	headersout.print(", ");
      printcomma=true;
      if(temp.getType().isClass() && temp.getType().getClassDesc().isEnum()) {
	headersout.print("int " + temp.getSafeSymbol());
      } else if (temp.getType().isClass()||temp.getType().isArray())
	headersout.print("struct " + temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol());
      else
	headersout.print(temp.getType().getSafeSymbol()+" "+temp.getSafeSymbol());
    }
    headersout.println(");\n");
  }


  /** This function outputs (1) structures that parameters are
   * passed in (when PRECISE GC is enabled) and (2) function
   * prototypes for the tasks */

  protected void generateTaskStructs(PrintWriter output, PrintWriter headersout) {
    /* Cycle through tasks */
    Iterator taskit=state.getTaskSymbolTable().getDescriptorsIterator();

    while(taskit.hasNext()) {
      /* Classify parameters */
      TaskDescriptor task=(TaskDescriptor)taskit.next();
      FlatMethod fm=state.getMethodFlat(task);
      generateTempStructs(fm);

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
	  if (!temp.getType().isArray() && temp.getType().isNull())
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

  protected void generateFlatMethod(FlatMethod fm, PrintWriter output) {
    if (State.PRINTFLAT)
      System.out.println(fm.printMethod());
    MethodDescriptor md=fm.getMethod();
    TaskDescriptor task=fm.getTask();
    ClassDescriptor cn=md!=null ? md.getClassDesc() : null;
    ParamsObject objectparams=(ParamsObject)paramstable.get(md!=null ? md : task);
    
    if((md != null) && md.isInvokedByStatic() && !md.isStaticBlock() && !md.getModifiers().isNative()) {
      // generate a special static init version
      mgcstaticinit = true;
      String mdstring = md.getSafeMethodDescriptor() + "staticinit";
      
      generateHeader(fm, md!=null ? md : task,output);
      TempObject objecttemp=(TempObject) tempstable.get(md!=null ? md : task);
      
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
        output.print("   struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+mdstring+"_locals "+localsprefix+"={");
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
        else if (type.isClass() && type.getClassDesc().isEnum()) {
          output.println("   int " + td.getSafeSymbol() + ";");
        } else if (type.isClass()||type.isArray())
          output.println("   struct "+type.getSafeSymbol()+" * "+td.getSafeSymbol()+";");
        else
          output.println("   "+type.getSafeSymbol()+" "+td.getSafeSymbol()+";");
      }

      additionalCodeAtTopFlatMethodBody(output, fm);

      /* Check to see if we need to do a GC if this is a
       * multi-threaded program...*/

      if (((state.OOOJAVA||state.THREAD)&&GENERATEPRECISEGC)
          || this.state.MULTICOREGC) {
        //Don't bother if we aren't in recursive methods...The loops case will catch it
        if (callgraph.getAllMethods(md).contains(md)) {
          if (this.state.MULTICOREGC) {
            output.println("if(gcflag) gc("+localsprefixaddr+");");
          } else {
            output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
          }
        }
      }
      
      generateCode(fm.getNext(0), fm, null, output);

      output.println("}\n\n");
      
      mgcstaticinit = false;
    }
    
    generateHeader(fm, md!=null ? md : task,output);
    TempObject objecttemp=(TempObject) tempstable.get(md!=null ? md : task);

    if((md != null) && (md.isStaticBlock())) {
      mgcstaticinit = true;
    }

    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      if (md!=null)
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
      else if (type.isClass() && type.getClassDesc().isEnum()) {
	output.println("   int " + td.getSafeSymbol() + ";");
      } else if (type.isClass()||type.isArray())
	output.println("   struct "+type.getSafeSymbol()+" * "+td.getSafeSymbol()+";");
      else
	output.println("   "+type.getSafeSymbol()+" "+td.getSafeSymbol()+";");
    }

    additionalCodeAtTopFlatMethodBody(output, fm);

    /* Check to see if we need to do a GC if this is a
     * multi-threaded program...*/

    if (((state.OOOJAVA||state.THREAD)&&GENERATEPRECISEGC)
        || this.state.MULTICOREGC) {
      //Don't bother if we aren't in recursive methods...The loops case will catch it
      if (callgraph.getAllMethods(md).contains(md)) {
	if (this.state.MULTICOREGC) {
	  output.println("if(gcflag) gc("+localsprefixaddr+");");
	} else {
	  output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
	}
      }
    }

    if(fm.getMethod().isStaticBlock()) {
      // a static block, check if it has been executed
      output.println("  if(global_defsprim_p->" + cn.getSafeSymbol()+"static_block_exe_flag != 0) {");
      output.println("    return;");
      output.println("  }");
      output.println("");
    }

    generateCode(fm.getNext(0), fm, null, output);

    output.println("}\n\n");
    
    mgcstaticinit = false;
  }

  protected void generateCode(FlatNode first,
                              FlatMethod fm,
                              Set<FlatNode> stopset,
                              PrintWriter output) {

    /* Assign labels to FlatNode's if necessary.*/

    Hashtable<FlatNode, Integer> nodetolabel;

    nodetolabel=assignLabels(first, stopset);

    Set<FlatNode> storeset=null;
    HashSet<FlatNode> genset=null;
    HashSet<FlatNode> refset=null;
    Set<FlatNode> unionset=null;

    /* Do the actual code generation */
    FlatNode current_node=null;
    HashSet tovisit=new HashSet();
    HashSet visited=new HashSet();
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
	if (state.THREAD) {
	  output.println("if ((++instructioncount)>failurecount) {instructioncount=0;injectinstructionfailure();}");
	} else
	  output.println("if ((--instructioncount)==0) injectinstructionfailure();");
      }
      if (current_node.numNext()==0||stopset!=null&&stopset.contains(current_node)) {
	output.print("   ");
	generateFlatNode(fm, current_node, output);

	if (state.OOOJAVA && stopset!=null) {
	  assert first.getPrev(0) instanceof FlatSESEEnterNode;
	  assert current_node       instanceof FlatSESEExitNode;
	  FlatSESEEnterNode fsen = (FlatSESEEnterNode) first.getPrev(0);
	  FlatSESEExitNode fsxn = (FlatSESEExitNode)  current_node;
	  assert fsen.getFlatExit().equals(fsxn);
	  assert fsxn.getFlatEnter().equals(fsen);
	}
	if (current_node.kind()!=FKind.FlatReturnNode) {
	  if((fm.getMethod() != null) && (fm.getMethod().isStaticBlock())) {
	    // a static block, check if it has been executed
	    output.println("  global_defsprim_p->" + fm.getMethod().getClassDesc().getSafeSymbol()+"static_block_exe_flag = 1;");
	    output.println("");
	  }
	  output.println("   return;");
	}
	current_node=null;
      } else if(current_node.numNext()==1) {
	FlatNode nextnode;
	if (state.OOOJAVA &&
	    current_node.kind()==FKind.FlatSESEEnterNode) {
	  FlatSESEEnterNode fsen = (FlatSESEEnterNode)current_node;
	  generateFlatNode(fm, current_node, output);
	  nextnode=fsen.getFlatExit().getNext(0);
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
	output.print("   ");
	generateFlatCondBranch(fm, (FlatCondBranch)current_node, "L"+nodetolabel.get(current_node.getNext(1)), output);
	if (!visited.contains(current_node.getNext(1)))
	  tovisit.add(current_node.getNext(1));
	if (visited.contains(current_node.getNext(0))) {
	  output.println("goto L"+nodetolabel.get(current_node.getNext(0))+";");
	  current_node=null;
	} else
	  current_node=current_node.getNext(0);
      } else throw new Error();
    }
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
  protected String generateTemp(FlatMethod fm, TempDescriptor td) {
    MethodDescriptor md=fm.getMethod();
    TaskDescriptor task=fm.getTask();
    TempObject objecttemps=(TempObject) tempstable.get(md!=null ? md : task);

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



  protected void generateFlatNode(FlatMethod fm, FlatNode fn, PrintWriter output) {
    if(state.LINENUM) printSourceLineNumber(fm,fn,output);
    additionalCodePreNode(fm, fn, output);

    switch(fn.kind()) {
    case FKind.FlatAtomicEnterNode:
      generateFlatAtomicEnterNode(fm, (FlatAtomicEnterNode) fn, output);
      break;

    case FKind.FlatAtomicExitNode:
      generateFlatAtomicExitNode(fm, (FlatAtomicExitNode) fn, output);
      break;

    case FKind.FlatInstanceOfNode:
      generateFlatInstanceOfNode(fm, (FlatInstanceOfNode)fn, output);
      break;

    case FKind.FlatSESEEnterNode:
      generateFlatSESEEnterNode(fm, (FlatSESEEnterNode)fn, output);
      break;

    case FKind.FlatSESEExitNode:
      generateFlatSESEExitNode(fm, (FlatSESEExitNode)fn, output);
      break;

    case FKind.FlatWriteDynamicVarNode:
      generateFlatWriteDynamicVarNode(fm, (FlatWriteDynamicVarNode)fn, output);
      break;

    case FKind.FlatGlobalConvNode:
      generateFlatGlobalConvNode(fm, (FlatGlobalConvNode) fn, output);
      break;

    case FKind.FlatTagDeclaration:
      generateFlatTagDeclaration(fm, (FlatTagDeclaration) fn,output);
      break;

    case FKind.FlatCall:
      generateFlatCall(fm, (FlatCall) fn,output);
      break;

    case FKind.FlatFieldNode:
      generateFlatFieldNode(fm, (FlatFieldNode) fn,output);
      break;

    case FKind.FlatElementNode:
      generateFlatElementNode(fm, (FlatElementNode) fn,output);
      break;

    case FKind.FlatSetElementNode:
      generateFlatSetElementNode(fm, (FlatSetElementNode) fn,output);
      break;

    case FKind.FlatSetFieldNode:
      generateFlatSetFieldNode(fm, (FlatSetFieldNode) fn,output);
      break;

    case FKind.FlatNew:
      generateFlatNew(fm, (FlatNew) fn,output);
      break;

    case FKind.FlatOpNode:
      generateFlatOpNode(fm, (FlatOpNode) fn,output);
      break;

    case FKind.FlatCastNode:
      generateFlatCastNode(fm, (FlatCastNode) fn,output);
      break;

    case FKind.FlatLiteralNode:
      generateFlatLiteralNode(fm, (FlatLiteralNode) fn,output);
      break;

    case FKind.FlatReturnNode:
      generateFlatReturnNode(fm, (FlatReturnNode) fn,output);
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
      generateFlatBackEdge(fm, (FlatBackEdge)fn, output);
      break;

    case FKind.FlatCheckNode:
      generateFlatCheckNode(fm, (FlatCheckNode) fn, output);
      break;

    case FKind.FlatFlagActionNode:
      generateFlatFlagActionNode(fm, (FlatFlagActionNode) fn, output);
      break;

    case FKind.FlatPrefetchNode:
      generateFlatPrefetchNode(fm, (FlatPrefetchNode) fn, output);
      break;

    case FKind.FlatOffsetNode:
      generateFlatOffsetNode(fm, (FlatOffsetNode)fn, output);
      break;

    default:
      throw new Error();
    }

    additionalCodePostNode(fm, fn, output);
  }

  public void generateFlatBackEdge(FlatMethod fm, FlatBackEdge fn, PrintWriter output) {
    if (((state.OOOJAVA||state.THREAD)&&GENERATEPRECISEGC)
        || (this.state.MULTICOREGC)) {
      if(this.state.MULTICOREGC) {
	output.println("if (gcflag) gc("+localsprefixaddr+");");
      } else {
	output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
      }
    } else
      output.println("/* nop */");
  }

  public void generateFlatOffsetNode(FlatMethod fm, FlatOffsetNode fofn, PrintWriter output) {
    output.println("/* FlatOffsetNode */");
    FieldDescriptor fd=fofn.getField();
    if(!fd.isStatic()) {
    output.println(generateTemp(fm, fofn.getDst())+ " = (short)(int) (&((struct "+fofn.getClassType().getSafeSymbol() +" *)0)->"+
                   fd.getSafeSymbol()+");");
    }
    output.println("/* offset */");
  }

  public void generateFlatPrefetchNode(FlatMethod fm, FlatPrefetchNode fpn, PrintWriter output) {
  }

  public void generateFlatGlobalConvNode(FlatMethod fm, FlatGlobalConvNode fgcn, PrintWriter output) {
  }

  public void generateFlatInstanceOfNode(FlatMethod fm,  FlatInstanceOfNode fion, PrintWriter output) {
    int type;
    int otype;
    if (fion.getType().isArray()) {
      type=state.getArrayNumber(fion.getType())+state.numClasses();
    } else if (fion.getType().getClassDesc().isInterface()) {
      type=fion.getType().getClassDesc().getId()+state.numClasses()+state.numArrays();
    } else {
      type=fion.getType().getClassDesc().getId();
    }
    if (fion.getSrc().getType().isArray()) {
      otype=state.getArrayNumber(fion.getSrc().getType())+state.numClasses();
    } else if (fion.getSrc().getType().getClassDesc().isInterface()) {
      otype=fion.getSrc().getType().getClassDesc().getId()+state.numClasses()+state.numArrays();
    } else {
      otype=fion.getSrc().getType().getClassDesc().getId();
    }

    if (fion.getType().getSymbol().equals(TypeUtil.ObjectClass))
      output.println(generateTemp(fm, fion.getDst())+"=(" + generateTemp(fm,fion.getSrc()) + "!= NULL);");
    else {
      output.println(generateTemp(fm, fion.getDst())+"=instanceof("+generateTemp(fm,fion.getSrc())+","+type+");");
    }
  }

  public void generateFlatAtomicEnterNode(FlatMethod fm, FlatAtomicEnterNode faen, PrintWriter output) {
  }

  public void generateFlatAtomicExitNode(FlatMethod fm,  FlatAtomicExitNode faen, PrintWriter output) {
  }

  public void generateFlatSESEEnterNode(FlatMethod fm,
                                        FlatSESEEnterNode fsen,
                                        PrintWriter output) {
    // if OOOJAVA flag is off, okay that SESE nodes are in IR graph,
    // just skip over them and code generates exactly the same
  }

  public void generateFlatSESEExitNode(FlatMethod fm,
                                       FlatSESEExitNode fsexn,
                                       PrintWriter output) {
    // if OOOJAVA flag is off, okay that SESE nodes are in IR graph,
    // just skip over them and code generates exactly the same
  }

  public void generateFlatWriteDynamicVarNode(FlatMethod fm,
                                              FlatWriteDynamicVarNode fwdvn,
                                              PrintWriter output) {
  }


  protected void generateFlatCheckNode(FlatMethod fm,  FlatCheckNode fcn, PrintWriter output) {
    if (state.CONSCHECK) {
      String specname=fcn.getSpec();
      String varname="repairstate___";
      output.println("{");
      output.println("struct "+specname+"_state * "+varname+"=allocate"+specname+"_state();");

      TempDescriptor[] temps=fcn.getTemps();
      String[] vars=fcn.getVars();
      for(int i=0; i<temps.length; i++) {
	output.println(varname+"->"+vars[i]+"=(unsigned int)"+generateTemp(fm, temps[i])+";");
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

  protected void generateFlatCall(FlatMethod fm, FlatCall fc, PrintWriter output) {
    MethodDescriptor md=fc.getMethod();
    ParamsObject objectparams=(ParamsObject)paramstable.get(md);
    ClassDescriptor cn=md.getClassDesc();
    String mdstring = md.getSafeMethodDescriptor();
    if(mgcstaticinit && !md.isStaticBlock() && !md.getModifiers().isNative()) {
      mdstring += "staticinit";
    }

    // if the called method is a static block or a static method or a constructor
    // need to check if it can be invoked inside some static block
    if((md.isStatic() || md.isStaticBlock() || md.isConstructor()) &&
       ((fm.getMethod() != null) && ((fm.getMethod().isStaticBlock()) || (fm.getMethod().isInvokedByStatic())))) {
      if(!md.isInvokedByStatic()) {
	System.err.println("Error: a method that is invoked inside a static block is not tagged!");
      }
      // is a static block or is invoked in some static block
      ClassDescriptor cd = fm.getMethod().getClassDesc();
      if(cd == cn) {
	// the same class, do nothing
      } else if(mgcstaticinit) {
        // generate static init check code if it has not done static init in main()
	if((cn.getNumStaticFields() != 0) || (cn.getNumStaticBlocks() != 0)) {
	  // need to check if the class' static fields have been initialized and/or
	  // its static blocks have been executed
	  output.println("if(global_defsprim_p->" + cn.getSafeSymbol()+"static_block_exe_flag == 0) {");
	  if(cn.getNumStaticBlocks() != 0) {
	    MethodDescriptor t_md = (MethodDescriptor)cn.getMethodTable().get("staticblocks");
        if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
          output.print("       struct "+cn.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"_params __parameterlist__={");
          output.println("0, NULL};");
          output.println("     "+cn.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"(& __parameterlist__);");
        } else {
          output.println("  "+cn.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"();");
        }
	  } else {
	    output.println("  global_defsprim_p->" + cn.getSafeSymbol()+"static_block_exe_flag = 1;");
	  }
	  output.println("}");
	}
      }
    }
    if((md.getSymbol().equals("MonitorEnter") || md.getSymbol().equals("MonitorExit")) && fc.getThis().getSymbol().equals("classobj")) {
      output.println("{");
      // call MonitorEnter/MonitorExit on a class obj
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
        output.print("       struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params __parameterlist__={");
        output.println("1," + localsprefixaddr + ", global_defs_p->"+ fc.getThis().getType().getClassDesc().getSafeSymbol() +"classobj};");
        output.println("     "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(& __parameterlist__);");
      } else {
      output.println("       " + cn.getSafeSymbol()+md.getSafeSymbol()+"_"
		     + md.getSafeMethodDescriptor() + "((struct ___Object___*)(global_defs_p->"
		     + fc.getThis().getType().getClassDesc().getSafeSymbol() +"classobj));");
      }
      output.println("}");
      return;
    }
    
    output.println("{");
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      output.print("       struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+mdstring+"_params __parameterlist__={");
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
      output.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+mdstring);
    } else {
      //yes
      output.print("((");
      if (md.getReturnType().isClass() && md.getReturnType().getClassDesc().isEnum()) {
	output.print("int ");
      } else if (md.getReturnType().isClass()||md.getReturnType().isArray())
	output.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
      else
	output.print(md.getReturnType().getSafeSymbol()+" ");
      output.print("(*)(");

      boolean printcomma=false;
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	output.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+mdstring+"_params * ");
	printcomma=true;
      }

      for(int i=0; i<objectparams.numPrimitives(); i++) {
	TempDescriptor temp=objectparams.getPrimitive(i);
	if (printcomma)
	  output.print(", ");
	printcomma=true;
	if (temp.getType().isClass() && temp.getType().getClassDesc().isEnum()) {
	  output.print("int ");
	} else if (temp.getType().isClass()||temp.getType().isArray())
	  output.print("struct " + temp.getType().getSafeSymbol()+" * ");
	else
	  output.print(temp.getType().getSafeSymbol());
      }


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
	if(ptd.isClass() && ptd.getClassDesc().isEnum()) {
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
	if (ptd.isClass() && ptd.getClassDesc().isEnum()) {
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

  protected boolean singleCall(ClassDescriptor thiscd, MethodDescriptor md) {
    if(thiscd.isInterface()) {
      // for interfaces, always need virtual dispatch
      return false;
    } else {
    Set subclasses=typeutil.getSubClasses(thiscd);
    if (subclasses==null)
      return true;
    for(Iterator classit=subclasses.iterator(); classit.hasNext(); ) {
      ClassDescriptor cd=(ClassDescriptor)classit.next();
      Set possiblematches=cd.getMethodTable().getSetFromSameScope(md.getSymbol());
      for(Iterator matchit=possiblematches.iterator(); matchit.hasNext(); ) {
	MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
	if (md.matches(matchmd))
	  return false;
      }
    }
    }
    return true;
  }

  protected void generateFlatFieldNode(FlatMethod fm, FlatFieldNode ffn, PrintWriter output) {
    
    if(ffn.getField().isStatic()) {
      // static field
      if((fm.getMethod().isStaticBlock()) || (fm.getMethod().isInvokedByStatic())) {
	// is a static block or is invoked in some static block
	ClassDescriptor cd = fm.getMethod().getClassDesc();
	ClassDescriptor cn = ffn.getSrc().getType().getClassDesc();
	if(cd == cn) {
	  // the same class, do nothing
	} else if(mgcstaticinit) {
      // generate the static init check code if has not done the static init in main()
	  if((cn.getNumStaticFields() != 0) || (cn.getNumStaticBlocks() != 0)) {
	    // need to check if the class' static fields have been initialized and/or
	    // its static blocks have been executed
	    output.println("if(global_defsprim_p->" + cn.getSafeSymbol()+"static_block_exe_flag == 0) {");
	    if(cn.getNumStaticBlocks() != 0) {
	      MethodDescriptor t_md = (MethodDescriptor)cn.getMethodTable().get("staticblocks");
          if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
            output.print("       struct "+cn.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"_params __parameterlist__={");
            output.println("0, NULL};");
            output.println("     "+cn.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"(& __parameterlist__);");
          } else {
            output.println("  "+cn.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"();");
          }
	    } else {
	      output.println("  global_defsprim_p->" + cn.getSafeSymbol()+"static_block_exe_flag = 1;");
	    }
	    output.println("}");
	  }
	}
      }
      // redirect to the global_defs_p structure
      if((ffn.getField().isStatic()) || (ffn.getSrc().getType().isClassNameRef())) {
	// reference to the static field with Class name
	if (ffn.getField().getType().isPtr())
	  output.println(generateTemp(fm, ffn.getDst())+"=global_defs_p->"+ffn.getField().getSafeSymbol()+";");
	else
	  output.println(generateTemp(fm, ffn.getDst())+"=global_defsprim_p->"+ffn.getField().getSafeSymbol()+";");
      } else {
	output.println(generateTemp(fm, ffn.getDst())+"=*"+ generateTemp(fm,ffn.getSrc())+"->"+ ffn.getField().getSafeSymbol()+";");
      }
    } else if (ffn.getField().isEnum()) {
      // an Enum value, directly replace the field access as int
      output.println(generateTemp(fm, ffn.getDst()) + "=" + ffn.getField().enumValue() + ";");
    } else {
      output.println(generateTemp(fm, ffn.getDst())+"="+ generateTemp(fm,ffn.getSrc())+"->"+ ffn.getField().getSafeSymbol()+";");
    }
  }


  protected void generateFlatSetFieldNode(FlatMethod fm, FlatSetFieldNode fsfn, PrintWriter output) {
    if (fsfn.getField().getSymbol().equals("length")&&fsfn.getDst().getType().isArray())
      throw new Error("Can't set array length");
    if (state.FASTCHECK) {
      String dst=generateTemp(fm, fsfn.getDst());
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

    if(fsfn.getField().isStatic()) {
      // static field
      if((fm.getMethod().isStaticBlock()) || (fm.getMethod().isInvokedByStatic())) {
	// is a static block or is invoked in some static block
	ClassDescriptor cd = fm.getMethod().getClassDesc();
	ClassDescriptor cn = fsfn.getDst().getType().getClassDesc();
	if(cd == cn) {
	  // the same class, do nothing
	} else if(mgcstaticinit){
      // generate static init check code if has not done the static init in main()
	  if((cn.getNumStaticFields() != 0) || (cn.getNumStaticBlocks() != 0)) {
	    // need to check if the class' static fields have been initialized and/or
	    // its static blocks have been executed
	    output.println("if(global_defsprim_p->" + cn.getSafeSymbol()+"static_block_exe_flag == 0) {");
	    if(cn.getNumStaticBlocks() != 0) {
	      MethodDescriptor t_md = (MethodDescriptor)cn.getMethodTable().get("staticblocks");
          if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
            output.print("       struct "+cn.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"_params __parameterlist__={");
            output.println("0, NULL};");
            output.println("     "+cn.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"(& __parameterlist__);");
          } else {
            output.println("  "+cn.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"();");
          }
	    } else {
	      output.println("  global_defsprim_p->" + cn.getSafeSymbol()+"static_block_exe_flag = 1;");
	    }
	    output.println("}");
	  }
	}
      }
      // redirect to the global_defs_p structure
      if((fsfn.getDst().getType().isClassNameRef()) || (fsfn.getField().isStatic())) {
	// reference to the static field with Class name
	if (fsfn.getField().getType().isPtr())
	  output.println("global_defs_p->" +
			 fsfn.getField().getSafeSymbol()+"="+ generateTemp(fm,fsfn.getSrc())+";");
	else
	  output.println("global_defsprim_p->" +
			 fsfn.getField().getSafeSymbol()+"="+ generateTemp(fm,fsfn.getSrc())+";");
      } else {
	output.println("*"+generateTemp(fm, fsfn.getDst())+"->"+
		       fsfn.getField().getSafeSymbol()+"="+ generateTemp(fm,fsfn.getSrc())+";");
      }
    } else {
      output.println(generateTemp(fm, fsfn.getDst())+"->"+
		     fsfn.getField().getSafeSymbol()+"="+ generateTemp(fm,fsfn.getSrc())+";");
    }
  }


  protected void generateFlatElementNode(FlatMethod fm, FlatElementNode fen, PrintWriter output) {
    TypeDescriptor elementtype=fen.getSrc().getType().dereference();
    String type="";

    if (elementtype.isClass() && elementtype.getClassDesc().isEnum()) {
      type="int ";
    } else if (elementtype.isArray()||elementtype.isClass())
      type="void *";
    else
      type=elementtype.getSafeSymbol()+" ";

    if (this.state.ARRAYBOUNDARYCHECK && fen.needsBoundsCheck()) {
      output.println("if (unlikely(((unsigned int)"+generateTemp(fm, fen.getIndex())+") >= "+generateTemp(fm,fen.getSrc()) + "->___length___))");
      output.println("failedboundschk();");
    }
    output.println(generateTemp(fm, fen.getDst())+"=(("+ type+"*)(((char *) &("+ generateTemp(fm,fen.getSrc())+"->___length___))+sizeof(int)))["+generateTemp(fm, fen.getIndex())+"];");
  }

  protected void generateFlatSetElementNode(FlatMethod fm, FlatSetElementNode fsen, PrintWriter output) {
    //TODO: need dynamic check to make sure this assignment is actually legal
    //Because Object[] could actually be something more specific...ie. Integer[]

    TypeDescriptor elementtype=fsen.getDst().getType().dereference();
    String type="";

    if (elementtype.isClass() && elementtype.getClassDesc().isEnum()) {
      type="int ";
    } else if (elementtype.isArray()||elementtype.isClass() || (elementtype.isNull()))
      type="void *";
    else
      type=elementtype.getSafeSymbol()+" ";

    if (this.state.ARRAYBOUNDARYCHECK && fsen.needsBoundsCheck()) {
      output.println("if (unlikely(((unsigned int)"+generateTemp(fm, fsen.getIndex())+") >= "+generateTemp(fm,fsen.getDst()) + "->___length___))");
      output.println("failedboundschk();");
    }
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


  protected void generateFlatNew(FlatMethod fm, FlatNew fn, PrintWriter output) {
    if (fn.getType().isArray()) {
      int arrayid=state.getArrayNumber(fn.getType())+state.numClasses();
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	output.println(generateTemp(fm,fn.getDst())+"=allocate_newarray("+localsprefixaddr+", "+arrayid+", "+generateTemp(fm, fn.getSize())+");");
      } else {
	output.println(generateTemp(fm,fn.getDst())+"=allocate_newarray("+arrayid+", "+generateTemp(fm, fn.getSize())+");");
      }
    } else {
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	output.println(generateTemp(fm,fn.getDst())+"=allocate_new("+localsprefixaddr+", "+fn.getType().getClassDesc().getId()+");");
      } else {
	output.println(generateTemp(fm,fn.getDst())+"=allocate_new("+fn.getType().getClassDesc().getId()+");");
      }
    }
    if (state.FASTCHECK) {
      String dst=generateTemp(fm,fn.getDst());
      output.println(dst+"->___localcopy___=(struct ___Object___*)1;");
      output.println(dst+"->"+nextobjstr+"="+fcrevert+";");
      output.println(fcrevert+"=(struct ___Object___ *)"+dst+";");
    }
  }

  protected void generateFlatTagDeclaration(FlatMethod fm, FlatTagDeclaration fn, PrintWriter output) {
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      output.println(generateTemp(fm,fn.getDst())+"=allocate_tag("+localsprefixaddr+", "+state.getTagId(fn.getType())+");");
    } else {
      output.println(generateTemp(fm,fn.getDst())+"=allocate_tag("+state.getTagId(fn.getType())+");");
    }
  }

  protected void generateFlatOpNode(FlatMethod fm, FlatOpNode fon, PrintWriter output) {
    if (fon.getRight()!=null) {
      if (fon.getOp().getOp()==Operation.URIGHTSHIFT) {
	if (fon.getLeft().getType().isLong())
	  output.println(generateTemp(fm, fon.getDest())+" = ((unsigned long long)"+generateTemp(fm, fon.getLeft())+")>>"+generateTemp(fm,fon.getRight())+";");
	else
	  output.println(generateTemp(fm, fon.getDest())+" = ((unsigned int)"+generateTemp(fm, fon.getLeft())+")>>"+generateTemp(fm,fon.getRight())+";");

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

  protected void generateFlatCastNode(FlatMethod fm, FlatCastNode fcn, PrintWriter output) {
    /* TODO: Do type check here */
    if (fcn.getType().isArray()) {
      output.println(generateTemp(fm,fcn.getDst())+"=(struct ArrayObject *)"+generateTemp(fm,fcn.getSrc())+";");
    } else if (fcn.getType().isClass() && fcn.getType().getClassDesc().isEnum()) {
      output.println(generateTemp(fm,fcn.getDst())+"=(int)"+generateTemp(fm,fcn.getSrc())+";");
    } else if (fcn.getType().isClass())
      output.println(generateTemp(fm,fcn.getDst())+"=(struct "+fcn.getType().getSafeSymbol()+" *)"+generateTemp(fm,fcn.getSrc())+";");
    else
      output.println(generateTemp(fm,fcn.getDst())+"=("+fcn.getType().getSafeSymbol()+")"+generateTemp(fm,fcn.getSrc())+";");
  }

  protected void generateFlatLiteralNode(FlatMethod fm, FlatLiteralNode fln, PrintWriter output) {
    if (fln.getValue()==null)
      output.println(generateTemp(fm, fln.getDst())+"=0;");
    else if (fln.getType().getSymbol().equals(TypeUtil.StringClass)) {
      if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	output.println(generateTemp(fm, fln.getDst())+"=NewString("+localsprefixaddr+", \""+FlatLiteralNode.escapeString((String)fln.getValue())+"\","+((String)fln.getValue()).length()+");");
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

  protected void generateFlatReturnNode(FlatMethod fm, FlatReturnNode frn, PrintWriter output) {
    if((fm.getMethod() != null) && (fm.getMethod().isStaticBlock())) {
      // a static block, check if it has been executed
      output.println("  global_defsprim_p->" + fm.getMethod().getClassDesc().getSafeSymbol()+"static_block_exe_flag = 1;");
      output.println("");
    }
    
    if (frn.getReturnTemp()!=null) {
      if (frn.getReturnTemp().getType().isPtr())
	output.println("return (struct "+fm.getMethod().getReturnType().getSafeSymbol()+"*)"+generateTemp(fm, frn.getReturnTemp())+";");
      else
	output.println("return "+generateTemp(fm, frn.getReturnTemp())+";");
    } else {
      output.println("return;");
    }
  }

  protected void generateFlatCondBranch(FlatMethod fm, FlatCondBranch fcb, String label, PrintWriter output) {
    output.println("if (!"+generateTemp(fm, fcb.getTest())+") goto "+label+";");
  }

  /** This method generates header information for the method or
   * task referenced by the Descriptor des. */
  protected void generateHeader(FlatMethod fm, Descriptor des, PrintWriter output) {
    generateHeader(fm, des, output, false);
  }

  protected void generateHeader(FlatMethod fm, Descriptor des, PrintWriter output, boolean addSESErecord) {
    /* Print header */
    ParamsObject objectparams=(ParamsObject)paramstable.get(des);
    MethodDescriptor md=null;
    TaskDescriptor task=null;
    if (des instanceof MethodDescriptor)
      md=(MethodDescriptor) des;
    else
      task=(TaskDescriptor) des;
    String mdstring = md != null ? md.getSafeMethodDescriptor() : null;

    ClassDescriptor cn=md!=null ? md.getClassDesc() : null;

    if (md!=null&&md.getReturnType()!=null) {
      if (md.getReturnType().isClass() && md.getReturnType().getClassDesc().isEnum()) {
	output.print("int ");
      } else if (md.getReturnType().isClass()||md.getReturnType().isArray())
	output.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
      else
	output.print(md.getReturnType().getSafeSymbol()+" ");
    } else
      //catch the constructor case
      output.print("void ");
    if (md!=null) {
      if(mgcstaticinit && !md.isStaticBlock() && !md.getModifiers().isNative()) {
        mdstring += "staticinit";
      }
      output.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+mdstring+"(");
    } else
      output.print(task.getSafeSymbol()+"(");

    boolean printcomma=false;
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      if (md!=null) {
	output.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+mdstring+"_params * "+paramsprefix);
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
	if(temp.getType().isClass() && temp.getType().getClassDesc().isEnum()) {
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
	if(temp.getType().isClass() && temp.getType().getClassDesc().isEnum()) {
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

  public void generateFlatFlagActionNode(FlatMethod fm, FlatFlagActionNode ffan, PrintWriter output) {
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
	    output.println("tagclear("+localsprefixaddr+", (struct ___Object___ *)"+generateTemp(fm, temp)+", "+generateTemp(fm,tagtmp)+");");
	  else
	    output.println("tagclear((struct ___Object___ *)"+generateTemp(fm, temp)+", "+generateTemp(fm,tagtmp)+");");
	}
      }

      tagtmps=tagsettable.get(temp);
      if (tagtmps!=null) {
	Iterator tagit=tagtmps.iterator();
	while(tagit.hasNext()) {
	  TempDescriptor tagtmp=(TempDescriptor)tagit.next();
	  if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC))
	    output.println("tagset("+localsprefixaddr+", (struct ___Object___ *)"+generateTemp(fm, temp)+", "+generateTemp(fm,tagtmp)+");");
	  else
	    output.println("tagset((struct ___Object___ *)"+generateTemp(fm, temp)+", "+generateTemp(fm,tagtmp)+");");
	}
      }

      int ormask=0;
      int andmask=0xFFFFFFF;

      if (flagortable.containsKey(temp))
	ormask=((Integer)flagortable.get(temp)).intValue();
      if (flagandtable.containsKey(temp))
	andmask=((Integer)flagandtable.get(temp)).intValue();
      generateFlagOrAnd(ffan, fm, temp, output, ormask, andmask);
      generateObjectDistribute(ffan, fm, temp, output);
    }
  }

  protected void generateFlagOrAnd(FlatFlagActionNode ffan, FlatMethod fm, TempDescriptor temp,
                                   PrintWriter output, int ormask, int andmask) {
    if (ffan.getTaskType()==FlatFlagActionNode.NEWOBJECT) {
      output.println("flagorandinit("+generateTemp(fm, temp)+", 0x"+Integer.toHexString(ormask)+", 0x"+Integer.toHexString(andmask)+");");
    } else {
      output.println("flagorand("+generateTemp(fm, temp)+", 0x"+Integer.toHexString(ormask)+", 0x"+Integer.toHexString(andmask)+");");
    }
  }

  protected void generateObjectDistribute(FlatFlagActionNode ffan, FlatMethod fm, TempDescriptor temp, PrintWriter output) {
    output.println("enqueueObject("+generateTemp(fm, temp)+");");
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

    for(Iterator vard_it = c_vard.iterator(); vard_it.hasNext(); ) {
      VarDescriptor vard = (VarDescriptor)vard_it.next();
      TypeDescriptor typed = vard.getType();

      //generate for flags
      HashSet fen_hashset = predicate.flags.get(vard.getSymbol());
      output.println("int predicateflags_"+predicateindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"[]={");
      int numberterms=0;
      if (fen_hashset!=null) {
	for (Iterator fen_it = fen_hashset.iterator(); fen_it.hasNext(); ) {
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
	for(Iterator otd_it = c_otd.iterator(); otd_it.hasNext(); ) {
	  OptionalTaskDescriptor otd = (OptionalTaskDescriptor)otd_it.next();

	  //generate the int arrays for the predicate
	  Predicate predicate = otd.predicate;
	  int predicateindex = generateOptionalPredicate(predicate, otd, cdtemp, output);
	  TreeSet<Integer> fsset=new TreeSet<Integer>();
	  //iterate through possible FSes corresponding to
	  //the state when entering

	  for(Iterator fses = otd.enterflagstates.iterator(); fses.hasNext(); ) {
	    FlagState fs = (FlagState)fses.next();
	    int flagid=0;
	    for(Iterator flags = fs.getFlags(); flags.hasNext(); ) {
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
	  for(Iterator<Integer> it=fsset.iterator(); it.hasNext(); ) {
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
	for(Iterator otd_it = c_otd.iterator(); otd_it.hasNext(); ) {
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
      for(Iterator fsit=fsts.iterator(); fsit.hasNext(); ) {
	FlagState fs = (FlagState)fsit.next();
	fscounter++;

	//get the set of OptionalTaskDescriptors corresponding
	HashSet<OptionalTaskDescriptor> availabletasks = (HashSet<OptionalTaskDescriptor>)hashtbtemp.get(fs);
	//iterate through the OptionalTaskDescriptors and
	//store the pointers to the optionals struct (see on
	//top) into an array

	output.println("struct optionaltaskdescriptor * optionaltaskdescriptorarray_FS"+fscounter+"_"+cdtemp.getSafeSymbol()+"[] = {");
	for(Iterator<OptionalTaskDescriptor> mos = ordertd(availabletasks).iterator(); mos.hasNext(); ) {
	  OptionalTaskDescriptor mm = mos.next();
	  if(!mos.hasNext())
	    output.println("&optionaltaskdescriptor_"+mm.getuid()+"_"+cdtemp.getSafeSymbol());
	  else
	    output.println("&optionaltaskdescriptor_"+mm.getuid()+"_"+cdtemp.getSafeSymbol()+",");
	}

	output.println("};\n");

	//process flag information (what the flag after failure is) so we know what optionaltaskdescriptors to choose.

	int flagid=0;
	for(Iterator flags = fs.getFlags(); flags.hasNext(); ) {
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
	for(Iterator<TaskIndex> itti=tiset.iterator(); itti.hasNext(); ) {
	  TaskIndex ti=itti.next();
	  if (ti.isRuntime())
	    continue;

	  Set<OptionalTaskDescriptor> otdset=sa.getOptions(fs, ti);

	  output.print("struct optionaltaskdescriptor * optionaltaskfailure_FS"+fscounter+"_"+ti.getTask().getSafeSymbol()+"_"+ti.getIndex()+"_array[] = {");
	  boolean needcomma=false;
	  for(Iterator<OptionalTaskDescriptor> otdit=ordertd(otdset).iterator(); otdit.hasNext(); ) {
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
	for(Iterator<TaskIndex> itti=tiset.iterator(); itti.hasNext(); ) {
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
    for(Iterator<OptionalTaskDescriptor>otdit=otdset.iterator(); otdit.hasNext(); ) {
      OptionalTaskDescriptor otd=otdit.next();
      TaskIndex ti=new TaskIndex(otd.td, otd.getIndex());
      r.put(ti, otd);
    }

    LinkedList<OptionalTaskDescriptor> l=new LinkedList<OptionalTaskDescriptor>();
    for(Iterator it=r.keySet().iterator(); it.hasNext(); ) {
      Set s=r.get(it.next());
      for(Iterator it2=s.iterator(); it2.hasNext(); ) {
	OptionalTaskDescriptor otd=(OptionalTaskDescriptor)it2.next();
	l.add(otd);
      }
    }

    return l;
  }

  // override these methods in a subclass of BuildCode
  // to generate code for additional systems
  protected void printExtraArrayFields(PrintWriter outclassdefs) {
  }
  protected void outputTransCode(PrintWriter output) {
  }
  protected void buildCodeSetup() {
  }
  protected void generateSizeArrayExtensions(PrintWriter outclassdefs) {
  }
  protected void additionalIncludesMethodsHeader(PrintWriter outmethodheader) {
  }
  protected void preCodeGenInitialization() {
  }
  protected void postCodeGenCleanUp() {
  }
  protected void additionalCodeGen(PrintWriter outmethodheader,
                                   PrintWriter outstructs,
                                   PrintWriter outmethod) {
  }
  protected void additionalCodeAtTopOfMain(PrintWriter outmethod) {
  }
  protected void additionalCodeAtBottomOfMain(PrintWriter outmethod) {
  }
  protected void additionalIncludesMethodsImplementation(PrintWriter outmethod) {
  }
  protected void additionalIncludesStructsHeader(PrintWriter outstructs) {
  }
  protected void additionalClassObjectFields(PrintWriter outclassdefs) {
  }
  protected void additionalCodeAtTopMethodsImplementation(PrintWriter outmethod) {
  }
  protected void additionalCodeAtTopFlatMethodBody(PrintWriter output, FlatMethod fm) {
  }
  protected void additionalCodePreNode(FlatMethod fm, FlatNode fn, PrintWriter output) {
  }
  protected void additionalCodePostNode(FlatMethod fm, FlatNode fn, PrintWriter output) {
  }
  
  private void printSourceLineNumber(FlatMethod fm, FlatNode fn, PrintWriter output) {
    // we do not print out line number if no one explicitly set the number
    if(fn.getNumLine()!=-1){
      
      int lineNum=fn.getNumLine();

      // do not generate the line number if it is same as the previous one
      boolean needtoprint;
      if(fn.prev.size()==0){
        needtoprint=true;
      }else{
        needtoprint=false;
      }

      for(int i=0;i<fn.prev.size();i++){
        int prevLineNum=((FlatNode)fn.prev.get(i)).getNumLine();
        if(prevLineNum!=lineNum){
          needtoprint=true;
          break;
        }
      }
      if(needtoprint){
        output.println("// "+fm.getMethod().getClassDesc().getSourceFileName()+":"+fn.getNumLine());
      }
    }
  }
  
}






