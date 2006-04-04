package IR.Flat;
import IR.*;
import java.util.*;
import java.io.*;

public class BuildCode {
    State state;
    Hashtable temptovar;
    Hashtable paramstable;
    Hashtable tempstable;
    Hashtable fieldorder;
    int tag=0;
    String localsprefix="___locals___";
    String paramsprefix="___params___";
    public static boolean GENERATEPRECISEGC=false;
    public static String PREFIX="";
    Virtual virtualcalls;
    TypeUtil typeutil;


    public BuildCode(State st, Hashtable temptovar, TypeUtil typeutil) {
	state=st;
	this.temptovar=temptovar;
	paramstable=new Hashtable();	
	tempstable=new Hashtable();
	fieldorder=new Hashtable();
	this.typeutil=typeutil;
	virtualcalls=new Virtual(state);
    }

    public void buildCode() {
	Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
	PrintWriter outclassdefs=null;
	PrintWriter outstructs=null;
	PrintWriter outmethodheader=null;
	PrintWriter outmethod=null;
	PrintWriter outvirtual=null;

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
	} catch (Exception e) {
	    e.printStackTrace();
	    System.exit(-1);
	}

	buildVirtualTables(outvirtual);
	outstructs.println("#include \"classdefs.h\"");
	outmethodheader.println("#include \"structdefs.h\"");

	// Output the C declarations
	// These could mutually reference each other
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    outclassdefs.println("struct "+cn.getSafeSymbol()+";");
	}
	outclassdefs.println("");

	it=state.getClassSymbolTable().getDescriptorsIterator();
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    generateCallStructs(cn, outclassdefs, outstructs, outmethodheader);
	}
	outstructs.close();
	outmethodheader.close();

	/* Build the actual methods */
	outmethod.println("#include \"methodheaders.h\"");
	outmethod.println("#include \"virtualtable.h\"");
	outmethod.println("#include <runtime.h>");
	Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
	while(classit.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)classit.next();
	    Iterator methodit=cn.getMethods();
	    while(methodit.hasNext()) {
		/* Classify parameters */
		MethodDescriptor md=(MethodDescriptor)methodit.next();
		FlatMethod fm=state.getMethodFlat(md);
		generateFlatMethod(fm,outmethod);
	    }
	}
	outmethod.close();
    }

    private int maxcount=0;

    private void buildVirtualTables(PrintWriter outvirtual) {
	int numclasses=0;
    	Iterator classit=state.getClassSymbolTable().getDescriptorsIterator();
	while(classit.hasNext()) {
	    ClassDescriptor cd=(ClassDescriptor)classit.next();
	    if (virtualcalls.getMethodCount(cd)>maxcount)
		maxcount=virtualcalls.getMethodCount(cd);
	    numclasses++;
	}
	MethodDescriptor[][] virtualtable=new MethodDescriptor[numclasses][maxcount];

	/* Fill in virtual table */
	classit=state.getClassSymbolTable().getDescriptorsIterator();
	while(classit.hasNext()) {
	    ClassDescriptor cd=(ClassDescriptor)classit.next();
	    fillinRow(cd, virtualtable, cd.getId());
	}
	outvirtual.print("void * virtualtable[]={");
	boolean needcomma=false;
	for(int i=0;i<numclasses;i++) {
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

    private void generateTempStructs(FlatMethod fm) {
	MethodDescriptor md=fm.getMethod();
	ParamsObject objectparams=new ParamsObject(md,tag++);
	paramstable.put(md, objectparams);
	for(int i=0;i<fm.numParameters();i++) {
	    TempDescriptor temp=fm.getParameter(i);
	    TypeDescriptor type=temp.getType();
	    if (type.isPtr()&&GENERATEPRECISEGC)
		objectparams.addPtr(temp);
	    else
		objectparams.addPrim(temp);
	}

	TempObject objecttemps=new TempObject(objectparams,md,tag++);
	tempstable.put(md, objecttemps);
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
    
    /* Force consistent field ordering between inherited classes */
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
	    if (fd.getType().isClass())
		classdefout.println("  struct "+fd.getType().getSafeSymbol()+" * "+fd.getSafeSymbol()+";");
	    else 
		classdefout.println("  "+fd.getType().getSafeSymbol()+" "+fd.getSafeSymbol()+";");
	}
    }

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
		if (md.getReturnType().isClass())
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

	    for(int i=0;i<objectparams.numPrimitives();i++) {
		TempDescriptor temp=objectparams.getPrimitive(i);
		if (printcomma)
		    headersout.print(", ");
		printcomma=true;
		if (temp.getType().isClass())
		    headersout.print("struct " + temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol());
		else
		    headersout.print(temp.getType().getSafeSymbol()+" "+temp.getSafeSymbol());
	    }
	    headersout.println(");\n");
   	}
    }

    private void generateFlatMethod(FlatMethod fm, PrintWriter output) {
	MethodDescriptor md=fm.getMethod();
       	ClassDescriptor cn=md.getClassDesc();
	ParamsObject objectparams=(ParamsObject)paramstable.get(md);

	generateHeader(md,output);
	/* Print code */
	output.println(" {");
	
	if (GENERATEPRECISEGC) {
	    output.println("   struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_locals "+localsprefix+";");
	}
	TempObject objecttemp=(TempObject) tempstable.get(md);
	for(int i=0;i<objecttemp.numPrimitives();i++) {
	    TempDescriptor td=objecttemp.getPrimitive(i);
	    TypeDescriptor type=td.getType();
	    if (type.isNull())
		output.println("   void * "+td.getSafeSymbol()+";");
	    else if (type.isClass())
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

    private String generateTemp(FlatMethod fm, TempDescriptor td) {
	MethodDescriptor md=fm.getMethod();
	TempObject objecttemps=(TempObject) tempstable.get(md);
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
	    if (md.getReturnType().isClass())
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
		if (temp.getType().isClass())
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
	output.println(generateTemp(fm, fsfn.getDst())+"->"+ fsfn.getField().getSafeSymbol()+"="+ generateTemp(fm,fsfn.getSrc())+";");
    }

    private void generateFlatNew(FlatMethod fm, FlatNew fn, PrintWriter output) {
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
	else if (fon.getOp().getOp()==Operation.POSTINC)
	    output.println(generateTemp(fm, fon.getDest())+" = "+generateTemp(fm, fon.getLeft())+"++;");
	else if (fon.getOp().getOp()==Operation.POSTDEC)
	    output.println(generateTemp(fm, fon.getDest())+" = "+generateTemp(fm, fon.getLeft())+"--;");
	else if (fon.getOp().getOp()==Operation.PREINC)
	    output.println(generateTemp(fm, fon.getDest())+" = ++"+generateTemp(fm, fon.getLeft())+";");
	else if (fon.getOp().getOp()==Operation.PREDEC)
	    output.println(generateTemp(fm, fon.getDest())+" = --"+generateTemp(fm, fon.getLeft())+";");
	else
	    output.println(generateTemp(fm, fon.getDest())+fon.getOp().toString()+generateTemp(fm, fon.getLeft())+";");
    }

    private void generateFlatCastNode(FlatMethod fm, FlatCastNode fcn, PrintWriter output) {
	/* TODO: Make call into runtime */
	output.println(generateTemp(fm,fcn.getDst())+"=("+fcn.getType().getSafeSymbol()+")"+generateTemp(fm,fcn.getSrc())+";");
    }

    private void generateFlatLiteralNode(FlatMethod fm, FlatLiteralNode fln, PrintWriter output) {
	if (fln.getValue()==null)
	    output.println(generateTemp(fm, fln.getDst())+"=0;");
	else if (fln.getType().getSymbol().equals(TypeUtil.StringClass))
	    output.println(generateTemp(fm, fln.getDst())+"=newstring(\""+FlatLiteralNode.escapeString((String)fln.getValue())+"\");");
	else
	    output.println(generateTemp(fm, fln.getDst())+"="+fln.getValue()+";");
    }

    private void generateFlatReturnNode(FlatMethod fm, FlatReturnNode frn, PrintWriter output) {
	output.println("return "+generateTemp(fm, frn.getReturnTemp())+";");
    }

    private void generateFlatCondBranch(FlatMethod fm, FlatCondBranch fcb, String label, PrintWriter output) {
	output.println("if (!"+generateTemp(fm, fcb.getTest())+") goto "+label+";");
    }

    private void generateHeader(MethodDescriptor md, PrintWriter output) {
	/* Print header */
	ParamsObject objectparams=(ParamsObject)paramstable.get(md);
	ClassDescriptor cn=md.getClassDesc();
	
	if (md.getReturnType()!=null) {
	    if (md.getReturnType().isClass())
		output.print("struct " + md.getReturnType().getSafeSymbol()+" * ");
	    else
		output.print(md.getReturnType().getSafeSymbol()+" ");
	} else 
	    //catch the constructor case
	    output.print("void ");

	output.print(cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"(");
	
	boolean printcomma=false;
	if (GENERATEPRECISEGC) {
	    output.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"_"+md.getSafeMethodDescriptor()+"_params * "+paramsprefix);
	    printcomma=true;
	} 

	for(int i=0;i<objectparams.numPrimitives();i++) {
	    TempDescriptor temp=objectparams.getPrimitive(i);
	    if (printcomma)
		output.print(", ");
	    printcomma=true;
	    if (temp.getType().isClass())
		output.print("struct "+temp.getType().getSafeSymbol()+" * "+temp.getSafeSymbol());
	    else
		output.print(temp.getType().getSafeSymbol()+" "+temp.getSafeSymbol());
	}
	output.print(")");
    }
}
