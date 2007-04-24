package IR.Flat;
import IR.Tree.FlagExpressionNode;
import IR.Tree.DNFFlag;
import IR.Tree.DNFFlagAtom;
import IR.*;
import java.util.*;
import java.io.*;
import Util.Relation;

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
    public static boolean GENERATEPRECISEGC=false;
    public static String PREFIX="";
    public static String arraytype="ArrayObject";
    Virtual virtualcalls;
    TypeUtil typeutil;
    private int maxtaskparams=0;
    ClassDescriptor[] cdarray;
    TypeDescriptor[] arraytable;

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

	try {
	    OutputStream str=new FileOutputStream(PREFIX+"structdefs.h");
	    outstructs=new java.io.PrintWriter(str, true);
	    str=new FileOutputStream(PREFIX+"methodheaders.h");
	    outmethodheader=new java.io.PrintWriter(str, true);
	    str=new FileOutputStream(PREFIX+"classdefs.h");
	    outclassdefs=new java.io.PrintWriter(str, true);
	    str=new FileOutputStream(PREFIX+"methods.c");
	    outmethod=new java.io.PrintWriter(str, true);
	    str=new FileOutputStream(PREFIX+"virtualtable.h");
	    outvirtual=new java.io.PrintWriter(str, true);
	    if (state.TASK) {
		str=new FileOutputStream(PREFIX+"task.h");
		outtask=new java.io.PrintWriter(str, true);
		str=new FileOutputStream(PREFIX+"taskdefs.c");
		outtaskdefs=new java.io.PrintWriter(str, true);
	    }
	    if (state.structfile!=null) {
		str=new FileOutputStream(PREFIX+state.structfile+".struct");
		outrepairstructs=new java.io.PrintWriter(str, true);
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

	outstructs.println("#ifndef STRUCTDEFS_H");
	outstructs.println("#define STRUCTDEFS_H");
	outstructs.println("#include \"classdefs.h\"");



	/* Output types for short array and string */
	outstructs.println("#define STRINGARRAYTYPE "+
			   (state.getArrayNumber(
						 (new TypeDescriptor(typeutil.getClass(TypeUtil.StringClass))).makeArray(state))+state.numClasses()));

	outstructs.println("#define STRINGTYPE "+typeutil.getClass(TypeUtil.StringClass).getId());
	outstructs.println("#define CHARARRAYTYPE "+
			   (state.getArrayNumber((new TypeDescriptor(TypeDescriptor.CHAR)).makeArray(state))+state.numClasses()));

	outstructs.println("#define BYTEARRAYTYPE "+
			   (state.getArrayNumber((new TypeDescriptor(TypeDescriptor.BYTE)).makeArray(state))+state.numClasses()));

	outstructs.println("#define BYTEARRAYARRAYTYPE "+
			   (state.getArrayNumber((new TypeDescriptor(TypeDescriptor.BYTE)).makeArray(state).makeArray(state))+state.numClasses()));
	
	outstructs.println("#define NUMCLASSES "+state.numClasses());
	if (state.TASK)
	    outstructs.println("#define STARTUPTYPE "+typeutil.getClass(TypeUtil.StartupClass).getId());

	// Output the C class declarations
	// These could mutually reference each other
	if (state.THREAD)
	    outclassdefs.println("#include <pthread.h>");

	outclassdefs.println("struct "+arraytype+";");

	Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    outclassdefs.println("struct "+cn.getSafeSymbol()+";");
	}
	outclassdefs.println("");
	{
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
	    }
	    printClassStruct(typeutil.getClass(TypeUtil.ObjectClass), outclassdefs);
	    outclassdefs.println("  int ___length___;");
	    outclassdefs.println("};\n");

	    if (state.TASK) {
	    //Print out definitions for task types
		outtask.println("struct parameterdescriptor {");
		outtask.println("int type;");
		outtask.println("int numberterms;");
		outtask.println("int *intarray;");
		outtask.println("void * queue;");
		outtask.println("};");

		outtask.println("struct taskdescriptor {");
		outtask.println("void * taskptr;");
		outtask.println("int numParameters;");
		outtask.println("struct parameterdescriptor **descriptorarray;");
		outtask.println("char * name;");
		outtask.println("};");
		outtask.println("extern struct taskdescriptor * taskarray[];");
		outtask.println("extern numtasks;");
	    }
	}

	// Output function prototypes and structures for parameters
	it=state.getClassSymbolTable().getDescriptorsIterator();
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    generateCallStructs(cn, outclassdefs, outstructs, outmethodheader);
	}


	if (state.TASK) {
	    /* Map flags to integers */
	    it=state.getClassSymbolTable().getDescriptorsIterator();
	    while(it.hasNext()) {
		ClassDescriptor cn=(ClassDescriptor)it.next();
		mapFlags(cn);
	    }
	    /* Generate Tasks */
	    generateTaskStructs(outstructs, outmethodheader);
	}

	outmethodheader.println("#endif");

	outmethodheader.close();

	/* Build the actual methods */
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
	outclassdefs.println("extern int classsize[];");
	outclassdefs.println("extern int hasflags[];");
	outclassdefs.println("extern int * pointerarray[];");
	outclassdefs.println("extern int supertypes[];");

	//Store the sizes of classes & array elements
	generateSizeArray(outmethod);
	
	//Store table of supertypes
	generateSuperTypeTable(outmethod);

	//Store the layout of classes
	generateLayoutStructs(outmethod);

	/* Generate code for methods */
	Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
	while(classit.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)classit.next();
	    Iterator methodit=cn.getMethods();
	    while(methodit.hasNext()) {
		/* Classify parameters */
		MethodDescriptor md=(MethodDescriptor)methodit.next();
		FlatMethod fm=state.getMethodFlat(md);
		if (!md.getModifiers().isNative())
		    generateFlatMethod(fm,outmethod);
	    }
	}

	if (state.TASK) {
	    /* Compile task based program */
	    outtaskdefs.println("#include \"task.h\"");
	    outtaskdefs.println("#include \"methodheaders.h\"");
	    Iterator taskit=state.getTaskSymbolTable().getDescriptorsIterator();
	    while(taskit.hasNext()) {
		TaskDescriptor td=(TaskDescriptor)taskit.next();
		FlatMethod fm=state.getMethodFlat(td);
		generateFlatMethod(fm, outmethod);
		generateTaskDescriptor(outtaskdefs, td);
	    }

	    {
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
	    }

	    outtaskdefs.println("int numtasks="+state.getTaskSymbolTable().getValueSet().size()+";");

	} else if (state.main!=null) {
	    /* Generate main method */
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


	    ClassDescriptor cd=typeutil.getClass(state.main);
	    Set mainset=cd.getMethodTable().getSet("main");
	    for(Iterator mainit=mainset.iterator();mainit.hasNext();) {
		MethodDescriptor md=(MethodDescriptor)mainit.next();
		if (md.numParameters()!=1)
		    continue;
		if (md.getParameter(0).getType().getArrayCount()!=1)
		    continue;
		if (!md.getParameter(0).getType().getSymbol().equals("String"))
		    continue;

		if (!md.getModifiers().isStatic())
		    throw new Error("Error: Non static main");
		outmethod.println("   {");
		if (GENERATEPRECISEGC) {
		    outmethod.print("       struct "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params __parameterlist__={");
		    outmethod.println("1, NULL,"+"stringarray};");
		    outmethod.println("     "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(& __parameterlist__);");
		} else
		    outmethod.println("     "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(stringarray);");
		outmethod.println("   }");
		break;
	    }
	    if (state.THREAD) {
		outmethod.println("pthread_mutex_lock(&gclistlock);");
		outmethod.println("threadcount--;");
		outmethod.println("pthread_cond_signal(&gccond);");
		outmethod.println("pthread_mutex_unlock(&gclistlock);");
		outmethod.println("pthread_exit(NULL);");
	    }
	    outmethod.println("}");
	}
	if (state.TASK)
	    outstructs.println("#define MAXTASKPARAMS "+maxtaskparams);


	/* Output structure definitions for repair tool */
	if (state.structfile!=null) {
	    buildRepairStructs(outrepairstructs);
	    outrepairstructs.close();
	}
	outstructs.println("#endif");

	outstructs.close();
	outmethod.close();
    }

    private int maxcount=0;

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
    void generateTaskDescriptor(PrintWriter output, TaskDescriptor task) {
	for (int i=0;i<task.numParameters();i++) {
	    VarDescriptor param_var=task.getParameter(i);
	    TypeDescriptor param_type=task.getParamType(i);
	    FlagExpressionNode param_flag=task.getFlag(param_var);
	    DNFFlag dflag=param_flag.getDNF();
	    
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

	    output.println("struct parameterdescriptor parameter_"+i+"_"+task.getSafeSymbol()+"={");
	    output.println("/* type */"+param_type.getClassDesc().getId()+",");
	    output.println("/* number of DNF terms */"+dflag.size()+",");
	    output.println("parameterdnf_"+i+"_"+task.getSafeSymbol()+",");
	    output.println("0");
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

    private void generateTempStructs(FlatMethod fm) {
	MethodDescriptor md=fm.getMethod();
	TaskDescriptor task=fm.getTask();

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
	    }
	}
    }
    
    private void generateLayoutStructs(PrintWriter output) {
	Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    output.println("int "+cn.getSafeSymbol()+"_pointers[]={");
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
		    output.print("((int)&(((struct "+cn.getSafeSymbol() +" *)0)->"+fd.getSafeSymbol()+"))");
		}
	    }
	    output.println("};");
	}
	output.println("int * pointerarray[]={");
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
	}
	printClassStruct(cn, classdefout);
	classdefout.println("};\n");

	/* Cycle through methods */
	Iterator methodit=cn.getMethods();
	while(methodit.hasNext()) {
	    /* Classify parameters */
	    MethodDescriptor md=(MethodDescriptor)methodit.next();
	    FlatMethod fm=state.getMethodFlat(md);
	    generateTempStructs(fm);

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
	    
	    /* Output method declaration */
	    if (md.getReturnType()!=null) {
		if (md.getReturnType().isClass()||md.getReturnType().isArray())
		    headersout.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
		else
		    headersout.print(md.getReturnType().getSafeSymbol()+" ");
	    } else 
		//catch the constructor case
		headersout.print("void ");
	    headersout.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");
	    
	    boolean printcomma=false;
	    if (GENERATEPRECISEGC) {
		headersout.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * "+paramsprefix);
		printcomma=true;
	    }

	    //output parameter list
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
	    generateTempStructs(fm);

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
		if (objectparams.numPointers()>maxtaskparams)
		    maxtaskparams=objectparams.numPointers();
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

    /** Generate code for flatmethod fm. */

    private void generateFlatMethod(FlatMethod fm, PrintWriter output) {
	MethodDescriptor md=fm.getMethod();
	TaskDescriptor task=fm.getTask();

       	ClassDescriptor cn=md!=null?md.getClassDesc():null;

	ParamsObject objectparams=(ParamsObject)paramstable.get(md!=null?md:task);

	generateHeader(md!=null?md:task,output);

	TempObject objecttemp=(TempObject) tempstable.get(md!=null?md:task);

	/* Print code */
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

	/* Generate labels first */
	HashSet tovisit=new HashSet();
	HashSet visited=new HashSet();
	int labelindex=0;
	Hashtable nodetolabel=new Hashtable();
	tovisit.add(fm.methodEntryNode());
	FlatNode current_node=null;

	//Assign labels 1st
	//Node needs a label if it is
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

	if (state.THREAD&&GENERATEPRECISEGC) {
	    output.println("checkcollect(&"+localsprefix+");");
	}
	
	//Do the actual code generation
	tovisit=new HashSet();
	visited=new HashSet();
	tovisit.add(fm.methodEntryNode());
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
		generateFlatNode(fm, current_node, output);
		if (current_node.kind()!=FKind.FlatReturnNode) {
		    output.println("   return;");
		}
		current_node=null;
	    } else if(current_node.numNext()==1) {
		output.print("   ");
		generateFlatNode(fm, current_node, output);
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

    /** Generate text string that corresponds to the Temp td. */
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

    private void generateFlatNode(FlatMethod fm, FlatNode fn, PrintWriter output) {
	switch(fn.kind()) {
	case FKind.FlatTagDeclaration:
	    generateFlatTagDeclaration(fm, (FlatTagDeclaration) fn,output);
	    return;
	case FKind.FlatCall:
	    generateFlatCall(fm, (FlatCall) fn,output);
	    return;
	case FKind.FlatFieldNode:
	    generateFlatFieldNode(fm, (FlatFieldNode) fn,output);
	    return;
	case FKind.FlatElementNode:
	    generateFlatElementNode(fm, (FlatElementNode) fn,output);
	    return;
	case FKind.FlatSetElementNode:
	    generateFlatSetElementNode(fm, (FlatSetElementNode) fn,output);
	    return;
	case FKind.FlatSetFieldNode:
	    generateFlatSetFieldNode(fm, (FlatSetFieldNode) fn,output);
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

    private void generateFlatCheckNode(FlatMethod fm,  FlatCheckNode fcn, PrintWriter output) {

	if (state.CONSCHECK) {
	    String specname=fcn.getSpec();
	    String varname="repairstate___";
	    output.println("{");
	    output.println("struct "+specname+"_state * "+varname+"=allocate"+specname+"_state();");

	    TempDescriptor[] temps=fcn.getTemps();
	    String[] vars=fcn.getVars();
	    for(int i=0;i<temps.length;i++) {
		output.println(varname+"->"+vars[i]+"=(int)"+generateTemp(fm, temps[i])+";");
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
		VarDescriptor var=md.getParameter(i);
		TempDescriptor paramtemp=(TempDescriptor)temptovar.get(var);
		if (objectparams.isParamPtr(paramtemp)) {
		    TempDescriptor targ=fc.getArg(i);
		    output.print(", ");
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
	    VarDescriptor var=md.getParameter(i);
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

    private void generateFlatFieldNode(FlatMethod fm, FlatFieldNode ffn, PrintWriter output) {
	output.println(generateTemp(fm, ffn.getDst())+"="+ generateTemp(fm,ffn.getSrc())+"->"+ ffn.getField().getSafeSymbol()+";");
    }

    private void generateFlatSetFieldNode(FlatMethod fm, FlatSetFieldNode fsfn, PrintWriter output) {
	if (fsfn.getField().getSymbol().equals("length")&&fsfn.getDst().getType().isArray())
	    throw new Error("Can't set array length");
	output.println(generateTemp(fm, fsfn.getDst())+"->"+ fsfn.getField().getSafeSymbol()+"="+ generateTemp(fm,fsfn.getSrc())+";");
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

    private void generateHeader(Descriptor des, PrintWriter output) {
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
	if (md!=null)
	    output.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");
	else
	    output.print(task.getSafeSymbol()+"(");
	
	boolean printcomma=false;
	if (GENERATEPRECISEGC) {
	    if (md!=null)
		output.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * "+paramsprefix);
	    else
		output.print("struct "+task.getSafeSymbol()+"_params * "+paramsprefix);
	    printcomma=true;
	} 

	if (md!=null) {
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
	} else if (!GENERATEPRECISEGC){
	    output.println("void * parameterarray[]) {");
	    for(int i=0;i<objectparams.numPrimitives();i++) {
		TempDescriptor temp=objectparams.getPrimitive(i);
		output.println("struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+"=parameterarray["+i+"];");
	    }
	    if (objectparams.numPrimitives()>maxtaskparams)
		maxtaskparams=objectparams.numPrimitives();
	} else output.println(") {");
    }

    public void generateFlatFlagActionNode(FlatMethod fm, FlatFlagActionNode ffan, PrintWriter output) {
	output.println("/* FlatFlagActionNode */");
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

	Iterator orit=flagortable.keySet().iterator();
	while(orit.hasNext()) {
	    TempDescriptor temp=(TempDescriptor)orit.next();
	    int ormask=((Integer)flagortable.get(temp)).intValue();
	    int andmask=0xFFFFFFF;
	    if (flagandtable.containsKey(temp))
		andmask=((Integer)flagandtable.get(temp)).intValue();
	    if (ffan.getTaskType()==FlatFlagActionNode.NEWOBJECT) {
		output.println("flagorandinit("+generateTemp(fm, temp)+", 0x"+Integer.toHexString(ormask)+", 0x"+Integer.toHexString(andmask)+");");
	    } else {
		output.println("flagorand("+generateTemp(fm, temp)+", 0x"+Integer.toHexString(ormask)+", 0x"+Integer.toHexString(andmask)+");");
	    }
	}
	Iterator andit=flagandtable.keySet().iterator();
	while(andit.hasNext()) {
	    TempDescriptor temp=(TempDescriptor)andit.next();
	    int andmask=((Integer)flagandtable.get(temp)).intValue();
	    if (!flagortable.containsKey(temp)) {
		if (ffan.getTaskType()==FlatFlagActionNode.NEWOBJECT)
		    output.println("flagorandinit("+generateTemp(fm, temp)+", 0, 0x"+Integer.toHexString(andmask)+");");
		else
		    output.println("flagorand("+generateTemp(fm, temp)+", 0, 0x"+Integer.toHexString(andmask)+");");
	    }
	}

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

	Iterator clearit=tagcleartable.keySet().iterator();
	while(clearit.hasNext()) {
	    TempDescriptor objtmp=(TempDescriptor)clearit.next();
	    Set tagtmps=tagcleartable.get(objtmp);
	    Iterator tagit=tagtmps.iterator();
	    while(tagit.hasNext()) {
		TempDescriptor tagtmp=(TempDescriptor)tagit.next();
		output.println("tagclear("+generateTemp(fm, objtmp)+", "+generateTemp(fm,tagtmp)+");");
	    }
	}

	Iterator setit=tagsettable.keySet().iterator();
	while(setit.hasNext()) {
	    TempDescriptor objtmp=(TempDescriptor)setit.next();
	    Set tagtmps=tagcleartable.get(objtmp);
	    Iterator tagit=tagtmps.iterator();
	    while(tagit.hasNext()) {
		TempDescriptor tagtmp=(TempDescriptor)tagit.next();
		output.println("tagset("+generateTemp(fm, objtmp)+", "+generateTemp(fm,tagtmp)+");");
	    }
	}
    }
}
