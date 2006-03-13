package IR.Flat;
import IR.*;
import java.util.*;
import java.io.*;

public class BuildCode {
    State state;
    Hashtable temptovar;
    Hashtable paramstable;
    Hashtable tempstable;
    int tag=0;
    private static final boolean GENERATEPRECISEGC=true;

    public BuildCode(State st, Hashtable temptovar) {
	state=st;
	this.temptovar=temptovar;
	paramstable=new Hashtable();	
	tempstable=new Hashtable();
    }

    public void buildCode() {
	Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
	PrintWriter outclassdefs=null;
	PrintWriter outstructs=null;
	PrintWriter outmethodheader=null;
	try {
	    OutputStream str=new FileOutputStream("structdefs.h");
	    outstructs=new java.io.PrintWriter(str, true);
	    str=new FileOutputStream("methodheaders.h");
	    outmethodheader=new java.io.PrintWriter(str, true);
	    str=new FileOutputStream("classdefs.h");
	    outclassdefs=new java.io.PrintWriter(str, true);
	} catch (Exception e) {
	    e.printStackTrace();
	    System.exit(-1);
	}
	outstructs.println("#include \"classdefs.h\"");
	outmethodheader.println("#include \"structdefs.h\"");
	while(it.hasNext()) {
	    ClassDescriptor cn=(ClassDescriptor)it.next();
	    generateCallStructs(cn, outclassdefs, outstructs, outmethodheader);
	}
	outstructs.close();
	outmethodheader.close();
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

    private void generateCallStructs(ClassDescriptor cn, PrintWriter classdefout, PrintWriter output, PrintWriter headersout) {
	/* Output class structure */
	Iterator fieldit=cn.getFields();
	classdefout.println("struct "+cn.getSafeSymbol()+" {");
	classdefout.println("  int type;");
	while(fieldit.hasNext()) {
	    FieldDescriptor fd=(FieldDescriptor)fieldit.next();
	    classdefout.println("  "+fd.getType().getSafeSymbol()+" "+fd.getSafeSymbol()+";");
	}
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
		output.println("struct "+cn.getSymbol()+md.getSafeSymbol()+"params {");
		output.println("  int type;");
		for(int i=0;i<objectparams.numPointers();i++) {
		    TempDescriptor temp=objectparams.getPointer(i);
		    output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSymbol()+";");
		}
		output.println("  void * next;");
		output.println("};\n");
	    }

	    /* Output temp structure */
	    if (GENERATEPRECISEGC) {
		output.println("struct "+cn.getSymbol()+md.getSafeSymbol()+"temps {");
		output.println("  int type;");
		for(int i=0;i<objecttemps.numPointers();i++) {
		    TempDescriptor temp=objecttemps.getPointer(i);
		    if (temp.getType().isNull())
			output.println("  void * "+temp.getSymbol()+";");
		    else 
			output.println("  struct "+temp.getType().getSafeSymbol()+" * "+temp.getSymbol()+";");
		}
		output.println("  void * next;");
		output.println("};\n");
	    }
	    
	    /* Output method declaration */
	    if (md.getReturnType()!=null)
		headersout.print(md.getReturnType().getSafeSymbol()+" ");
	    headersout.print(cn.getSafeSymbol()+md.getSafeSymbol()+"(");
	    
	    boolean printcomma=false;
	    if (GENERATEPRECISEGC) {
		headersout.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"params * base");
		printcomma=true;
	    }
	    for(int i=0;i<objectparams.numPrimitives();i++) {
		TempDescriptor temp=objectparams.getPrimitive(i);
		if (printcomma)
		    headersout.print(", ");
		printcomma=true;
		headersout.print(temp.getType().getSafeSymbol()+" "+temp.getSafeSymbol());
	    }
	    headersout.println(");\n");
   	}
    }

    private void generateFlatMethod(FlatMethod fm, PrintWriter output) {
	MethodDescriptor md=fm.getMethod();
	ParamsObject objectparams=(ParamsObject)paramstable.get(md);

	generateHeader(md,output);
	/* Print code */
	
	output.println("}");
    }

    private void generateHeader(MethodDescriptor md, PrintWriter output) {
	/* Print header */
	ParamsObject objectparams=(ParamsObject)paramstable.get(md);
	ClassDescriptor cn=md.getClassDesc();
	
	if (md.getReturnType()!=null)
	    output.print(md.getReturnType().getSafeSymbol()+" ");
	output.print(cn.getSafeSymbol()+md.getSafeSymbol()+"(");
	
	boolean printcomma=false;
	if (GENERATEPRECISEGC) {
	    output.print("struct "+cn.getSafeSymbol()+md.getSafeSymbol()+"params * base");
	    printcomma=true;
	}
	for(int i=0;i<objectparams.numPrimitives();i++) {
	    TempDescriptor temp=objectparams.getPrimitive(i);
	    if (printcomma)
		output.print(", ");
	    printcomma=true;
	    output.print(temp.getType().getSafeSymbol()+" "+temp.getSafeSymbol());
	}
	output.println(") {");
    }
}
