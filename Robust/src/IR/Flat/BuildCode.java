package IR.Flat;
import IR.Tree.FlagExpressionNode;
import IR.Tree.DNFFlag;
import IR.Tree.DNFFlagAtom;
import IR.Tree.TagExpressionList;
import IR.*;
import java.util.*;
import java.io.*;
import Util.Relation;
import Analysis.TaskStateAnalysis.FlagState;
import Analysis.TaskStateAnalysis.OptionalTaskDescriptor;
import Analysis.TaskStateAnalysis.Predicate;
import Analysis.Locality.LocalityAnalysis;
import Analysis.Locality.LocalityBinding;

public class BuildCode {
    State state;
    Hashtable temptovar;
    Hashtable paramstable;
    Hashtable tempstable;
    Hashtable fieldorder;
    Hashtable flagorder;
    int tag=0;
    String localsprefix="___locals___";
    String paramsprefix="___params___";
    String oidstr="___nextobject___";
    String nextobjstr="___nextobject___";
    String localcopystr="___localcopy___";
    public static boolean GENERATEPRECISEGC=false;
    public static String PREFIX="";
    public static String arraytype="ArrayObject";
    Virtual virtualcalls;
    TypeUtil typeutil;
    private int maxtaskparams=0;
    private int maxcount=0;
    ClassDescriptor[] cdarray;
    TypeDescriptor[] arraytable;
    LocalityAnalysis locality;
    Hashtable<TempDescriptor, TempDescriptor> backuptable;

    public BuildCode(State st, Hashtable temptovar, TypeUtil typeutil) {
	state=st;
	this.temptovar=temptovar;
	paramstable=new Hashtable();	
	tempstable=new Hashtable();
	fieldorder=new Hashtable();
	flagorder=new Hashtable();
	this.typeutil=typeutil;
	virtualcalls=new Virtual(state);
    }

    public BuildCode(State st, Hashtable temptovar, TypeUtil typeutil, LocalityAnalysis locality) {
	this(st, temptovar, typeutil);
	this.locality=locality;
	this.backuptable=new Hashtable<TempDescriptor, TempDescriptor>();
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

	try {
	    outstructs=new PrintWriter(new FileOutputStream(PREFIX+"structdefs.h"), true);
	    outmethodheader=new PrintWriter(new FileOutputStream(PREFIX+"methodheaders.h"), true);
	    outclassdefs=new PrintWriter(new FileOutputStream(PREFIX+"classdefs.h"), true);
	    outmethod=new PrintWriter(new FileOutputStream(PREFIX+"methods.c"), true);
	    outvirtual=new PrintWriter(new FileOutputStream(PREFIX+"virtualtable.h"), true);
	    if (state.TASK) {
		outtask=new PrintWriter(new FileOutputStream(PREFIX+"task.h"), true);
		outtaskdefs=new PrintWriter(new FileOutputStream(PREFIX+"taskdefs.c"), true);
		if (state.OPTIONAL){
		    outoptionalarrays=new PrintWriter(new FileOutputStream(PREFIX+"optionalarrays.c"), true);
		    optionalheaders=new PrintWriter(new FileOutputStream(PREFIX+"optionalstruct.h"), true);
		} 
	    }
	    if (state.structfile!=null) {
		outrepairstructs=new PrintWriter(new FileOutputStream(PREFIX+state.structfile+".struct"), true);
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	    System.exit(-1);
	}

	/* Build the virtual dispatch tables */
	buildVirtualTables(outvirtual);

	/* Output includes */
	outmethodheader.println("#ifndef METHODHEADERS_H");
	outmethodheader.println("#define METHODHEADERS_H");
	outmethodheader.println("#include \"structdefs.h\"");

	/* Output Structures */
	outputStructs(outstructs);

	// Output the C class declarations
	// These could mutually reference each other
	outputClassDeclarations(outclassdefs);

	// Output function prototypes and structures for parameters
	Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    generateCallStructs(cn, outclassdefs, outstructs, outmethodheader);
	}
	outclassdefs.close();

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

	/* Build the actual methods */
	outputMethods(outmethod);

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
	if (state.TASK&&state.OPTIONAL){
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
    }

    /* This code just generates the main C method for java programs.
     * The main C method packs up the arguments into a string array
     * and passes it to the java main method. */

    private void outputMainMethod(PrintWriter outmethod) {
	outmethod.println("int main(int argc, const char *argv[]) {");
	outmethod.println("  int i;");
	if (GENERATEPRECISEGC) {
	    outmethod.println("  struct ArrayObject * stringarray=allocate_newarray(NULL, STRINGARRAYTYPE, argc-1);");
	} else {
	    outmethod.println("  struct ArrayObject * stringarray=allocate_newarray(STRINGARRAYTYPE, argc-1);");
	}
	if (state.THREAD) {
	    outmethod.println("initializethreads();");
	}
	outmethod.println("  for(i=1;i<argc;i++) {");
	outmethod.println("    int length=strlen(argv[i]);");
	if (GENERATEPRECISEGC) {
	    outmethod.println("    struct ___String___ *newstring=NewString(NULL, argv[i], length);");
	} else {
	    outmethod.println("    struct ___String___ *newstring=NewString(argv[i], length);");
	}
	outmethod.println("    ((void **)(((char *)& stringarray->___length___)+sizeof(int)))[i-1]=newstring;");
	outmethod.println("  }");
	
	
	MethodDescriptor md=typeutil.getMain();
	ClassDescriptor cd=typeutil.getMainClass();
	
	outmethod.println("   {");
	if (GENERATEPRECISEGC) {
	    outmethod.print("       struct "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params __parameterlist__={");
	    outmethod.println("1, NULL,"+"stringarray};");
	    outmethod.println("     "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(& __parameterlist__);");
	} else
	    outmethod.println("     "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(stringarray);");
	outmethod.println("   }");
	
	if (state.THREAD) {
	    outmethod.println("pthread_mutex_lock(&gclistlock);");
	    outmethod.println("threadcount--;");
	    outmethod.println("pthread_cond_signal(&gccond);");
	    outmethod.println("pthread_mutex_unlock(&gclistlock);");
	    outmethod.println("pthread_exit(NULL);");
	}
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

    private void outputMethods(PrintWriter outmethod) {
	outmethod.println("#include \"methodheaders.h\"");
	outmethod.println("#include \"virtualtable.h\"");
	outmethod.println("#include <runtime.h>");
	if (state.THREAD)
	    outmethod.println("#include <thread.h>");
	if (state.main!=null) {
	    outmethod.println("#include <string.h>");	    
	}
	if (state.CONSCHECK) {
	    outmethod.println("#include \"checkers.h\"");
	}
	//Store the sizes of classes & array elements
	generateSizeArray(outmethod);
	
	//Store table of supertypes
	generateSuperTypeTable(outmethod);

	//Store the layout of classes
	generateLayoutStructs(outmethod);

	/* Generate code for methods */
	if (state.DSM) {
	    for(Iterator<LocalityBinding> lbit=locality.getLocalityBindings().iterator();lbit.hasNext();) {
		LocalityBinding lb=lbit.next();
		MethodDescriptor md=lb.getMethod();
		FlatMethod fm=state.getMethodFlat(md);
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
		    if (!md.getModifiers().isNative())
			generateFlatMethod(fm, null, outmethod);
		}
	    }
	} 
    }

    private void outputStructs(PrintWriter outstructs) {
	outstructs.println("#ifndef STRUCTDEFS_H");
	outstructs.println("#define STRUCTDEFS_H");
	outstructs.println("#include \"classdefs.h\"");

	/* Output #defines that the runtime uses to determine type
	 * numbers for various objects it needs */

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
	if (state.TASK) {
	    outstructs.println("#define STARTUPTYPE "+typeutil.getClass(TypeUtil.StartupClass).getId());
	    outstructs.println("#define TAGTYPE "+typeutil.getClass(TypeUtil.TagClass).getId());
	    outstructs.println("#define TAGARRAYTYPE "+
			       (state.getArrayNumber(new TypeDescriptor(typeutil.getClass(TypeUtil.TagClass)).makeArray(state))+state.numClasses()));
	}
    }

    private void outputClassDeclarations(PrintWriter outclassdefs) {
	if (state.THREAD)
	    outclassdefs.println("#include <pthread.h>");
	outclassdefs.println("struct "+arraytype+";");
	/* Start by declaring all structs */
	Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    outclassdefs.println("struct "+cn.getSafeSymbol()+";");
	}
	outclassdefs.println("");
	//Print out definition for array type
	outclassdefs.println("struct "+arraytype+" {");
	outclassdefs.println("  int type;");
	if (state.THREAD) {
	    outclassdefs.println("  pthread_t tid;");
	    outclassdefs.println("  void * lockentry;");
	    outclassdefs.println("  int lockcount;");
	}
	if (state.TASK) {
	    outclassdefs.println("  int flag;");
	    outclassdefs.println("  void * flagptr;");
	    if(state.OPTIONAL) 
		outclassdefs.println("  int failedstatus;");
	}
	printClassStruct(typeutil.getClass(TypeUtil.ObjectClass), outclassdefs);
	
	outclassdefs.println("  int ___length___;");
	outclassdefs.println("};\n");
	outclassdefs.println("extern int classsize[];");
	outclassdefs.println("extern int hasflags[];");
	outclassdefs.println("extern unsigned int * pointerarray[];");
	outclassdefs.println("extern int supertypes[];");
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
	outtask.println("int numTotal;");
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
		outrepairstructs.println("  int __flagptr__;");
	    }
	    printRepairStruct(cn, outrepairstructs);
	    outrepairstructs.println("}\n");
	}
	
	for(int i=0;i<state.numArrays();i++) {
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

	for(int i=0;i<fields.size();i++) {
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
    void generateTaskDescriptor(PrintWriter output, FlatMethod fm, TaskDescriptor task) {
	for (int i=0;i<task.numParameters();i++) {
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

	    output.println("int parametertag_"+i+"_"+task.getSafeSymbol()+"[]={");
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
	for (int i=0;i<task.numParameters();i++) {
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

    private void buildVirtualTables(PrintWriter outvirtual) {
    	Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
	while(classit.hasNext()) {
	    ClassDescriptor cd=(ClassDescriptor)classit.next();
	    if (virtualcalls.getMethodCount(cd)>maxcount)
		maxcount=virtualcalls.getMethodCount(cd);
	}
	MethodDescriptor[][] virtualtable=new MethodDescriptor[state.numClasses()+state.numArrays()][maxcount];

	/* Fill in virtual table */
	classit=state.getClassSymbolTable().getDescriptorsIterator();
	while(classit.hasNext()) {
	    ClassDescriptor cd=(ClassDescriptor)classit.next();
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
	for(int i=0;i<state.numClasses()+state.numArrays();i++) {
	    for(int j=0;j<maxcount;j++) {
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

    private void fillinRow(ClassDescriptor cd, MethodDescriptor[][] virtualtable, int rownum) {
	/* Get inherited methods */
	if (cd.getSuperDesc()!=null)
	    fillinRow(cd.getSuperDesc(), virtualtable, rownum);
	/* Override them with our methods */
	for(Iterator it=cd.getMethods();it.hasNext();) {
	    MethodDescriptor md=(MethodDescriptor)it.next();
	    if (md.isStatic()||md.getReturnType()==null)
		continue;
	    int methodnum=virtualcalls.getMethodNumber(md);
	    virtualtable[rownum][methodnum]=md;
	}
    }

    /** Generate array that contains the sizes of class objects.  The
     * object allocation functions in the runtime use this
     * information. */

    private void generateSizeArray(PrintWriter outclassdefs) {
	outclassdefs.print("int classsize[]={");
	Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
	cdarray=new ClassDescriptor[state.numClasses()];
	while(it.hasNext()) {
	    ClassDescriptor cd=(ClassDescriptor)it.next();
	    cdarray[cd.getId()]=cd;
	}
	boolean needcomma=false;
	for(int i=0;i<state.numClasses();i++) {
	    if (needcomma)
		outclassdefs.print(", ");
	    outclassdefs.print("sizeof(struct "+cdarray[i].getSafeSymbol()+")");	    
	    needcomma=true;
	}

	arraytable=new TypeDescriptor[state.numArrays()];

	Iterator arrayit=state.getArrayIterator();
	while(arrayit.hasNext()) {
	    TypeDescriptor td=(TypeDescriptor)arrayit.next();
	    int id=state.getArrayNumber(td);
	    arraytable[id]=td;
	}
	
	for(int i=0;i<state.numArrays();i++) {
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
    }

    /** Constructs params and temp objects for each method or task.
     * These objects tell the compiler which temps need to be
     * allocated.  */

    private void generateTempStructs(FlatMethod fm, LocalityBinding lb) {
	MethodDescriptor md=fm.getMethod();
	TaskDescriptor task=fm.getTask();
	Set<TempDescriptor> saveset=state.DSM?locality.getTempSet(lb):null;
	ParamsObject objectparams=md!=null?new ParamsObject(md,tag++):new ParamsObject(task, tag++);

	if (md!=null)
	    paramstable.put(md, objectparams);
	else
	    paramstable.put(task, objectparams);

	for(int i=0;i<fm.numParameters();i++) {
	    TempDescriptor temp=fm.getParameter(i);
	    TypeDescriptor type=temp.getType();
	    if ((type.isPtr()||type.isArray())&&GENERATEPRECISEGC)
		objectparams.addPtr(temp);
	    else
		objectparams.addPrim(temp);
	    if(state.DSM&&saveset.contains(temp)) {
		backuptable.put(temp, temp.createNew());
	    }
	}

	for(int i=0;i<fm.numTags();i++) {
	    TempDescriptor temp=fm.getTag(i);
	    if (GENERATEPRECISEGC)
		objectparams.addPtr(temp);
	    else
		objectparams.addPrim(temp);
	}

	TempObject objecttemps=md!=null?new TempObject(objectparams,md,tag++):new TempObject(objectparams, task, tag++);
	if (md!=null)
	    tempstable.put(md, objecttemps);
	else
	    tempstable.put(task, objecttemps);

	for(Iterator nodeit=fm.getNodeSet().iterator();nodeit.hasNext();) {
	    FlatNode fn=(FlatNode)nodeit.next();
	    TempDescriptor[] writes=fn.writesTemps();
	    for(int i=0;i<writes.length;i++) {
		TempDescriptor temp=writes[i];
		TypeDescriptor type=temp.getType();
		if ((type.isPtr()||type.isArray())&&GENERATEPRECISEGC)
		    objecttemps.addPtr(temp);
		else
		    objecttemps.addPrim(temp);
		if(state.DSM&&saveset.contains(temp)&&
		   !backuptable.containsKey(temp))
		    backuptable.put(temp, temp.createNew());
	    }
	}

	/* Create backup temps */
	if (state.DSM)
	    for(Iterator<TempDescriptor> tmpit=backuptable.values().iterator();tmpit.hasNext();) {
		TempDescriptor tmp=tmpit.next();
		TypeDescriptor type=tmp.getType();
		if ((type.isPtr()||type.isArray())&&GENERATEPRECISEGC)
		    objecttemps.addPtr(tmp);
		else
		    objecttemps.addPrim(tmp);
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
	    output.println("unsigned int "+cn.getSafeSymbol()+"_pointers[]={");
	    Iterator allit=cn.getFieldTable().getAllDescriptorsIterator();
	    int count=0;
	    while(allit.hasNext()) {
		FieldDescriptor fd=(FieldDescriptor)allit.next();
		TypeDescriptor type=fd.getType();
		if (type.isPtr()||type.isArray())
		    count++;
	    }
	    output.print(count);
	    allit=cn.getFieldTable().getAllDescriptorsIterator();
	    while(allit.hasNext()) {
		FieldDescriptor fd=(FieldDescriptor)allit.next();
		TypeDescriptor type=fd.getType();
		if (type.isPtr()||type.isArray()) {
		    output.println(",");
		    output.print("((unsigned int)&(((struct "+cn.getSafeSymbol() +" *)0)->"+fd.getSafeSymbol()+"))");
		}
	    }
	    output.println("};");
	}
	output.println("unsigned int * pointerarray[]={");
	boolean needcomma=false;
	for(int i=0;i<state.numClasses();i++) {
	    ClassDescriptor cn=cdarray[i];
	    if (needcomma)
		output.println(",");
	    needcomma=true;
	    output.print(cn.getSafeSymbol()+"_pointers");
	}

	for(int i=0;i<state.numArrays();i++) {
	    if (needcomma)
		output.println(", ");
	    TypeDescriptor tdelement=arraytable[i].dereference();
	    if (tdelement.isArray()||tdelement.isClass())
		output.print("((int *)1)");
	    else
		output.print("0");
	    needcomma=true;
	}
	
	output.println("};");
	needcomma=false;
	output.println("int hasflags[]={");
	for(int i=0;i<state.numClasses();i++) {
	    ClassDescriptor cn=cdarray[i];
	    if (needcomma)
		output.println(", ");
	    needcomma=true;
	    if (cn.hasFlags())
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
	for(int i=0;i<state.numClasses();i++) {
	    ClassDescriptor cn=cdarray[i];
	    if (needcomma)
		output.println(",");
	    needcomma=true;
	    if (cn.getSuperDesc()!=null) {
		ClassDescriptor cdsuper=cn.getSuperDesc();
		output.print(cdsuper.getId());
	    } else
		output.print("-1");
	}
	output.println("};");
    }

    /** Force consistent field ordering between inherited classes. */

    private void printClassStruct(ClassDescriptor cn, PrintWriter classdefout) {
	ClassDescriptor sp=cn.getSuperDesc();
	if (sp!=null)
	    printClassStruct(sp, classdefout);
	
	if (!fieldorder.containsKey(cn)) {
	    Vector fields=new Vector();
	    fieldorder.put(cn,fields);
	    Iterator fieldit=cn.getFields();
	    while(fieldit.hasNext()) {
		FieldDescriptor fd=(FieldDescriptor)fieldit.next();
		if (sp==null||!sp.getFieldTable().contains(fd.getSymbol()))
		    fields.add(fd);
	    }
	}
	Vector fields=(Vector)fieldorder.get(cn);

	for(int i=0;i<fields.size();i++) {
	    FieldDescriptor fd=(FieldDescriptor)fields.get(i);
	    if (fd.getType().isClass()||fd.getType().isArray())
		classdefout.println("  struct "+fd.getType().getSafeSymbol()+" * "+fd.getSafeSymbol()+";");
	    else 
		classdefout.println("  "+fd.getType().getSafeSymbol()+" "+fd.getSafeSymbol()+";");
	}
    }


    /* Map flags to integers consistently between inherited
     * classes. */

    private void mapFlags(ClassDescriptor cn) {
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

    private void generateCallStructs(ClassDescriptor cn, PrintWriter classdefout, PrintWriter output, PrintWriter headersout) {
	/* Output class structure */
	classdefout.println("struct "+cn.getSafeSymbol()+" {");
	classdefout.println("  int type;");
	if (state.THREAD) {
	    classdefout.println("  pthread_t tid;");
	    classdefout.println("  void * lockentry;");
	    classdefout.println("  int lockcount;");
	}

	if (state.TASK) {
	    classdefout.println("  int flag;");
	    classdefout.println("  void * flagptr;");
	    if (state.OPTIONAL) classdefout.println("  int failedstatus;");
	}
	printClassStruct(cn, classdefout);
	classdefout.println("};\n");

	if (state.DSM) {
	    /* Cycle through LocalityBindings */
	    for(Iterator<LocalityBinding> lbit=locality.getClassBindings(cn).iterator();lbit.hasNext();) {
		LocalityBinding lb=lbit.next();
		MethodDescriptor md=lb.getMethod();
		generateMethod(cn, md, lb, headersout, output);
	    }
	} else {
	    /* Cycle through methods */
	    for(Iterator methodit=cn.getMethods();methodit.hasNext();) {
		/* Classify parameters */
		MethodDescriptor md=(MethodDescriptor)methodit.next();
		generateMethod(cn, md, null, headersout, output);
	    }
	}
    }

    private void generateMethod(ClassDescriptor cn, MethodDescriptor md, LocalityBinding lb, PrintWriter headersout, PrintWriter output) {
	FlatMethod fm=state.getMethodFlat(md);
	generateTempStructs(fm, null);
	
	ParamsObject objectparams=(ParamsObject) paramstable.get(md);
	TempObject objecttemps=(TempObject) tempstable.get(md);
	
	/* Output parameter structure */
	if (GENERATEPRECISEGC) {
	    output.println("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params {");
	    output.println("  int size;");
	    output.println("  void * next;");
	    for(int i=0;i<objectparams.numPointers();i++) {
		TempDescriptor temp=objectparams.getPointer(i);
		output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
	    }
	    output.println("};\n");
	}
	
	/* Output temp structure */
	if (GENERATEPRECISEGC) {
	    output.println("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_locals {");
	    output.println("  int size;");
	    output.println("  void * next;");
	    for(int i=0;i<objecttemps.numPointers();i++) {
		TempDescriptor temp=objecttemps.getPointer(i);
		if (temp.getType().isNull())
		    output.println("  void * "+temp.getSafeSymbol()+";");
		else
		    output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
	    }
	    output.println("};\n");
	}
	
	/********* Output method declaration ***********/

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
	if (state.DSM) {
	    headersout.print(cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");
	} else
	    headersout.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");
	
	boolean printcomma=false;
	if (GENERATEPRECISEGC) {
	    headersout.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * "+paramsprefix);
	    printcomma=true;
	}
	
	if (state.DSM&&lb.isAtomic()) {
	    if (printcomma)
		headersout.print(", ");
	    headersout.print("transrecord_t * trans");
	    printcomma=true;
	}

	/*  Output parameter list*/
	for(int i=0;i<objectparams.numPrimitives();i++) {
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
	    if (GENERATEPRECISEGC) {
		output.println("struct "+task.getSafeSymbol()+"_params {");

		output.println("  int size;");
		output.println("  void * next;");
		for(int i=0;i<objectparams.numPointers();i++) {
		    TempDescriptor temp=objectparams.getPointer(i);
		    output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
		}

		output.println("};\n");
		if ((objectparams.numPointers()+fm.numTags())>maxtaskparams) {
		    maxtaskparams=objectparams.numPointers()+fm.numTags();
		}
	    }

	    /* Output temp structure */
	    if (GENERATEPRECISEGC) {
		output.println("struct "+task.getSafeSymbol()+"_locals {");
		output.println("  int size;");
		output.println("  void * next;");
		for(int i=0;i<objecttemps.numPointers();i++) {
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
	    if (GENERATEPRECISEGC) {
		headersout.print("struct "+task.getSafeSymbol()+"_params * "+paramsprefix);
	    } else
		headersout.print("void * parameterarray[]");
	    headersout.println(");\n");
   	}
    }

    /** Generate code for FlatMethod fm. */

    private void generateFlatMethod(FlatMethod fm, LocalityBinding lb, PrintWriter output) {
	MethodDescriptor md=fm.getMethod();
	TaskDescriptor task=fm.getTask();

       	ClassDescriptor cn=md!=null?md.getClassDesc():null;

	ParamsObject objectparams=(ParamsObject)paramstable.get(md!=null?md:task);
	generateHeader(fm, lb, md!=null?md:task,output);
	TempObject objecttemp=(TempObject) tempstable.get(md!=null?md:task);
	if (state.DSM&&lb.getHasAtomic()) {
	    output.println("transrecord_t * trans;");
	}

	if (GENERATEPRECISEGC) {
	    if (md!=null)
		output.print("   struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_locals "+localsprefix+"={");
	    else
		output.print("   struct "+task.getSafeSymbol()+"_locals "+localsprefix+"={");

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
		output.println("   void * "+td.getSafeSymbol()+";");
	    else if (type.isClass()||type.isArray())
		output.println("   struct "+type.getSafeSymbol()+" * "+td.getSafeSymbol()+";");
	    else
		output.println("   "+type.getSafeSymbol()+" "+td.getSafeSymbol()+";");
	}

	/* Assign labels to FlatNode's if necessary.*/

	Hashtable<FlatNode, Integer> nodetolabel=assignLabels(fm);

	/* Check to see if we need to do a GC if this is a
	 * multi-threaded program...*/

	if (state.THREAD&&GENERATEPRECISEGC) {
	    output.println("checkcollect(&"+localsprefix+");");
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
	    if (state.INSTRUCTIONFAILURE) {
		if (state.THREAD) {
		    output.println("if ((++instructioncount)>failurecount) {instructioncount=0;injectinstructionfailure();}");
		}
		else
		    output.println("if ((--instructioncount)==0) injectinstructionfailure();");
	    }
	    if (current_node.numNext()==0) {
		output.print("   ");
		generateFlatNode(fm, lb, current_node, output);
		if (current_node.kind()!=FKind.FlatReturnNode) {
		    output.println("   return;");
		}
		current_node=null;
	    } else if(current_node.numNext()==1) {
		output.print("   ");
		generateFlatNode(fm, lb, current_node, output);
		FlatNode nextnode=current_node.getNext(0);
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

	output.println("}\n\n");
    }

    /** This method assigns labels to FlatNodes */

    private Hashtable<FlatNode, Integer> assignLabels(FlatMethod fm) {
	HashSet tovisit=new HashSet();
	HashSet visited=new HashSet();
	int labelindex=0;
	Hashtable<FlatNode, Integer> nodetolabel=new Hashtable<FlatNode, Integer>();
	tovisit.add(fm.getNext(0));

	/*Assign labels first.  A node needs a label if the previous
	 * node has two exits or this node is a join point. */

	while(!tovisit.isEmpty()) {
	    FlatNode fn=(FlatNode)tovisit.iterator().next();
	    tovisit.remove(fn);
	    visited.add(fn);
	    for(int i=0;i<fn.numNext();i++) {
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
    private String generateTemp(FlatMethod fm, TempDescriptor td) {
	MethodDescriptor md=fm.getMethod();
	TaskDescriptor task=fm.getTask();
	TempObject objecttemps=(TempObject) tempstable.get(md!=null?md:task);

	if (objecttemps.isLocalPrim(td)||objecttemps.isParamPrim(td)) {
	    return td.getSafeSymbol();
	}

	if (objecttemps.isLocalPtr(td)) {
	    return localsprefix+"."+td.getSafeSymbol();
	}

	if (objecttemps.isParamPtr(td)) {
	    return paramsprefix+"->"+td.getSafeSymbol();
	}
	throw new Error();
    }

    private void generateFlatNode(FlatMethod fm, LocalityBinding lb, FlatNode fn, PrintWriter output) {
	switch(fn.kind()) {
	case FKind.FlatAtomicEnterNode:
	    generateFlatAtomicEnterNode(fm, lb, (FlatAtomicEnterNode) fn, output);
	    return;
	case FKind.FlatAtomicExitNode:
	    generateFlatAtomicExitNode(fm, lb, (FlatAtomicExitNode) fn, output);
	    return;
	case FKind.FlatTagDeclaration:
	    generateFlatTagDeclaration(fm, (FlatTagDeclaration) fn,output);
	    return;
	case FKind.FlatCall:
	    generateFlatCall(fm, (FlatCall) fn,output);
	    return;
	case FKind.FlatFieldNode:
	    generateFlatFieldNode(fm, lb, (FlatFieldNode) fn,output);
	    return;
	case FKind.FlatElementNode:
	    generateFlatElementNode(fm, (FlatElementNode) fn,output);
	    return;
	case FKind.FlatSetElementNode:
	    generateFlatSetElementNode(fm, (FlatSetElementNode) fn,output);
	    return;
	case FKind.FlatSetFieldNode:
	    generateFlatSetFieldNode(fm, lb, (FlatSetFieldNode) fn,output);
	    return;
	case FKind.FlatNew:
	    generateFlatNew(fm, (FlatNew) fn,output);
	    return;
	case FKind.FlatOpNode:
	    generateFlatOpNode(fm, (FlatOpNode) fn,output);
	    return;
	case FKind.FlatCastNode:
	    generateFlatCastNode(fm, (FlatCastNode) fn,output);
	    return;
	case FKind.FlatLiteralNode:
	    generateFlatLiteralNode(fm, (FlatLiteralNode) fn,output);
	    return;
	case FKind.FlatReturnNode:
	    generateFlatReturnNode(fm, (FlatReturnNode) fn,output);
	    return;
	case FKind.FlatNop:
	    output.println("/* nop */");
	    return;
	case FKind.FlatBackEdge:
	    if (state.THREAD&&GENERATEPRECISEGC) {
		output.println("checkcollect(&"+localsprefix+");");
	    } else
		output.println("/* nop */");
	    return;
	case FKind.FlatCheckNode:
	    generateFlatCheckNode(fm, (FlatCheckNode) fn, output);
	    return;
	case FKind.FlatFlagActionNode:
	    generateFlatFlagActionNode(fm, (FlatFlagActionNode) fn, output);
	    return;
	}
	throw new Error();

    }
    
    public void generateFlatAtomicEnterNode(FlatMethod fm,  LocalityBinding lb, FlatAtomicEnterNode faen, PrintWriter output) {
	/* Check to see if we need to generate code for this atomic */
	if (locality.getAtomic(lb).get(faen.getPrev(0)).intValue()>0)
	    return;
	/* Backup the temps. */
	for(Iterator<TempDescriptor> tmpit=locality.getTemps(lb).get(faen).iterator();tmpit.hasNext();) {
	    TempDescriptor tmp=tmpit.next();
	    output.println(generateTemp(fm, backuptable.get(tmp))+"="+generateTemp(fm,tmp)+";");
	}
	output.println("goto transstart"+faen.getIdentifier()+";");

	/******* Print code to retry aborted transaction *******/
	output.println("transretry"+faen.getIdentifier()+":");

	/* Restore temps */
	for(Iterator<TempDescriptor> tmpit=locality.getTemps(lb).get(faen).iterator();tmpit.hasNext();) {
	    TempDescriptor tmp=tmpit.next();
	    output.println(generateTemp(fm, tmp)+"="+generateTemp(fm,backuptable.get(tmp))+";");
	}

	/* Need to revert local object store */

	/******* Tell the runtime to start the transaction *******/
	
	output.println("transstart"+faen.getIdentifier()+":");
	output.println("trans=transStart();");
    }

    public void generateFlatAtomicExitNode(FlatMethod fm,  LocalityBinding lb, FlatAtomicExitNode faen, PrintWriter output) {
	/* Check to see if we need to generate code for this atomic */
	if (locality.getAtomic(lb).get(faen).intValue()>0)
	    return;
	output.println("if (transCommit(trans))");
	/* Transaction aborts if it returns true */
	output.println("goto transretry"+faen.getAtomicEnter().getIdentifier()+";");
	/* Need to commit local object store */
	//TODO
    }

    private void generateFlatCheckNode(FlatMethod fm,  FlatCheckNode fcn, PrintWriter output) {
	if (state.CONSCHECK) {
	    String specname=fcn.getSpec();
	    String varname="repairstate___";
	    output.println("{");
	    output.println("struct "+specname+"_state * "+varname+"=allocate"+specname+"_state();");

	    TempDescriptor[] temps=fcn.getTemps();
	    String[] vars=fcn.getVars();
	    for(int i=0;i<temps.length;i++) {
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

    private void generateFlatCall(FlatMethod fm, FlatCall fc, PrintWriter output) {
	MethodDescriptor md=fc.getMethod();
	ParamsObject objectparams=(ParamsObject) paramstable.get(md);
	ClassDescriptor cn=md.getClassDesc();
	output.println("{");
	if (GENERATEPRECISEGC) {
	    output.print("       struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params __parameterlist__={");
	    
	    output.print(objectparams.numPointers());
	    //	    output.print(objectparams.getUID());
	    output.print(", & "+localsprefix);
	    if (fc.getThis()!=null) {
		output.print(", ");
		output.print("(struct "+md.getThis().getType().getSafeSymbol() +" *)"+ generateTemp(fm,fc.getThis()));
	    }
	    for(int i=0;i<fc.numArgs();i++) {
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
	    output.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor());
	} else {
	    output.print("((");
	    if (md.getReturnType().isClass()||md.getReturnType().isArray())
		output.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
	    else
		output.print(md.getReturnType().getSafeSymbol()+" ");
	    output.print("(*)(");

	    boolean printcomma=false;
	    if (GENERATEPRECISEGC) {
		output.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * ");
		printcomma=true;
	    }

	    for(int i=0;i<objectparams.numPrimitives();i++) {
		TempDescriptor temp=objectparams.getPrimitive(i);
		if (printcomma)
		    output.print(", ");
		printcomma=true;
		if (temp.getType().isClass()||temp.getType().isArray())
		    output.print("struct " + temp.getType().getSafeSymbol()+" * ");
		else
		    output.print(temp.getType().getSafeSymbol());
	    }

	    output.print("))virtualtable["+generateTemp(fm,fc.getThis())+"->type*"+maxcount+"+"+virtualcalls.getMethodNumber(md)+"])");
	}

	output.print("(");
	boolean needcomma=false;
	if (GENERATEPRECISEGC) {
	    output.print("&__parameterlist__");
	    needcomma=true;
	} else {
	    if (fc.getThis()!=null) {
		TypeDescriptor ptd=md.getThis().getType();
		if (ptd.isClass()&&!ptd.isArray())
		    output.print("(struct "+ptd.getSafeSymbol()+" *) ");
		output.print(generateTemp(fm,fc.getThis()));
		needcomma=true;
	    }
	}
	for(int i=0;i<fc.numArgs();i++) {
	    Descriptor var=md.getParameter(i);
	    TempDescriptor paramtemp=(TempDescriptor)temptovar.get(var);
	    if (objectparams.isParamPrim(paramtemp)) {
		TempDescriptor targ=fc.getArg(i);
		if (needcomma)
		    output.print(", ");

		TypeDescriptor ptd=md.getParamType(i);
		if (ptd.isClass()&&!ptd.isArray())
		    output.print("(struct "+ptd.getSafeSymbol()+" *) ");
		output.print(generateTemp(fm, targ));
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
	for(Iterator classit=subclasses.iterator();classit.hasNext();) {
	    ClassDescriptor cd=(ClassDescriptor)classit.next();
	    Set possiblematches=cd.getMethodTable().getSet(md.getSymbol());
	    for(Iterator matchit=possiblematches.iterator();matchit.hasNext();) {
		MethodDescriptor matchmd=(MethodDescriptor)matchit.next();
		if (md.matches(matchmd))
		    return false;
	    }
	}
	return true;
    }

    private void generateFlatFieldNode(FlatMethod fm, LocalityBinding lb, FlatFieldNode ffn, PrintWriter output) {
	if (state.DSM) {
	    Integer status=locality.getNodeTempInfo(lb).get(ffn).get(ffn.getSrc());
	    if (status==LocalityAnalysis.GLOBAL) {
		String field=ffn.getField().getSafeSymbol();
		String src="((struct "+ffn.getSrc().getType().getSafeSymbol()+" *)((unsigned int)"+generateTemp(fm, ffn.getSrc())+"+sizeof(objheader_t)))";
		String dst=generateTemp(fm, ffn.getDst());
		    
		if (ffn.getField().getType().isPtr()||
		    ffn.getField().getType().isArray()) {

		    //TODO: Uncomment this when we have runtime support
		    //if (ffn.getSrc()==ffn.getDst()) {
		    //output.println("{");
		    //output.println("void * temp="+src+";");
		    //output.println("if (temp&0x1) {");
		    //output.println("temp=transRead(trans, temp);");
		    //output.println(src+"->"+field+"="+temp+";");
		    //output.println("}");
		    //output.println(dst+"=temp;");
		    //output.println("}");
		    //} else {
		    output.println(dst+"="+ src +"->"+field+ ";");
		    //output.println("if ("+dst+"&0x1) {");
		    output.println(dst+"=transRead(trans,"+dst+");");
		    //output.println(src+"->"+field+"="+src+"->"+field+";");
		    //output.println("}");
		    //}
		} else {
		    output.println(dst+"="+ src+"->"+field+";");
		}
	    } else if (status==LocalityAnalysis.LOCAL) {
		output.println(generateTemp(fm, ffn.getDst())+"="+ generateTemp(fm,ffn.getSrc())+"->"+ ffn.getField().getSafeSymbol()+";");
	    } else if (status==LocalityAnalysis.EITHER) {
		//Code is reading from a null pointer
 		output.println("if ("+generateTemp(fm, ffn.getSrc())+") {");
		output.println("printf(\"BIG ERROR\n\");exit(-1);}");
		//This should throw a suitable null pointer error
		output.println(generateTemp(fm, ffn.getDst())+"="+ generateTemp(fm,ffn.getSrc())+"->"+ ffn.getField().getSafeSymbol()+";");
	    } else
		throw new Error("Read from non-global/non-local in:"+lb.getExplanation());
	} else
	    output.println(generateTemp(fm, ffn.getDst())+"="+ generateTemp(fm,ffn.getSrc())+"->"+ ffn.getField().getSafeSymbol()+";");
    }

    private void generateFlatSetFieldNode(FlatMethod fm, LocalityBinding lb, FlatSetFieldNode fsfn, PrintWriter output) {
	if (fsfn.getField().getSymbol().equals("length")&&fsfn.getDst().getType().isArray())
	    throw new Error("Can't set array length");
	if (state.DSM) {
	    Integer statussrc=locality.getNodeTempInfo(lb).get(fsfn).get(fsfn.getDst());
	    Integer statusdst=locality.getNodeTempInfo(lb).get(fsfn).get(fsfn.getDst());
	    boolean srcglobal=statusdst==LocalityAnalysis.GLOBAL;

	    String src=generateTemp(fm,fsfn.getSrc());
	    String dst=generateTemp(fm,fsfn.getDst());
	    if (srcglobal) {
		output.println("{");
		output.println("int srcoid="+src+"->"+oidstr+";");
	    }
	    if (statusdst.equals(LocalityAnalysis.GLOBAL)) {
		String glbdst="(struct "+fsfn.getDst().getType().getSafeSymbol()+" *)((unsigned int)"+dst+" +sizeof(objheader_t)))";
		//mark it dirty
		output.println("((objheader_t *)"+dst+")->status|=DIRTY;");
		if (srcglobal)
		    output.println(glbdst+"->"+ fsfn.getField().getSafeSymbol()+"=srcoid;");
		else
		    output.println(glbdst+"->"+ fsfn.getField().getSafeSymbol()+"="+ src+";");		
	    } else if (statusdst.equals(LocalityAnalysis.LOCAL)) {
		/** Check if we need to copy */
		output.println("if(!"+dst+"->"+localcopystr+") {");
		/* Link object into list */
		output.println(dst+"->"+nextobjstr+"=trans->localtrans;");
		output.println("trans->localtrans="+dst+";");
		output.println("OBJECT_COPY("+dst+");");
		output.println("}");
		if (srcglobal)
		    output.println(dst+"->"+ fsfn.getField().getSafeSymbol()+"=srcoid;");
		else
		    output.println(dst+"->"+ fsfn.getField().getSafeSymbol()+"="+ src+";");
	    } else if (statusdst.equals(LocalityAnalysis.EITHER)) {
		//writing to a null...bad
		output.println("if ("+dst+") {");
		output.println("printf(\"BIG ERROR 2\n\");exit(-1);}");
		if (srcglobal)
		    output.println(dst+"->"+ fsfn.getField().getSafeSymbol()+"=srcoid;");
		else
		    output.println(dst+"->"+ fsfn.getField().getSafeSymbol()+"="+ src+";");
	    }
	    if (srcglobal) {
		output.println("}");
	    }
	} else {
	    output.println(generateTemp(fm, fsfn.getDst())+"->"+ fsfn.getField().getSafeSymbol()+"="+ generateTemp(fm,fsfn.getSrc())+";");
	}
    }

    private void generateFlatElementNode(FlatMethod fm, FlatElementNode fen, PrintWriter output) {
	TypeDescriptor elementtype=fen.getSrc().getType().dereference();
	String type="";

	if (elementtype.isArray()||elementtype.isClass())
	    type="void *";
	else 
	    type=elementtype.getSafeSymbol()+" ";

	if (fen.needsBoundsCheck()) {
	    output.println("if ("+generateTemp(fm, fen.getIndex())+"< 0 || "+generateTemp(fm, fen.getIndex())+" >= "+generateTemp(fm,fen.getSrc()) + "->___length___)");
	    output.println("failedboundschk();");
	}

	output.println(generateTemp(fm, fen.getDst())+"=(("+ type+"*)(((char *) &("+ generateTemp(fm,fen.getSrc())+"->___length___))+sizeof(int)))["+generateTemp(fm, fen.getIndex())+"];");
    }

    private void generateFlatSetElementNode(FlatMethod fm, FlatSetElementNode fsen, PrintWriter output) {
	//TODO: need dynamic check to make sure this assignment is actually legal
	//Because Object[] could actually be something more specific...ie. Integer[]

	TypeDescriptor elementtype=fsen.getDst().getType().dereference();
	String type="";

	if (elementtype.isArray()||elementtype.isClass())
	    type="void *";
	else 
	    type=elementtype.getSafeSymbol()+" ";

	if (fsen.needsBoundsCheck()) {
	    output.println("if ("+generateTemp(fm, fsen.getIndex())+"< 0 || "+generateTemp(fm, fsen.getIndex())+" >= "+generateTemp(fm,fsen.getDst()) + "->___length___)");
	    output.println("failedboundschk();");
	}

	output.println("(("+type +"*)(((char *) &("+ generateTemp(fm,fsen.getDst())+"->___length___))+sizeof(int)))["+generateTemp(fm, fsen.getIndex())+"]="+generateTemp(fm,fsen.getSrc())+";");
    }

    private void generateFlatNew(FlatMethod fm, FlatNew fn, PrintWriter output) {
	if (fn.getType().isArray()) {
	    int arrayid=state.getArrayNumber(fn.getType())+state.numClasses();
	    if (GENERATEPRECISEGC) {
		output.println(generateTemp(fm,fn.getDst())+"=allocate_newarray(&"+localsprefix+", "+arrayid+", "+generateTemp(fm, fn.getSize())+");");
	    } else {
		output.println(generateTemp(fm,fn.getDst())+"=allocate_newarray("+arrayid+", "+generateTemp(fm, fn.getSize())+");");
	    }
	} else {
	    if (GENERATEPRECISEGC) {
		output.println(generateTemp(fm,fn.getDst())+"=allocate_new(&"+localsprefix+", "+fn.getType().getClassDesc().getId()+");");
	    } else {
		output.println(generateTemp(fm,fn.getDst())+"=allocate_new("+fn.getType().getClassDesc().getId()+");");
	    }
	}
    }

    private void generateFlatTagDeclaration(FlatMethod fm, FlatTagDeclaration fn, PrintWriter output) {
	if (GENERATEPRECISEGC) {
	    output.println(generateTemp(fm,fn.getDst())+"=allocate_tag(&"+localsprefix+", "+state.getTagId(fn.getType())+");");
	} else {
	    output.println(generateTemp(fm,fn.getDst())+"=allocate_tag("+state.getTagId(fn.getType())+");");
	}
    }

    private void generateFlatOpNode(FlatMethod fm, FlatOpNode fon, PrintWriter output) {
	if (fon.getRight()!=null)
	    output.println(generateTemp(fm, fon.getDest())+" = "+generateTemp(fm, fon.getLeft())+fon.getOp().toString()+generateTemp(fm,fon.getRight())+";");
	else if (fon.getOp().getOp()==Operation.ASSIGN)
	    output.println(generateTemp(fm, fon.getDest())+" = "+generateTemp(fm, fon.getLeft())+";");
	else if (fon.getOp().getOp()==Operation.UNARYPLUS)
	    output.println(generateTemp(fm, fon.getDest())+" = "+generateTemp(fm, fon.getLeft())+";");
	else if (fon.getOp().getOp()==Operation.UNARYMINUS)
	    output.println(generateTemp(fm, fon.getDest())+" = -"+generateTemp(fm, fon.getLeft())+";");
	else if (fon.getOp().getOp()==Operation.LOGIC_NOT)
	    output.println(generateTemp(fm, fon.getDest())+" = !"+generateTemp(fm, fon.getLeft())+";");
	else
	    output.println(generateTemp(fm, fon.getDest())+fon.getOp().toString()+generateTemp(fm, fon.getLeft())+";");
    }

    private void generateFlatCastNode(FlatMethod fm, FlatCastNode fcn, PrintWriter output) {
	/* TODO: Do type check here */
	if (fcn.getType().isArray()) {
	    throw new Error();
	} else if (fcn.getType().isClass())
	    output.println(generateTemp(fm,fcn.getDst())+"=(struct "+fcn.getType().getSafeSymbol()+" *)"+generateTemp(fm,fcn.getSrc())+";");
	else
	    output.println(generateTemp(fm,fcn.getDst())+"=("+fcn.getType().getSafeSymbol()+")"+generateTemp(fm,fcn.getSrc())+";");
    }

    private void generateFlatLiteralNode(FlatMethod fm, FlatLiteralNode fln, PrintWriter output) {
	if (fln.getValue()==null)
	    output.println(generateTemp(fm, fln.getDst())+"=0;");
	else if (fln.getType().getSymbol().equals(TypeUtil.StringClass)) {
	    if (GENERATEPRECISEGC) {
		output.println(generateTemp(fm, fln.getDst())+"=NewString(&"+localsprefix+", \""+FlatLiteralNode.escapeString((String)fln.getValue())+"\","+((String)fln.getValue()).length()+");");
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
	} else
	    output.println(generateTemp(fm, fln.getDst())+"="+fln.getValue()+";");
    }

    private void generateFlatReturnNode(FlatMethod fm, FlatReturnNode frn, PrintWriter output) {
	if (frn.getReturnTemp()!=null)
	    output.println("return "+generateTemp(fm, frn.getReturnTemp())+";");
	else
	    output.println("return;");
    }

    private void generateFlatCondBranch(FlatMethod fm, FlatCondBranch fcb, String label, PrintWriter output) {
	output.println("if (!"+generateTemp(fm, fcb.getTest())+") goto "+label+";");
    }

    /** This method generates header information for the method or
     * task referenced by the Descriptor des. */

    private void generateHeader(FlatMethod fm, LocalityBinding lb, Descriptor des, PrintWriter output) {
	/* Print header */
	ParamsObject objectparams=(ParamsObject)paramstable.get(des);
	MethodDescriptor md=null;
	TaskDescriptor task=null;
	if (des instanceof MethodDescriptor)
	    md=(MethodDescriptor) des;
	else
	    task=(TaskDescriptor) des;

	ClassDescriptor cn=md!=null?md.getClassDesc():null;
	
	if (md!=null&&md.getReturnType()!=null) {
	    if (md.getReturnType().isClass()||md.getReturnType().isArray())
		output.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
	    else
		output.print(md.getReturnType().getSafeSymbol()+" ");
	} else 
	    //catch the constructor case
	    output.print("void ");
	if (md!=null) {
	    if (state.DSM) {
		output.print(cn.getSafeSymbol()+lb.getSignature()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");
	    } else
		output.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");
	} else
	    output.print(task.getSafeSymbol()+"(");
	
	boolean printcomma=false;
	if (GENERATEPRECISEGC) {
	    if (md!=null)
		output.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * "+paramsprefix);
	    else
		output.print("struct "+task.getSafeSymbol()+"_params * "+paramsprefix);
	    printcomma=true;
	}

	if (state.DSM&&lb.isAtomic()) {
	    if (printcomma)
		output.print(", ");
	    output.print("transrecord_t * trans");
	    printcomma=true;
	}

	if (md!=null) {
	    /* Method */
	    for(int i=0;i<objectparams.numPrimitives();i++) {
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
	} else if (!GENERATEPRECISEGC) {
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
		    if (GENERATEPRECISEGC) 
			output.println("tagclear(&"+localsprefix+", (struct ___Object___ *)"+generateTemp(fm, temp)+", "+generateTemp(fm,tagtmp)+");");
		    else
			output.println("tagclear((struct ___Object___ *)"+generateTemp(fm, temp)+", "+generateTemp(fm,tagtmp)+");");
		}
	    }

	    tagtmps=tagsettable.get(temp);
	    if (tagtmps!=null) {
		Iterator tagit=tagtmps.iterator();
		while(tagit.hasNext()) {
		    TempDescriptor tagtmp=(TempDescriptor)tagit.next();
		    if (GENERATEPRECISEGC)
			output.println("tagset(&"+localsprefix+", (struct ___Object___ *)"+generateTemp(fm, temp)+", "+generateTemp(fm,tagtmp)+");");
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
	    if (ffan.getTaskType()==FlatFlagActionNode.NEWOBJECT) {
		output.println("flagorandinit("+generateTemp(fm, temp)+", 0x"+Integer.toHexString(ormask)+", 0x"+Integer.toHexString(andmask)+");");
	    } else {
		output.println("flagorand("+generateTemp(fm, temp)+", 0x"+Integer.toHexString(ormask)+", 0x"+Integer.toHexString(andmask)+");");
	    }
	}
    }

     void generateOptionalArrays(PrintWriter output, PrintWriter headers, Hashtable<ClassDescriptor, Hashtable<FlagState, HashSet>> safeexecution, Hashtable optionaltaskdescriptors) {
	 
	 //GENERATE HEADERS
	 headers.println("#include \"task.h\"\n\n");
	 
	 
	 //STRUCT PREDICATEMEMBER
	 headers.println("struct predicatemember{");
	 headers.println("int type;");
	 headers.println("int numdnfterms;");
	 headers.println("int * flags;");
	 headers.println("int numtags;");
	 headers.println("int * tags;\n};\n\n");

	 //STRUCT EXITFLAGSTATE
	 headers.println("struct exitflagstate{");
	 headers.println("int numflags;");
	 headers.println("int * flags;");
	 /*
	   headers.println("int numtags;");
	   headers.println("int * tags;");
	 */
	 headers.println("\n};\n\n");
	 
	 //STRUCT EXITSTATES
	 headers.println("struct exitstates{");
	 headers.println("int numexitflagstates;");
	 headers.println("struct exitflagstate * exitflagstatearray;\n};\n\n");

	 //STRUCT OPTIONALTASKDESCRIPTOR
	 headers.println("struct optionaltaskdescriptor{");
	 headers.println("struct taskdescriptor * task;");
	 headers.println("int numpredicatemembers;");
	 headers.println("struct predicatemember * predicatememberarray;");
	 headers.println("int numexitstates;");
	 headers.println("struct existates * exitstatesarray;\n};\n\n");
	 	 
	 //STRUCT FSANALYSISWRAPPER
	 headers.println("struct fsanalysiswrapper{");
	 headers.println("int numflags;");
	 headers.println("int * flags;");
	 headers.println("int numtags;");
	 headers.println("int * tags;");
	 headers.println("int numoptionaltaskdescriptors;");
	 headers.println("struct optionaltaskdescriptor * optionaltaskdescriptorarray;\n};\n\n");

	 //STRUCT CLASSANALYSISWRAPPER
	 headers.println("struct classanalyiswrapper{");
	 headers.println("int type;");
	 headers.println("int numfsanalysiswrappers;");
	 headers.println("struct fsanalysiswrapper * fsanalysiswrapperarray;\n};\n\n");

	 Iterator taskit=state.getTaskSymbolTable().getDescriptorsIterator();
	 while(taskit.hasNext()) {
	     TaskDescriptor td=(TaskDescriptor)taskit.next();
	     headers.println("extern struct taskdescriptor task_"+td.getSafeSymbol()+";");
	 }
	 
						   
	 
	 //GENERATE STRUCTS
	 output.println("#include \"optionalstruct.h\"\n\n");	 
	 HashSet processedcd = new HashSet();
	

	 Enumeration e = safeexecution.keys();
	 while (e.hasMoreElements()) {
	     
	     //get the class
	     ClassDescriptor cdtemp=(ClassDescriptor)e.nextElement();
	     Hashtable flaginfo=(Hashtable)flagorder.get(cdtemp);//will be used several times
	     
	     //Generate the struct of optionals
	     if((Hashtable)optionaltaskdescriptors.get(cdtemp)==null) System.out.println("Was in cd :"+cdtemp.getSymbol());
	     Collection c_otd = ((Hashtable)optionaltaskdescriptors.get(cdtemp)).values();
	     if( !c_otd.isEmpty() ){
		 for(Iterator otd_it = c_otd.iterator(); otd_it.hasNext();){
		     OptionalTaskDescriptor otd = (OptionalTaskDescriptor)otd_it.next();
		     
		     //generate the int arrays for the predicate
		     Predicate predicate = otd.predicate;
		     int predicateindex = 0;
		     //iterate through the classes concerned by the predicate
		     Collection c_vard = predicate.vardescriptors.values();
		     for(Iterator vard_it = c_vard.iterator(); vard_it.hasNext();){
			 VarDescriptor vard = (VarDescriptor)vard_it.next();
			 TypeDescriptor typed = vard.getType();
			 
			 //generate for flags
			 HashSet fen_hashset = predicate.flags.get(vard.getSymbol());
			 output.println("int predicateflags_"+predicateindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"[]={");
			 int numberterms=0;
			 if (fen_hashset!=null){
			     for (Iterator fen_it = fen_hashset.iterator(); fen_it.hasNext();){
				 FlagExpressionNode fen = (FlagExpressionNode)fen_it.next();
				 if (fen==null) {
				     //output.println("0x0, 0x0 };");
				     //numberterms+=1;
				 }
				 else {
				     
				     DNFFlag dflag=fen.getDNF();
				     numberterms+=dflag.size();
				     
				     Hashtable flags=(Hashtable)flagorder.get(typed.getClassDesc());
				     
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
					 output.print("/*andmask*/0x"+Integer.toHexString(andmask)+", /*checkmask*/0x"+Integer.toHexString(checkmask));
				     }
				 }
			     }
			 }
			 output.println("};\n");
			 
			 //generate for tags
			 TagExpressionList tagel = predicate.tags.get(vard.getSymbol()); 
			 output.println("int predicatetags_"+predicateindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"[]={");
			 //BUG...added next line to fix, test with any task program
			 int numtags = 0;
			 if (tagel!=null){
			     for(int j=0;j<tagel.numTags();j++) {
				 if (j!=0)
				     output.println(",");
				 /* for each tag we need */
				 /* which slot it is */
				 /* what type it is */
				 //TagVarDescriptor tvd=(TagVarDescriptor)task.getParameterTable().get(tagel.getName(j));
				 TempDescriptor tmp=tagel.getTemp(j);
				 //got rid of slot
				 output.println("/*tagid*/"+state.getTagId(tmp.getTag()));
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
		     for( int j = 0; j<predicateindex; j++){
			 if( j != predicateindex-1)output.println("&predicatemember_"+j+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+",");
			 else output.println("&predicatemember_"+j+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"};\n");
		     }

		     //generate the struct for possible exitfses
		     HashSet<HashSet> exitfses = otd.exitfses;
		     int exitindex = 0;
		     int nbexit = exitfses.size();
		     int fsnumber;
		     
		     //iterate through possible exits
		     for(Iterator exitfseshash = exitfses.iterator(); exitfseshash.hasNext();){
			 HashSet temp_hashset = (HashSet)exitfseshash.next();
			 fsnumber = 0 ;
			 
			 //iterate through possible FSes corresponding to the exit
			 for(Iterator exfses = temp_hashset.iterator(); exfses.hasNext();){
			    FlagState fs = (FlagState)exfses.next();
			    fsnumber++;
			    output.println("int flags"+fsnumber+"_EXIT"+exitindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"[]={");
			    int counterflag = 0;
			    for(Iterator flags = fs.getFlags(); flags.hasNext();){
				FlagDescriptor flagd = (FlagDescriptor)flags.next();
				int flagid=1<<((Integer)flaginfo.get(flagd)).intValue();
				if( flags.hasNext() ) output.print("0x"+Integer.toHexString(flagid)+" /*"+Integer.toBinaryString(flagid)+"*/,");
				else  output.print("0x"+Integer.toHexString(flagid)+" /*"+Integer.toBinaryString(flagid)+"*/");
				counterflag++;
			    } 
			    output.println("};\n");
			    //do the same for tags;
			    //maybe not needed because no tag changes tolerated.

			    //store the information into a struct
			    output.println("struct exitflagstate exitflagstate"+fsnumber+"_EXIT"+exitindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"={");
			    output.println("/*number of flags*/"+counterflag+",");
			    output.println("flags"+fsnumber+"_EXIT"+exitindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol());
			    output.println("};\n");
			 }
			 
			 //store fses corresponding to this exit into an array
			 output.println("struct exitflagstate * exitflagstatearray"+"_EXIT"+exitindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+" [] = {");
			 for( int j = 0; j<fsnumber; j++){
			     if( j != fsnumber-1)output.println("&exitflagstate"+(j+1)+"_EXIT"+exitindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"'");
			     else output.println("&exitflagstate"+(j+1)+"_EXIT"+exitindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"};\n");
			 }
			 
			 //store that information in a struct
			 output.println("struct exitstates exitstates"+exitindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"={");
			 output.println("/*number of exitflagstate*/"+fsnumber+",");
			 output.println("exitflagstatearray"+"_EXIT"+exitindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol());
			 output.println("};\n");

			 exitindex++;
		     }
		     
		     //store the information concerning all exits into an array
		     output.println("struct exitstates * exitstatesarray_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"[]={");
		     for( int j = 0; j<nbexit; j++){
			 if( j != nbexit-1)output.println("&exitstates"+j+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+",");
			 else output.println("&exitstates"+j+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"};\n");
		     }
		     		     
		     		     
		     //generate optionaltaskdescriptor that actually includes exit fses, predicate and the task concerned
		     output.println("struct optionaltaskdescriptor optionaltaskdescriptor_"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"={");
		     output.println("task_"+otd.td.getSafeSymbol()+",");
		     output.println("/*number of members */"+predicateindex+",");
		     output.println("predicatememberarray_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+",");
		     output.println("/*number of exit fses */"+nbexit+",");
		     output.println("exitstatearray_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol());
		     output.println("};\n");
		 }	
	     }
	     else continue; // if there is no optionals, there is no need to build the rest of the struct 
	     
	     //get all the possible falgstates reachable by an object
	     Hashtable hashtbtemp = safeexecution.get(cdtemp);
	     Enumeration fses = hashtbtemp.keys();
	     int fscounter = 0;
	     while(fses.hasMoreElements()){
		 FlagState fs = (FlagState)fses.nextElement();
		 fscounter++;
		 
		 //get the set of OptionalTaskDescriptors corresponding
		 HashSet availabletasks = (HashSet)hashtbtemp.get(fs);
		 //iterate through the OptionalTaskDescriptors and store the pointers to the optionals struct (see on top) into an array
		 
		 output.println("struct optionaltaskdescriptor * optionaltaskdescriptorarray_FS"+fscounter+"_"+cdtemp.getSafeSymbol()+"[] = {");
		 for(Iterator mos = availabletasks.iterator(); mos.hasNext();){
		     OptionalTaskDescriptor mm = (OptionalTaskDescriptor)mos.next();
		     if(!mos.hasNext()) output.println("&optionaltaskdescriptor_"+mm.getuid()+"_"+cdtemp.getSafeSymbol()+"};\n");
		     
		     else output.println("&optionaltaskdescriptor_"+mm.getuid()+"_"+cdtemp.getSafeSymbol()+",");
		 }

		 //process flag information (what the flag after failure is) so we know what optionaltaskdescriptors to choose.
		 
		 output.println("int flags_FS"+fscounter+"_"+cdtemp.getSafeSymbol()+"[]={");
		 for(Iterator flags = fs.getFlags(); flags.hasNext();){
		     FlagDescriptor flagd = (FlagDescriptor)flags.next();
		     int flagid=1<<((Integer)flaginfo.get(flagd)).intValue();
		     if( flags.hasNext() ) output.print("0x"+Integer.toHexString(flagid)+" /*"+Integer.toBinaryString(flagid)+"*/,");
		     else  output.print("0x"+Integer.toHexString(flagid)+" /*"+Integer.toBinaryString(flagid)+"*/");
		     
		 }
		 //process tag information
		 
		 int tagcounter = 0;
		 //TagExpressionList tagel = fs.getTags(); 
		 //output.println("int predicatetags_"+predicateindex+"_OTD"+otd.getuid()+"_"+cdtemp.getSafeSymbol()+"[]={");
		 //BUG...added next line to fix, test with any task program
		 
		 //if (tagel!=null){
		 //    for(int j=0;j<tagel.numTags();j++) {
		 //	 if (j!=0)
		 //	     output.println(",");
		 //	 TempDescriptor tmp=tagel.getTemp(j);
		 //	 output.println("/*tagid*/"+state.getTagId(tmp.getTag()));
		 //   }
		 //  numtags = tagel.numTags();
		 //}
		 //output.println("};");
		 
		 
		 //Store the result in fsanalysiswrapper
		 output.println("};\n");
		 output.println("struct fsanalysiswrapper fsanalysiswrapper_FS"+fscounter+"_"+cdtemp.getSafeSymbol()+"={");
		 output.println("/* number of flags*/"+fs.numFlags()+",");
		 output.println("flags_FS"+fscounter+"_"+cdtemp.getSafeSymbol()+",");
		 output.println("/* number of tags*/"+tagcounter+",");
		 output.println("tags_FS"+fscounter+"_"+cdtemp.getSafeSymbol()+",");
		 output.println("/* number of optionaltaskdescriptors */"+availabletasks.size()+",");
		 output.println("optionaltaskdescriptorarray_FS"+fscounter+"_"+cdtemp.getSafeSymbol());
		 output.println("};\n");
		 
	     }

	     //Build the array of fsanalysiswrappers
	     output.println("struct fsanalysiswrapper * fsanalysiswrapperarray_"+cdtemp.getSafeSymbol()+"[] = {");
	     for(int i = 0; i<fscounter; i++){
		 if(i==fscounter-1) output.println("&fsanalysiswrapper_FS"+(i+1)+"_"+cdtemp.getSafeSymbol()+"};\n");
			 
		     else output.println("&fsanalysiswrapper_FS"+(i+1)+"_"+cdtemp.getSafeSymbol()+",");
	     }

	     //Build the classanalysiswrapper referring to the previous array
	     output.println("struct classanalysiswrapper classanalysiswrapper_"+cdtemp.getSafeSymbol()+"={");
	     output.println("/*type*/"+cdtemp.getId()+",");
	     output.println("/* number of fsanalysiswrappers */"+fscounter+",");
	     output.println("fsanalysiswrapperarray_"+cdtemp.getSafeSymbol()+"};\n");
	     fscounter = 0;
	     processedcd.add(cdtemp);
	 }

	 //build an array containing every classes for which code has been build
	 output.println("struct classanalysiswrapper * classanalysiswrapperarray[]={");
	 for(Iterator classit = processedcd.iterator(); classit.hasNext();){
	     ClassDescriptor cdtemp=(ClassDescriptor)classit.next();
	     if(!classit.hasNext()) output.println("&classanalysiswrapper_"+cdtemp.getSafeSymbol()+"};\n");
	     else output.println("&classanalysiswrapper_"+cdtemp.getSafeSymbol()+",");
	 }
	 output.println("int numclasses = "+processedcd.size()+";");
	 
     }
    
}
	 





