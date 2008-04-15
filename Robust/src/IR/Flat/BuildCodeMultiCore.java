package IR.Flat;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Queue;
import java.util.Vector;

import Analysis.Locality.LocalityBinding;
import Analysis.Scheduling.Schedule;
import Analysis.TaskStateAnalysis.FEdge;
import Analysis.TaskStateAnalysis.FlagState;
import Analysis.TaskStateAnalysis.SafetyAnalysis;
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
    Schedule currentSchedule;
    Hashtable[] fsate2qnames;
    String objqs4startupprefix= "objqueuearray4startup";
    String objqs4socketprefix= "objqueuearray4socket";
    String objqueueprefix = "objqueue4parameter_";
    String taskprefix = "task_";
    String taskarrayprefix = "taskarray_core";
    String otqueueprefix = "___otqueue";

    public BuildCodeMultiCore(State st, Hashtable temptovar, TypeUtil typeutil, SafetyAnalysis sa, Vector<Schedule> scheduling, int coreNum) {
	super(st, temptovar, typeutil, sa);
	this.scheduling = scheduling;
	this.coreNum = coreNum;
	this.currentSchedule = null;
	this.fsate2qnames = null;
    }

    public void buildCode() {
	/* Create output streams to write to */
	PrintWriter outclassdefs=null;
	PrintWriter outstructs=null;
	//PrintWriter outrepairstructs=null;
	PrintWriter outmethodheader=null;
	PrintWriter outmethod=null;
	PrintWriter outvirtual=null;
	PrintWriter outtask=null;
	PrintWriter outtaskdefs=null;
	//PrintWriter[] outtaskdefs=null;
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
		/*if(this.scheduling != null) {
		    outtaskdefs = new PrintWriter[this.coreNum];
		    for(int i = 0; i < this.scheduling.size(); ++i) {
			this.currentSchedule = this.scheduling.elementAt(i);
			outtaskdefs[this.currentSchedule.getCoreNum()] = new PrintWriter(
				new FileOutputStream(PREFIX+"taskdefs_"+this.currentSchedule.getCoreNum()+".c"), true);
		    }
		}*/
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
	int numclasses = 0;
	while(it.hasNext()) {
	    ++numclasses;
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
	    // arrays record the queues for startup object & socket object
	    int[][] numqueues = new int[2][this.coreNum];
	    /*Vector qnames[][]= new Vector[2][this.coreNum];
	    for(int i = 0; i < qnames.length; ++i) {
		qnames[i] = null;
	    }*/
	    /* Output code for tasks */
	    for(int i = 0; i < this.scheduling.size(); ++i) {
		this.currentSchedule = this.scheduling.elementAt(i);
		outputTaskCode(outtaskdefs, outmethod, outtask, taskits, numtasks, numqueues);//, qnames);
		/*outputTaskCode(outtaskdefs[this.currentSchedule.getCoreNum()], outmethod);
		outtaskdefs[this.currentSchedule.getCoreNum()].close();*/
	    }
	    
	    // Output task descriptors
	    boolean comma = false;
	    for(int index = 0; index < 2; ++index) {
		if(index == 0) {
		    outtaskdefs.println("struct parameterwrapper ** objq4startupobj[] = {");
		} else {
		    outtaskdefs.println("struct parameterwrapper ** objq4socketobj[] = {");
		}
		comma = false;
		for(int i = 0; i < this.coreNum; ++i) {
		    if(comma) {
			outtaskdefs.println(",");
		    } else {
			comma = true;
		    }
		    outtaskdefs.println("/* object queue array for core " + i + "*/");
		    outtaskdefs.print(this.objqs4startupprefix + "_core" + i);
		}
		outtaskdefs.println("};");
		if(index == 0) {
		    outtaskdefs.println("int numqueues4startupobj[] = {");
		} else {
		    outtaskdefs.println("int numqueues4socketobj[] = {");
		}
		int[] tmparray = numqueues[index];
		comma = false;
		for(int i = 0; i < tmparray.length; ++i) {
		    if(comma) {
			outtaskdefs.print(",");
		    } else {
			comma = true;
		    }
		    outtaskdefs.print(tmparray[i]);
		}
		outtaskdefs.println("};");
	    }
	    
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
	    for(int i = 0; i < taskits.length; ++i) {
		boolean first=true;
		if (first)
		    first=false;
		else
		    outtaskdefs.print(",");
		outtaskdefs.print(numtasks[i]);
	    }
	    outtaskdefs.println("};");

	    outtaskdefs.println("#ifdef RAW");
	    outtaskdefs.println("#include \"raw.h\"");
	    outtaskdefs.println("int corenum=raw_get_tile_num();");
	    outtaskdefs.println("#else");
	    outtaskdefs.println("int corenum=0;");
	    outtaskdefs.println("#endif");
	    
	    outtaskdefs.close();
	    
	    outtask.println("#endif");
	    outtask.close();
	    /* Record maximum number of task parameters */
	    outstructs.println("#define MAXTASKPARAMS "+maxtaskparams);
	} //else if (state.main!=null) {
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

    private void generateTaskStructs(PrintWriter output, PrintWriter headersout) {
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
		if (GENERATEPRECISEGC) {
		    output.println("struct "+task.getCoreSafeSymbol(num)+"_params {");
		    output.println("  int size;");
		    output.println("  void * next;");
		    for(int j=0;j<objectparams.numPointers();j++) {
			TempDescriptor temp=objectparams.getPointer(j);
			output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
		    }

		    output.println("};\n");
		    if ((objectparams.numPointers()+fm.numTags())>maxtaskparams) {
			maxtaskparams=objectparams.numPointers()+fm.numTags();
		    }
		}

		/* Output temp structure */
		if (GENERATEPRECISEGC) {
		    output.println("struct "+task.getCoreSafeSymbol(num)+"_locals {");
		    output.println("  int size;");
		    output.println("  void * next;");
		    for(int j=0;j<objecttemps.numPointers();j++) {
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

		if (GENERATEPRECISEGC) {
		    headersout.print("struct "+task.getCoreSafeSymbol(num)+"_params * "+paramsprefix);
		} else
		    headersout.print("void * parameterarray[]");
		headersout.println(");\n");
	    }
	}

    }

    /* This method outputs code for each task. */

    private void outputTaskCode(PrintWriter outtaskdefs, PrintWriter outmethod, PrintWriter outtask, Iterator[] taskits, int[] numtasks, 
	                        int[][] numqueues) {//, Vector[] qnames) {
	/* Compile task based program */
	outtaskdefs.println("#include \"task.h\"");
	outtaskdefs.println("#include \"methodheaders.h\"");

	/* Output object transfer queues into method.c*/
	generateObjectTransQueues(outmethod);

	Vector[] qnames = new Vector[2];
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
	for(int i = 0; i < 2; ++i) {
	    if(i == 0) {
		outtaskdefs.println("/* object queue array for class StartupObject on core " + num + "*/");
	    } else {
		outtaskdefs.println("/* object queue array for class Socket on core " + num + "*/");
	    }
	    if(i == 0) {
		outtaskdefs.println("struct parameterwrapper * " + this.objqs4startupprefix + "_core" + num + "[] = {");
	    } else {
		outtaskdefs.println("struct parameterwrapper * " + this.objqs4socketprefix + "_core" + num + "[] = {");
	    }
	    Vector tmpvector = qnames[i];
	    comma = false;
	    if(tmpvector != null) {
		for(int j = 0; j < tmpvector.size(); ++j) {
		    if(comma) {
			outtaskdefs.println(",");
		    } else {
			comma = true;
		    }
		   outtaskdefs.print("&" + tmpvector.elementAt(j));
		}
		numqueues[i][num] = tmpvector.size();
	    } else {
		numqueues[i][num] = 0;
	    }
	    outtaskdefs.println();
	    outtaskdefs.println("};");
	}

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
	outtask.println("  //struct parameterwrapper *next;");
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
	outtask.println("extern struct parameterwrapper ** objq4startupobj[];");
	outtask.println("extern int numqueues4startupobj[];");
	outtask.println("extern struct parameterwrapper ** objq4socketobj[];");
	outtask.println("extern int numqueues4socketobj[];");
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
	outtask.println("extern int corenum;");  // define corenum to identify different core
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
	    output.println(/*".cores = " + */queuename + "cores,");
	    output.println(/*".index = " + */"0,");
	    output.println(/*".length = " +*/ targetcores.length + "};");
	}
	output.println();
    }

    private void generateTaskMethod(FlatMethod fm, LocalityBinding lb, PrintWriter output) {
	/*if (State.PRINTFLAT)
	    System.out.println(fm.printMethod());*/	
	TaskDescriptor task=fm.getTask();
	assert(task != null);
	int num = this.currentSchedule.getCoreNum();

	//ParamsObject objectparams=(ParamsObject)paramstable.get(lb!=null?lb:task);
	generateTaskHeader(fm, lb, task,output);
	TempObject objecttemp=(TempObject) tempstable.get(lb!=null?lb:task);
	/*if (state.DSM&&lb.getHasAtomic()) {
	    output.println("transrecord_t * trans;");
	}*/

	if (GENERATEPRECISEGC) {
	    output.print("   struct "+task.getCoreSafeSymbol(num)+"_locals "+localsprefix+"={");

	    output.print(objecttemp.numPointers()+",");
	    output.print(paramsprefix);
	    for(int j=0;j<objecttemp.numPointers();j++)
		output.print(", NULL");
	    output.println("};");
	}

	for(int i=0;i<objecttemp.numPrimitives();i++) {
	    TempDescriptor td=objecttemp.getPrimitive(i);
	    TypeDescriptor type=td.getType();
	    if (type.isNull())
		//output.println("   void * "+td.getCoreSafeSymbol(num)+";");
		output.println("   void * "+td.getSafeSymbol()+";");
	    else if (type.isClass()||type.isArray())
		//output.println("   struct "+type.getSafeSymbol()+" * "+td.getCoreSafeSymbol(num)+";");
		output.println("   struct "+type.getSafeSymbol()+" * "+td.getSafeSymbol()+";");
	    else
		//output.println("   "+type.getSafeSymbol()+" "+td.getCoreSafeSymbol(num)+";");
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

	/*if ((state.THREAD||state.DSM)&&GENERATEPRECISEGC) {
	    if (state.DSM&&lb.isAtomic())
		output.println("checkcollect2(&"+localsprefix+",trans);");
	    else
		output.println("checkcollect(&"+localsprefix+");");
	}*/

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
    private void generateTaskDescriptor(PrintWriter output, PrintWriter outtask, FlatMethod fm, TaskDescriptor task, Vector[] qnames) {
	int num = this.currentSchedule.getCoreNum();
	
	output.println("/* TaskDescriptor information for task " + task.getSymbol() + " on core " + num + "*/");
	
	for (int i=0;i<task.numParameters();i++) {
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
		for(int j=0;j<dflag.size();j++) {
		    if (j!=0)
			output.println(",");
		    Vector term=dflag.get(j);
		    int andmask=0;
		    int checkmask=0;
		    for(int k=0;k<term.size();k++) {
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
		for(int j=0;j<param_tag.numTags();j++) {
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
		if(qnames[0] == null) {
		    qnames[0] = new Vector();
		}
		qnames[0].addElement(qname);
	    } else if(param_type.getClassDesc().getSymbol().equals("Socket")) {
		if(qnames[1] == null) {
		    qnames[1] = new Vector();
		}
		qnames[1].addElement(qname);
	    }
	    outtask.println("extern struct parameterwrapper " + qname + ";"); 
	    output.println("struct parameterwrapper " + qname + "={"); 
	    output.println(".objectset = 0,"); // objectset
	    output.println("/* number of DNF terms */ .numberofterms = "+dnfterms+","); // numberofterms
	    output.println(".intarray = parameterdnf_"+i+"_"+task.getCoreSafeSymbol(num)+","); // intarray
	    // numbertags
	    if (param_tag!=null)
		output.println("/* number of tags */ .numbertags = "+param_tag.numTags()+",");
	    else
		output.println("/* number of tags */ .numbertags = 0,");
	    output.println(".tagarray = parametertag_"+i+"_"+task.getCoreSafeSymbol(num)+","); // tagarray
	    output.println(".task = 0,"); // task
	    output.println(".slot = " + i + ",");// slot
	    // iterators
	    output.println("};");
	    
	    output.println("struct parameterdescriptor parameter_"+i+"_"+task.getCoreSafeSymbol(num)+"={");
	    output.println("/* type */"+param_type.getClassDesc().getId()+",");
	    output.println("/* number of DNF terms */"+dnfterms+",");
	    output.println("parameterdnf_"+i+"_"+task.getCoreSafeSymbol(num)+","); // intarray
	    output.println("&" + qname + ","); // queue
	    //BUG, added next line to fix and else statement...test
	    //with any task program
	    if (param_tag!=null)
		output.println("/* number of tags */"+param_tag.numTags()+",");
	    else
		output.println("/* number of tags */ 0,");
	    output.println("parametertag_"+i+"_"+task.getCoreSafeSymbol(num)); // tagarray
	    output.println("};");
	}


	output.println("struct parameterdescriptor * parameterdescriptors_"+task.getCoreSafeSymbol(num)+"[] = {");
	for (int i=0;i<task.numParameters();i++) {
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

    private void generateTaskHeader(FlatMethod fm, LocalityBinding lb, Descriptor des, PrintWriter output) {
	/* Print header */
	ParamsObject objectparams=(ParamsObject)paramstable.get(lb!=null?lb:des);
	TaskDescriptor task=(TaskDescriptor) des;

	int num = this.currentSchedule.getCoreNum();
	//catch the constructor case
	output.print("void ");
	output.print(task.getCoreSafeSymbol(num)+"(");

	boolean printcomma=false;
	if (GENERATEPRECISEGC) {
	    output.print("struct "+task.getCoreSafeSymbol(num)+"_params * "+paramsprefix);
	    printcomma=true;
	}

	/*if (state.DSM&&lb.isAtomic()) {
	    if (printcomma)
		output.print(", ");
	    output.print("transrecord_t * trans");
	    printcomma=true;
	}*/

	if (!GENERATEPRECISEGC) {
	    /* Imprecise Task */
	    output.println("void * parameterarray[]) {");
	    /* Unpack variables */
	    for(int i=0;i<objectparams.numPrimitives();i++) {
		TempDescriptor temp=objectparams.getPrimitive(i);
		output.println("struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+"=parameterarray["+i+"];");
	    }
	    for(int i=0;i<fm.numTags();i++) {
		TempDescriptor temp=fm.getTag(i);
		int offset=i+objectparams.numPrimitives();
		output.println("struct ___TagDescriptor___ * "+temp.getSafeSymbol()+"=parameterarray["+offset+"];");
	    }

	    if ((objectparams.numPrimitives()+fm.numTags())>maxtaskparams)
		maxtaskparams=objectparams.numPrimitives()+fm.numTags();
	} else output.println(") {");
    }
    
    protected void generateFlagOrAnd(FlatFlagActionNode ffan, FlatMethod fm, LocalityBinding lb, TempDescriptor temp, 
	    PrintWriter output, int ormask, int andmask) {
	if (ffan.getTaskType()==FlatFlagActionNode.NEWOBJECT) {
	    output.println("flagorandinit("+super.generateTemp(fm, temp, lb)+", 0x"+Integer.toHexString(ormask)+", 0x"+Integer.toHexString(andmask)+");");
	} else {
	    int num = this.currentSchedule.getCoreNum();
	    ClassDescriptor cd = temp.getType().getClassDesc();
	    Vector<FlagState> initfstates = ffan.getInitFStates(cd);
	    for(int i = 0; i < initfstates.size(); ++i) {
		FlagState tmpFState = initfstates.elementAt(i);
		QueueInfo qinfo = outputqueues(tmpFState, num, output);
		output.println("flagorand("+super.generateTemp(fm, temp, lb)+", 0x"+Integer.toHexString(ormask)+
			       ", 0x"+Integer.toHexString(andmask)+", " + qinfo.qname + 
			       ", " + qinfo.length + ");");
	    }
	    //output.println("flagorand("+generateTemp(fm, temp, lb)+", 0x"+Integer.toHexString(ormask)+", 0x"+Integer.toHexString(andmask)+");");
	}
    }

    protected void generateObjectDistribute(FlatFlagActionNode ffan, FlatMethod fm, LocalityBinding lb, TempDescriptor temp, 
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
		    output.println("/* reside on this core*");
		    output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+", objq4socketobj[corenum], numqueues4socketobj[corenum]);");
		    //output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+");");
		}
		if(initfstates != null) {
		    output.println("}");
		}
	    }
	    //output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+");");
	    return;
	}
	
	int num = this.currentSchedule.getCoreNum();
	Hashtable<FlagState, Queue<Integer>> targetCoreTbl = this.currentSchedule.getTargetCoreTable();
	for(int j = 0; j < targetFStates.length; ++j) {
	    if(initfstates != null) {
		FlagState fs = initfstates.elementAt(j);
		output.println("if((" + generateTempFlagName(fm, temp, lb) + "&(0x" + Integer.toHexString(fs.getAndmask())
			+ "))==(0x" + Integer.toHexString(fs.getCheckmask()) + ")) {");
	    }
	    Vector<FlagState> tmpfstates = (Vector<FlagState>)targetFStates[j];
	    for(int i = 0; i < tmpfstates.size(); ++i) {
		FlagState tmpFState = tmpfstates.elementAt(i);
		Queue<Integer> queue = targetCoreTbl.get(tmpFState);
		if((queue != null) && 
			((queue.size() != 1) ||
				((queue.size() == 1) && (queue.element().intValue() != num)))) {
		    // this object may be transferred to other cores
		    String queuename = (String)this.fsate2qnames[num].get(tmpFState);
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
				QueueInfo qinfo = outputqueues(tmpFState, num, output);
				output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+", " + qinfo.qname + 
					       ", " + qinfo.length + ");");

				//output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+");");
			    } else {
				output.println("/* transfer to core " + targetcore.toString() + "*/");
				// method call of transfer objects
				output.println("transferObject("+super.generateTemp(fm, temp, lb)+", " + targetcore.toString() + ");");
			    }
			    output.println("break;");
			}
			output.println("}");
		    } else {
			output.println("/* transfer to core " + targetcore.toString() + "*/");
			// method call of transfer objectts
			output.println("transferObject("+super.generateTemp(fm, temp, lb)+", " + targetcore.toString() + ");");
		    }
		    output.println("/* increase index*/");
		    output.println("++" + queueins + ".index;");
		} else {
		    // this object will reside on current core
		    output.println("/* reside on this core*/");
		    QueueInfo qinfo = outputqueues(tmpFState, num, output);
		    output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+", " + qinfo.qname + 
			           ", " + qinfo.length + ");");

		    //output.println("enqueueObject("+super.generateTemp(fm, temp, lb)+");");
		}
	    }
	    if(initfstates != null) {
		output.println("}");
	    }
	}
    }
    
    private QueueInfo outputqueues(FlagState tmpFState, int num, PrintWriter output) {
	// queue array
	QueueInfo qinfo = new QueueInfo();
	output.println(";");
	qinfo.qname  = "queues_" + tmpFState.getLabel() + "_" + tmpFState.getiuid();
	output.println("struct parameterwrapper * " + qinfo.qname + "[] = {");
	Iterator it_edges = tmpFState.getEdgeVector().iterator();
	Vector<TaskDescriptor> tasks = new Vector<TaskDescriptor>();
	Vector<Integer> indexes = new Vector<Integer>();
	boolean comma = false;
	qinfo.length = 0;
	while(it_edges.hasNext()) {
	    FEdge fe = (FEdge)it_edges.next();
	    TaskDescriptor td = fe.getTask();
	    int paraindex = fe.getIndex();
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
	output.println("};");
	return qinfo;
    }
    
    private class QueueInfo {
	public int length;
	public String qname;
    }
    
    private String generateTempFlagName(FlatMethod fm, TempDescriptor td, LocalityBinding lb) {
	MethodDescriptor md=fm.getMethod();
	TaskDescriptor task=fm.getTask();
	TempObject objecttemps=(TempObject) tempstable.get(lb!=null?lb:md!=null?md:task);

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
}