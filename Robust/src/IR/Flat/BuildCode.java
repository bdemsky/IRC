package IR.Flat;
import IR.Tree.FlagExpressionNode;
import IR.Tree.DNFFlag;
import IR.Tree.DNFFlagAtom;
import IR.*;
import java.util.*;
import java.io.*;

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
	} catch (Exception e) {
	    e.printStackTrace();
	    System.exit(-1);
	}

	/* Build the virtual dispatch tables */

	buildVirtualTables(outvirtual);

	/* Output includes */
	outstructs.println("#include \"classdefs.h\"");
	outmethodheader.println("#include \"structdefs.h\"");

	/* Output types for short array and string */
	outstructs.println("#define STRINGARRAYTYPE "+
			   (state.getArrayNumber(
						 (new TypeDescriptor(typeutil.getClass(TypeUtil.StringClass))).makeArray(state))+state.numClasses()));

	outstructs.println("#define STRINGTYPE "+typeutil.getClass(TypeUtil.StringClass).getId());
	outstructs.println("#define CHARARRAYTYPE "+
			   (state.getArrayNumber((new TypeDescriptor(TypeDescriptor.CHAR)).makeArray(state))+state.numClasses()));
	if (state.TASK)
	    outstructs.println("#define STARTUPTYPE "+typeutil.getClass(TypeUtil.StartupClass).getId());

	// Output the C class declarations
	// These could mutually reference each other
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
	    printClassStruct(typeutil.getClass(TypeUtil.ObjectClass), outclassdefs);
	    outclassdefs.println("  int ___length___;");
	    outclassdefs.println("};\n");

	    if (state.TASK) {
	    //Print out definitions for task types
		outtask.println("struct parameterdescriptor {");
		outtask.println("int type;");
		outtask.println("int numberterms;");
		outtask.println("int *intarray;");
		outtask.println("};");

		outtask.println("struct taskdescriptor {");
		outtask.println("void * taskptr;");
		outtask.println("int numParameters;");
		outtask.println("struct parameterdescriptor **descriptorarray;");
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
	outstructs.close();

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

	outmethodheader.close();

	/* Build the actual methods */
	outmethod.println("#include \"methodheaders.h\"");
	outmethod.println("#include \"virtualtable.h\"");
	outmethod.println("#include <runtime.h>");
	outclassdefs.println("extern int classsize[];");

	//Store the sizes of classes & array elements
	generateSizeArray(outmethod);

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

	} else if (state.main!=null) {
	    /* Generate main method */
	    outmethod.println("int main(int argc, const char *argv[]) {");
	    ClassDescriptor cd=typeutil.getClass(state.main);
	    Set mainset=cd.getMethodTable().getSet("main");
	    for(Iterator mainit=mainset.iterator();mainit.hasNext();) {
		MethodDescriptor md=(MethodDescriptor)mainit.next();
		if (md.numParameters()!=0)
		    continue;
		if (!md.getModifiers().isStatic())
		    throw new Error("Error: Non static main");
		outmethod.println("   "+cd.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"();");
		break;
	    }
	    outmethod.println("}");
	}
	outmethod.close();
    }

    private int maxcount=0;

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
		    int flagid=((Integer)flags.get(fd)).intValue();
		    andmask|=flagid;
		    if (!negated)
			checkmask|=flagid;
		}
		output.print(andmask+", "+checkmask);
	    }
	    output.println("};");

	    output.println("struct parameterdescriptor parameter_"+i+"_"+task.getSafeSymbol()+"={");
	    output.println("/* type */"+param_type.getClassDesc().getId()+",");
	    output.println("/* number of DNF terms */"+dflag.size()+",");
	    output.println("parameterdnf_"+i+"_"+task.getSafeSymbol());
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
	output.println("parameterdescriptors_"+task.getSafeSymbol());
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
	ClassDescriptor[] cdarray=new ClassDescriptor[state.numClasses()];
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

	TypeDescriptor[] sizetable=new TypeDescriptor[state.numArrays()];

	Iterator arrayit=state.getArrayIterator();
	while(arrayit.hasNext()) {
	    TypeDescriptor td=(TypeDescriptor)arrayit.next();
	    int id=state.getArrayNumber(td);
	    sizetable[id]=td;
	}
	
	for(int i=0;i<state.numArrays();i++) {
	    if (needcomma)
		outclassdefs.print(", ");
	    TypeDescriptor tdelement=sizetable[i].dereference();
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
	    if (type.isPtr()&&GENERATEPRECISEGC)
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
		if (type.isPtr()&&GENERATEPRECISEGC)
		    objecttemps.addPtr(temp);
		else
		    objecttemps.addPrim(temp);
	    }
	}
    }
    
    /* Force consistent field ordering between inherited classes. */

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
		    if (number.intValue()>max)
			max=number.intValue();
		}
	    }
	    
	    Iterator flagit=cn.getFlags();
	    while(flagit.hasNext()) {
		FlagDescriptor fd=(FlagDescriptor)flagit.next();
		if (sp==null||!sp.getFlagTable().contains(fd.getSymbol()))
		    flags.put(fd, new Integer(++max));
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
		output.println("  int type;");
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
		output.println("  int type;");
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
		output.println("  int type;");
		output.println("  void * next;");
		for(int i=0;i<objectparams.numPointers();i++) {
		    TempDescriptor temp=objectparams.getPointer(i);
		    output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol()+";");
		}
		output.println("};\n");
	    }

	    /* Output temp structure */
	    if (GENERATEPRECISEGC) {
		output.println("struct "+task.getSafeSymbol()+"_locals {");
		output.println("  int type;");
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

    /** Generate code for flatmethod fm. */

    private void generateFlatMethod(FlatMethod fm, PrintWriter output) {
	MethodDescriptor md=fm.getMethod();
	TaskDescriptor task=fm.getTask();

       	ClassDescriptor cn=md!=null?md.getClassDesc():null;

	ParamsObject objectparams=(ParamsObject)paramstable.get(md!=null?md:task);

	generateHeader(md!=null?md:task,output);

	/* Print code */
	output.println(" {");
	
	if (GENERATEPRECISEGC) {
	    if (md!=null)
		output.println("   struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_locals "+localsprefix+";");
	    else
		output.println("   struct "+task.getSafeSymbol()+"_locals "+localsprefix+";");
	}
	TempObject objecttemp=(TempObject) tempstable.get(md!=null?md:task);
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
	case FKind.FlatFlagActionNode:
	    generateFlatFlagActionNode(fm, (FlatFlagActionNode) fn, output);
	    return;
	}
	throw new Error();

    }

    private void generateFlatCall(FlatMethod fm, FlatCall fc, PrintWriter output) {
	MethodDescriptor md=fc.getMethod();
	ParamsObject objectparams=(ParamsObject) paramstable.get(md);
	ClassDescriptor cn=md.getClassDesc();
	output.println("{");
	if (GENERATEPRECISEGC) {
	    output.print("       struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params __parameterlist__={");
	    
	    output.print(objectparams.getUID());
	    output.print(", & "+localsprefix);
	    if (fc.getThis()!=null) {
		output.print(", ");
		output.print(generateTemp(fm,fc.getThis()));
	    }
	    for(int i=0;i<fc.numArgs();i++) {
		VarDescriptor var=md.getParameter(i);
		TempDescriptor paramtemp=(TempDescriptor)temptovar.get(var);
		if (objectparams.isParamPtr(paramtemp)) {
		    TempDescriptor targ=fc.getArg(i);
		    output.print(", ");
		    output.print(generateTemp(fm, targ));
		}
	    }
	    output.println("};");
	}
	output.print("       ");


	if (fc.getReturnTemp()!=null)
	    output.print(generateTemp(fm,fc.getReturnTemp())+"=");
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
	    output.println(generateTemp(fm,fn.getDst())+"=allocate_newarray("+arrayid+", "+generateTemp(fm, fn.getSize())+");");
	} else
	    output.println(generateTemp(fm,fn.getDst())+"=allocate_new("+fn.getType().getClassDesc().getId()+");");
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
	output.println(generateTemp(fm,fcn.getDst())+"=("+fcn.getType().getSafeSymbol()+")"+generateTemp(fm,fcn.getSrc())+";");
    }

    private void generateFlatLiteralNode(FlatMethod fm, FlatLiteralNode fln, PrintWriter output) {
	if (fln.getValue()==null)
	    output.println(generateTemp(fm, fln.getDst())+"=0;");
	else if (fln.getType().getSymbol().equals(TypeUtil.StringClass))
	    output.println(generateTemp(fm, fln.getDst())+"=NewString(\""+FlatLiteralNode.escapeString((String)fln.getValue())+"\","+((String)fln.getValue()).length()+");");
	else if (fln.getType().isBoolean()) {
	    if (((Boolean)fln.getValue()).booleanValue())
		output.println(generateTemp(fm, fln.getDst())+"=1;");
	    else
		output.println(generateTemp(fm, fln.getDst())+"=0;");
	} else if (fln.getType().isChar()) {
	    output.println(generateTemp(fm, fln.getDst())+"='"+fln.getValue()+"';");
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
	output.print(")");
    }

    public void generateFlatFlagActionNode(FlatMethod fm, FlatFlagActionNode ffann, PrintWriter output) {
	output.print("/* FlatFlagActionNode will go here */");
	
    }
}
