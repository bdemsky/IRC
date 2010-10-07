package IR.Flat;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;

import Analysis.Locality.LocalityBinding;
import Analysis.Scheduling.Schedule;
import Analysis.TaskStateAnalysis.FEdge;
import Analysis.TaskStateAnalysis.FlagState;
import Analysis.TaskStateAnalysis.SafetyAnalysis;
import Analysis.OwnershipAnalysis.AllocationSite;
import Analysis.OwnershipAnalysis.OwnershipAnalysis;
import Analysis.OwnershipAnalysis.HeapRegionNode;
import Analysis.Prefetch.*;
import IR.ClassDescriptor;
import IR.Descriptor;
import IR.FlagDescriptor;
import IR.MethodDescriptor;
import IR.State;
import IR.TagVarDescriptor;
import IR.TaskDescriptor;
import IR.TypeDescriptor;
import IR.TypeUtil;
import IR.VarDescriptor;
import IR.Tree.DNFFlag;
import IR.Tree.DNFFlagAtom;
import IR.Tree.FlagExpressionNode;
import IR.Tree.TagExpressionList;

public class BuildCodeMultiCore extends BuildCode {
  private Vector<Schedule> scheduling;
  int coreNum;
  int tcoreNum;
  int gcoreNum;
  Schedule currentSchedule;
  Hashtable[] fsate2qnames;
  String objqarrayprefix= "objqueuearray4class";
  String objqueueprefix = "objqueue4parameter_";
  String paramqarrayprefix = "paramqueuearray4task";
  String coreqarrayprefix = "paramqueuearrays_core";
  String taskprefix = "task_";
  String taskarrayprefix = "taskarray_core";
  String otqueueprefix = "___otqueue";
  int startupcorenum;    // record the core containing startup task, suppose only one core can hava startup object

  private OwnershipAnalysis m_oa;
  private Vector<Vector<Integer>> m_aliasSets;
  Hashtable<Integer, Vector<FlatNew>> m_aliasFNTbl4Para;
  Hashtable<FlatNew, Vector<FlatNew>> m_aliasFNTbl;
  Hashtable<FlatNew, Vector<Integer>> m_aliaslocksTbl4FN;

  public BuildCodeMultiCore(State st, 
	                    Hashtable temptovar, 
	                    TypeUtil typeutil, 
	                    SafetyAnalysis sa, 
	                    Vector<Schedule> scheduling, 
	                    int coreNum, 
                        int gcoreNum,
	                    PrefetchAnalysis pa) {
    super(st, temptovar, typeutil, sa, pa);
    this.scheduling = scheduling;
    this.coreNum = coreNum; // # of the active cores
    this.tcoreNum = coreNum;  // # of the cores setup by users
    this.gcoreNum = gcoreNum; // # of the cores for gc if any
    this.currentSchedule = null;
    this.fsate2qnames = null;
    this.startupcorenum = 0;

    // sometimes there are extra cores then needed in scheduling
    // TODO
    // currently, it is guaranteed that in scheduling, the corenum
    // is started from 0 and continuous.
    // MAY need modification here in the future when take hardware
    // information into account.
    if(this.scheduling.size() < this.coreNum) {
      this.coreNum = this.scheduling.size();
    }

    this.m_oa = null;
    this.m_aliasSets = null;
    this.m_aliasFNTbl4Para = null;
    this.m_aliasFNTbl = null;
    this.m_aliaslocksTbl4FN = null;
  }

  public void setOwnershipAnalysis(OwnershipAnalysis m_oa) {
    this.m_oa = m_oa;
  }

  public void buildCode() {
    /* Create output streams to write to */
    PrintWriter outclassdefs=null;
    PrintWriter outstructs=null;
    PrintWriter outmethodheader=null;
    PrintWriter outmethod=null;
    PrintWriter outvirtual=null;
    PrintWriter outtask=null;
    PrintWriter outtaskdefs=null;
    //PrintWriter outoptionalarrays=null;
    //PrintWriter optionalheaders=null;

    try {
      outstructs=new PrintWriter(new FileOutputStream(PREFIX+"structdefs.h"), true);
      outmethodheader=new PrintWriter(new FileOutputStream(PREFIX+"methodheaders.h"), true);
      outclassdefs=new PrintWriter(new FileOutputStream(PREFIX+"classdefs.h"), true);
      outvirtual=new PrintWriter(new FileOutputStream(PREFIX+"virtualtable.h"), true);
      outmethod=new PrintWriter(new FileOutputStream(PREFIX+"methods.c"), true);
      if (state.TASK) {
	outtask=new PrintWriter(new FileOutputStream(PREFIX+"task.h"), true);
	outtaskdefs=new PrintWriter(new FileOutputStream(PREFIX+"taskdefs.c"), true);
	/* optional
	   if (state.OPTIONAL){
	    outoptionalarrays=new PrintWriter(new FileOutputStream(PREFIX+"optionalarrays.c"), true);
	    optionalheaders=new PrintWriter(new FileOutputStream(PREFIX+"optionalstruct.h"), true);
	   } */
      }
      /*if (state.structfile!=null) {
          outrepairstructs=new PrintWriter(new FileOutputStream(PREFIX+state.structfile+".struct"), true);
         }*/
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }

    /* Build the virtual dispatch tables */
    super.buildVirtualTables(outvirtual);

    /* Output includes */
    outmethodheader.println("#ifndef METHODHEADERS_H");
    outmethodheader.println("#define METHODHEADERS_H");
    outmethodheader.println("#include \"structdefs.h\"");
    /*if (state.DSM)
        outmethodheader.println("#include \"dstm.h\"");*/

    /* Output Structures */
    super.outputStructs(outstructs);

    // Output the C class declarations
    // These could mutually reference each other
    super.outputClassDeclarations(outclassdefs);

    // Output function prototypes and structures for parameters
    Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
    int numclasses = this.state.numClasses();
    while(it.hasNext()) {
      ClassDescriptor cn=(ClassDescriptor)it.next();
      super.generateCallStructs(cn, outclassdefs, outstructs, outmethodheader);
    }
    outclassdefs.close();

    if (state.TASK) {
      /* Map flags to integers */
      /* The runtime keeps track of flags using these integers */
      it=state.getClassSymbolTable().getDescriptorsIterator();
      while(it.hasNext()) {
	ClassDescriptor cn=(ClassDescriptor)it.next();
	super.mapFlags(cn);
      }
      /* Generate Tasks */
      generateTaskStructs(outstructs, outmethodheader);

      /* Outputs generic task structures if this is a task
         program */
      outputTaskTypes(outtask);
    }

    /* Build the actual methods */
    super.outputMethods(outmethod);

    if (state.TASK) {
      Iterator[] taskits = new Iterator[this.coreNum];
      for(int i = 0; i < taskits.length; ++i) {
	taskits[i] = null;
      }
      int[] numtasks = new int[this.coreNum];
      int[][] numqueues = new int[this.coreNum][numclasses];
      /* Output code for tasks */
      for(int i = 0; i < this.scheduling.size(); ++i) {
	this.currentSchedule = this.scheduling.elementAt(i);
	outputTaskCode(outtaskdefs, outmethod, outtask, taskits, numtasks, numqueues);
      }

      // Output task descriptors
      boolean comma = false;
      outtaskdefs.println("struct parameterwrapper ** objectqueues[][NUMCLASSES] = {");
      boolean needcomma = false;
      for(int i = 0; i < numqueues.length ; ++i) {
	if(needcomma) {
	  outtaskdefs.println(",");
	} else {
	  needcomma = true;
	}
	outtaskdefs.println("/* object queue array for core " + i + "*/");
	outtaskdefs.print("{");
	comma = false;
	for(int j = 0; j < numclasses; ++j) {
	  if(comma) {
	    outtaskdefs.println(",");
	  } else {
	    comma = true;
	  }
	  outtaskdefs.print(this.objqarrayprefix + j + "_core" + i);
	}
	outtaskdefs.print("}");
      }
      outtaskdefs.println("};");
      needcomma = false;
      outtaskdefs.println("int numqueues[][NUMCLASSES] = {");
      for(int i = 0; i < numqueues.length; ++i) {
	if(needcomma) {
	  outtaskdefs.println(",");
	} else {
	  needcomma = true;
	}
	int[] tmparray = numqueues[i];
	comma = false;
	outtaskdefs.print("{");
	for(int j = 0; j < tmparray.length; ++j) {
	  if(comma) {
	    outtaskdefs.print(",");
	  } else {
	    comma = true;
	  }
	  outtaskdefs.print(tmparray[j]);
	}
	outtaskdefs.print("}");
      }
      outtaskdefs.println("};");

      /* parameter queue arrays for all the tasks*/
      outtaskdefs.println("struct parameterwrapper *** paramqueues[] = {");
      needcomma = false;
      for(int i = 0; i < this.coreNum ; ++i) {
	if(needcomma) {
	  outtaskdefs.println(",");
	} else {
	  needcomma = true;
	}
	outtaskdefs.println("/* parameter queue array for core " + i + "*/");
	outtaskdefs.print(this.coreqarrayprefix + i);
      }
      outtaskdefs.println("};");

      for(int i = 0; i < taskits.length; ++i) {
	outtaskdefs.println("struct taskdescriptor * " + this.taskarrayprefix + i + "[]={");
	Iterator taskit = taskits[i];
	if(taskit != null) {
	  boolean first=true;
	  while(taskit.hasNext()) {
	    TaskDescriptor td=(TaskDescriptor)taskit.next();
	    if (first)
	      first=false;
	    else
	      outtaskdefs.println(",");
	    outtaskdefs.print("&" + this.taskprefix +td.getCoreSafeSymbol(i));
	  }
	}
	outtaskdefs.println();
	outtaskdefs.println("};");
      }
      outtaskdefs.println("struct taskdescriptor ** taskarray[]= {");
      comma = false;
      for(int i = 0; i < taskits.length; ++i) {
	if (comma)
	  outtaskdefs.println(",");
	else
	  comma = true;
	outtaskdefs.print(this.taskarrayprefix + i);
      }
      outtaskdefs.println("};");

      outtaskdefs.print("int numtasks[]= {");
      comma = false;
      for(int i = 0; i < taskits.length; ++i) {
	if (comma)
	  outtaskdefs.print(",");
	else
	  comma=true;
	outtaskdefs.print(numtasks[i]);
      }
      outtaskdefs.println("};");
      outtaskdefs.println("int corenum=0;");

      outtaskdefs.close();
      outtask.println("#endif");
      outtask.close();
      /* Record maximum number of task parameters */
      outstructs.println("#define MAXTASKPARAMS "+maxtaskparams);
      /* Record maximum number of all types, i.e. length of classsize[] */
      outstructs.println("#define NUMTYPES "+(state.numClasses() + state.numArrays()));
      /* Record number of total cores */
      outstructs.println("#define NUMCORES "+this.tcoreNum);
      /* Record number of active cores */
      outstructs.println("#define NUMCORESACTIVE "+this.coreNum); // this.coreNum 
                                      // can be reset by the scheduling analysis
      /* Record number of garbage collection cores */
      outtask.println("#ifdef MULTICORE_GC");
      outstructs.println("#define NUMCORES4GC "+this.gcoreNum);
      outtask.println("#endif");
      /* Record number of core containing startup task */
      outstructs.println("#define STARTUPCORE "+this.startupcorenum);
    }     //else if (state.main!=null) {
          /* Generate main method */
          // outputMainMethod(outmethod);
          //}

    /* Generate information for task with optional parameters */
    /*if (state.TASK&&state.OPTIONAL){
        generateOptionalArrays(outoptionalarrays, optionalheaders, state.getAnalysisResult(), state.getOptionalTaskDescriptors());
        outoptionalarrays.close();
       } */

    /* Output structure definitions for repair tool */
    /*if (state.structfile!=null) {
        buildRepairStructs(outrepairstructs);
        outrepairstructs.close();
       }*/

    /* Close files */
    outmethodheader.println("#endif");
    outmethodheader.close();
    outmethod.close();
    outstructs.println("#endif");
    outstructs.close();
  }

  /** This function outputs (1) structures that parameters are
   * passed in (when PRECISE GC is enabled) and (2) function
   * prototypes for the tasks */

  private void generateTaskStructs(PrintWriter output, 
	                           PrintWriter headersout) {
    /* Cycle through tasks */
    for(int i = 0; i < this.scheduling.size(); ++i) {
      Schedule tmpschedule = this.scheduling.elementAt(i);
      int num = tmpschedule.getCoreNum();
      Iterator<TaskDescriptor> taskit = tmpschedule.getTasks().iterator();

      while(taskit.hasNext()) {
	/* Classify parameters */
	TaskDescriptor task=taskit.next();
	FlatMethod fm=state.getMethodFlat(task);
	super.generateTempStructs(fm, null);

	ParamsObject objectparams=(ParamsObject) paramstable.get(task);
	TempObject objecttemps=(TempObject) tempstable.get(task);

	/* Output parameter structure */
	if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	  output.println("struct "+task.getCoreSafeSymbol(num)+"_params {");
	  output.println("  int size;");
	  output.println("  void * next;");
	  for(int j=0; j<objectparams.numPointers(); j++) {
	    TempDescriptor temp=objectparams.getPointer(j);
	    output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
	  }

	  output.println("};\n");
	  if ((objectparams.numPointers()+fm.numTags())>maxtaskparams) {
	    maxtaskparams=objectparams.numPointers()+fm.numTags();
	  }
	}

	/* Output temp structure */
	if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	  output.println("struct "+task.getCoreSafeSymbol(num)+"_locals {");
	  output.println("  int size;");
	  output.println("  void * next;");
	  for(int j=0; j<objecttemps.numPointers(); j++) {
	    TempDescriptor temp=objecttemps.getPointer(j);
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
	headersout.print("void " + task.getCoreSafeSymbol(num)+"(");

	if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	  headersout.print("struct "+task.getCoreSafeSymbol(num)+"_params * "+paramsprefix);
	} else
	  headersout.print("void * parameterarray[]");
	headersout.println(");\n");
      }
    }

  }

  /* This method outputs code for each task. */

  private void outputTaskCode(PrintWriter outtaskdefs, 
	                          PrintWriter outmethod, 
	                          PrintWriter outtask, 
	                          Iterator[] taskits, 
	                          int[] numtasks,
                              int[][] numqueues) {
    /* Compile task based program */
    outtaskdefs.println("#include \"task.h\"");
    outtaskdefs.println("#include \"methodheaders.h\"");

    /* Output object transfer queues into method.c*/
    generateObjectTransQueues(outmethod);

    //Vector[] qnames = new Vector[2];
    int numclasses = numqueues[0].length;
    Vector qnames[]= new Vector[numclasses];
    for(int i = 0; i < qnames.length; ++i) {
      qnames[i] = null;
    }
    Iterator<TaskDescriptor> taskit=this.currentSchedule.getTasks().iterator();
    while(taskit.hasNext()) {
      TaskDescriptor td=taskit.next();
      FlatMethod fm=state.getMethodFlat(td);
      generateTaskMethod(fm, null, outmethod);
      generateTaskDescriptor(outtaskdefs, outtask, fm, td, qnames);
    }

    // generate queuearray for this core
    int num = this.currentSchedule.getCoreNum();
    boolean comma = false;
    for(int i = 0; i < qnames.length; ++i) {
      outtaskdefs.println("/* object queue array for class " + i + " on core " + num + "*/");
      outtaskdefs.println("struct parameterwrapper * " + this.objqarrayprefix + i + "_core" + num + "[] = {");
      comma = false;
      Vector tmpvector = qnames[i];
      if(tmpvector != null) {
	for(int j = 0; j < tmpvector.size(); ++j) {
	  if(comma) {
	    outtaskdefs.println(",");
	  } else {
	    comma = true;
	  }
	  outtaskdefs.print("&" + tmpvector.elementAt(j));
	}
	numqueues[num][i] = tmpvector.size();
      } else {
	numqueues[num][i] = 0;
      }
      outtaskdefs.println("};");
    }

    /* All the queues for tasks residing on this core*/
    comma = false;
    outtaskdefs.println("/* object queue array for tasks on core " + num + "*/");
    outtaskdefs.println("struct parameterwrapper ** " + this.coreqarrayprefix + num + "[] = {");
    taskit=this.currentSchedule.getTasks().iterator();
    while(taskit.hasNext()) {
      if (comma) {
	outtaskdefs.println(",");
      } else {
	comma = true;
      }
      TaskDescriptor td=taskit.next();
      outtaskdefs.print(this.paramqarrayprefix + td.getCoreSafeSymbol(num));
    }
    outtaskdefs.println("};");

    // record the iterator of tasks on this core
    taskit=this.currentSchedule.getTasks().iterator();
    taskits[num] = taskit;
    numtasks[num] = this.currentSchedule.getTasks().size();
  }

  /** Prints out definitions for generic task structures */
  private void outputTaskTypes(PrintWriter outtask) {
    outtask.println("#ifndef _TASK_H");
    outtask.println("#define _TASK_H");
    outtask.println("#include \"ObjectHash.h\"");
    outtask.println("#include \"structdefs.h\"");
    outtask.println("#include \"Queue.h\"");
    outtask.println("#include <string.h>");
	outtask.println("#include \"runtime_arch.h\"");
    //outtask.println("#ifdef RAW");
    //outtask.println("#include <raw.h>");
    //outtask.println("#endif");
    outtask.println();
    outtask.println("struct tagobjectiterator {");
    outtask.println("  int istag; /* 0 if object iterator, 1 if tag iterator */");
    outtask.println("  struct ObjectIterator it; /* Object iterator */");
    outtask.println("  struct ObjectHash * objectset;");
    outtask.println("#ifdef OPTIONAL");
    outtask.println("  int failedstate;");
    outtask.println("#endif");
    outtask.println("  int slot;");
    outtask.println("  int tagobjindex; /* Index for tag or object depending on use */");
    outtask.println("  /*if tag we have an object binding */");
    outtask.println("  int tagid;");
    outtask.println("  int tagobjectslot;");
    outtask.println("  /*if object, we may have one or more tag bindings */");
    outtask.println("  int numtags;");
    outtask.println("  int tagbindings[MAXTASKPARAMS-1]; /* list slots */");
    outtask.println("};");
    outtask.println();
    outtask.println("struct parameterwrapper {");
    outtask.println("  //int type;");
    outtask.println("  struct ObjectHash * objectset;");
    outtask.println("  int numberofterms;");
    outtask.println("  int * intarray;");
    outtask.println("  int numbertags;");
    outtask.println("  int * tagarray;");
    outtask.println("  struct taskdescriptor * task;");
    outtask.println("  int slot;");
    outtask.println("  struct tagobjectiterator iterators[MAXTASKPARAMS-1];");
    outtask.println("};");
    outtask.println();
    outtask.println("extern struct parameterwrapper ** objectqueues[][NUMCLASSES];");
    outtask.println("extern int numqueues[][NUMCLASSES];");
    outtask.println();
    outtask.println("struct parameterdescriptor {");
    outtask.println("  int type;");
    outtask.println("  int numberterms;");
    outtask.println("  int *intarray;");
    outtask.println("  struct parameterwrapper * queue;");
    outtask.println("  int numbertags;");
    outtask.println("  int *tagarray;");
    outtask.println("};");
    outtask.println();
    outtask.println("struct taskdescriptor {");
    outtask.println("  void * taskptr;");
    outtask.println("  int numParameters;");
    outtask.println("  int numTotal;");
    outtask.println("  struct parameterdescriptor **descriptorarray;");
    outtask.println("  char * name;");
    outtask.println("};");
    outtask.println();
    outtask.println("extern struct taskdescriptor ** taskarray[];");
    outtask.println("extern int numtasks[];");
    outtask.println("extern int corenum;");     // define corenum to identify different core
    outtask.println("extern struct parameterwrapper *** paramqueues[];");
    outtask.println();
  }

  private void generateObjectTransQueues(PrintWriter output) {
    if(this.fsate2qnames == null) {
      this.fsate2qnames = new Hashtable[this.coreNum];
      for(int i = 0; i < this.fsate2qnames.length; ++i) {
	this.fsate2qnames[i] = null;
      }
    }
    int num = this.currentSchedule.getCoreNum();
    assert(this.fsate2qnames[num] == null);
    Hashtable<FlagState, String> flag2qname = new Hashtable<FlagState, String>();
    this.fsate2qnames[num] = flag2qname;
    Hashtable<FlagState, Queue<Integer>> targetCoreTbl = this.currentSchedule.getTargetCoreTable();
    if(targetCoreTbl != null) {
      Object[] keys = targetCoreTbl.keySet().toArray();
      output.println();
      output.println("/* Object transfer queues for core" + num + ".*/");
      for(int i = 0; i < keys.length; ++i) {
	FlagState tmpfstate = (FlagState)keys[i];
	Object[] targetcores = targetCoreTbl.get(tmpfstate).toArray();
	String queuename = this.otqueueprefix + tmpfstate.getClassDescriptor().getCoreSafeSymbol(num) + tmpfstate.getuid() + "___";
	String queueins = queuename + "ins";
	flag2qname.put(tmpfstate, queuename);
	output.println("struct " + queuename + " {");
	output.println("  int * cores;");
	output.println("  int index;");
	output.println("  int length;");
	output.println("};");
	output.print("int " + queuename + "cores[] = {");
	for(int j = 0; j < targetcores.length; ++j) {
	  if(j > 0) {
	    output.print(", ");
	  }
	  output.print(((Integer)targetcores[j]).intValue());
	}
	output.println("};");
	output.println("struct " + queuename + " " + queueins + "= {");
	output.println(/*".cores = " + */ queuename + "cores,");
	output.println(/*".index = " + */ "0,");
	output.println(/*".length = " +*/ targetcores.length + "};");
      }
    }
    output.println();
  }

  private void generateTaskMethod(FlatMethod fm, 
	                              LocalityBinding lb, 
	                              PrintWriter output) {
    /*if (State.PRINTFLAT)
        System.out.println(fm.printMethod());*/
    TaskDescriptor task=fm.getTask();
    assert(task != null);
    int num = this.currentSchedule.getCoreNum();

    //ParamsObject objectparams=(ParamsObject)paramstable.get(lb!=null?lb:task);
    generateTaskHeader(fm, lb, task,output);

    TempObject objecttemp=(TempObject) tempstable.get(lb!=null ? lb : task);
    /*if (state.DSM&&lb.getHasAtomic()) {
        output.println("transrecord_t * trans;");
       }*/

    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      output.print("   struct "+task.getCoreSafeSymbol(num)+"_locals "+localsprefix+"={");

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
      else if (type.isClass()||type.isArray())
	output.println("   struct "+type.getSafeSymbol()+" * "+td.getSafeSymbol()+";");
      else
	output.println("   "+type.getSafeSymbol()+" "+td.getSafeSymbol()+";");
    }

    for(int i = 0; i < fm.numParameters(); ++i) {
      TempDescriptor temp = fm.getParameter(i);
      output.println("   int "+generateTempFlagName(fm, temp, lb)+" = "+super.generateTemp(fm, temp, lb)+
                     "->flag;");
    }

    /* Assign labels to FlatNode's if necessary.*/

    Hashtable<FlatNode, Integer> nodetolabel=super.assignLabels(fm);

    /* Check to see if we need to do a GC if this is a
     * multi-threaded program...*/
    if(this.state.MULTICOREGC) {
      output.println("if(gcflag) gc("+localsprefixaddr+");");
    }

    /*if ((state.THREAD||state.DSM)&&GENERATEPRECISEGC) {
        if (state.DSM&&lb.isAtomic())
            output.println("checkcollect2(&"+localsprefix+",trans);");
        else
            output.println("checkcollect(&"+localsprefix+");");
       }*/

    /* Create queues to store objects need to be transferred to other cores and their destination*/
    //output.println("   struct Queue * totransobjqueue = createQueue();");
    output.println("   clearQueue(totransobjqueue);");
    output.println("   struct transObjInfo * tmpObjInfo = NULL;");

    this.m_aliasSets = null;
    this.m_aliasFNTbl4Para = null;
    this.m_aliasFNTbl = null;
    this.m_aliaslocksTbl4FN = null;
    outputAliasLockCode(fm, lb, output);

    /* generate print information for RAW version */
    output.println("#ifdef MULTICORE");
	if(this.state.RAW) {
		output.println("{");
		output.println("int tmpsum = 0;");
		output.println("char * taskname = \"" + task.getSymbol() + "\";");
		output.println("int tmplen = " + task.getSymbol().length() + ";");
		output.println("int tmpindex = 1;");
		output.println("for(;tmpindex < tmplen; tmpindex++) {");
		output.println("   tmpsum = tmpsum * 10 + *(taskname + tmpindex) - '0';");
		output.println("}");
	}
    output.println("#ifdef RAWPATH");
	if(this.state.RAW) {
		output.println("BAMBOO_DEBUGPRINT(0xAAAA);");
		output.println("BAMBOO_DEBUGPRINT_REG(tmpsum);"); 
	} else {
		//output.println("BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();");
		output.println("printf(\"(%x,%x) Process %x(%d): task %s\\n\", udn_tile_coord_x(), udn_tile_coord_y(), corenum, corenum, \"" + task.getSymbol() + "\");");
		//output.println("BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();");
	}
	//output.println("BAMBOO_DEBUGPRINT(BAMBOO_GET_EXE_TIME());");
    output.println("#endif");
    output.println("#ifdef DEBUG");
	if(this.state.RAW) {
		output.println("BAMBOO_DEBUGPRINT(0xAAAA);");
		output.println("BAMBOO_DEBUGPRINT_REG(tmpsum);");
	} else {
		//output.println("BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();");
		output.println("printf(\"(%x,%x) Process %x(%d): task %s\\n\", udn_tile_coord_x(), udn_tile_coord_y(), corenum, corenum, \"" + task.getSymbol() + "\");");
		//output.println("BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();");
	}
    output.println("#endif");
	if(this.state.RAW) {
		output.println("}");
	}
	output.println("#endif");

    for(int i = 0; i < fm.numParameters(); ++i) {
      TempDescriptor temp = fm.getParameter(i);
      output.println("   ++" + super.generateTemp(fm, temp, lb)+"->version;");
    }

    /* Do the actual code generation */
    FlatNode current_node=null;
    HashSet tovisit=new HashSet();
    HashSet visited=new HashSet();
    tovisit.add(fm.getNext(0));
    while(current_node!=null||!tovisit.isEmpty()) {
      if (current_node==null) {
	current_node=(FlatNode)tovisit.iterator().next();
	tovisit.remove(current_node);
      }
      visited.add(current_node);
      if (nodetolabel.containsKey(current_node))
	output.println("L"+nodetolabel.get(current_node)+":");
      /*if (state.INSTRUCTIONFAILURE) {
          if (state.THREAD||state.DSM) {
              output.println("if ((++instructioncount)>failurecount) {instructioncount=0;injectinstructionfailure();}");
          }
          else
              output.println("if ((--instructioncount)==0) injectinstructionfailure();");
         }*/
      if (current_node.numNext()==0) {
	output.print("   ");
	super.generateFlatNode(fm, lb, current_node, output);
	if (current_node.kind()!=FKind.FlatReturnNode) {
	  //output.println("   flushAll();");
	  output.println("#ifdef CACHEFLUSH");
	  output.println("BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();");
	  output.println("#ifdef DEBUG");
	  output.println("BAMBOO_DEBUGPRINT(0xec00);");
	  output.println("#endif");
	  output.println("BAMBOO_CACHE_FLUSH_ALL();");
	  output.println("#ifdef DEBUG");
	  output.println("BAMBOO_DEBUGPRINT(0xecff);");
	  output.println("#endif");
	  output.println("BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();");
	  output.println("#endif");
	  outputTransCode(output);
	  output.println("   return;");
	}
	current_node=null;
      } else if(current_node.numNext()==1) {
	output.print("   ");
	super.generateFlatNode(fm, lb, current_node, output);
	FlatNode nextnode=current_node.getNext(0);
	if (visited.contains(nextnode)) {
	  output.println("goto L"+nodetolabel.get(nextnode)+";");
	  current_node=null;
	} else
	  current_node=nextnode;
      } else if (current_node.numNext()==2) {
	/* Branch */
	output.print("   ");
	super.generateFlatCondBranch(fm, lb, (FlatCondBranch)current_node, "L"+nodetolabel.get(current_node.getNext(1)), output);
	if (!visited.contains(current_node.getNext(1)))
	  tovisit.add(current_node.getNext(1));
	if (visited.contains(current_node.getNext(0))) {
	  output.println("goto L"+nodetolabel.get(current_node.getNext(0))+";");
	  current_node=null;
	} else
	  current_node=current_node.getNext(0);
      } else throw new Error();
    }

    output.println("}\n\n");
  }

  /** This method outputs TaskDescriptor information */
  private void generateTaskDescriptor(PrintWriter output, 
	                              PrintWriter outtask, 
	                              FlatMethod fm, 
	                              TaskDescriptor task, 
	                              Vector[] qnames) {
    int num = this.currentSchedule.getCoreNum();

    output.println("/* TaskDescriptor information for task " + task.getSymbol() + " on core " + num + "*/");

    for (int i=0; i<task.numParameters(); i++) {
      VarDescriptor param_var=task.getParameter(i);
      TypeDescriptor param_type=task.getParamType(i);
      FlagExpressionNode param_flag=task.getFlag(param_var);
      TagExpressionList param_tag=task.getTag(param_var);

      int dnfterms;
      if (param_flag==null) {
	output.println("int parameterdnf_"+i+"_"+task.getCoreSafeSymbol(num)+"[]={");
	output.println("0x0, 0x0 };");
	dnfterms=1;
      } else {
	DNFFlag dflag=param_flag.getDNF();
	dnfterms=dflag.size();

	Hashtable flags=(Hashtable)flagorder.get(param_type.getClassDesc());
	output.println("int parameterdnf_"+i+"_"+task.getCoreSafeSymbol(num)+"[]={");
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

      output.println("int parametertag_"+i+"_"+task.getCoreSafeSymbol(num)+"[]={");
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

      // generate object queue for this parameter
      String qname = this.objqueueprefix+i+"_"+task.getCoreSafeSymbol(num);
      if(param_type.getClassDesc().getSymbol().equals("StartupObject")) {
	this.startupcorenum = num;
      }
      if(qnames[param_type.getClassDesc().getId()] == null) {
	qnames[param_type.getClassDesc().getId()] = new Vector();
      }
      qnames[param_type.getClassDesc().getId()].addElement(qname);
      outtask.println("extern struct parameterwrapper " + qname + ";");
      output.println("struct parameterwrapper " + qname + "={");
      output.println(".objectset = 0,");      // objectset
      output.println("/* number of DNF terms */ .numberofterms = "+dnfterms+",");     // numberofterms
      output.println(".intarray = parameterdnf_"+i+"_"+task.getCoreSafeSymbol(num)+",");    // intarray
      // numbertags
      if (param_tag!=null)
	output.println("/* number of tags */ .numbertags = "+param_tag.numTags()+",");
      else
	output.println("/* number of tags */ .numbertags = 0,");
      output.println(".tagarray = parametertag_"+i+"_"+task.getCoreSafeSymbol(num)+",");    // tagarray
      output.println(".task = 0,");      // task
      output.println(".slot = " + i + ",");    // slot
      // iterators
      output.println("};");

      output.println("struct parameterdescriptor parameter_"+i+"_"+task.getCoreSafeSymbol(num)+"={");
      output.println("/* type */"+param_type.getClassDesc().getId()+",");
      output.println("/* number of DNF terms */"+dnfterms+",");
      output.println("parameterdnf_"+i+"_"+task.getCoreSafeSymbol(num)+",");    // intarray
      output.println("&" + qname + ",");     // queue
      //BUG, added next line to fix and else statement...test
      //with any task program
      if (param_tag!=null)
	output.println("/* number of tags */"+param_tag.numTags()+",");
      else
	output.println("/* number of tags */ 0,");
      output.println("parametertag_"+i+"_"+task.getCoreSafeSymbol(num));     // tagarray
      output.println("};");
    }

    /* parameter queues for this task*/
    output.println("struct parameterwrapper * " + this.paramqarrayprefix + task.getCoreSafeSymbol(num)+"[] = {");
    for (int i=0; i<task.numParameters(); i++) {
      if (i!=0)
	output.println(",");
      output.print("&" + this.objqueueprefix + i + "_" + task.getCoreSafeSymbol(num));
    }
    output.println("};");

    output.println("struct parameterdescriptor * parameterdescriptors_"+task.getCoreSafeSymbol(num)+"[] = {");
    for (int i=0; i<task.numParameters(); i++) {
      if (i!=0)
	output.println(",");
      output.print("&parameter_"+i+"_"+task.getCoreSafeSymbol(num));
    }
    output.println("};");

    output.println("struct taskdescriptor " + this.taskprefix + task.getCoreSafeSymbol(num) + "={");
    output.println("&"+task.getCoreSafeSymbol(num)+",");
    output.println("/* number of parameters */" +task.numParameters() + ",");
    int numtotal=task.numParameters()+fm.numTags();
    output.println("/* number total parameters */" +numtotal + ",");
    output.println("parameterdescriptors_"+task.getCoreSafeSymbol(num)+",");
    output.println("\""+task.getSymbol()+"\"");
    output.println("};");

    output.println();
  }

  /** This method generates header information for the task
   *  referenced by the Descriptor des. */

  private void generateTaskHeader(FlatMethod fm, 
	                          LocalityBinding lb, 
	                          Descriptor des, 
	                          PrintWriter output) {
    /* Print header */
    ParamsObject objectparams=(ParamsObject)paramstable.get(lb!=null ? lb : des);
    TaskDescriptor task=(TaskDescriptor) des;

    int num = this.currentSchedule.getCoreNum();
    //catch the constructor case
    output.print("void ");
    output.print(task.getCoreSafeSymbol(num)+"(");

    boolean printcomma=false;
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      output.print("struct "+task.getCoreSafeSymbol(num)+"_params * "+paramsprefix);
      printcomma=true;
    }

    /*if (state.DSM&&lb.isAtomic()) {
        if (printcomma)
            output.print(", ");
        output.print("transrecord_t * trans");
        printcomma=true;
       }*/

    if (!GENERATEPRECISEGC && !this.state.MULTICOREGC) {
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
	output.println("struct ___TagDescriptor___ * "+temp.getSafeSymbol()+i+"___=parameterarray["+offset+"];");     // add i to fix bugs of duplicate definition of tags
      }

      if ((objectparams.numPrimitives()+fm.numTags())>maxtaskparams)
	maxtaskparams=objectparams.numPrimitives()+fm.numTags();
    } else output.println(") {");
  }

  protected void generateFlagOrAnd(FlatFlagActionNode ffan, 
	                           FlatMethod fm, 
	                           LocalityBinding lb, 
	                           TempDescriptor temp,
                                   PrintWriter output, 
                                   int ormask, 
                                   int andmask) {
    if (ffan.getTaskType()==FlatFlagActionNode.NEWOBJECT) {
      output.println("flagorandinit("+super.generateTemp(fm, temp, lb)+", 0x"+Integer.toHexString(ormask)+", 0x"+Integer.toHexString(andmask)+");");
    } else {
      int num = this.currentSchedule.getCoreNum();
      ClassDescriptor cd = temp.getType().getClassDesc();
      Vector<FlagState> initfstates = ffan.getInitFStates(cd);
      for(int i = 0; i < initfstates.size(); ++i) {
	FlagState tmpFState = initfstates.elementAt(i);
	output.println("{");
	QueueInfo qinfo = outputqueues(tmpFState, num, output, false);
	output.println("flagorand("+super.generateTemp(fm, temp, lb)+", 0x"+Integer.toHexString(ormask)+
	               ", 0x"+Integer.toHexString(andmask)+", " + qinfo.qname +
	               ", " + qinfo.length + ");");
	output.println("}");
      }
      if(ffan.getTaskType()==FlatFlagActionNode.TASKEXIT) {
	  // generate codes for profiling, recording which task exit it is
	  output.println("#ifdef PROFILE");
	  output.println("setTaskExitIndex(" + ffan.getTaskExitIndex() + ");");
	  output.println("#endif");
      }
    }
  }

  protected void generateObjectDistribute(FlatFlagActionNode ffan, 
	                                      FlatMethod fm, 
	                                      LocalityBinding lb, 
	                                      TempDescriptor temp,
                                          PrintWriter output) {
    ClassDescriptor cd = temp.getType().getClassDesc();
    Vector<FlagState> initfstates = null;
    Vector[] targetFStates = null;
    if (ffan.getTaskType()==FlatFlagActionNode.NEWOBJECT) {
      targetFStates = new Vector[1];
      targetFStates[0] = ffan.getTargetFStates4NewObj(cd);
    } else {
      initfstates = ffan.getInitFStates(cd);
      targetFStates = new Vector[initfstates.size()];
      for(int i = 0; i < initfstates.size(); ++i) {
	FlagState fs = initfstates.elementAt(i);
	targetFStates[i] = ffan.getTargetFStates(fs);

	if(!fs.isSetmask()) {
	  Hashtable flags=(Hashtable)flagorder.get(cd);
	  int andmask=0;
	  int checkmask=0;
	  Iterator it_flags = fs.getFlags();
	  while(it_flags.hasNext()) {
	    FlagDescriptor fd = (FlagDescriptor)it_flags.next();
	    int flagid=1<<((Integer)flags.get(fd)).intValue();
	    andmask|=flagid;
	    checkmask|=flagid;
	  }
	  fs.setAndmask(andmask);
	  fs.setCheckmask(checkmask);
	  fs.setSetmask(true);
	}
      }
    }
    boolean isolate = true;     // check if this flagstate can associate to some task with multiple params which can
                                // reside on multiple cores
    if((this.currentSchedule == null) && (fm.getMethod().getClassDesc().getSymbol().equals("ServerSocket"))) {
      // ServerSocket object will always reside on current core
      for(int j = 0; j < targetFStates.length; ++j) {
	if(initfstates != null) {
	  FlagState fs = initfstates.elementAt(j);
	  output.println("if(" + generateTempFlagName(fm, temp, lb) + "&(0x" + Integer.toHexString(fs.getAndmask())
	                 + ")==(0x" + Integer.toHexString(fs.getCheckmask()) + ")) {");
	}
	Vector<FlagState> tmpfstates = (Vector<FlagState>)targetFStates[j];
	for(int i = 0; i < tmpfstates.size(); ++i) {
	  FlagState tmpFState = tmpfstates.elementAt(i);
	  // TODO
	  // may have bugs here
	  output.println("/* reside on this core*");
	  output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+", NULL, 0);");
	}
	if(initfstates != null) {
	  output.println("}");
	}
      }
      return;
    }

    int num = this.currentSchedule.getCoreNum();
    Hashtable<FlagState, Queue<Integer>> targetCoreTbl = this.currentSchedule.getTargetCoreTable();
    for(int j = 0; j < targetFStates.length; ++j) {
      FlagState fs = null;
      if(initfstates != null) {
	fs = initfstates.elementAt(j);
	output.println("if((" + generateTempFlagName(fm, temp, lb) + "&(0x" + Integer.toHexString(fs.getAndmask())
	               + "))==(0x" + Integer.toHexString(fs.getCheckmask()) + ")) {");
      }
      Vector<FlagState> tmpfstates = (Vector<FlagState>)targetFStates[j];
      for(int i = 0; i < tmpfstates.size(); ++i) {
	FlagState tmpFState = tmpfstates.elementAt(i);

	if(this.currentSchedule.getAllyCoreTable() == null) {
	  isolate = true;
	} else {
	  isolate = (this.currentSchedule.getAllyCoreTable().get(tmpFState) == null) ||
	            (this.currentSchedule.getAllyCoreTable().get(tmpFState).size() == 0);
	}

	Vector<Integer> sendto = new Vector<Integer>();
	Queue<Integer> queue = null;
	if(targetCoreTbl != null) {
	  queue = targetCoreTbl.get(tmpFState);
	}
	if((queue != null) &&
	   ((queue.size() != 1) ||
	    ((queue.size() == 1) && (queue.element().intValue() != num)))) {
	  // this object may be transferred to other cores
	  String queuename = (String) this.fsate2qnames[num].get(tmpFState);
	  String queueins = queuename + "ins";

	  Object[] cores = queue.toArray();
	  String index = "0";
	  Integer targetcore = (Integer)cores[0];
	  if(queue.size() > 1) {
	    index = queueins + ".index";
	  }
	  if(queue.size() > 1) {
	    output.println("switch(" + queueins + ".index % " + queueins + ".length) {");
	    for(int k = 0; k < cores.length; ++k) {
	      output.println("case " + k + ":");
	      targetcore = (Integer)cores[k];
	      if(targetcore.intValue() == num) {
		output.println("/* reside on this core*/");
		if(isolate) {
		  output.println("{");
		  QueueInfo qinfo = outputqueues(tmpFState, num, output, true);
		  output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+", " + qinfo.qname +
		                 ", " + qinfo.length + ");");
		  output.println("}");
		} /*else {
		  // TODO
		  // really needed?
		  output.println("/* possibly needed by multi-parameter tasks on this core*//*");
		  output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+", NULL, 0);");
		}*/  // deleted 09/07/06, multi-param tasks are pinned to one core now
	      } else {
		/*if(!isolate) {
		  // TODO
		  // Is it possible to decide the actual queues?
		  output.println("/* possibly needed by multi-parameter tasks on this core*//*");
		  output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+", NULL, 0);");
		}*/ // deleted 09/07/06, multi-param tasks are pinned to one core now
		output.println("/* transfer to core " + targetcore.toString() + "*/");
		output.println("{");
		// enqueue this object and its destinations for later process
		// all the possible queues
		QueueInfo qinfo = null;
		TranObjInfo tmpinfo = new TranObjInfo();
		tmpinfo.name = super.generateTemp(fm, temp, lb);
		tmpinfo.targetcore = targetcore;
		FlagState targetFS = this.currentSchedule.getTargetFState(tmpFState);
		if(targetFS != null) {
		  tmpinfo.fs = targetFS;
		} else {
		  tmpinfo.fs = tmpFState;
		}
		  qinfo = outputtransqueues(tmpinfo.fs, targetcore, output);
		  output.println("tmpObjInfo = RUNMALLOC(sizeof(struct transObjInfo));");
		  output.println("tmpObjInfo->objptr = (void *)" + tmpinfo.name + ";");
		  output.println("tmpObjInfo->targetcore = "+targetcore.toString()+";");
		  output.println("tmpObjInfo->queues = " + qinfo.qname + ";");
		  output.println("tmpObjInfo->length = " + qinfo.length + ";");
		  output.println("addNewItem(totransobjqueue, (void*)tmpObjInfo);");
		output.println("}");
	      }
	      output.println("break;");
	    }
	    output.println("}");
	  } else {
	    /*if(!isolate) {
	      // TODO
	      // Is it possible to decide the actual queues?
	      output.println("/* possibly needed by multi-parameter tasks on this core*//*");
	      output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+", NULL, 0);");
	    }*/ // deleted 09/07/06, multi-param tasks are pinned to one core now
	    output.println("/* transfer to core " + targetcore.toString() + "*/");
	    output.println("{");
	    // enqueue this object and its destinations for later process
	    // all the possible queues
	    QueueInfo qinfo = null;
	    TranObjInfo tmpinfo = new TranObjInfo();
	    tmpinfo.name = super.generateTemp(fm, temp, lb);
	    tmpinfo.targetcore = targetcore;
	    FlagState targetFS = this.currentSchedule.getTargetFState(tmpFState);
	    if(targetFS != null) {
	      tmpinfo.fs = targetFS;
	    } else {
	      tmpinfo.fs = tmpFState;
	    }
	      qinfo = outputtransqueues(tmpinfo.fs, targetcore, output);
	      output.println("tmpObjInfo = RUNMALLOC(sizeof(struct transObjInfo));");
	      output.println("tmpObjInfo->objptr = (void *)" + tmpinfo.name + ";");
	      output.println("tmpObjInfo->targetcore = "+targetcore.toString()+";");
	      output.println("tmpObjInfo->queues = " + qinfo.qname + ";");
	      output.println("tmpObjInfo->length = " + qinfo.length + ";");
	      output.println("addNewItem(totransobjqueue, (void*)tmpObjInfo);");
	    output.println("}");
	  }
	  output.println("/* increase index*/");
	  output.println("++" + queueins + ".index;");
	} else {
	  // this object will reside on current core
	  output.println("/* reside on this core*/");
	  if(isolate) {
	    output.println("{");
	    QueueInfo qinfo = outputqueues(tmpFState, num, output, true);
	    output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+", " + qinfo.qname +
	                   ", " + qinfo.length + ");");
	    output.println("}");
	  } /*else {
	    // TODO
	    // really needed?
	    output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+", NULL, 0);");
	  }*/ // deleted 09/07/06, multi-param tasks are pinned to one core now
	}

	// codes for multi-params tasks
	if(!isolate) {
	  // flagstate associated with some multi-params tasks
	  // need to be send to other cores
	  Vector<Integer> targetcores = this.currentSchedule.getAllyCores(tmpFState);
	  output.println("/* send the shared object to possible queues on other cores*/");
	  // TODO, temporary solution, send to mostly the first two 
	  int upperbound = targetcores.size() > 2? 2: targetcores.size();
	  for(int k = 0; k < upperbound; ++k) {
	    // TODO
	    // add the information of exactly which queue
	    int targetcore = targetcores.elementAt(k).intValue();
	    if(!sendto.contains(targetcore)) {
	    // previously not sended to this target core
	    // enqueue this object and its destinations for later process
	    output.println("{");
	    // all the possible queues
	    QueueInfo qinfo = null;
	    TranObjInfo tmpinfo = new TranObjInfo();
	    tmpinfo.name = super.generateTemp(fm, temp, lb);
	    tmpinfo.targetcore = targetcore;
	    FlagState targetFS = this.currentSchedule.getTargetFState(tmpFState);
	    if(targetFS != null) {
	      tmpinfo.fs = targetFS;
	    } else {
	      tmpinfo.fs = tmpFState;
	    }
	      qinfo = outputtransqueues(tmpinfo.fs, targetcore, output);
	      output.println("tmpObjInfo = RUNMALLOC(sizeof(struct transObjInfo));");
	      output.println("tmpObjInfo->objptr = (void *)" + tmpinfo.name + ";");
	      output.println("tmpObjInfo->targetcore = "+targetcore+";");
	      output.println("tmpObjInfo->queues = " + qinfo.qname + ";");
	      output.println("tmpObjInfo->length = " + qinfo.length + ";");
	      output.println("addNewItem(totransobjqueue, (void*)tmpObjInfo);");
	      output.println("}");
	      sendto.addElement(targetcore);
	    }
	  }
	}
      }

      if(initfstates != null) {
	output.println("}");
      }
    }
  }

  private QueueInfo outputqueues(FlagState tmpFState, 
	                         int num, 
	                         PrintWriter output, 
	                         boolean isEnqueue) {
    // queue array
    QueueInfo qinfo = new QueueInfo();
    qinfo.qname  = "queues_" + tmpFState.getLabel() + "_" + tmpFState.getiuid();
    output.println("struct parameterwrapper * " + qinfo.qname + "[] = {");
    Iterator it_edges = tmpFState.getEdgeVector().iterator();
    Vector<TaskDescriptor> residetasks = this.currentSchedule.getTasks();
    Vector<TaskDescriptor> tasks = new Vector<TaskDescriptor>();
    Vector<Integer> indexes = new Vector<Integer>();
    boolean comma = false;
    qinfo.length = 0;
    while(it_edges.hasNext()) {
      FEdge fe = (FEdge)it_edges.next();
      TaskDescriptor td = fe.getTask();
      int paraindex = fe.getIndex();
      if((!isEnqueue) || (isEnqueue && residetasks.contains(td))) {
	if((!tasks.contains(td)) ||
	   ((tasks.contains(td)) && (paraindex != indexes.elementAt(tasks.indexOf(td)).intValue()))) {
	  tasks.addElement(td);
	  indexes.addElement(paraindex);
	  if(comma) {
	    output.println(",");
	  } else {
	    comma = true;
	  }
	  output.print("&" + this.objqueueprefix + paraindex + "_" + td.getCoreSafeSymbol(num));
	  ++qinfo.length;
	}
      }
    }
    output.println("};");
    return qinfo;
  }

  private QueueInfo outputtransqueues(FlagState tmpFState, 
	                              int targetcore, 
	                              PrintWriter output) {
    // queue array
    QueueInfo qinfo = new QueueInfo();
    qinfo.qname  = "queues_" + tmpFState.getLabel() + "_" + tmpFState.getiuid();
    output.println("int " + qinfo.qname + "_clone[] = {");
    Iterator it_edges = tmpFState.getEdgeVector().iterator();
    Vector<TaskDescriptor> residetasks = this.scheduling.get(targetcore).getTasks();
    Vector<TaskDescriptor> tasks = new Vector<TaskDescriptor>();
    Vector<Integer> indexes = new Vector<Integer>();
    boolean comma = false;
    qinfo.length = 0;
    while(it_edges.hasNext()) {
      FEdge fe = (FEdge)it_edges.next();
      TaskDescriptor td = fe.getTask();
      int paraindex = fe.getIndex();
      if(residetasks.contains(td)) {
	if((!tasks.contains(td)) ||
	   ((tasks.contains(td)) && (paraindex != indexes.elementAt(tasks.indexOf(td)).intValue()))) {
	  tasks.addElement(td);
	  indexes.addElement(paraindex);
	  if(comma) {
	    output.println(",");
	  } else {
	    comma = true;
	  }
	  output.print(residetasks.indexOf(td) + ", ");
	  output.print(paraindex);
	  ++qinfo.length;
	}
      }
    }
    output.println("};");
    output.println("int * " + qinfo.qname + " = RUNMALLOC(sizeof(int) * " + qinfo.length * 2 + ");");
    output.println("memcpy(" + qinfo.qname + ", (int *)" + qinfo.qname + "_clone, sizeof(int) * " + qinfo.length * 2 + ");");
    return qinfo;
  }

  private class QueueInfo {
    public int length;
    public String qname;
  }

  private String generateTempFlagName(FlatMethod fm, 
	                              TempDescriptor td, 
	                              LocalityBinding lb) {
    MethodDescriptor md=fm.getMethod();
    TaskDescriptor task=fm.getTask();
    TempObject objecttemps=(TempObject) tempstable.get(lb!=null ? lb : md!=null ? md : task);

    if (objecttemps.isLocalPrim(td)||objecttemps.isParamPrim(td)) {
      return td.getSafeSymbol() + "_oldflag";
    }

    if (objecttemps.isLocalPtr(td)) {
      return localsprefix+"_"+td.getSafeSymbol() + "_oldflag";
    }

    if (objecttemps.isParamPtr(td)) {
      return paramsprefix+"_"+td.getSafeSymbol() + "_oldflag";
    }
    throw new Error();
  }

  protected void outputTransCode(PrintWriter output) {
    output.println("while(0 == isEmpty(totransobjqueue)) {");
    output.println("   struct transObjInfo * totransobj = (struct transObjInfo *)(getItem(totransobjqueue));");
    output.println("   transferObject(totransobj);");
    output.println("   RUNFREE(totransobj->queues);");
    output.println("   RUNFREE(totransobj);");
    output.println("}");
    //output.println("freeQueue(totransobjqueue);");
  }

  protected void outputAliasLockCode(FlatMethod fm, 
	                                 LocalityBinding lb, 
	                                 PrintWriter output) {
    if(this.m_oa == null) {
      return;
    }
    TaskDescriptor td = fm.getTask();
    Object[] allocSites = this.m_oa.getFlaggedAllocationSitesReachableFromTask(td).toArray();
    Vector<Vector<Integer>> aliasSets = new Vector<Vector<Integer>>();
    Vector<Vector<FlatNew>> aliasFNSets = new Vector<Vector<FlatNew>>();
    Hashtable<Integer, Vector<FlatNew>> aliasFNTbl4Para = new Hashtable<Integer, Vector<FlatNew>>();
    Hashtable<FlatNew, Vector<FlatNew>> aliasFNTbl = new Hashtable<FlatNew, Vector<FlatNew>>();
    Set<HeapRegionNode> common;
    for( int i = 0; i < fm.numParameters(); ++i ) {
      // for the ith parameter check for aliases to all
      // higher numbered parameters
      aliasSets.add(null);
      for( int j = i + 1; j < fm.numParameters(); ++j ) {
	common = this.m_oa.createsPotentialAliases(td, i, j);
	if(!common.isEmpty()) {
      System.out.println("Alias between " + i + " and " + j);
	  // ith parameter and jth parameter has alias, create lock to protect them
	  if(aliasSets.elementAt(i) == null) {
	    aliasSets.setElementAt(new Vector<Integer>(), i);
	  }
	  aliasSets.elementAt(i).add(j);
	}
      }

      // for the ith parameter, check for aliases against
      // the set of allocation sites reachable from this
      // task context
      aliasFNSets.add(null);
      for(int j = 0; j < allocSites.length; j++) {
	AllocationSite as = (AllocationSite)allocSites[j];
	common = this.m_oa.createsPotentialAliases(td, i, as);
	if( !common.isEmpty() ) {
      // ith parameter and allocationsite as has alias
	  if(aliasFNSets.elementAt(i) == null) {
	    aliasFNSets.setElementAt(new Vector<FlatNew>(), i);
	  }
	  aliasFNSets.elementAt(i).add(as.getFlatNew());
	}
      }
    }

    // for each allocation site check for aliases with
    // other allocation sites in the context of execution
    // of this task
    for( int i = 0; i < allocSites.length; ++i ) {
      AllocationSite as1 = (AllocationSite)allocSites[i];
      for(int j = i + 1; j < allocSites.length; j++) {
	AllocationSite as2 = (AllocationSite)allocSites[j];

	common = this.m_oa.createsPotentialAliases(td, as1, as2);
	if( !common.isEmpty() ) {
      // as1 and as2 has alias
	  if(!aliasFNTbl.containsKey(as1.getFlatNew())) {
	    aliasFNTbl.put(as1.getFlatNew(), new Vector<FlatNew>());
	  }
	  if(!aliasFNTbl.get(as1.getFlatNew()).contains(as2.getFlatNew())) {
	    aliasFNTbl.get(as1.getFlatNew()).add(as2.getFlatNew());
	  }
	}
      }
    }

    // if FlatNew N1->N2->N3, we group N1, N2, N3 together
    Iterator<FlatNew> it = aliasFNTbl.keySet().iterator();
    Vector<FlatNew> visited = new Vector<FlatNew>();
    while(it.hasNext()) {
      FlatNew tmpfn = it.next();
      if(visited.contains(tmpfn)) {
	continue;
      }
      visited.add(tmpfn);
      Queue<FlatNew> tovisit = new LinkedList<FlatNew>();
      Vector<FlatNew> tmpv = aliasFNTbl.get(tmpfn);
      if(tmpv == null) {
	continue;
      }

      for(int j = 0; j < tmpv.size(); j++) {
	tovisit.add(tmpv.elementAt(j));
      }

      while(!tovisit.isEmpty()) {
	FlatNew fn = tovisit.poll();
	visited.add(fn);
	Vector<FlatNew> tmpset = aliasFNTbl.get(fn);
	if(tmpset != null) {
	  // merge tmpset to the alias set of the ith parameter
	  for(int j = 0; j < tmpset.size(); j++) {
	    if(!tmpv.contains(tmpset.elementAt(j))) {
	      tmpv.add(tmpset.elementAt(j));
	      tovisit.add(tmpset.elementAt(j));
	    }
	  }
	  aliasFNTbl.remove(fn);
	}
      }
      it = aliasFNTbl.keySet().iterator();
    }

    // check alias between parameters and between parameter-flatnew
    for(int i = 0; i < aliasSets.size(); i++) {
      Queue<Integer> tovisit = new LinkedList<Integer>();
      Vector<Integer> tmpv = aliasSets.elementAt(i);
      if(tmpv == null) {
	continue;
      }

      for(int j = 0; j < tmpv.size(); j++) {
	tovisit.add(tmpv.elementAt(j));
      }

      while(!tovisit.isEmpty()) {
	int index = tovisit.poll().intValue();
	Vector<Integer> tmpset = aliasSets.elementAt(index);
	if(tmpset != null) {
	  // merge tmpset to the alias set of the ith parameter
	  for(int j = 0; j < tmpset.size(); j++) {
	    if(!tmpv.contains(tmpset.elementAt(j))) {
	      tmpv.add(tmpset.elementAt(j));
	      tovisit.add(tmpset.elementAt(j));
	    }
	  }
	  aliasSets.setElementAt(null, index);
	}

	Vector<FlatNew> tmpFNSet = aliasFNSets.elementAt(index);
	if(tmpFNSet != null) {
	  // merge tmpFNSet to the aliasFNSet of the ith parameter
	  if(aliasFNSets.elementAt(i) == null) {
	    aliasFNSets.setElementAt(tmpFNSet, i);
	  } else {
	    Vector<FlatNew> tmpFNv = aliasFNSets.elementAt(i);
	    for(int j = 0; j < tmpFNSet.size(); j++) {
	      if(!tmpFNv.contains(tmpFNSet.elementAt(j))) {
		tmpFNv.add(tmpFNSet.elementAt(j));
	      }
	    }
	  }
	  aliasFNSets.setElementAt(null, index);
	}
      }
    }

    int numlock = 0;
    int numparalock = 0;
    Vector<Vector<Integer>> tmpaliasSets = new Vector<Vector<Integer>>();
    for(int i = 0; i < aliasSets.size(); i++) {
      Vector<Integer> tmpv = aliasSets.elementAt(i);
      if(tmpv != null) {
	tmpv.add(0, i);
	tmpaliasSets.add(tmpv);
	numlock++;
      }

      Vector<FlatNew> tmpFNv = aliasFNSets.elementAt(i);
      if(tmpFNv != null) {
	aliasFNTbl4Para.put(i, tmpFNv);
	if(tmpv == null) {
	  numlock++;
	}
      }
    }
    numparalock = numlock;
    aliasSets.clear();
    aliasSets = null;
    this.m_aliasSets = tmpaliasSets;
    aliasFNSets.clear();
    aliasFNSets = null;
    this.m_aliasFNTbl4Para = aliasFNTbl4Para;
    this.m_aliasFNTbl = aliasFNTbl;
    numlock += this.m_aliasFNTbl.size();

    // create locks
    if(numlock > 0) {
      output.println("int aliaslocks[" + numlock + "];");
      output.println("int tmpi = 0;");      
      // associate locks with parameters
      int lockindex = 0;
      for(int i = 0; i < this.m_aliasSets.size(); i++) {
	Vector<Integer> toadd = this.m_aliasSets.elementAt(i);
	
	output.print("int tmplen_" + lockindex + " = 0;");
	output.println("void * tmpptrs_" + lockindex + "[] = {");
	for(int j = 0; j < toadd.size(); j++) {
	    int para = toadd.elementAt(j).intValue();
	    output.print(super.generateTemp(fm, fm.getParameter(para), lb));
	    if(j < toadd.size() - 1) {
		output.print(", ");
	    } else {
		output.println("};");
	    }
	}
	output.println("aliaslocks[tmpi++] = getAliasLock(tmpptrs_" + lockindex + ", tmplen_" + lockindex + ", lockRedirectTbl);");
	
	for(int j = 0; j < toadd.size(); j++) {
	  int para = toadd.elementAt(j).intValue();
	  output.println("addAliasLock("  + super.generateTemp(fm, fm.getParameter(para), lb) + ", aliaslocks[" + i + "]);");
	}
	// check if this lock is also associated with any FlatNew nodes
	if(this.m_aliasFNTbl4Para.containsKey(toadd.elementAt(0))) {
	  if(this.m_aliaslocksTbl4FN == null) {
	    this.m_aliaslocksTbl4FN = new Hashtable<FlatNew, Vector<Integer>>();
	  }
	  Vector<FlatNew> tmpv = this.m_aliasFNTbl4Para.get(toadd.elementAt(0));
	  for(int j = 0; j < tmpv.size(); j++) {
	    FlatNew fn = tmpv.elementAt(j);
	    if(!this.m_aliaslocksTbl4FN.containsKey(fn)) {
	      this.m_aliaslocksTbl4FN.put(fn, new Vector<Integer>());
	    }
	    this.m_aliaslocksTbl4FN.get(fn).add(i);
	  }
	  this.m_aliasFNTbl4Para.remove(toadd.elementAt(0));
	}
	lockindex++;
      }
      
      Object[] key = this.m_aliasFNTbl4Para.keySet().toArray();
      for(int i = 0; i < key.length; i++) {
	int para = ((Integer)key[i]).intValue();

	output.println("void * tmpptrs_" + lockindex + "[] = {" + super.generateTemp(fm, fm.getParameter(para), lb) + "};");
	output.println("aliaslocks[tmpi++] = getAliasLock(tmpptrs_" + lockindex + ", 1, lockRedirectTbl);");
	
	output.println("addAliasLock(" + super.generateTemp(fm, fm.getParameter(para), lb) + ", aliaslocks[" + lockindex + "]);");
	Vector<FlatNew> tmpv = this.m_aliasFNTbl4Para.get(para);
	for(int j = 0; j < tmpv.size(); j++) {
	  FlatNew fn = tmpv.elementAt(j);
	  if(this.m_aliaslocksTbl4FN == null) {
	    this.m_aliaslocksTbl4FN = new Hashtable<FlatNew, Vector<Integer>>();
	  }
	  if(!this.m_aliaslocksTbl4FN.containsKey(fn)) {
	    this.m_aliaslocksTbl4FN.put(fn, new Vector<Integer>());
	  }
	  this.m_aliaslocksTbl4FN.get(fn).add(lockindex);
	}
	lockindex++;
      }
      
      // check m_aliasFNTbl for locks associated with FlatNew nodes
      Object[] FNkey = this.m_aliasFNTbl.keySet().toArray();
      for(int i = 0; i < FNkey.length; i++) {
	FlatNew fn = (FlatNew)FNkey[i];
	Vector<FlatNew> tmpv = this.m_aliasFNTbl.get(fn);
	
	output.println("aliaslocks[tmpi++] = (int)(RUNMALLOC(sizeof(int)));");
	
	if(this.m_aliaslocksTbl4FN == null) {
	  this.m_aliaslocksTbl4FN = new Hashtable<FlatNew, Vector<Integer>>();
	}
	if(!this.m_aliaslocksTbl4FN.containsKey(fn)) {
	  this.m_aliaslocksTbl4FN.put(fn, new Vector<Integer>());
	}
	this.m_aliaslocksTbl4FN.get(fn).add(lockindex);
	for(int j = 0; j < tmpv.size(); j++) {
	  FlatNew tfn = tmpv.elementAt(j);
	  if(!this.m_aliaslocksTbl4FN.containsKey(tfn)) {
	    this.m_aliaslocksTbl4FN.put(tfn, new Vector<Integer>());
	  }
	  this.m_aliaslocksTbl4FN.get(tfn).add(lockindex);
	}
	lockindex++;
      }
    }
  }

  protected void generateFlatReturnNode(FlatMethod fm, 
	                                LocalityBinding lb, 
	                                FlatReturnNode frn, 
	                                PrintWriter output) {
    if (frn.getReturnTemp()!=null) {
      if (frn.getReturnTemp().getType().isPtr())
	output.println("return (struct "+fm.getMethod().getReturnType().getSafeSymbol()+"*)"+generateTemp(fm, frn.getReturnTemp(), lb)+";");
      else
	output.println("return "+generateTemp(fm, frn.getReturnTemp(), lb)+";");
    } else {
      if(fm.getTask() != null) {
	output.println("#ifdef CACHEFLUSH");
	output.println("BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();");
	output.println("#ifdef DEBUG");
	output.println("BAMBOO_DEBUGPRINT(0xec00);");
	output.println("#endif");
	output.println("BAMBOO_CACHE_FLUSH_ALL();");
	output.println("#ifdef DEBUG");
	output.println("BAMBOO_DEBUGPRINT(0xecff);");
	output.println("#endif");
	output.println("BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();");
	output.println("#endif");
	outputTransCode(output);
      }
      output.println("return;");
    }
  }

  protected void generateFlatNew(FlatMethod fm, 
	                         LocalityBinding lb, 
	                         FlatNew fn,
                                 PrintWriter output) {
    if (state.DSM && locality.getAtomic(lb).get(fn).intValue() > 0
        && !fn.isGlobal()) {
      // Stash pointer in case of GC
      String revertptr = super.generateTemp(fm, reverttable.get(lb), lb);
      output.println(revertptr + "=trans->revertlist;");
    }
    if (fn.getType().isArray()) {
      int arrayid = state.getArrayNumber(fn.getType())
                    + state.numClasses();
      if (fn.isGlobal()) {
	output.println(super.generateTemp(fm, fn.getDst(), lb)
	               + "=allocate_newarrayglobal(trans, " + arrayid + ", "
	               + super.generateTemp(fm, fn.getSize(), lb) + ");");
      } else if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	output.println(super.generateTemp(fm, fn.getDst(), lb)
	               + "=allocate_newarray(&" + localsprefix + ", "
	               + arrayid + ", " + super.generateTemp(fm, fn.getSize(), lb)
	               + ");");
      } else {
	output.println(super.generateTemp(fm, fn.getDst(), lb)
	               + "=allocate_newarray(" + arrayid + ", "
	               + super.generateTemp(fm, fn.getSize(), lb) + ");");
      }
    } else {
      if (fn.isGlobal()) {
	output.println(super.generateTemp(fm, fn.getDst(), lb)
	               + "=allocate_newglobal(trans, "
	               + fn.getType().getClassDesc().getId() + ");");
      } else if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
	output.println(super.generateTemp(fm, fn.getDst(), lb)
	               + "=allocate_new(&" + localsprefix + ", "
	               + fn.getType().getClassDesc().getId() + ");");
      } else {
	output.println(super.generateTemp(fm, fn.getDst(), lb)
	               + "=allocate_new("
	               + fn.getType().getClassDesc().getId() + ");");
      }
    }
    if (state.DSM && locality.getAtomic(lb).get(fn).intValue() > 0
        && !fn.isGlobal()) {
      String revertptr = super.generateTemp(fm, reverttable.get(lb), lb);
      output.println("trans->revertlist=" + revertptr + ";");
    }
    // create alias lock if necessary
    if((this.m_aliaslocksTbl4FN != null) && (this.m_aliaslocksTbl4FN.containsKey(fn))) {
      Vector<Integer> tmpv = this.m_aliaslocksTbl4FN.get(fn);
      for(int i = 0; i < tmpv.size(); i++) {
	output.println("addAliasLock(" + super.generateTemp(fm, fn.getDst(), lb) + ", aliaslocks[" + tmpv.elementAt(i).intValue() + "]);");
      }
    }
    // generate codes for profiling, recording how many new objects are created
    if(!fn.getType().isArray() && 
	    (fn.getType().getClassDesc() != null) 
	    && (fn.getType().getClassDesc().hasFlags())) {
	output.println("#ifdef PROFILE");
	output.println("addNewObjInfo(\"" + fn.getType().getClassDesc().getSymbol() + "\");");
	output.println("#endif");
    }
  }

  class TranObjInfo {
    public String name;
    public int targetcore;
    public FlagState fs;
  }

  private boolean contains(Vector<TranObjInfo> sendto, 
	                   TranObjInfo t) {
    if(sendto.size() == 0) {
      return false;
    }
    for(int i = 0; i < sendto.size(); i++) {
      TranObjInfo tmp = sendto.elementAt(i);
      if(!tmp.name.equals(t.name)) {
	return false;
      }
      if(tmp.targetcore != t.targetcore) {
	return false;
      }
      if(tmp.fs != t.fs) {
	return false;
      }
    }
    return true;
  }
}
