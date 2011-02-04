package IR.Flat;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import Analysis.Locality.LocalityBinding;
import Analysis.Prefetch.*;
import Analysis.TaskStateAnalysis.SafetyAnalysis;
import IR.ClassDescriptor;
import IR.Descriptor;
import IR.FlagDescriptor;
import IR.MethodDescriptor;
import IR.State;
import IR.SymbolTable;
import IR.TagVarDescriptor;
import IR.TaskDescriptor;
import IR.TypeDescriptor;
import IR.TypeUtil;
import IR.VarDescriptor;
import IR.Tree.DNFFlag;
import IR.Tree.DNFFlagAtom;
import IR.Tree.ExpressionNode;
import IR.Tree.FlagEffect;
import IR.Tree.FlagEffects;
import IR.Tree.FlagExpressionNode;
import IR.Tree.TagEffect;
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
    PrintWriter outglobaldefs=null;
    PrintWriter outstructs=null;
    PrintWriter outmethodheader=null;
    PrintWriter outmethod=null;
    PrintWriter outvirtual=null;

    try {
      outstructs=new PrintWriter(new FileOutputStream(PREFIX+"structdefs.h"), true);
      outmethodheader=new PrintWriter(new FileOutputStream(PREFIX+"methodheaders.h"), true);
      outclassdefs=new PrintWriter(new FileOutputStream(PREFIX+"classdefs.h"), true);
      outglobaldefs=new PrintWriter(new FileOutputStream(PREFIX+"globaldefs.h"), true);
      outvirtual=new PrintWriter(new FileOutputStream(PREFIX+"virtualtable.h"), true);
      outmethod=new PrintWriter(new FileOutputStream(PREFIX+"methods.c"), true);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }

    /* Build the virtual dispatch tables */
    super.buildVirtualTables(outvirtual);
    
    /* Tag the methods that are invoked by static blocks */
    super.tagMethodInvokedByStaticBlock();

    /* Output includes */
    outmethodheader.println("#ifndef METHODHEADERS_H");
    outmethodheader.println("#define METHODHEADERS_H");
    outmethodheader.println("#include \"structdefs.h\"");

    /* Output Structures */
    super.outputStructs(outstructs);

    outglobaldefs.println("#ifndef __GLOBALDEF_H_");
    outglobaldefs.println("#define __GLOBALDEF_H_");
    outglobaldefs.println("");
    outglobaldefs.println("struct global_defs_t {");
    
    // Output the C class declarations
    // These could mutually reference each other    
    outclassdefs.println("#ifndef __CLASSDEF_H_");
    outclassdefs.println("#define __CLASSDEF_H_");
    super.outputClassDeclarations(outclassdefs, outglobaldefs);

    // Output function prototypes and structures for parameters
    Iterator it=state.getClassSymbolTable().getDescriptorsIterator();
    int numclasses = this.state.numClasses();
    while(it.hasNext()) {
      ClassDescriptor cn=(ClassDescriptor)it.next();
      super.generateCallStructs(cn, outclassdefs, outstructs, outmethodheader, outglobaldefs);
    }
    // TODO add version for normal Java later
    outclassdefs.println("#include \"globaldefs.h\"");
    outclassdefs.println("#endif");
    outclassdefs.close();
    outglobaldefs.println("};");
    outglobaldefs.println("");
    outglobaldefs.println("extern struct global_defs_t * global_defs_p;");
    outglobaldefs.println("#endif");
    outglobaldefs.flush();
    outglobaldefs.close();

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
    
    outputStaticBlocks(outmethod);
    outputClassObjects(outmethod);
    
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
  
  protected void outputStaticBlocks(PrintWriter outmethod) {
    // execute all the static blocks and all the static field initializations
    SymbolTable sctbl = this.state.getSClassSymbolTable();
    Iterator it_sclasses = sctbl.getDescriptorsIterator();
    if(it_sclasses.hasNext()) {
      outmethod.println("#define MGC_STATIC_INIT_CHECK");
      while(it_sclasses.hasNext()) {
        ClassDescriptor t_cd = (ClassDescriptor)it_sclasses.next();
        if(t_cd.getNumStaticFields() != 0) {
          // TODO may need to invoke static field initialization here
        }
        MethodDescriptor t_md = (MethodDescriptor)t_cd.getMethodTable().get("staticblocks");
        if(t_md != null) {
          outmethod.println("   {");
          if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
            outmethod.print("       struct "+t_cd.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"_params __parameterlist__={");
            outmethod.println("1, NULL};");
            outmethod.println("     "+t_cd.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"(& __parameterlist__);");
          } else {
            outmethod.println("     "+t_cd.getSafeSymbol()+t_md.getSafeSymbol()+"_"+t_md.getSafeMethodDescriptor()+"();");
          }
          outmethod.println("   }");
        }
      }
      outmethod.println("#undef MGC_STATIC_INIT_CHECK");
    }
  }
  
  protected void outputClassObjects(PrintWriter outmethod) {
    // for each class, initialize its Class object
    if(state.MGC) {
      SymbolTable ctbl = this.state.getClassSymbolTable();
      Iterator it_classes = ctbl.getDescriptorsIterator();

      /*TypeDescriptor[] tdarray=new TypeDescriptor[1];
      tdarray[0] = new TypeDescriptor(((ClassDescriptor)this.state.getClassSymbolTable().get("Object")));
      
      TypeDescriptor typetolookin=new TypeDescriptor(((ClassDescriptor)this.state.getClassSymbolTable().get("Class")));;

      //find the constructor for 'Class' class
      ClassDescriptor classtolookin=typetolookin.getClassDesc();

      Set methoddescriptorset=classtolookin.getMethodTable().getSet(typetolookin.getSymbol());
      MethodDescriptor bestmd=null;
NextMethod:
      for(Iterator methodit=methoddescriptorset.iterator(); methodit.hasNext();) {
        MethodDescriptor currmd=(MethodDescriptor)methodit.next();
        // Need correct number of parameters 
        if (1!=currmd.numParameters())
          continue;
        for(int i=0; i<1; i++) {
          if (!typeutil.isSuperorType(currmd.getParamType(i),tdarray[i]))
            continue NextMethod;
        }
        // Local allocations can't call global allocator 
        if (currmd.isGlobal())
          continue;

        // Method okay so far 
        if (bestmd==null)
          bestmd=currmd;
        else {
          if (typeutil.isMoreSpecific(currmd,bestmd)) {
            bestmd=currmd;
          } else if (!typeutil.isMoreSpecific(bestmd, currmd)) {
            throw new Error("No method is most specific");
          }

          // Is this more specific than bestmd 
        }
      }
      if (bestmd==null)
        throw new Error("No constructor found for Class in ");
      */
      while(it_classes.hasNext()) {
        ClassDescriptor t_cd = (ClassDescriptor)it_classes.next();
        /*if(t_cd.getSymbol().equals("Class") || t_cd.getSymbol().equals("VMClass")) {
          continue;
        }*/
        // TODO initialize the Class object for this class  ++
        outmethod.println(" {");
        /*
        // create the vmdata object that record the class's type
        if(this.state.MULTICOREGC) {
          outmethod.println("    void * " + t_cd.getSafeSymbol() + "vmdata=allocate_new("+localsprefixaddr+", "+t_cd.getId()+");");              
        } else {
          outmethod.println("    void * " + t_cd.getSafeSymbol() + "vmdata=allocate_new("+t_cd.getId()+");");
        }
        // invoke the Class.constructor
        ParamsObject objectparams=(ParamsObject)paramstable.get(bestmd);
        if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
          outmethod.print("    struct "+classtolookin.getSafeSymbol()+bestmd.getSafeSymbol()+"_"+bestmd.getSafeMethodDescriptor()+"_params __parameterlist__={");
          outmethod.print(objectparams.numPointers());
          outmethod.print(", "+localsprefixaddr);
          if (bestmd.getThis()!=null) {
            outmethod.print(", ");
            outmethod.print("(struct "+bestmd.getThis().getType().getSafeSymbol() +" *)&(global_defs_p->"+t_cd.getSafeSymbol()+"classobj)");
          }

          Descriptor var=bestmd.getParameter(0);
          TempDescriptor paramtemp=(TempDescriptor)temptovar.get(var);
          if (objectparams.isParamPtr(paramtemp)) {
            outmethod.print(", ");
            TypeDescriptor td=bestmd.getParamType(0);
            outmethod.print("(struct "+bestmd.getParamType(0).getSafeSymbol()  +" *)" + t_cd.getSafeSymbol() + "vmdata");
          }
          outmethod.println("};");
        }
        outmethod.print("    ");

        outmethod.print(classtolookin.getSafeSymbol()+bestmd.getSafeSymbol()+"_"+bestmd.getSafeMethodDescriptor());

        outmethod.print("(");
        boolean needcomma=false;
        if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
          outmethod.print("&__parameterlist__");
          needcomma=true;
        }

        if (!GENERATEPRECISEGC && !this.state.MULTICOREGC) {
          TypeDescriptor ptd=null;
          if(bestmd.getThis() != null) {
            ptd = bestmd.getThis().getType();
          }
          if (needcomma)
            outmethod.print(",");
          if (ptd.isClass()&&!ptd.isArray())
            outmethod.print("(struct "+ptd.getSafeSymbol()+" *) ");
          outmethod.print("&(global_defs_p->"+t_cd.getSafeSymbol()+"classobj)");
          needcomma=true;
        }

        Descriptor var=bestmd.getParameter(0);
        TempDescriptor paramtemp=(TempDescriptor)temptovar.get(var);
        if (objectparams.isParamPrim(paramtemp)) {
          if (needcomma)
            outmethod.print(", ");

          TypeDescriptor ptd=bestmd.getParamType(0);
          if (ptd.isClass()&&!ptd.isArray())
            outmethod.print("(struct "+ptd.getSafeSymbol()+" *) ");
          outmethod.print(t_cd.getSafeSymbol() + "vmdata");
          needcomma=true;
        }
        outmethod.println(");");
        */
        outmethod.println("    global_defs_p->"+t_cd.getSafeSymbol()+"classobj.type = " + t_cd.getId() + ";");
        
        outmethod.println("    initlock((struct ___Object___ *)(&(global_defs_p->"+t_cd.getSafeSymbol()+"classobj)));");
        outmethod.println(" }");
        
      }
    } // else TODO normal java version
    
  }
}
