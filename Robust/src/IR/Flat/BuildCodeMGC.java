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

public class BuildCodeMGC extends BuildCode {
  int coreNum;
  int tcoreNum;
  int gcoreNum;
  int startupcorenum;    // record the core containing startup task, s
  // uppose only one core can have startup object

  public BuildCodeMGC(State st, 
                      Hashtable temptovar, 
                      TypeUtil typeutil, 
                      SafetyAnalysis sa,
                      int coreNum, 
                      int tcoreNum,
                      int gcoreNum,
                      PrefetchAnalysis pa) {
    super(st, temptovar, typeutil, sa, pa);
    this.coreNum = coreNum; // # of the active cores
    this.tcoreNum = tcoreNum; // # of the total number of cores
    this.gcoreNum = gcoreNum; // # of the cores for gc if any
    this.startupcorenum = 0;
  }

  public void buildCode() {
    /* Create output streams to write to */
    PrintWriter outclassdefs=null;
    PrintWriter outstructs=null;
    PrintWriter outmethodheader=null;
    PrintWriter outmethod=null;
    PrintWriter outvirtual=null;

    try {
      outstructs=new PrintWriter(new FileOutputStream(PREFIX+"structdefs.h"), true);
      outmethodheader=new PrintWriter(new FileOutputStream(PREFIX+"methodheaders.h"), true);
      outclassdefs=new PrintWriter(new FileOutputStream(PREFIX+"classdefs.h"), true);
      outvirtual=new PrintWriter(new FileOutputStream(PREFIX+"virtualtable.h"), true);
      outmethod=new PrintWriter(new FileOutputStream(PREFIX+"methods.c"), true);
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

    /* Build the actual methods */
    super.outputMethods(outmethod);

    /* Record maximum number of task parameters */
    //outstructs.println("#define MAXTASKPARAMS "+maxtaskparams);
    /* Record maximum number of all types, i.e. length of classsize[] */
    outstructs.println("#define NUMTYPES "+(state.numClasses() + state.numArrays()));
    /* Record number of total cores */
    outstructs.println("#define NUMCORES "+this.tcoreNum);
    /* Record number of active cores */
    outstructs.println("#define NUMCORESACTIVE "+this.coreNum); // this.coreNum 
                                    // can be reset by the scheduling analysis
    /* Record number of garbage collection cores */
    outstructs.println("#ifdef MULTICORE_GC");
    outstructs.println("#define NUMCORES4GC "+this.gcoreNum);
    outstructs.println("#endif");
    /* Record number of core containing startup task */
    outstructs.println("#define STARTUPCORE "+this.startupcorenum);
    
    if (state.main!=null) {
    /* Generate main method */
      outputMainMethod(outmethod);
    }

    /* Close files */
    outmethodheader.println("#endif");
    outmethodheader.close();
    outmethod.close();
    outstructs.println("#endif");
    outstructs.close();
  }
  
  protected void outputMainMethod(PrintWriter outmethod) {
    outmethod.println("int mgc_main(int argc, const char *argv[]) {");
    outmethod.println("  int i;");
    
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

    outmethod.println("}");
  }
}
