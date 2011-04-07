package IR.Flat;
import IR.*;
import IR.Tree.*;

import java.util.*;
import java.io.*;

import Util.*;
import Analysis.TaskStateAnalysis.*;
import Analysis.CallGraph.*;
import Analysis.Disjoint.*;
import Analysis.OoOJava.*;
import Analysis.Loops.*;
import Analysis.Locality.*;



public class BuildOoOJavaCode extends BuildCode {

  OoOJavaAnalysis oooa;

  String maxTaskRecSizeStr="__maxTaskRecSize___";

  String mlperrstr = 
    "if(status != 0) { "+
    "sprintf(errmsg, \"MLP error at %s:%d\", __FILE__, __LINE__); "+
    "perror(errmsg); exit(-1); }";

  RuntimeConflictResolver rcr = null;

  public BuildOoOJavaCode( State            st, 
                           Hashtable        temptovar, 
                           TypeUtil         typeutil, 
                           SafetyAnalysis   sa, 
                           OoOJavaAnalysis  oooa
                           ) {
    super( st, temptovar, typeutil, sa);

    this.oooa = oooa;
  }


  protected void additionalIncludesMethodsHeader( PrintWriter outmethodheader ) {

    outmethodheader.println("#include <stdlib.h>");
    outmethodheader.println("#include <stdio.h>");
    outmethodheader.println("#include <string.h>");
    outmethodheader.println("#include \"mlp_runtime.h\"");
    outmethodheader.println("#include \"psemaphore.h\"");
    outmethodheader.println("#include \"memPool.h\"");

    if (state.RCR) {
      outmethodheader.println("#include \"rcr_runtime.h\"");
    }

    // spit out a global to inform all worker threads what
    // the maximum size is for any task record
    outmethodheader.println("extern int "+maxTaskRecSizeStr+";");
  }


  protected void preCodeGenInitialization() {

    // have to initialize some SESE compiler data before
    // analyzing normal methods, which must happen before
    // generating SESE internal code
      
    Iterator<FlatSESEEnterNode> seseit = oooa.getAllSESEs().iterator();

    while( seseit.hasNext() ) {
      FlatSESEEnterNode fsen = seseit.next();
      initializeSESE( fsen );
    }
      
    //TODO signal the object that will report errors
    if( state.RCR ) {
      try {
        rcr = new RuntimeConflictResolver( PREFIX, 
                                           oooa,
                                           state );
        System.out.println("Runtime Conflict Resolver started.");
      } catch (FileNotFoundException e) {
        System.out.println("Runtime Conflict Resolver could not create output file.");
      }
    }
  }


  protected void initializeSESE( FlatSESEEnterNode fsen ) {

    FlatMethod       fm = fsen.getfmEnclosing();
    MethodDescriptor md = fm.getMethod();
    ClassDescriptor  cn = md.getClassDesc();    
        
    // Creates bogus method descriptor to index into tables
    Modifiers modBogus = new Modifiers();
    MethodDescriptor mdBogus = 
      new MethodDescriptor( modBogus, 
			    new TypeDescriptor( TypeDescriptor.VOID ), 
			    "sese_"+fsen.getPrettyIdentifier()+fsen.getIdentifier()
			    );
    
    mdBogus.setClassDesc( fsen.getcdEnclosing() );
    FlatMethod fmBogus = new FlatMethod( mdBogus, null );
    fsen.setfmBogus( fmBogus );
    fsen.setmdBogus( mdBogus );
    
    Set<TempDescriptor> inSetAndOutSet = new HashSet<TempDescriptor>();
    inSetAndOutSet.addAll( fsen.getInVarSet() );
    inSetAndOutSet.addAll( fsen.getOutVarSet() );

    // Build paramsobj for bogus method descriptor
    ParamsObject objectparams = new ParamsObject( mdBogus, tag++ );
    paramstable.put( mdBogus, objectparams );
    
    Iterator<TempDescriptor> itr = inSetAndOutSet.iterator();
    while( itr.hasNext() ) {
      TempDescriptor temp = itr.next();
      TypeDescriptor type = temp.getType();
      if( type.isPtr() ) {
	objectparams.addPtr( temp );
      } else {
	objectparams.addPrim( temp );
      }
    }
        
    // Build normal temp object for bogus method descriptor
    TempObject objecttemps = new TempObject( objectparams, mdBogus, tag++ );
    tempstable.put( mdBogus, objecttemps );

    for( Iterator nodeit = fsen.getNodeSet().iterator(); nodeit.hasNext(); ) {
      FlatNode         fn     = (FlatNode)nodeit.next();
      TempDescriptor[] writes = fn.writesTemps();

      for( int i = 0; i < writes.length; i++ ) {
	TempDescriptor temp = writes[i];
	TypeDescriptor type = temp.getType();

	if( type.isPtr() ) {
	  objecttemps.addPtr( temp );
	} else {
	  objecttemps.addPrim( temp );
	}
      }
    }
  }


  protected void postCodeGenCleanUp() {
    if(rcr != null) {
      rcr.close();
      System.out.println("Runtime Conflict Resolver Done.");
    }
  }

  
  protected void additionalCodeGen( PrintWriter outmethodheader,
                                    PrintWriter outstructs,
                                    PrintWriter outmethod ) {

    // Output function prototypes and structures for SESE's and code

    // spit out a global to inform all worker threads with
    // the maximum size is for any task record
    outmethod.println( "int "+maxTaskRecSizeStr+" = 0;" );

    // first generate code for each sese's internals     
    Iterator<FlatSESEEnterNode> seseit;
    seseit = oooa.getAllSESEs().iterator();
      
    while( seseit.hasNext() ) {
      FlatSESEEnterNode fsen = seseit.next();
      generateMethodSESE( fsen, outstructs, outmethodheader, outmethod );
    }

    // then write the invokeSESE switch to decouple scheduler
    // from having to do unique details of sese invocation
    generateSESEinvocationMethod( outmethodheader, outmethod );
  }


  protected void additionalCodeAtTopOfMain( PrintWriter outmethod ) {
    
    // do a calculation to determine which task record
    // is the largest, store that as a global value for
    // allocating records
    Iterator<FlatSESEEnterNode> seseit = oooa.getAllSESEs().iterator();
    while( seseit.hasNext() ) {
      FlatSESEEnterNode fsen = seseit.next();
      outmethod.println("if( sizeof( "+fsen.getSESErecordName()+
                        " ) > "+maxTaskRecSizeStr+
                        " ) { "+maxTaskRecSizeStr+
                        " = sizeof( "+fsen.getSESErecordName()+
                        " ); }" );
    }
      
    outmethod.println("  runningSESE = NULL;");

    outmethod.println("  workScheduleInit( "+state.OOO_NUMCORES+", invokeSESEmethod );");
      
    //initializes data structures needed for the RCR traverser
    if( state.RCR && rcr != null ) {
      outmethod.println("  initializeStructsRCR();");
      outmethod.println("  createAndFillMasterHashStructureArray();");
    }
  }


  protected void additionalCodeAtBottomOfMain( PrintWriter outmethod ) {
    outmethod.println("  workScheduleBegin();");
  }


  protected void additionalIncludesMethodsImplementation( PrintWriter outmethod ) {
    outmethod.println("#include <stdlib.h>");
    outmethod.println("#include <stdio.h>");
    outmethod.println("#include \"mlp_runtime.h\"");
    outmethod.println("#include \"psemaphore.h\"");
      
    if( state.RCR ) {
      outmethod.println("#include \"trqueue.h\"");
      outmethod.println("#include \"RuntimeConflictResolver.h\"");
      outmethod.println("#include \"rcr_runtime.h\"");
      outmethod.println("#include \"hashStructure.h\"");
    }
  }


  protected void additionalIncludesStructsHeader( PrintWriter outstructs ) {
    outstructs.println("#include \"mlp_runtime.h\"");
    outstructs.println("#include \"psemaphore.h\"");
    if( state.RCR ) {
      outstructs.println("#include \"rcr_runtime.h\"");
    }
  }


  protected void additionalClassObjectFields( PrintWriter outclassdefs ) {
    outclassdefs.println("  int oid;");
    outclassdefs.println("  int allocsite;");
  }


  protected void additionalCodeAtTopMethodsImplementation( PrintWriter outmethod ) {
    outmethod.print("extern __thread int oid;\n");
    outmethod.print("extern int oidIncrement;\n");
  }


  protected void additionalCodeAtTopFlatMethodBody( PrintWriter output, FlatMethod fm ) {

    // declare variables for naming static and dynamic SESE's
    ContextTaskNames context = oooa.getContextTaskNames( fm );

    output.println("   /* static SESE names */");
    Iterator<SESEandAgePair> pItr = context.getNeededStaticNames().iterator();
    while( pItr.hasNext() ) {
      SESEandAgePair pair = pItr.next();
      output.println("   void* "+pair+" = NULL;");
    }
    
    output.println("   /* dynamic variable sources */");
    Iterator<TempDescriptor> dynSrcItr = context.getDynamicVarSet().iterator();
    while( dynSrcItr.hasNext() ) {
      TempDescriptor dynSrcVar = dynSrcItr.next();
      output.println("   SESEcommon*  "+dynSrcVar+"_srcSESE = NULL;");
      output.println("   INTPTR       "+dynSrcVar+"_srcOffset = 0x1;");
    }    

          
    // eom - set up related allocation sites's waiting queues
    // TODO: we have to do a table-based thing here...
    // jjenista, I THINK WE LOSE THIS ALTOGETHER!
    /*
    FlatSESEEnterNode callerSESEplaceholder = (FlatSESEEnterNode) fm.getNext( 0 );
    if(callerSESEplaceholder!= oooa.getMainSESE()){
      Analysis.OoOJava.ConflictGraph graph = oooa.getConflictGraph(callerSESEplaceholder);       
      if (graph != null && graph.hasConflictEdge()) {          
        output.println("   // set up waiting queues ");
        output.println("   int numMemoryQueue=0;");
        output.println("   int memoryQueueItemID=0;");
        Set<Analysis.OoOJava.SESELock> lockSet = oooa.getLockMappings(graph);
        System.out.println("#lockSet="+lockSet.hashCode());
        System.out.println("lockset="+lockSet);
        for (Iterator iterator = lockSet.iterator(); iterator.hasNext();) {
          Analysis.OoOJava.SESELock seseLock = (Analysis.OoOJava.SESELock) iterator.next();
          System.out.println("id="+seseLock.getID());
          System.out.println("#="+seseLock);
        }
        System.out.println("size="+lockSet.size());
        if (lockSet.size() > 0) {
          output.println("   numMemoryQueue=" + lockSet.size() + ";");
          output.println("   runningSESE->numMemoryQueue=numMemoryQueue;");
          output.println("   runningSESE->memoryQueueArray=mlpCreateMemoryQueueArray(numMemoryQueue);");
          output.println();
        }
      }
    }
    */
  }


  protected void generateMethodSESE(FlatSESEEnterNode fsen,
                                    PrintWriter       outputStructs,
                                    PrintWriter       outputMethHead,
                                    PrintWriter       outputMethods) {

    ParamsObject objectparams = (ParamsObject) paramstable.get( fsen.getmdBogus() );                
    TempObject   objecttemps  = (TempObject)   tempstable .get( fsen.getmdBogus() );
    
    // generate locals structure
    outputStructs.println("struct "+
			  fsen.getcdEnclosing().getSafeSymbol()+
			  fsen.getmdBogus().getSafeSymbol()+"_"+
			  fsen.getmdBogus().getSafeMethodDescriptor()+
			  "_locals {");
    
    outputStructs.println("  int size;");
    outputStructs.println("  void * next;");

    for(int i=0; i<objecttemps.numPointers(); i++) {
      TempDescriptor temp=objecttemps.getPointer(i);

      if (temp.getType().isNull())
        outputStructs.println("  void * "+temp.getSafeSymbol()+";");
      else
        outputStructs.println("  struct "+
			      temp.getType().getSafeSymbol()+" * "+
			      temp.getSafeSymbol()+";");
    }
    outputStructs.println("};\n");

    
    // divide in-set and out-set into objects and primitives to prep
    // for the record generation just below
    Set<TempDescriptor> inSetAndOutSet = new HashSet<TempDescriptor>();
    inSetAndOutSet.addAll( fsen.getInVarSet() );
    inSetAndOutSet.addAll( fsen.getOutVarSet() );

    Set<TempDescriptor> inSetAndOutSetObjs  = new HashSet<TempDescriptor>();
    Set<TempDescriptor> inSetAndOutSetPrims = new HashSet<TempDescriptor>();

    Iterator<TempDescriptor> itr = inSetAndOutSet.iterator();
    while( itr.hasNext() ) {
      TempDescriptor temp = itr.next();
      TypeDescriptor type = temp.getType();
      if( type.isPtr() ) {
        inSetAndOutSetObjs.add( temp );
      } else {
	inSetAndOutSetPrims.add( temp );
      }
    }


    // generate the SESE record structure
    outputStructs.println(fsen.getSESErecordName()+" {");
    
    // data common to any SESE, and it must be placed first so
    // a module that doesn't know what kind of SESE record this
    // is can cast the pointer to a common struct
    outputStructs.println("  SESEcommon common;");

    // then garbage list stuff
    outputStructs.println("  /* next is in-set and out-set objects that look like a garbage list */");
    outputStructs.println("  int size;");
    outputStructs.println("  void * next;");

    // I think that the set of TempDescriptors inSetAndOutSetObjs
    // calculated above should match the pointer object params
    // used in the following code, but let's just leave the working
    // implementation unless there is actually a problem...

    Vector<TempDescriptor> inset=fsen.getInVarsForDynamicCoarseConflictResolution();
    for(int i=0; i<inset.size();i++) {
      TempDescriptor temp=inset.get(i);
      if (temp.getType().isNull())
	outputStructs.println("  void * "+temp.getSafeSymbol()+
			      ";  /* in-or-out-set obj in gl */");
      else
	outputStructs.println("  struct "+temp.getType().getSafeSymbol()+" * "+
			      temp.getSafeSymbol()+"; /* in-or-out-set obj in gl */");
    }

    for(int i=0; i<objectparams.numPointers(); i++) {
      TempDescriptor temp=objectparams.getPointer(i);
      if (!inset.contains(temp)) {
	if (temp.getType().isNull())
	  outputStructs.println("  void * "+temp.getSafeSymbol()+
				";  /* in-or-out-set obj in gl */");
	else
	  outputStructs.println("  struct "+temp.getType().getSafeSymbol()+" * "+
				temp.getSafeSymbol()+"; /* in-or-out-set obj in gl */");
      }
    }
    
    outputStructs.println("  /* next is primitives for in-set and out-set and dynamic tracking */");

    Iterator<TempDescriptor> itrPrims = inSetAndOutSetPrims.iterator();
    while( itrPrims.hasNext() ) {
      TempDescriptor temp = itrPrims.next();
      TypeDescriptor type = temp.getType();
      if(type.isPrimitive()){
    	  outputStructs.println("  "+temp.getType().getSafeSymbol()+" "+
                                temp.getSafeSymbol()+"; /* in-set or out-set primitive */");
      }      
    }
    
    // note that the sese record pointer will be added below, just primitive part of tracking here
    Iterator<TempDescriptor> itrDynInVars = fsen.getDynamicInVarSet().iterator();
    while( itrDynInVars.hasNext() ) {
      TempDescriptor dynInVar = itrDynInVars.next();
      outputStructs.println("  INTPTR "+dynInVar+"_srcOffset; /* dynamic tracking primitive */");
    }  
    
    
    outputStructs.println("  /* everything after this should be pointers to an SESE record */" );

    // other half of info for dynamic tracking, the SESE record pointer
    itrDynInVars = fsen.getDynamicInVarSet().iterator();
    while( itrDynInVars.hasNext() ) {
      TempDescriptor dynInVar = itrDynInVars.next();
      String depRecField = dynInVar+"_srcSESE";
      outputStructs.println("  SESEcommon* "+depRecField+";");
      addingDepRecField( fsen, depRecField );
    }  
    
    // statically known sese sources are record pointers, too
    Iterator<SESEandAgePair> itrStaticInVarSrcs = fsen.getStaticInVarSrcs().iterator();
    while( itrStaticInVarSrcs.hasNext() ) {
      SESEandAgePair srcPair = itrStaticInVarSrcs.next();
      outputStructs.println("  "+srcPair.getSESE().getSESErecordName()+"* "+srcPair+";");
      addingDepRecField(fsen, srcPair.toString());
    }

    if (state.RCR) {
      if (inset.size()!=0) {
        outputStructs.println("struct rcrRecord rcrRecords["+inset.size()+"];");
      } 
    }
    
    if( fsen.getFirstDepRecField() != null ) {
      outputStructs.println("  /* compiler believes first dependent SESE record field above is: "+
                            fsen.getFirstDepRecField()+" */" );
    }
    outputStructs.println("};\n");

    
    // write method declaration to header file
    outputMethHead.print("void ");
    outputMethHead.print(fsen.getSESEmethodName()+"(");
    outputMethHead.print(fsen.getSESErecordName()+"* "+paramsprefix);
    outputMethHead.println(");\n");


    generateFlatMethodSESE( fsen.getfmBogus(), 
			    fsen.getcdEnclosing(), 
			    fsen, 
			    fsen.getFlatExit(), 
			    outputMethods );
  }

  // used when generating the specific SESE record struct
  // to remember the FIRST field name of sese records 
  // that the current SESE depends on--we need to know the
  // offset to the first one for garbage collection
  protected void addingDepRecField( FlatSESEEnterNode fsen,
                                    String            field ) {
    if( fsen.getFirstDepRecField() == null ) {
      fsen.setFirstDepRecField( field );
    }
    fsen.incNumDepRecs();
  }


  private void generateFlatMethodSESE(FlatMethod        fm, 
                                      ClassDescriptor   cn, 
                                      FlatSESEEnterNode fsen, 
                                      FlatSESEExitNode  seseExit, 
                                      PrintWriter       output
                                      ) {

    MethodDescriptor md = fm.getMethod();

    output.print("void ");
    output.print(fsen.getSESEmethodName()+"(");
    output.print(fsen.getSESErecordName()+"* "+paramsprefix);
    output.println("){\n");


    TempObject objecttemp=(TempObject) tempstable.get(md);

    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
      output.print("   struct "+
                   cn.getSafeSymbol()+
                   md.getSafeSymbol()+"_"+
                   md.getSafeMethodDescriptor()+
                   "_locals "+localsprefix+"={");
      output.print(objecttemp.numPointers()+",");
      output.print("&(((SESEcommon*)(___params___))[1])");
      for(int j=0; j<objecttemp.numPointers(); j++)
	output.print(", NULL");
      output.println("};");
    }

    output.println("   /* regular local primitives */");
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


    // declare variables for naming static and dynamic SESE's
    ContextTaskNames context = oooa.getContextTaskNames( fsen );

    output.println("   /* static SESE names */");
    Iterator<SESEandAgePair> pItr = context.getNeededStaticNames().iterator();
    while( pItr.hasNext() ) {
      SESEandAgePair pair = pItr.next();
      output.println("   SESEcommon* "+pair+" = NULL;");
    }
    
    // declare variables for tracking dynamic sources
    output.println("   /* dynamic variable sources */");
    Iterator<TempDescriptor> dynSrcItr = context.getDynamicVarSet().iterator();
    while( dynSrcItr.hasNext() ) {
      TempDescriptor dynSrcVar = dynSrcItr.next();
      output.println("   SESEcommon*  "+dynSrcVar+"_srcSESE = NULL;");
      output.println("   INTPTR       "+dynSrcVar+"_srcOffset = 0x1;");
    }    


    // declare local temps for in-set primitives, and if it is
    // a ready-source variable, get the value from the record
    output.println("   /* local temps for in-set primitives */");
    Iterator<TempDescriptor> itrInSet = fsen.getInVarSet().iterator();
    while( itrInSet.hasNext() ) {
      TempDescriptor temp = itrInSet.next();
      TypeDescriptor type = temp.getType();
      if( !type.isPtr() ) {
	if( fsen.getReadyInVarSet().contains( temp ) ) {
	  output.println("   "+type+" "+temp+" = "+paramsprefix+"->"+temp+";");
	} else {
	  output.println("   "+type+" "+temp+";");
	}
      }
    }    

    // declare local temps for out-set primitives if its not already
    // in the in-set, and it's value will get written so no problem
    output.println("   /* local temp for out-set prim, not already in the in-set */");
    Iterator<TempDescriptor> itrOutSet = fsen.getOutVarSet().iterator();
    while( itrOutSet.hasNext() ) {
      TempDescriptor temp = itrOutSet.next();
      TypeDescriptor type = temp.getType();
      if( !type.isPtr() && !fsen.getInVarSet().contains( temp ) ) {
	output.println("   "+type+" "+temp+";");       
      }
    }


    // initialize thread-local var to a the task's record, which is fused
    // with the param list
    output.println("   ");
    output.println("   // code of this task's body should use this to access the running task record");
    output.println("   runningSESE = &(___params___->common);");
    output.println("   childSESE = 0;");
    output.println("   ");
    

    // eom - setup memory queue
    output.println("   // set up memory queues ");
    output.println("   int numMemoryQueue=0;");
    output.println("   int memoryQueueItemID=0;");
    Analysis.OoOJava.ConflictGraph graph = oooa.getConflictGraph( fsen );
    if( graph != null && graph.hasConflictEdge() ) {
      output.println("   {");
      Set<Analysis.OoOJava.SESELock> lockSet = oooa.getLockMappings( graph );
      System.out.println("#lockSet="+lockSet);
      if( lockSet.size() > 0 ) {
        output.println("   numMemoryQueue=" + lockSet.size() + ";");
        output.println("   runningSESE->numMemoryQueue=numMemoryQueue;");
        output.println("   runningSESE->memoryQueueArray=mlpCreateMemoryQueueArray(numMemoryQueue);");
        output.println();
      }
      output.println("   }");
    }


    // set up a task's mem pool to recycle the allocation of children tasks
    // don't bother if the task never has children (a leaf task)
    output.println( "#ifndef OOO_DISABLE_TASKMEMPOOL" );
    output.println( "/////////////////////////////////////////////" );
    output.println( "//" );
    output.println( "//  TODO: use poolcreate to make one record pool" );
    output.println( "//  per WORKER THREAD and never destroy it..." );
    output.println( "//" );
    output.println( "/////////////////////////////////////////////" );
    if( !fsen.getIsLeafSESE() ) {
      output.println("   runningSESE->taskRecordMemPool = poolcreate( "+
                     maxTaskRecSizeStr+", freshTaskRecordInitializer );");
      if (state.RCR && !rcr.hasEmptyTraversers(fsen)) {
        output.println("   createTR();");
        output.println("   runningSESE->allHashStructures=TRqueue->allHashStructures;");
      }
    } else {
      // make it clear we purposefully did not initialize this
      output.println("   runningSESE->taskRecordMemPool = (MemPool*)0x7;");
    }
    output.println( "#endif // OOO_DISABLE_TASKMEMPOOL" );


    // copy in-set into place, ready vars were already 
    // copied when the SESE was issued
    Iterator<TempDescriptor> tempItr;

    // static vars are from a known SESE
    output.println("   // copy variables from static sources");
    tempItr = fsen.getStaticInVarSet().iterator();
    while( tempItr.hasNext() ) {
      TempDescriptor temp = tempItr.next();
      VariableSourceToken vst = fsen.getStaticInVarSrc( temp );
      SESEandAgePair srcPair = new SESEandAgePair( vst.getSESE(), vst.getAge() );
      output.println("   "+generateTemp( fsen.getfmBogus(), temp)+
		     " = "+paramsprefix+"->"+srcPair+"->"+vst.getAddrVar()+";");
    }
    
    output.println("   // decrement references to static sources");
    for( Iterator<SESEandAgePair> pairItr = fsen.getStaticInVarSrcs().iterator(); pairItr.hasNext(); ) {
      SESEandAgePair srcPair = pairItr.next();
      output.println("#ifndef OOO_DISABLE_TASKMEMPOOL" );
      output.println("   {");
      output.println("     SESEcommon* src = &("+paramsprefix+"->"+srcPair+"->common);");
      output.println("     RELEASE_REFERENCE_TO( src );");
      output.println("   }");
      output.println("#endif // OOO_DISABLE_TASKMEMPOOL" );
    }


    // dynamic vars come from an SESE and src
    output.println("     // copy variables from dynamic sources");
    tempItr = fsen.getDynamicInVarSet().iterator();
    while( tempItr.hasNext() ) {
      TempDescriptor temp = tempItr.next();
      TypeDescriptor type = temp.getType();
      
      // go grab it from the SESE source
      output.println("   if( "+paramsprefix+"->"+temp+"_srcSESE != NULL ) {");

      String typeStr;
      if( type.isNull() ) {
	typeStr = "void*";
      } else if( type.isClass() || type.isArray() ) {
	typeStr = "struct "+type.getSafeSymbol()+"*";
      } else {
	typeStr = type.getSafeSymbol();
      }
      
      output.println("     "+generateTemp( fsen.getfmBogus(), temp)+
		     " = *(("+typeStr+"*) ((void*)"+
		     paramsprefix+"->"+temp+"_srcSESE + "+
		     paramsprefix+"->"+temp+"_srcOffset));");

      output.println("#ifndef OOO_DISABLE_TASKMEMPOOL" );
      output.println("     SESEcommon* src = "+paramsprefix+"->"+temp+"_srcSESE;");
      output.println("     RELEASE_REFERENCE_TO( src );");
      output.println("#endif // OOO_DISABLE_TASKMEMPOOL" );

      // or if the source was our parent, its already in our record to grab
      output.println("   } else {");
      output.println("     "+generateTemp( fsen.getfmBogus(), temp)+
		           " = "+paramsprefix+"->"+temp+";");
      output.println("   }");
    }

    // Check to see if we need to do a GC if this is a
    // multi-threaded program...    
    if ((GENERATEPRECISEGC) || (this.state.MULTICOREGC)) {
    	output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
      //Don't bother if we aren't in recursive methods...The loops case will catch it
//      if (callgraph.getAllMethods(md).contains(md)) {
//        if(this.state.MULTICOREGC) {
//          output.println("if(gcflag) gc("+localsprefixaddr+");");
//        } else {
//	  output.println("if (unlikely(needtocollect)) checkcollect("+localsprefixaddr+");");
//	}
//      }
    }    

    if( state.COREPROF ) {
      output.println("#ifdef CP_EVENTID_TASKEXECUTE");
      output.println("   CP_LOGEVENT( CP_EVENTID_TASKEXECUTE, CP_EVENTTYPE_BEGIN );");
      output.println("#endif");
    }

    HashSet<FlatNode> exitset=new HashSet<FlatNode>();
    exitset.add(seseExit);    
    generateCode(fsen.getNext(0), fm, exitset, output);
    output.println("}\n\n");    
  }


  // when a new mlp thread is created for an issued SESE, it is started
  // by running this method which blocks on a cond variable until
  // it is allowed to transition to execute.  Then a case statement
  // allows it to invoke the method with the proper SESE body, and after
  // exiting the SESE method, executes proper SESE exit code before the
  // thread can be destroyed
  private void generateSESEinvocationMethod(PrintWriter outmethodheader,
                                            PrintWriter outmethod
                                            ) {

    outmethodheader.println("void* invokeSESEmethod( void* seseRecord );");
    outmethod.println(      "void* invokeSESEmethod( void* seseRecord ) {");
    outmethod.println(      "  int status;");
    outmethod.println(      "  char errmsg[128];");

    // generate a case for each SESE class that can be invoked
    outmethod.println(      "  switch( ((SESEcommon*)seseRecord)->classID ) {");
    outmethod.println(      "    ");
    Iterator<FlatSESEEnterNode> seseit;
    seseit = oooa.getAllSESEs().iterator();

    while( seseit.hasNext() ) {
      FlatSESEEnterNode fsen = seseit.next();

      outmethod.println(    "    /* "+fsen.getPrettyIdentifier()+" */");
      outmethod.println(    "    case "+fsen.getIdentifier()+":");
      outmethod.println(    "      "+fsen.getSESEmethodName()+"( seseRecord );");  
      
      if( fsen.getIsMainSESE() ) {
        outmethod.println(  "      workScheduleExit();");
      }

      outmethod.println(    "      break;");
      outmethod.println(    "");
    }

    // default case should never be taken, error out
    outmethod.println(      "    default:");
    outmethod.println(      "      printf(\"Error: unknown SESE class ID in invoke method.\\n\");");
    outmethod.println(      "      exit(-30);");
    outmethod.println(      "      break;");
    outmethod.println(      "  }");
    outmethod.println(      "  return NULL;");
    outmethod.println(      "}\n\n");
  }



  protected void stallMEMRCR( FlatMethod fm, 
                              FlatNode fn, 
                              Set<WaitingElement> waitingElementSet, PrintWriter output) {
    output.println("// stall on parent's stall sites ");
    output.println("   {");
    output.println("     REntry* rentry;");
    output.println("     // stallrecord sometimes is used as a task record for instance ");
    output.println("     // when you call RELEASE_REFERENCE_TO on a stall record.");
    output.println("     // so the parent field must be initialized.");
    output.println("     SESEstall * stallrecord=(SESEstall *) poolalloc(runningSESE->taskRecordMemPool);");    
    output.println("     stallrecord->common.parent=runningSESE;");
    output.println("     stallrecord->common.unresolvedDependencies=10000;");
    output.println("     stallrecord->common.rcrstatus=1;");
    output.println("     stallrecord->common.offsetToParamRecords=(INTPTR) & (((SESEstall *)0)->rcrRecords);");
    output.println("     stallrecord->common.refCount = 3;");
    output.println("     int localCount=10000;");
    output.println("     stallrecord->rcrRecords[0].index=0;");
    output.println("     stallrecord->rcrRecords[0].flag=0;");
    output.println("     stallrecord->rcrRecords[0].next=NULL;");
    output.println("     stallrecord->common.parentsStallSem=&runningSESEstallSem;");
    output.println("     psem_reset( &runningSESEstallSem);");
    output.println("     stallrecord->tag=runningSESEstallSem.tag;");

    TempDescriptor stalltd=null;
    for (Iterator iterator = waitingElementSet.iterator(); iterator.hasNext();) {
      WaitingElement waitingElement =(WaitingElement) iterator.next();
      if (waitingElement.getStatus() >= ConflictNode.COARSE) {
	output.println("     rentry=mlpCreateREntry(runningSESE->memoryQueueArray["
		       + waitingElement.getQueueID() + "]," + waitingElement.getStatus()
		       + ", (SESEcommon *) stallrecord, 1LL);");
      } else {
	throw new Error("Fine-grained conflict: This should not happen in RCR");
      }
      output.println("     rentry->queue=runningSESE->memoryQueueArray["
		     + waitingElement.getQueueID() + "];");
      output.println("     if(ADDRENTRY(runningSESE->memoryQueueArray["
		     + waitingElement.getQueueID() + "],rentry)==NOTREADY) {");
      output.println("       localCount--;");
      output.println("     }");
      output.println("#if defined(RCR)&&!defined(OOO_DISABLE_TASKMEMPOOL)");
      output.println("     else poolfreeinto(runningSESE->memoryQueueArray["+
                     waitingElement.getQueueID()+
                     "]->rentrypool, rentry);");
      output.println("#endif");
      if (stalltd==null) {
	stalltd=waitingElement.getTempDesc();
      } else if (stalltd!=waitingElement.getTempDesc()) {
	throw new Error("Multiple temp descriptors at stall site"+stalltd+"!="+waitingElement.getTempDesc());
      }
    }

    //did all of the course grained stuff
    output.println("     if(!atomic_sub_and_test(localCount, &(stallrecord->common.unresolvedDependencies))) {");
    //have to do fine-grained work also
    output.println("       stallrecord->___obj___=(struct ___Object___ *)"
		   + generateTemp(fm, stalltd) + ";");
    output.println("       stallrecord->common.classID=-"
		   + rcr.getTraverserID(stalltd, fn) + ";");
    
    output.println("       enqueueTR(TRqueue, (void *)stallrecord);");

    if (state.COREPROF) {
      output.println("#ifdef CP_EVENTID_TASKSTALLMEM");
      output
	.println("        CP_LOGEVENT( CP_EVENTID_TASKSTALLMEM, CP_EVENTTYPE_BEGIN );");
      output.println("#endif");
    }    
    
    output.println("       psem_take( &runningSESEstallSem, (struct garbagelist *)&___locals___ );");
    
    if (state.COREPROF) {
      output.println("#ifdef CP_EVENTID_TASKSTALLMEM");
      output
	.println("        CP_LOGEVENT( CP_EVENTID_TASKSTALLMEM, CP_EVENTTYPE_END );");
      output.println("#endif");
    }

    output.println("     } else {");//exit if condition
    //release traversers reference if we didn't use traverser
    output.println("#ifndef OOO_DISABLE_TASKMEMPOOL");
    output.println("  RELEASE_REFERENCES_TO((SESEcommon *)stallrecord, 2);");
    output.println("#endif");
    output.println("     }");
    //release our reference to stall record
    output.println("#ifndef OOO_DISABLE_TASKMEMPOOL");
    output.println("  RELEASE_REFERENCE_TO((SESEcommon *)stallrecord);");
    output.println("#endif");
    output.println("   }");//exit block
  }


  protected void additionalCodePreNode( FlatMethod      fm, 
                                        FlatNode        fn, 
                                        PrintWriter     output ) {
    // insert pre-node actions from the code plan
      
    CodePlan cp = oooa.getCodePlan(fn);

    if( cp != null ) {

      // the current task for a code plan is either the
      // locally-defined enclosing task, or the caller proxy task.
      // When it is the caller proxy, it is possible to ask what are
      // all the possible tasks that the proxy might stand for
      FlatSESEEnterNode currentSESE = cp.getCurrentSESE();

      FlatMethod fmContext;
      if( currentSESE.getIsCallerProxySESE() ) {
        fmContext = oooa.getContainingFlatMethod( fn );
      } else {
        fmContext = currentSESE.getfmBogus();
      }

      ContextTaskNames contextTaskNames;
      if( currentSESE.getIsCallerProxySESE() ) {
        contextTaskNames = oooa.getContextTaskNames( oooa.getContainingFlatMethod( fn ) );
      } else {
        contextTaskNames = oooa.getContextTaskNames( currentSESE );
      }

      // for each sese and age pair that this parent statement
      // must stall on, take that child's stall semaphore, the
      // copying of values comes after the statement
      Iterator<VariableSourceToken> vstItr = cp.getStallTokens().iterator();
      while( vstItr.hasNext() ) {
        VariableSourceToken vst = vstItr.next();

        SESEandAgePair pair = new SESEandAgePair( vst.getSESE(), vst.getAge() );

        output.println("   {");
        output.println("     "+
                       pair.getSESE().getSESErecordName()+"* child = ("+
                       pair.getSESE().getSESErecordName()+"*) "+pair+";");

        output.println("     SESEcommon* childCom = (SESEcommon*) "+pair+";");

        if( state.COREPROF ) {
          output.println("#ifdef CP_EVENTID_TASKSTALLVAR");
          output.println("     CP_LOGEVENT( CP_EVENTID_TASKSTALLVAR, CP_EVENTTYPE_BEGIN );");
          output.println("#endif");
        }

        output.println("     pthread_mutex_lock( &(childCom->lock) );");
        output.println("     if( childCom->doneExecuting == FALSE ) {");
        output.println("       psem_reset( &runningSESEstallSem );");
        output.println("       childCom->parentsStallSem = &runningSESEstallSem;");
        output.println("       pthread_mutex_unlock( &(childCom->lock) );");
        output.println("       psem_take( &runningSESEstallSem, (struct garbagelist *)&___locals___ );");
        output.println("     } else {");
        output.println("       pthread_mutex_unlock( &(childCom->lock) );");
        output.println("     }");

        // copy things we might have stalled for	  	  
        Iterator<TempDescriptor> tdItr = cp.getCopySet( vst ).iterator();
        while( tdItr.hasNext() ) {
          TempDescriptor td = tdItr.next();
          output.println("       "+generateTemp( fmContext, td)+
                         " = child->"+vst.getAddrVar().getSafeSymbol()+";");
        }

        if( state.COREPROF ) {
          output.println("#ifdef CP_EVENTID_TASKSTALLVAR");
          output.println("     CP_LOGEVENT( CP_EVENTID_TASKSTALLVAR, CP_EVENTTYPE_END );");
          output.println("#endif");
        }

        output.println("   }");
      }
  
      // for each variable with a dynamic source, stall just for that variable
      Iterator<TempDescriptor> dynItr = cp.getDynamicStallSet().iterator();
      while( dynItr.hasNext() ) {
        TempDescriptor dynVar = dynItr.next();

        // only stall if the dynamic source is not yourself, denoted by src==NULL
        // otherwise the dynamic write nodes will have the local var up-to-date
        output.println("   {");
        output.println("     if( "+dynVar+"_srcSESE != NULL ) {");

        output.println("       SESEcommon* childCom = (SESEcommon*) "+dynVar+"_srcSESE;");

        if( state.COREPROF ) {
          output.println("#ifdef CP_EVENTID_TASKSTALLVAR");
          output.println("       CP_LOGEVENT( CP_EVENTID_TASKSTALLVAR, CP_EVENTTYPE_BEGIN );");
          output.println("#endif");
        }

        output.println("     pthread_mutex_lock( &(childCom->lock) );");
        output.println("     if( childCom->doneExecuting == FALSE ) {");
        output.println("       psem_reset( &runningSESEstallSem );");
        output.println("       childCom->parentsStallSem = &runningSESEstallSem;");
        output.println("       pthread_mutex_unlock( &(childCom->lock) );");
        output.println("       psem_take( &runningSESEstallSem, (struct garbagelist *)&___locals___ );");
        output.println("     } else {");
        output.println("       pthread_mutex_unlock( &(childCom->lock) );");
        output.println("     }");
	  
        TypeDescriptor type = dynVar.getType();
        String typeStr;
        if( type.isNull() ) {
          typeStr = "void*";
        } else if( type.isClass() || type.isArray() ) {
          typeStr = "struct "+type.getSafeSymbol()+"*";
        } else {
          typeStr = type.getSafeSymbol();
        }
      
        output.println("       "+generateTemp( fmContext, dynVar )+
                       " = *(("+typeStr+"*) ((void*)"+
                       dynVar+"_srcSESE + "+dynVar+"_srcOffset));");

        if( state.COREPROF ) {
          output.println("#ifdef CP_EVENTID_TASKSTALLVAR");
          output.println("       CP_LOGEVENT( CP_EVENTID_TASKSTALLVAR, CP_EVENTTYPE_END );");
          output.println("#endif");
        }

        output.println("     }");
        output.println("   }");
      }

      // for each assignment of a variable to rhs that has a dynamic source,
      // copy the dynamic sources
      Iterator dynAssignItr = cp.getDynAssigns().entrySet().iterator();
      while( dynAssignItr.hasNext() ) {
        Map.Entry      me  = (Map.Entry)      dynAssignItr.next();
        TempDescriptor lhs = (TempDescriptor) me.getKey();
        TempDescriptor rhs = (TempDescriptor) me.getValue();

        output.println("   {");
        output.println("   SESEcommon* oldSrc = "+lhs+"_srcSESE;");
          
        output.println("   "+lhs+"_srcSESE   = "+rhs+"_srcSESE;");
        output.println("   "+lhs+"_srcOffset = "+rhs+"_srcOffset;");

        // no matter what we did above, track reference count of whatever
        // this variable pointed to, do release last in case we're just
        // copying the same value in because 1->2->1 is safe but ref count
        // 1->0->1 has a window where it looks like it should be free'd
        output.println("#ifndef OOO_DISABLE_TASKMEMPOOL" );
        output.println("     if( "+rhs+"_srcSESE != NULL ) {");
        output.println("       ADD_REFERENCE_TO( "+rhs+"_srcSESE );");
        output.println("     }");
        output.println("     if( oldSrc != NULL ) {");
        output.println("       RELEASE_REFERENCE_TO( oldSrc );");
        output.println("     }");
        output.println("   }");
        output.println("#endif // OOO_DISABLE_TASKMEMPOOL" );
      }

      // for each lhs that is dynamic from a non-dynamic source, set the
      // dynamic source vars to the current SESE
      dynItr = cp.getDynAssignCurr().iterator();
      while( dynItr.hasNext() ) {
        TempDescriptor dynVar = dynItr.next();	  

        assert contextTaskNames.getDynamicVarSet().contains( dynVar );

        // first release a reference to current record
        output.println("#ifndef OOO_DISABLE_TASKMEMPOOL" );
        output.println("   if( "+dynVar+"_srcSESE != NULL ) {");
        output.println("     RELEASE_REFERENCE_TO( oldSrc );");
        output.println("   }");
        output.println("#endif // OOO_DISABLE_TASKMEMPOOL" );

        output.println("   "+dynVar+"_srcSESE = NULL;");
      }

	
      // handling stall site, consider that one of several tasks might be
      // executing, so create a switch on task ID, because waiting elements
      // generated by this stall site should be inserted into possibly a
      // different memory queue index, depending on which task type it is
      // update: only generate the switch statement if there is at least
      // one non-empty case that will go in it!
      boolean atLeastOneCase = false;

      // create a case for each class of task that might be executing
      Iterator<FlatSESEEnterNode> taskItr = oooa.getPossibleExecutingRBlocks( fn ).iterator();
      while( taskItr.hasNext() ) {
        FlatSESEEnterNode parent = taskItr.next();
        ConflictGraph     graph  = oooa.getConflictGraph( parent );

        if( graph == null ) {
          continue;
        }

        Set<SESELock>       seseLockSet       = oooa.getLockMappings( graph );
        Set<WaitingElement> waitingElementSet = graph.getStallSiteWaitingElementSet( fn, seseLockSet );
        
        if( waitingElementSet.size() == 0 ) {
          continue;
        }

        // TODO: THIS STRATEGY CAN BE OPTIMIZED EVEN FURTHER, IF THERE
        // IS EXACTLY ONE CASE, DON'T GENERATE A SWITCH AT ALL
        if( atLeastOneCase == false ) {
          atLeastOneCase = true;
          output.println("   // potential stall site ");      
          output.println("   switch( runningSESE->classID ) {");
        }

        output.println("     case "+parent.getIdentifier()+": {");

        if( state.RCR ) {
          stallMEMRCR(fm, fn, waitingElementSet, output);
        } else {

          output.println("       REntry* rentry;");
		
          for( Iterator iterator = waitingElementSet.iterator(); iterator.hasNext(); ) {
            WaitingElement waitingElement = (WaitingElement) iterator.next();

            if (waitingElement.getStatus() >= ConflictNode.COARSE) {
              output.println("       rentry=mlpCreateREntry(runningSESE->memoryQueueArray["
                             + waitingElement.getQueueID() + "]," + waitingElement.getStatus()
                             + ", runningSESE);");
            } else {
              output.println("       rentry=mlpCreateFineREntry(runningSESE->memoryQueueArray["
                             + waitingElement.getQueueID() + "]," + waitingElement.getStatus()
                             + ", runningSESE,  (void*)&"
                             + generateTemp(fm, waitingElement.getTempDesc()) + ");");
            }
            output.println("       rentry->parentStallSem=&runningSESEstallSem;");
            output.println("       psem_reset( &runningSESEstallSem);");
            output.println("       rentry->tag=runningSESEstallSem.tag;");
            output.println("       rentry->queue=runningSESE->memoryQueueArray["
                           + waitingElement.getQueueID() + "];");
            output.println("       if(ADDRENTRY(runningSESE->memoryQueueArray["
                           + waitingElement.getQueueID() + "],rentry)==NOTREADY){");
            if (state.COREPROF) {
              output.println("#ifdef CP_EVENTID_TASKSTALLMEM");
              output.println("       CP_LOGEVENT( CP_EVENTID_TASKSTALLMEM, CP_EVENTTYPE_BEGIN );");
              output.println("#endif");
            }
		  
            output.println("       psem_take( &runningSESEstallSem, (struct garbagelist *)&___locals___ );");
		  
            if (state.COREPROF) {
              output.println("#ifdef CP_EVENTID_TASKSTALLMEM");
              output.println("       CP_LOGEVENT( CP_EVENTID_TASKSTALLMEM, CP_EVENTTYPE_END );");
              output.println("#endif");
            }
            output.println("     }  ");
          }

        }
        output.println("     } break; // end case "+parent.getIdentifier());
      }

      if( atLeastOneCase ) {
        output.println("   } // end stall site switch");
      }
    }
  }
  

  protected void additionalCodePostNode( FlatMethod      fm, 
                                         FlatNode        fn, 
                                         PrintWriter     output ) {

    // insert post-node actions from the code-plan (none right now...)
  }


  public void generateFlatSESEEnterNode( FlatMethod        fm,  
					 FlatSESEEnterNode fsen, 
					 PrintWriter       output ) {

    // there may be an SESE in an unreachable method, skip over
    if( !oooa.getAllSESEs().contains( fsen ) ) {
      return;
    }

    // assert we are never generating code for the caller proxy
    // it should only appear in analysis results
    assert !fsen.getIsCallerProxySESE();


    output.println("   { // issue new task instance");

    if( state.COREPROF ) {
      output.println("#ifdef CP_EVENTID_TASKDISPATCH");
      output.println("     CP_LOGEVENT( CP_EVENTID_TASKDISPATCH, CP_EVENTTYPE_BEGIN );");
      output.println("#endif");
    }


    // before doing anything, lock your own record and increment the running children
    if( !fsen.getIsMainSESE() ) {
      output.println("     childSESE++;");
    }

    // allocate the space for this record
    output.println( "#ifndef OOO_DISABLE_TASKMEMPOOL" );

    output.println( "#ifdef CP_EVENTID_POOLALLOC");
    output.println( "     CP_LOGEVENT( CP_EVENTID_POOLALLOC, CP_EVENTTYPE_BEGIN );");
    output.println( "#endif");
    if( !fsen.getIsMainSESE() ) {
      output.println("     "+
                     fsen.getSESErecordName()+"* seseToIssue = ("+
                     fsen.getSESErecordName()+"*) poolalloc( runningSESE->taskRecordMemPool );");
      output.println("     CHECK_RECORD( seseToIssue );");
    } else {
      output.println("     "+
                     fsen.getSESErecordName()+"* seseToIssue = ("+
                     fsen.getSESErecordName()+"*) mlpAllocSESErecord( sizeof( "+
                     fsen.getSESErecordName()+" ) );");
    }
    output.println( "#ifdef CP_EVENTID_POOLALLOC");
    output.println( "     CP_LOGEVENT( CP_EVENTID_POOLALLOC, CP_EVENTTYPE_END );");
    output.println( "#endif");

    output.println( "#else // OOO_DISABLE_TASKMEMPOOL" );
      output.println("     "+
                     fsen.getSESErecordName()+"* seseToIssue = ("+
                     fsen.getSESErecordName()+"*) mlpAllocSESErecord( sizeof( "+
                     fsen.getSESErecordName()+" ) );");
    output.println( "#endif // OOO_DISABLE_TASKMEMPOOL" );


    // set up the SESE in-set and out-set objects, which look
    // like a garbage list
    output.println("     struct garbagelist * gl= (struct garbagelist *)&(((SESEcommon*)(seseToIssue))[1]);");
    output.println("     gl->size="+calculateSizeOfSESEParamList(fsen)+";");
    output.println("     gl->next = NULL;");
    output.println("     seseToIssue->common.rentryIdx=0;");

    if(state.RCR) {
      //flag the SESE status as 1...it will be reset
      output.println("     seseToIssue->common.rcrstatus=1;");
    }

    // there are pointers to SESE records the newly-issued SESE
    // will use to get values it depends on them for--how many
    // are there, and what is the offset from the total SESE
    // record to the first dependent record pointer?
    output.println("     seseToIssue->common.numDependentSESErecords="+
                   fsen.getNumDepRecs()+";");
    
    // we only need this (and it will only compile) when the number of dependent
    // SESE records is non-zero
    if( fsen.getFirstDepRecField() != null ) {
      output.println("     seseToIssue->common.offsetToDepSESErecords=(INTPTR)sizeof("+
                     fsen.getSESErecordName()+") - (INTPTR)&((("+
                     fsen.getSESErecordName()+"*)0)->"+fsen.getFirstDepRecField()+");"
                     );
    }
    
    if( state.RCR &&
        fsen.getInVarsForDynamicCoarseConflictResolution().size() > 0 
        ) {
      output.println("    seseToIssue->common.offsetToParamRecords=(INTPTR) & ((("+
                     fsen.getSESErecordName()+"*)0)->rcrRecords);");
    }

    // fill in common data
    output.println("     int localCount=0;");
    output.println("     seseToIssue->common.classID = "+fsen.getIdentifier()+";");
    output.println("     seseToIssue->common.unresolvedDependencies = 10000;");
    output.println("     seseToIssue->common.parentsStallSem = NULL;");
    output.println("     initQueue(&seseToIssue->common.forwardList);");
    output.println("     seseToIssue->common.doneExecuting = FALSE;");    
    output.println("     seseToIssue->common.numRunningChildren = 0;");
    output.println( "#ifdef OOO_DISABLE_TASKMEMPOOL" );
    output.println("     pthread_cond_init( &(seseToIssue->common.runningChildrenCond), NULL );");
    output.println("#endif");
    output.println("     seseToIssue->common.parent = runningSESE;");
    // start with refCount = 2, one being the count that the child itself
    // will decrement when it retires, to say it is done using its own
    // record, and the other count is for the parent that will remember
    // the static name of this new child below
    if( state.RCR ) {
      // if we're using RCR, ref count is 3 because the traverser has
      // a reference, too
      if( !fsen.getIsMainSESE() && fsen.getInVarsForDynamicCoarseConflictResolution().size()>0){
        output.println("     seseToIssue->common.refCount = 10003;");
      } else {
        output.println("     seseToIssue->common.refCount = 10002;");
      }
      output.println("     int refCount=10000;");
    } else {
      output.println("     seseToIssue->common.refCount = 2;");
    }

    // all READY in-vars should be copied now and be done with it
    Iterator<TempDescriptor> tempItr = fsen.getReadyInVarSet().iterator();
    while( tempItr.hasNext() ) {
      TempDescriptor temp = tempItr.next();

      // determine whether this new task instance is in a method context,
      // or within the body of another task
      assert !fsen.getIsCallerProxySESE();
      FlatSESEEnterNode parent = fsen.getLocalParent();
      if( parent != null && !parent.getIsCallerProxySESE() ) {
	output.println("     seseToIssue->"+temp+" = "+
		       generateTemp( parent.getfmBogus(), temp )+";");	 
      } else {
	output.println("     seseToIssue->"+temp+" = "+
		       generateTemp( fsen.getfmEnclosing(), temp )+";");
      }
    }
    
    // before potentially adding this SESE to other forwarding lists,
    // create it's lock
    output.println( "#ifdef OOO_DISABLE_TASKMEMPOOL" );
    output.println("     pthread_mutex_init( &(seseToIssue->common.lock), NULL );");
    output.println("#endif");
  
    if( !fsen.getIsMainSESE() ) {
      // count up outstanding dependencies, static first, then dynamic
      Iterator<SESEandAgePair> staticSrcsItr = fsen.getStaticInVarSrcs().iterator();
      while( staticSrcsItr.hasNext() ) {
	SESEandAgePair srcPair = staticSrcsItr.next();
	output.println("     {");
	output.println("       SESEcommon* src = (SESEcommon*)"+srcPair+";");
	output.println("       pthread_mutex_lock( &(src->lock) );");
        // FORWARD TODO - ...what? make it a chain of arrays instead of true linked-list?
	output.println("       if( !src->doneExecuting ) {");
        output.println("         addNewItem( &src->forwardList, seseToIssue );");	
	output.println("         ++(localCount);");
	output.println("       }");
        output.println("#ifndef OOO_DISABLE_TASKMEMPOOL" );
        output.println("       ADD_REFERENCE_TO( src );");
        output.println("#endif" );
	output.println("       pthread_mutex_unlock( &(src->lock) );");
	output.println("     }");

	// whether or not it is an outstanding dependency, make sure
	// to pass the static name to the child's record
	output.println("     seseToIssue->"+srcPair+" = "+
                       "("+srcPair.getSESE().getSESErecordName()+"*)"+
                       srcPair+";");
      }
      
      // dynamic sources might already be accounted for in the static list,
      // so only add them to forwarding lists if they're not already there
      Iterator<TempDescriptor> dynVarsItr = fsen.getDynamicInVarSet().iterator();
      while( dynVarsItr.hasNext() ) {
	TempDescriptor dynInVar = dynVarsItr.next();
	output.println("     {");
	output.println("       SESEcommon* src = (SESEcommon*)"+dynInVar+"_srcSESE;");

	// the dynamic source is NULL if it comes from your own space--you can't pass
	// the address off to the new child, because you're not done executing and
	// might change the variable, so copy it right now
	output.println("       if( src != NULL ) {");
	output.println("         pthread_mutex_lock( &(src->lock) );");

        // FORWARD TODO

	output.println("         if( isEmpty( &src->forwardList ) ||");
	output.println("             seseToIssue != peekItem( &src->forwardList ) ) {");
	output.println("           if( !src->doneExecuting ) {");
	output.println("             addNewItem( &src->forwardList, seseToIssue );");
	output.println("             ++(localCount);");
	output.println("           }");
	output.println("         }");
        output.println("#ifndef OOO_DISABLE_TASKMEMPOOL" );
        output.println("         ADD_REFERENCE_TO( src );");
        output.println("#endif" );
	output.println("         pthread_mutex_unlock( &(src->lock) );");	
	output.println("         seseToIssue->"+dynInVar+"_srcOffset = "+dynInVar+"_srcOffset;");
	output.println("       } else {");


        // determine whether this new task instance is in a method context,
        // or within the body of another task
        assert !fsen.getIsCallerProxySESE();
        FlatSESEEnterNode parent = fsen.getLocalParent();
        if( parent != null && !parent.getIsCallerProxySESE() ) {
          output.println("         seseToIssue->"+dynInVar+" = "+
          		 generateTemp( parent.getfmBogus(), dynInVar )+";");
        } else {
          output.println("         seseToIssue->"+dynInVar+" = "+
			 generateTemp( fsen.getfmEnclosing(), dynInVar )+";");
        }
	
	output.println("       }");
	output.println("     }");
	
	// even if the value is already copied, make sure your NULL source
	// gets passed so child knows it already has the dynamic value
	output.println("     seseToIssue->"+dynInVar+"_srcSESE = "+dynInVar+"_srcSESE;");
      }


      // maintain pointers for finding dynamic SESE 
      // instances from static names, do a shuffle as instances age
      // and also release references that have become too old
      if( !fsen.getIsMainSESE() ) {

        FlatSESEEnterNode currentSESE = fsen.getLocalParent();        

        ContextTaskNames contextTaskNames;
        if( currentSESE == null ) {
          contextTaskNames = oooa.getContextTaskNames( oooa.getContainingFlatMethod( fsen ) );
        } else {
          contextTaskNames = oooa.getContextTaskNames( currentSESE );
        }

        SESEandAgePair pairNewest = new SESEandAgePair( fsen, 0 );
        SESEandAgePair pairOldest = new SESEandAgePair( fsen, fsen.getOldestAgeToTrack() );
        if( contextTaskNames.getNeededStaticNames().contains( pairNewest ) ) {       
          output.println("     {");
          output.println("#ifndef OOO_DISABLE_TASKMEMPOOL" );
          output.println("       SESEcommon* oldest = "+pairOldest+";");
          output.println("#endif // OOO_DISABLE_TASKMEMPOOL" );

          for( int i = fsen.getOldestAgeToTrack(); i > 0; --i ) {
            SESEandAgePair pair1 = new SESEandAgePair( fsen, i   );
            SESEandAgePair pair2 = new SESEandAgePair( fsen, i-1 );
            output.println("       "+pair1+" = "+pair2+";");
          }      
          output.println("       "+pairNewest+" = &(seseToIssue->common);");

          // no need to add a reference to whatever is the newest record, because
          // we initialized seseToIssue->refCount to *2*
          // but release a reference to whatever was the oldest BEFORE the shift
          output.println("#ifndef OOO_DISABLE_TASKMEMPOOL" );
          output.println("       if( oldest != NULL ) {");
          output.println("         RELEASE_REFERENCE_TO( oldest );");
          output.println("       }");
          output.println("#endif // OOO_DISABLE_TASKMEMPOOL" );
          output.println("     }");
        }
      }
    }


    //////////////////////
    // count up memory conflict dependencies,
    ///////////////////////
    if( !fsen.getIsMainSESE() ) {

      if( state.COREPROF ) {
        output.println("#ifdef CP_EVENTID_PREPAREMEMQ");
        output.println("     CP_LOGEVENT( CP_EVENTID_PREPAREMEMQ, CP_EVENTTYPE_BEGIN );");
        output.println("#endif");
      }

      if(state.RCR) {
        dispatchMEMRC(fm, fsen, output);
      } else {

        // there may be several task types that can get to this
        // program point (issue this new task) so create a switch
        // based on task ID, each type of task has a different index
        // scheme for its memory queue's, and the cases here drop the
        // new task instance in the right bucket
        boolean atLeastOneCase = false;

        // create a case for each class of task that might be executing
        Iterator<FlatSESEEnterNode> taskItr = oooa.getPossibleExecutingRBlocks( fsen ).iterator();
        while( taskItr.hasNext() ) {
          FlatSESEEnterNode parent = taskItr.next();
          ConflictGraph     graph  = oooa.getConflictGraph( parent );

          if( graph == null || !graph.hasConflictEdge() ) {
            continue;
          }

          Set<SESELock> seseLockSet = oooa.getLockMappings(graph);

          SESEWaitingQueue seseWaitingQueue =
            graph.getWaitingElementSetBySESEID(fsen.getIdentifier(), seseLockSet);
          
          if( seseWaitingQueue.getWaitingElementSize() == 0 ) {
            continue;
          }

          if( atLeastOneCase == false ) {
            atLeastOneCase = true;
            output.println("   // add new task instance to current task's memory queues if needed ");      
            output.println("   switch( runningSESE->classID ) {");
          }

          output.println("     case "+parent.getIdentifier()+": {");
          output.println("       REntry* rentry=NULL;");
          output.println("       INTPTR* pointer=NULL;");
          output.println("       seseToIssue->common.rentryIdx=0;");

          Set<Integer> queueIDSet=seseWaitingQueue.getQueueIDSet();
          for (Iterator iterator = queueIDSet.iterator(); iterator.hasNext();) {
            Integer key = (Integer) iterator.next();
            int queueID=key.intValue();
            Set<WaitingElement> waitingQueueSet =  
              seseWaitingQueue.getWaitingElementSet(queueID);
            int enqueueType=seseWaitingQueue.getType(queueID);
            if(enqueueType==SESEWaitingQueue.EXCEPTION) {
              output.println("       INITIALIZEBUF(runningSESE->memoryQueueArray[" + queueID+ "]);");
            }
            for (Iterator iterator2 = waitingQueueSet.iterator(); iterator2.hasNext();) {
              WaitingElement waitingElement 
                = (WaitingElement) iterator2.next();
              if (waitingElement.getStatus() >= ConflictNode.COARSE) {
                output.println("       rentry=mlpCreateREntry(runningSESE->memoryQueueArray["+ queueID+ "],"
                               + waitingElement.getStatus()
                               + ", &(seseToIssue->common));");
              } else {
                TempDescriptor td = waitingElement.getTempDesc();
                // decide whether waiting element is dynamic or static
                if (fsen.getDynamicInVarSet().contains(td)) {
                  // dynamic in-var case
                  output.println("       pointer=seseToIssue->"
                                 + waitingElement.getDynID()
                                 + "_srcSESE+seseToIssue->"
                                 + waitingElement.getDynID()
                                 + "_srcOffset;");
                  output.println("       rentry=mlpCreateFineREntry(runningSESE->memoryQueueArray["+ queueID+ "],"
                                 + waitingElement.getStatus()
                                 + ", &(seseToIssue->common),  pointer );");
                } else if (fsen.getStaticInVarSet().contains(td)) {
                  // static in-var case
                  VariableSourceToken vst = fsen.getStaticInVarSrc(td);
                  if (vst != null) {
  
                    String srcId = "SESE_" + vst.getSESE().getPrettyIdentifier()
                      + vst.getSESE().getIdentifier()
                      + "_" + vst.getAge();
                    output.println("       pointer=(void*)&seseToIssue->"
                                   + srcId
                                   + "->"
                                   + waitingElement
                                   .getDynID()
                                   + ";");
                    output.println("       rentry=mlpCreateFineREntry(runningSESE->memoryQueueArray["+ queueID+ "],"
                                   + waitingElement.getStatus()
                                   + ", &(seseToIssue->common),  pointer );");
                  }
                } else {
                  output.println("       rentry=mlpCreateFineREntry(runningSESE->memoryQueueArray["+ queueID+ "],"
                                 + waitingElement.getStatus()
                                 + ", &(seseToIssue->common), (void*)&seseToIssue->"
                                 + waitingElement.getDynID()
                                 + ");");
                }
              }
              output.println("       rentry->queue=runningSESE->memoryQueueArray["
                             + waitingElement.getQueueID()
                             + "];");
                
              if(enqueueType==SESEWaitingQueue.NORMAL){
                output.println("       seseToIssue->common.rentryArray[seseToIssue->common.rentryIdx++]=rentry;");
                output.println("       if(ADDRENTRY(runningSESE->memoryQueueArray["
                               + waitingElement.getQueueID()
                               + "],rentry)==NOTREADY) {");
                output.println("          localCount++;");
                output.println("       }");
              } else {
                output.println("       ADDRENTRYTOBUF(runningSESE->memoryQueueArray[" + waitingElement.getQueueID() + "],rentry);");
              }
            }
            if(enqueueType!=SESEWaitingQueue.NORMAL){
              output.println("       localCount+=RESOLVEBUF(runningSESE->memoryQueueArray["
                             + queueID+ "],&seseToIssue->common);");
            }       
          }
          output.println("     } break; // end case "+parent.getIdentifier());
          output.println();          
        }

        if( atLeastOneCase ) {
          output.println("   } // end stall site switch");
        }
      }      
      
      if( state.COREPROF ) {
        output.println("#ifdef CP_EVENTID_PREPAREMEMQ");
        output.println("     CP_LOGEVENT( CP_EVENTID_PREPAREMEMQ, CP_EVENTTYPE_END );");
        output.println("#endif");
      }
    }

    // Enqueue Task Record
    if (state.RCR) {
      if( fsen != oooa.getMainSESE() && fsen.getInVarsForDynamicCoarseConflictResolution().size()>0){
        output.println("    enqueueTR(TRqueue, (void *)seseToIssue);");
      }
    }

    // if there were no outstanding dependencies, issue here
    output.println("     if(  atomic_sub_and_test(10000-localCount,&(seseToIssue->common.unresolvedDependencies) ) ) {");
    output.println("       workScheduleSubmit( (void*)seseToIssue );");
    output.println("     }");

    

    if( state.COREPROF ) {
      output.println("#ifdef CP_EVENTID_TASKDISPATCH");
      output.println("     CP_LOGEVENT( CP_EVENTID_TASKDISPATCH, CP_EVENTTYPE_END );");
      output.println("#endif");
    }

    output.println("   } // end task issue");
  }


  void dispatchMEMRC( FlatMethod        fm,  
                      FlatSESEEnterNode newChild, 
                      PrintWriter       output ) {   
    // what we need to do here is create RCR records for the
    // new task and insert it into the appropriate parent queues
    // IF NEEDED!!!!!!!!
    assert newChild.getParents().size() > 0;

    output.println("     switch( runningSESE->classID ) {");

    Iterator<FlatSESEEnterNode> pItr = newChild.getParents().iterator();
    while( pItr.hasNext() ) {

      FlatSESEEnterNode parent = pItr.next();
      ConflictGraph     graph  = oooa.getConflictGraph( parent );

      if( graph != null && graph.hasConflictEdge() ) {
        Set<SESELock> seseLockSet = oooa.getLockMappings(graph);
        SESEWaitingQueue seseWaitingQueue=graph.getWaitingElementSetBySESEID(newChild.getIdentifier(), seseLockSet);
        if(seseWaitingQueue.getWaitingElementSize()>0) {

          output.println("       /* "+parent.getPrettyIdentifier()+" */");
          output.println("       case "+parent.getIdentifier()+": {");

          output.println("         REntry* rentry=NULL;");
          output.println("         INTPTR* pointer=NULL;");
          output.println("         seseToIssue->common.rentryIdx=0;");
          Vector<TempDescriptor> invars=newChild.getInVarsForDynamicCoarseConflictResolution();
          //System.out.println(fm.getMethod()+"["+invars+"]");
	
          Vector<Long> queuetovar=new Vector<Long>();

          for(int i=0;i<invars.size();i++) {
            TempDescriptor td=invars.get(i);
            Set<WaitingElement> weset=seseWaitingQueue.getWaitingElementSet(td);
            
            //TODO FIX MEEEEE!!!!
            //Weset is sometimes null which breaks the following code and 
            //we don't know what weset = null means. For now, we bail when it's null
            //until we find out what to do....
//            if(weset == null) {
//              continue;
//            }
            //UPDATE: This hack DOES NOT FIX IT!.
            
            
            
            int numqueues=0;
            Set<Integer> queueSet=new HashSet<Integer>();
            for (Iterator iterator = weset.iterator(); iterator.hasNext();) {
              WaitingElement  we = (WaitingElement) iterator.next();
              Integer queueID=new Integer( we.getQueueID());
              if(!queueSet.contains(queueID)){
                numqueues++;
                queueSet.add(queueID);
              }	   
            }

            output.println("        seseToIssue->rcrRecords["+i+"].flag="+numqueues+";");
            output.println("        seseToIssue->rcrRecords["+i+"].index=0;");
            output.println("        seseToIssue->rcrRecords["+i+"].next=NULL;");
            output.println("        int dispCount"+i+"=0;");

            for (Iterator<WaitingElement> wtit = weset.iterator(); wtit.hasNext();) {
              WaitingElement waitingElement = wtit.next();
              int queueID = waitingElement.getQueueID();
              if (queueID >= queuetovar.size())
                queuetovar.setSize(queueID + 1);
              Long l = queuetovar.get(queueID);
              long val = (l != null) ? l.longValue() : 0;
              val = val | (1 << i);
              queuetovar.set(queueID, new Long(val));
            }
          }

          HashSet generatedqueueentry=new HashSet();
          for(int i=0;i<invars.size();i++) {
            TempDescriptor td=invars.get(i);
            Set<WaitingElement> weset=seseWaitingQueue.getWaitingElementSet(td);
            
            
            
            //TODO FIX MEEEEE!!!!
            //Weset is sometimes null which breaks the following code and 
            //we don't know what weset = null means. For now, we bail when it's null
            //until we find out what to do....
//            if(weset == null) {
//              continue;
//            }
            //UPDATE: This hack DOES NOT FIX IT!.
            
            
            
            for(Iterator<WaitingElement> wtit=weset.iterator();wtit.hasNext();) {
              WaitingElement waitingElement=wtit.next();
              int queueID=waitingElement.getQueueID();
              
              if(waitingElement.isBogus()){
                continue;
              }
	    
              if (generatedqueueentry.contains(queueID))
                continue;
              else 
                generatedqueueentry.add(queueID);
              
              assert(waitingElement.getStatus()>=ConflictNode.COARSE);
              long mask=queuetovar.get(queueID);
              output.println("         rentry=mlpCreateREntry(runningSESE->memoryQueueArray["+ waitingElement.getQueueID()+ "]," + waitingElement.getStatus() + ", &(seseToIssue->common), "+mask+"LL);");
              output.println("         rentry->count=2;");
              output.println("         seseToIssue->common.rentryArray[seseToIssue->common.rentryIdx++]=rentry;");
              output.println("         rentry->queue=runningSESE->memoryQueueArray[" + waitingElement.getQueueID()+"];");
	                        
              output.println("         if(ADDRENTRY(runningSESE->memoryQueueArray["+ waitingElement.getQueueID()+ "],rentry)==READY) {");
              for(int j=0;mask!=0;j++) {
                if ((mask&1)==1)
                  output.println("            dispCount"+j+"++;");
                mask=mask>>1;
              }
              output.println("         } else ");
              output.println("           refCount--;");
	  }

            if (newChild.getDynamicInVarSet().contains(td)) {
              // dynamic in-var case
              //output.println("       pointer=seseToIssue->"+waitingElement.getDynID()+ 
              //               "_srcSESE+seseToIssue->"+waitingElement.getDynID()+ 
              //               "_srcOffset;");
              //output.println("       rentry=mlpCreateFineREntry("+ waitingElement.getStatus()+
              //               ", &(seseToIssue->common),  pointer );");
            }
          }
          for(int i=0;i<invars.size();i++) {
            output.println("       if(!dispCount"+i+" || !atomic_sub_and_test(dispCount"+i+",&(seseToIssue->rcrRecords["+i+"].flag)))");
            output.println("         localCount++;");
          }
          output.println("      } break;");
        }
      }
    }

    output.println("     } // end switch");

    output.println("#ifndef OOO_DISABLE_TASKMEMPOOL");
    output.println("  RELEASE_REFERENCES_TO((SESEcommon *)seseToIssue, refCount);");
    output.println("#endif");
  }


  public void generateFlatSESEExitNode( FlatMethod       fm,
					FlatSESEExitNode fsexn,
					PrintWriter      output ) {

    // get the enter node for this exit that has meta data embedded
    FlatSESEEnterNode fsen = fsexn.getFlatEnter();

    // there may be an SESE in an unreachable method, skip over
    if( !oooa.getAllSESEs().contains( fsen ) ) {
      return;
    }

    // assert we are never generating code for the caller proxy
    // it should only appear in analysis results
    assert !fsen.getIsCallerProxySESE();

    
    if( state.COREPROF ) {
      output.println("#ifdef CP_EVENTID_TASKEXECUTE");
      output.println("   CP_LOGEVENT( CP_EVENTID_TASKEXECUTE, CP_EVENTTYPE_END );");
      output.println("#endif");
    }

    output.println("   /* SESE exiting */");

    if( state.COREPROF ) {
      output.println("#ifdef CP_EVENTID_TASKRETIRE");
      output.println("   CP_LOGEVENT( CP_EVENTID_TASKRETIRE, CP_EVENTTYPE_BEGIN );");
      output.println("#endif");
    }
    

    // this SESE cannot be done until all of its children are done
    // so grab your own lock with the condition variable for watching
    // that the number of your running children is greater than zero    
    output.println("   atomic_add(childSESE, &runningSESE->numRunningChildren);");
    output.println("   pthread_mutex_lock( &(runningSESE->lock) );");
    output.println("   if( runningSESE->numRunningChildren > 0 ) {");
    output.println("     stopforgc( (struct garbagelist *)&___locals___ );");
    output.println("     do {");
    output.println("       pthread_cond_wait( &(runningSESE->runningChildrenCond), &(runningSESE->lock) );");
    output.println("     } while( runningSESE->numRunningChildren > 0 );");
    output.println("     restartaftergc();");
    output.println("   }");



    ////////////////////////////////////////
    // go through all out-vars and determine where to get them
    ////////////////////////////////////////
    output.println("   // copy ready out-set primitive variables from locals into record");
    Iterator<TempDescriptor> itr = fsen.getReadyOutVarSet().iterator();
    while( itr.hasNext() ) {
      TempDescriptor temp = itr.next();

      // only have to do this for primitives, non-arrays
      if( !(
            temp.getType().isPrimitive() && !temp.getType().isArray()
            )
          ) {
	continue;
      }

      String from = generateTemp( fsen.getfmBogus(), temp );

      output.println("   "+paramsprefix+
		     "->"+temp.getSafeSymbol()+
		     " = "+from+";");
    }

    // static vars are from a known SESE
    Iterator<TempDescriptor> tempItr;
    output.println("   // copy out-set from static sources");
    tempItr = fsen.getStaticOutVarSet().iterator();
    while( tempItr.hasNext() ) {
      TempDescriptor      temp    = tempItr.next();
      VariableSourceToken vst     = fsen.getStaticOutVarSrc( temp );
      SESEandAgePair      srcPair = new SESEandAgePair( vst.getSESE(), vst.getAge() );
      output.println("   "+paramsprefix+
		     "->"+temp.getSafeSymbol()+
		     " = "+paramsprefix+"->"+srcPair+"->"+vst.getAddrVar()+";");
    }
    
    //output.println("   // decrement references to static sources");
    //for( Iterator<SESEandAgePair> pairItr = fsen.getStaticOutVarSrcs().iterator(); pairItr.hasNext(); ) {
    //  SESEandAgePair srcPair = pairItr.next();
    //  output.println("#ifndef OOO_DISABLE_TASKMEMPOOL" );
    //  output.println("   {");
    //  output.println("     SESEcommon* src = &("+paramsprefix+"->"+srcPair+"->common);");
    //  output.println("     RELEASE_REFERENCE_TO( src );");
    //  output.println("   }");
    //  output.println("#endif // OOO_DISABLE_TASKMEMPOOL" );
    //}

    output.println("     // copy out-set from dynamic sources");
    tempItr = fsen.getDynamicOutVarSet().iterator();
    while( tempItr.hasNext() ) {
      TempDescriptor temp = tempItr.next();
      TypeDescriptor type = temp.getType();
      
      // go grab it from the SESE source, when the source is NULL it is
      // this exiting task, so nothing to do!
      output.println("   if( "+temp+"_srcSESE != NULL ) {");

      output.println("     "+paramsprefix+
		     "->"+temp.getSafeSymbol()+
		     " = *(void**)( (void*)"+
		     temp+"_srcSESE + "+
		     temp+"_srcOffset);");

      //output.println("#ifndef OOO_DISABLE_TASKMEMPOOL" );
      //output.println("     SESEcommon* src = "+paramsprefix+"->"+temp+"_srcSESE;");
      //output.println("     RELEASE_REFERENCE_TO( src );");
      //output.println("#endif // OOO_DISABLE_TASKMEMPOOL" );
      
      output.println("   }");
    }
    


    
    // mark yourself done, your task data is now read-only
    output.println("   runningSESE->doneExecuting = TRUE;");

    // if parent is stalling on you, let them know you're done
    if( !fsen.getIsMainSESE() ) {
      output.println("   if( runningSESE->parentsStallSem != NULL ) {");
      output.println("     psem_give( runningSESE->parentsStallSem );");
      output.println("   }");
    }

    output.println("   pthread_mutex_unlock( &(runningSESE->lock) );");

    // decrement dependency count for all SESE's on your forwarding list

    // FORWARD TODO
    output.println("   while( !isEmpty( &runningSESE->forwardList ) ) {");
    output.println("     SESEcommon* consumer = (SESEcommon*) getItem( &runningSESE->forwardList );");
    
   
    if (!state.RCR) {
      output.println("     if(consumer->rentryIdx>0){");
      output.println("        // resolved null pointer");
      output.println("        int idx;");
      output.println("        for(idx=0;idx<consumer->rentryIdx;idx++){");
      output.println("           resolvePointer(consumer->rentryArray[idx]);");
      output.println("        }");
      output.println("     }");
    }

    output.println("     if( atomic_sub_and_test( 1, &(consumer->unresolvedDependencies) ) ){");
    output.println("       workScheduleSubmit( (void*)consumer );");
    output.println("     }");
    output.println("   }");
    
    
    // clean up its lock element from waiting queue, and decrement dependency count for next SESE block
    if( !fsen.getIsMainSESE() ) {
      output.println();
      output.println("   /* check memory dependency*/");
      output.println("  {");
      output.println("      int idx;");
      output.println("      for(idx=0;idx<___params___->common.rentryIdx;idx++){");
      output.println("           REntry* re=___params___->common.rentryArray[idx];");
      output.println("           RETIRERENTRY(re->queue,re);");
      output.println("      }");
      output.println("   }");
    }
    
    Vector<TempDescriptor> inset=fsen.getInVarsForDynamicCoarseConflictResolution();
    if (state.RCR && inset.size() > 0) {
      /* Make sure the running SESE is finished */
      output.println("   if (unlikely(runningSESE->rcrstatus!=0)) {");
      output.println("     if(CAS(&runningSESE->rcrstatus,1,0)==2) {");
      output.println("       while(runningSESE->rcrstatus) {");
      output.println("         BARRIER();");
      output.println("         sched_yield();");
      output.println("       }");
      output.println("     }");
      output.println("   }");
      output.println("{");
      output.println("  int idx,idx2;");

      output.println("    struct rcrRecord *rec;");
      output.println("    struct Hashtable_rcr ** hashstruct=runningSESE->parent->allHashStructures;");

      for (int i = 0; i < inset.size(); i++) {
        output.println("    rec=&" + paramsprefix + "->rcrRecords[" + i + "];");
        output.println("    while(rec!=NULL) {");
        output.println("      for(idx2=0;idx2<rec->index;idx2++) {");

        int weaklyConnectedComponentIndex = rcr.getWeakID(inset.get(i), fsen);

        output.println("        rcr_RETIREHASHTABLE(hashstruct[" + weaklyConnectedComponentIndex
            + "],&(___params___->common), rec->array[idx2], (BinItem_rcr *) rec->ptrarray[idx2]);");

        output.println("      }");// exit idx2 for loop
        output.println("      rec=rec->next;");
        output.println("    }");// exit rec while loop
      }
      output.println("}");
    }


    // a task has variables to track static/dynamic instances
    // that serve as sources, release the parent's ref of each
    // non-null var of these types
    output.println("   // releasing static SESEs");
    output.println("#ifndef OOO_DISABLE_TASKMEMPOOL" );

    ContextTaskNames contextTaskNames = oooa.getContextTaskNames( fsen );

    Iterator<SESEandAgePair> pItr = contextTaskNames.getNeededStaticNames().iterator();
    while( pItr.hasNext() ) {
      SESEandAgePair pair = pItr.next();
      output.println("   if( "+pair+" != NULL ) {");
      output.println("     RELEASE_REFERENCE_TO( "+pair+" );");
      output.println("   }");
    }
    output.println("   // releasing dynamic variable sources");
    Iterator<TempDescriptor> dynSrcItr = contextTaskNames.getDynamicVarSet().iterator();
    while( dynSrcItr.hasNext() ) {
      TempDescriptor dynSrcVar = dynSrcItr.next();
      output.println("   if( "+dynSrcVar+"_srcSESE != NULL ) {");
      output.println("     RELEASE_REFERENCE_TO( "+dynSrcVar+"_srcSESE );");
      output.println("   }");
    }    

    // destroy this task's mempool if it is not a leaf task
    if( !fsen.getIsLeafSESE() ) {
      output.println("     pooldestroy( runningSESE->taskRecordMemPool );");
      if (state.RCR && fsen.getInVarsForDynamicCoarseConflictResolution().size() > 0 ) {
        output.println("     returnTR();");
      }
    }
    output.println("#endif // OOO_DISABLE_TASKMEMPOOL" );


    output.println("{");
    output.println("SESEcommon *myparent=runningSESE->parent;");

    // if this is not the Main sese (which has no parent) then return
    // THIS task's record to the PARENT'S task record pool, and only if
    // the reference count is now zero
    if( !fsen.getIsMainSESE() ) {
      output.println("#ifndef OOO_DISABLE_TASKMEMPOOL" );
      output.println("   RELEASE_REFERENCE_TO( runningSESE );");
      output.println("#endif // OOO_DISABLE_TASKMEMPOOL" );
    } else {
      // the main task has no parent, just free its record
      output.println("   mlpFreeSESErecord( runningSESE );");
    }


    // last of all, decrement your parent's number of running children    
    output.println("   if( myparent != NULL ) {");
    output.println("     if( atomic_sub_and_test( 1, &(myparent->numRunningChildren) ) ) {");
    output.println("       pthread_mutex_lock  ( &(myparent->lock) );");
    output.println("       pthread_cond_signal ( &(myparent->runningChildrenCond) );");
    output.println("       pthread_mutex_unlock( &(myparent->lock) );");
    output.println("     }");
    output.println("   }");

    output.println("}");
    
    // as this thread is wrapping up the task, make sure the thread-local var
    // for the currently running task record references an invalid task
    output.println("   runningSESE = (SESEcommon*) 0x1;");

    if( state.COREPROF ) {
      output.println("#ifdef CP_EVENTID_TASKRETIRE");
      output.println("   CP_LOGEVENT( CP_EVENTID_TASKRETIRE, CP_EVENTTYPE_END );");
      output.println("#endif");
    }
  }

 
  public void generateFlatWriteDynamicVarNode( FlatMethod              fm,  
					       FlatWriteDynamicVarNode fwdvn,
					       PrintWriter             output
					     ) {
    	
    Hashtable<TempDescriptor, VSTWrapper> writeDynamic = fwdvn.getVar2src();

    assert writeDynamic != null;

    Iterator wdItr = writeDynamic.entrySet().iterator();
    while( wdItr.hasNext() ) {
      Map.Entry           me     = (Map.Entry)      wdItr.next();
      TempDescriptor      refVar = (TempDescriptor) me.getKey();
      VSTWrapper          vstW   = (VSTWrapper)     me.getValue();
      VariableSourceToken vst    =                  vstW.vst;

      output.println("     {");
      output.println("       SESEcommon* oldSrc = "+refVar+"_srcSESE;");

      if( vst == null ) {
	// if there is no given source, this variable is ready so
	// mark src pointer NULL to signify that the var is up-to-date
	output.println("       "+refVar+"_srcSESE   = NULL;");
      } else {
        // otherwise we track where it will come from
        SESEandAgePair instance = new SESEandAgePair( vst.getSESE(), vst.getAge() );
        output.println("       "+refVar+"_srcSESE = "+instance+";");    
        output.println("       "+refVar+"_srcOffset = (INTPTR) &((("+
                       vst.getSESE().getSESErecordName()+"*)0)->"+vst.getAddrVar()+");");
      }

      // no matter what we did above, track reference count of whatever
      // this variable pointed to, do release last in case we're just
      // copying the same value in because 1->2->1 is safe but ref count
      // 1->0->1 has a window where it looks like it should be free'd
      output.println("#ifndef OOO_DISABLE_TASKMEMPOOL" );
      output.println("       if( "+refVar+"_srcSESE != NULL ) {");
      output.println("         ADD_REFERENCE_TO( "+refVar+"_srcSESE );");
      output.println("       }");
      output.println("       if( oldSrc != NULL ) {");
      output.println("         RELEASE_REFERENCE_TO( oldSrc );");
      output.println("       }");
      output.println("#endif // OOO_DISABLE_TASKMEMPOOL" );

      output.println("     }");
    }	
  }


  protected void generateFlatNew( FlatMethod      fm, 
                                  FlatNew         fn, 
                                  PrintWriter     output ) {

    if( fn.getType().isArray() ) {
      int arrayid = state.getArrayNumber( fn.getType() )+state.numClasses();

      if( GENERATEPRECISEGC ) {
        output.println(generateTemp( fm, fn.getDst())+
                       "=allocate_newarray_mlp("+localsprefixaddr+
                       ", "+arrayid+", "+generateTemp( fm, fn.getSize())+
                       ", oid, "+
                       oooa.getDisjointAnalysis().getAllocationSiteFromFlatNew( fn ).getUniqueAllocSiteID()+
                       ");");
        output.println("    oid += oidIncrement;");
      } else {
	output.println(generateTemp( fm, fn.getDst())+
                       "=allocate_newarray("+arrayid+
                       ", "+generateTemp( fm, fn.getSize())+
                       ");");
      }

    } else {
      // not an array
      if( GENERATEPRECISEGC ) {
        output.println( generateTemp( fm, fn.getDst())+
                        "=allocate_new_mlp("+localsprefixaddr+
                        ", "+fn.getType().getClassDesc().getId()+
                        ", oid, "+
                        oooa.getDisjointAnalysis().getAllocationSiteFromFlatNew( fn ).getUniqueAllocSiteID()+
                        ");");
        output.println("    oid += oidIncrement;");        
      } else {
	output.println( generateTemp( fm, fn.getDst())+
                        "=allocate_new("+fn.getType().getClassDesc().getId()+
                        ");");
      }
    }
  }


  private int calculateSizeOfSESEParamList(FlatSESEEnterNode fsen){
	  
    Set<TempDescriptor> tdSet=new HashSet<TempDescriptor>();
	  
    for (Iterator iterator = fsen.getInVarSet().iterator(); iterator.hasNext();) {
      TempDescriptor tempDescriptor = (TempDescriptor) iterator.next();
      if(!tempDescriptor.getType().isPrimitive() || tempDescriptor.getType().isArray()){
        tdSet.add(tempDescriptor);
      }	
    }
	  
    for (Iterator iterator = fsen.getOutVarSet().iterator(); iterator.hasNext();) {
      TempDescriptor tempDescriptor = (TempDescriptor) iterator.next();
      if(!tempDescriptor.getType().isPrimitive() || tempDescriptor.getType().isArray()){
        tdSet.add(tempDescriptor);
      }	
    }	  
	  	  
    return tdSet.size();
  }

  
  private String calculateSizeOfSESEParamSize(FlatSESEEnterNode fsen){
    HashMap <String,Integer> map=new HashMap();
    HashSet <TempDescriptor> processed=new HashSet<TempDescriptor>();
    String rtr="";
	  
    // space for all in and out set primitives
    Set<TempDescriptor> inSetAndOutSet = new HashSet<TempDescriptor>();
    inSetAndOutSet.addAll( fsen.getInVarSet() );
    inSetAndOutSet.addAll( fsen.getOutVarSet() );
	    
    Set<TempDescriptor> inSetAndOutSetPrims = new HashSet<TempDescriptor>();

    Iterator<TempDescriptor> itr = inSetAndOutSet.iterator();
    while( itr.hasNext() ) {
      TempDescriptor temp = itr.next();
      TypeDescriptor type = temp.getType();
      if( !type.isPtr() ) {
        inSetAndOutSetPrims.add( temp );
      }
    }
	    
    Iterator<TempDescriptor> itrPrims = inSetAndOutSetPrims.iterator();
    while( itrPrims.hasNext() ) {
      TempDescriptor temp = itrPrims.next();
      TypeDescriptor type = temp.getType();
      if(type.isPrimitive()){
        Integer count=map.get(type.getSymbol());
        if(count==null){
          count=new Integer(1);
          map.put(type.getSymbol(), count);
        }else{
          map.put(type.getSymbol(), new Integer(count.intValue()+1));
        }
      }      
    }
	  
    Set<String> keySet=map.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      String key = (String) iterator.next();
      rtr+="+sizeof("+key+")*"+map.get(key);
    }
    return  rtr;
  }

}
