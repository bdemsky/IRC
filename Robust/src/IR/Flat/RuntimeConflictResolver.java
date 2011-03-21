package IR.Flat;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;
import Util.Pair;
import Analysis.Disjoint.*;
import Analysis.Pointer.*;
import Analysis.Pointer.AllocFactory.AllocNode;
import IR.State;
import IR.TypeDescriptor;
import Analysis.OoOJava.ConflictGraph;
import Analysis.OoOJava.ConflictNode;
import Analysis.OoOJava.OoOJavaAnalysis;
import Util.CodePrinter;

/* An instance of this class manages all OoOJava coarse-grained runtime conflicts
 * by generating C-code to either rule out the conflict at runtime or resolve one.
 * 
 * How to Use:
 * 1) Instantiate singleton object (String input is to specify output dir)
 * 2) Call setGlobalEffects setGlobalEffects(Hashtable<Taint, Set<Effect>> ) ONCE
 * 3) Input SESE blocks, for each block:
 *    3a) call addToTraverseToDoList(FlatSESEEnterNode , ReachGraph , Hashtable<Taint, Set<Effect>>) for the seseBlock
 *    3b) call String getTraverserInvocation(TempDescriptor, String, FlatSESEEnterNode) to get the name of the traverse method in C
 * 4) Call void close() 
 * Note: All computation is done upon closing the object. Steps 1-3 only input data
 */
public class RuntimeConflictResolver {
  //Shows weakly connected heaproots and which allocation sites were considered for traversal
  private boolean generalDebug = false;
  
  //Prints out effects passed in, internal representation of effects, and internal representation of reach graph
  private boolean verboseDebug = false;
  
  private CodePrinter headerFile, cFile;
  private static final String hashAndQueueCFileDir = "oooJava/";
  
  //This keeps track of taints we've traversed to prevent printing duplicate traverse functions
  //The Integer keeps track of the weakly connected group it's in (used in enumerateHeapRoots)
  //private Hashtable<Taint, Integer> doneTaints;
  private Hashtable<Pair, Integer> idMap=new Hashtable<Pair,Integer>();
  
  //Keeps track of stallsites that we've generated code for. 
  protected Hashtable <FlatNode, TempDescriptor> processedStallSites = new Hashtable <FlatNode, TempDescriptor>();
  //private Hashtable<Pair, Integer> weakMap=new Hashtable<Pair,Integer>();
  //private Hashtable<Taint, Set<Effect>> globalEffects;
  //private Hashtable<Taint, Set<Effect>> globalConflicts;
  
  //private ArrayList<TraversalInfo> traverserTODO;
  
  // Hashtable provides fast access to heaproot # lookups
  //private Hashtable<Taint, WeaklyConectedHRGroup> connectedHRHash;
  //private ArrayList<WeaklyConectedHRGroup> num2WeaklyConnectedHRGroup;
  //private int traverserIDCounter;
  public int currentID=1;
  private int weaklyConnectedHRCounter;
  //private ArrayList<TaintAndInternalHeapStructure> pendingPrintout;
  //private EffectsTable effectsLookupTable;
  private OoOJavaAnalysis oooa;  
  private State globalState;
  
  // initializing variables can be found in printHeader()
  private static final String getAllocSiteInC = "->allocsite";
  private static final String queryAndAddToVistedHashtable = "hashRCRInsert";
  //TODO add to queue for transitions?!
  private static final String addToQueueInC = "enqueueRCRQueue(";
  private static final String dequeueFromQueueInC = "dequeueRCRQueue()";
  private static final String clearQueue = "resetRCRQueue()";
  // Make hashtable; hashRCRCreate(unsigned int size, double loadfactor)
  private static final String mallocVisitedHashtable = "hashRCRCreate(128, 0.75)";
  private static final String deallocVisitedHashTable = "hashRCRDelete()";
  private static final String resetVisitedHashTable = "hashRCRreset()";

  /*
   * Basic Strategy:
   * 1) Get global effects and conflicts 
   * 2) Create a hash structure (EffectsTable) to manage effects (hashed by affected Allocsite, then taint, then field)
   *     2a) Use Effects to verify we can access something (reads)
   *     2b) Use conflicts to mark conflicts (read/write/strongupdate)
   *     2c) At second level of hash, store Heaproots that can cause conflicts at the field
   * 3) Walk hash structure to identify and enumerate weakly connected groups
   * 4) Build internal representation of the rgs (pruned)
   * 5) Print c methods by walking internal representation
   */
  
  public RuntimeConflictResolver( String buildir, 
                                  OoOJavaAnalysis oooa, 
                                  State state) 
  throws FileNotFoundException {
    this.oooa         = oooa;
    this.globalState  = state;
    this.generalDebug = state.RCR_DEBUG || state.RCR_DEBUG_VERBOSE;
    this.verboseDebug = state.RCR_DEBUG_VERBOSE;
    
    //doneTaints = new Hashtable<Taint, Integer>();
    //connectedHRHash = new Hashtable<Taint, WeaklyConectedHRGroup>();
    //pendingPrintout = new ArrayList<TaintAndInternalHeapStructure>();
    //traverserTODO = new ArrayList<TraversalInfo>();
    
    //traverserIDCounter = 1;
    
    //TODO pass in max weakly connected groups number
    weaklyConnectedHRCounter = 1;
    
    //note: the order below MATTERS 
    setupOutputFiles(buildir);
    //getAllTasksAndConflicts();
    //createInternalGraphs();
    //After the internal graphs are created, we can print,
    //but printing is done in close();
  }

  private void setupOutputFiles(String buildir) throws FileNotFoundException {
    cFile = new CodePrinter(new File(buildir + "RuntimeConflictResolver" + ".c"));
    headerFile = new CodePrinter(new File(buildir + "RuntimeConflictResolver" + ".h"));
    
    cFile.println("#include \"" + hashAndQueueCFileDir + "hashRCR.h\"\n#include \""
        + hashAndQueueCFileDir + "Queue_RCR.h\"\n#include <stdlib.h>");
    cFile.println("#include \"classdefs.h\"");
    cFile.println("#include \"structdefs.h\"");
    cFile.println("#include \"mlp_runtime.h\"");
    cFile.println("#include \"RuntimeConflictResolver.h\"");
    cFile.println("#include \"hashStructure.h\"");
    
    headerFile.println("#ifndef __3_RCR_H_");
    headerFile.println("#define __3_RCR_H_");
  }
	  
  //Performs a reverse traversal from the conflict nodes up to the
  //inset variables and sets conflict flags on inner nodes.
  private void propagateConflicts(Hashtable<Alloc, ConcreteRuntimeObjNode> created) {
    for(ConcreteRuntimeObjNode node: created.values()) {
      if(node.hasConflict()) {
        markReferencers(node, node.objConfRead || node.objConfWrite, node.primConfRead || node.primConfWrite);
      }
    }
  }

  private void markReferencers(ConcreteRuntimeObjNode node, boolean ObjConf, boolean PrimConf) {
    for(ObjRef ref: node.referencers) {      
      //if not already marked or data does not match
      if(!ref.reachesConflict || 
          (ObjConf  && !ref.parent.descendantsObjConflict) ||
          (PrimConf && !ref.parent.descendantsPrimConflict)) {
        
        ref.parent.descendantsObjConflict  |= ObjConf;        
        ref.parent.descendantsPrimConflict |= PrimConf;
        ref.reachesConflict = true;
        markReferencers(ref.parent, ObjConf, PrimConf);
      }
    }
  }

  //This extends a tempDescriptor's isPrimitive test by also excluding primitive arrays. 
  private boolean isReallyAPrimitive(TypeDescriptor type) {
    return (type.isPrimitive() && !type.isArray());
  }
  
  //The official way to generate the name for a traverser call
  public String getTraverserInvocation(TempDescriptor invar, String varString, FlatNode fn) {
    String flatname;
    if(fn instanceof FlatSESEEnterNode) {  //is SESE block
      flatname = ((FlatSESEEnterNode) fn).getPrettyIdentifier();
    } else {  //is stallsite
      flatname = fn.toString();
    }
    
    return "traverse___" + invar.getSafeSymbol() + 
    removeInvalidChars(flatname) + "___("+varString+");";
  }
  
  public String removeInvalidChars(String in) {
    StringBuilder s = new StringBuilder(in);
    for(int i = 0; i < s.length(); i++) {
      if(s.charAt(i) == ' ' || 
         s.charAt(i) == '.' || 
         s.charAt(i) == '=' ||
         s.charAt(i) == '[' ||
         s.charAt(i) == ']'    ) {

        s.deleteCharAt(i);
        i--;
      }
    }
    return s.toString();
  }

  // TODO, THIS WORKS A NEW WAY
  public int getWeakID(TempDescriptor invar, FlatNode fn) {
    //return weakMap.get(new Pair(invar, fn)).intValue();
    return -12;
  }
  public int getTraverserID(TempDescriptor invar, FlatNode fn) {
    Pair t=new Pair(invar, fn);
    if (idMap.containsKey(t))
      return idMap.get(t).intValue();
    int value=currentID++;
    idMap.put(t, new Integer(value));
    return value;
  }



  public void close() {

    BuildStateMachines bsm = oooa.getBuildStateMachines();

    for( Pair p: bsm.getAllMachineNames() ) {
      FlatNode       taskOrStallSite = (FlatNode)       p.getFirst();
      TempDescriptor var             = (TempDescriptor) p.getSecond();

      //TODO put real graph here
      Graph g = new Graph(null);
      
      //prints the traversal code
      //TODO get real connected component number
      printCMethod( taskOrStallSite, 
                    var, 
                    bsm.getStateMachine( taskOrStallSite, var ),
                    0, // weakly connected component group
                    g); 
    }

    
    //Prints out the master traverser Invocation that'll call all other traversers
    //based on traverserID
    printMasterTraverserInvocation();    
    createMasterHashTableArray();
    
    // Adds Extra supporting methods
    cFile.println("void initializeStructsRCR() {\n  " + mallocVisitedHashtable + ";\n  " + clearQueue + ";\n}");
    cFile.println("void destroyRCR() {\n  " + deallocVisitedHashTable + ";\n}");
    
    headerFile.println("void initializeStructsRCR();\nvoid destroyRCR();");
    headerFile.println("#endif\n");

    cFile.close();
    headerFile.close();
  }

  private void createMasterHashTableArray() {
    headerFile.println("struct Hashtable_rcr ** createAndFillMasterHashStructureArray();");
    cFile.println("struct Hashtable_rcr ** createAndFillMasterHashStructureArray() {");

    cFile.println("  struct Hashtable_rcr **table=rcr_createMasterHashTableArray("+weaklyConnectedHRCounter + ");");
    
    for(int i = 0; i < weaklyConnectedHRCounter; i++) {
      cFile.println("  table["+i+"] = (struct Hashtable_rcr *) rcr_createHashtable();");
    }
    cFile.println("  return table;");
    cFile.println("}");
  }

  private void printMasterTraverserInvocation() {
    headerFile.println("\nint tasktraverse(SESEcommon * record);");
    cFile.println("\nint tasktraverse(SESEcommon * record) {");
    cFile.println("  if(!CAS(&record->rcrstatus,1,2)) {");

    //release traverser reference...no traversal necessary
    cFile.println("#ifndef OOO_DISABLE_TASKMEMPOOL");
    cFile.println("    RELEASE_REFERENCE_TO(record);");
    cFile.println("#endif");

    cFile.println("    return;");
    cFile.println("  }");
    cFile.println("  switch(record->classID) {");
    
    for(Iterator<FlatSESEEnterNode> seseit=oooa.getAllSESEs().iterator();seseit.hasNext();) {
      FlatSESEEnterNode fsen=seseit.next();
      cFile.println(    "    /* "+fsen.getPrettyIdentifier()+" */");
      cFile.println(    "    case "+fsen.getIdentifier()+": {");
      cFile.println(    "      "+fsen.getSESErecordName()+" * rec=("+fsen.getSESErecordName()+" *) record;");
      Vector<TempDescriptor> invars=fsen.getInVarsForDynamicCoarseConflictResolution();
      for(int i=0;i<invars.size();i++) {
        TempDescriptor tmp=invars.get(i);
        
        // TODO!!!!! FIX IT LATER! Right now, we assume that there is only one parent
        // JCJ ask yong hun what we should do in the multi-parent future!
        FlatSESEEnterNode parentSESE = (FlatSESEEnterNode) fsen.getParents().iterator().next();
        ConflictGraph     graph      = oooa.getConflictGraph(parentSESE);
        String            id         = tmp + "_sese" + fsen.getPrettyIdentifier();
        ConflictNode      node       = graph.getId2cn().get(id);        
        
      	if (i!=0) {
      	    cFile.println("      if (record->rcrstatus!=0)");
      	}
      	
        if(globalState.NOSTALLTR && node.IsValidToPrune()){
          cFile.println("    /*  " + this.getTraverserInvocation(tmp, "rec->"+tmp+", rec", fsen)+"*/");
        }else{
          cFile.println("      " + this.getTraverserInvocation(tmp, "rec->"+tmp+", rec", fsen));
        }
        
      }
      //release traverser reference...traversal finished...
      //executing thread will clean bins for us
      cFile.println("     record->rcrstatus=0;");
      cFile.println("#ifndef OOO_DISABLE_TASKMEMPOOL");
      cFile.println("    RELEASE_REFERENCE_TO(record);");
      cFile.println("#endif");
      cFile.println(    "    }");
      cFile.println(    "    break;");
    }
    
    for(FlatNode stallsite: processedStallSites.keySet()) {
      TempDescriptor var = processedStallSites.get(stallsite);
      
      cFile.println(    "    case -" + getTraverserID(var, stallsite)+ ": {");
      cFile.println(    "      SESEstall * rec=(SESEstall*) record;");
      cFile.println(    "      " + this.getTraverserInvocation(var, "rec->___obj___, rec", stallsite)+";");
      cFile.println(    "     record->rcrstatus=0;");
      cFile.println(    "    }");
      cFile.println("    break;");
    }

    cFile.println("    default:\n    printf(\"Invalid SESE ID was passed in: %d.\\n\",record->classID);\n    break;");
    cFile.println("  }");
    cFile.println("}");
  }


  //Currently UNUSED method but may be useful in the future.
  //This will print the traverser invocation that takes in a traverserID and starting ptr
  private void printResumeTraverserInvocation() {
    headerFile.println("\nint traverse(void * startingPtr, SESEcommon * record, int traverserID);");
    cFile.println("\nint traverse(void * startingPtr, SESEcommon *record, int traverserID) {");
    cFile.println("  switch(traverserID) {");
    
    /*
      TODO WHAT IS THE RIGHT THING TO DO HERE?@!?!?
    for(Taint t: doneTaints.keySet()) {
      cFile.println("  case " + doneTaints.get(t)+ ":");
      if(t.isRBlockTaint()) {
        cFile.println("    " + this.getTraverserInvocation(t.getVar(), "startingPtr, ("+t.getSESE().getSESErecordName()+" *)record", t.getSESE()));
      } else if (t.isStallSiteTaint()){
        // JCJ either remove this or consider writing a comment explaining what it is commented out for
        cFile.println("//    " + this.getTraverserInvocation(t.getVar(), "startingPtr, record", t.getStallSite())+"");
      } else {
        System.out.println("RuntimeConflictResolver encountered a taint that is neither SESE nor stallsite: " + t);
      }
      cFile.println("    break;");
    }
    */

    cFile.println("  default:\n    break;");
    
    cFile.println(" }");
    cFile.println("}");
  }

  
  /*
   * This method generates a C method for every inset variable and rblock. 
   * 
   * The C method works by generating a large switch statement that will run the appropriate 
   * checking code for each object based on its allocation site. The switch statement is 
   * surrounded by a while statement which dequeues objects to be checked from a queue. An
   * object is added to a queue only if it contains a conflict (in itself or in its referencees)
   * and we came across it while checking through it's referencer. Because of this property, 
   * conflicts will be signaled by the referencer; the only exception is the inset variable which can 
   * signal a conflict within itself. 
   */
  
  private void printCMethod( FlatNode taskOrStallSite,
                             TempDescriptor var,
                             StateMachineForEffects smfe,
                             int heaprootNum,
                             Graph ptrGraph) {

    // collect info for code gen
    FlatSESEEnterNode task         = null;
    String            inVar        = var.getSafeSymbol();
    SMFEState         initialState = smfe.getInitialState();
    boolean           isStallSite  = !(taskOrStallSite instanceof FlatSESEEnterNode);    

    String blockName;    
    if( isStallSite ) {
      blockName = taskOrStallSite.toString();
      processedStallSites.put(taskOrStallSite, var);
    } else {
      task = (FlatSESEEnterNode) taskOrStallSite;
      blockName = task.getPrettyIdentifier();
    }
    
    String methodName = "void traverse___" + inVar + removeInvalidChars(blockName) + "___(void * InVar, ";
    int    index      = -1;

    if( isStallSite ) {
      methodName += "SESEstall *record)";
    } else {
      methodName += task.getSESErecordName() +" *record)";
      index = task.getInVarsForDynamicCoarseConflictResolution().indexOf( var );
    }
    
    cFile     .println( methodName + " {");
    headerFile.println( methodName + ";" );

    cFile.println(  "  int totalcount = RUNBIAS;");      
    if( isStallSite ) {
      cFile.println("  record->rcrRecords[0].count = RUNBIAS;");
    } else {
      cFile.println("  record->rcrRecords["+index+"].count = RUNBIAS;");
    }

    //clears queue and hashtable that keeps track of where we've been. 
    cFile.println(clearQueue + ";");
    cFile.println(resetVisitedHashTable + ";"); 
    cFile.println("  RCRQueueEntry * queueEntry; //needed for dequeuing");
    
    cFile.println("  int traverserState = "+initialState.getID()+";");

    //generic cast to ___Object___ to access ptr->allocsite field. 
    cFile.println("  RCRQueueEntry* entry = (struct ___Object___ *) InVar;");
    cFile.println("  struct ___Object___ * ptr = (struct ___Object___ *) InVar;");
    cFile.println("  if (InVar != NULL) {");
    cFile.println("    " + queryAndAddToVistedHashtable + "(ptr, "+initialState.getID()+");");
    cFile.println("    do {");

    if( !isStallSite ) {
      cFile.println("      if(unlikely(record->common.doneExecuting)) {");
      cFile.println("        record->common.rcrstatus=0;");
      cFile.println("        return;");
      cFile.println("      }");
    }

    
    // Traverse the StateMachineForEffects (a graph)
    // that serves as a plan for building the heap examiner code.
    // SWITCH on the states in the state machine, THEN
    //   SWITCH on the concrete object's allocation site THEN
    //     consider conflicts, enqueue more work, inline more SWITCHES, etc.
    Set<SMFEState> toVisit = new HashSet<SMFEState>();
    Set<SMFEState> visited = new HashSet<SMFEState>();
      
    cFile.println("  switch( traverserState ) {");

    toVisit.add( initialState );
    while( !toVisit.isEmpty() ) {
      Set<Alloc> printedAllocs = new MySet<Alloc>();
      SMFEState state = toVisit.iterator().next();
      toVisit.remove( state );
      
      printDebug(generalDebug, "Considering state: " + state.getID() + " for traversal");
      
      if(visited.add( state ) && (state.getRefCount() != 1 || initialState == state)) {
        printDebug(generalDebug, "+   state:" + state.getID() + " qualified for case statement");
        
        cFile.println("    case "+state.getID()+":");
        cFile.println("      switch(ptr->allocsite) {");
        
        //TODO consider separating out the traversal graph creation into another step. 
        EffectsTable et = new EffectsTable(state);
        //TODO Var is not the same for all traversals....
        Hashtable<Alloc, ConcreteRuntimeObjNode> traversalGraph = createTraversalGraph(et, ptrGraph, var);
        propagateConflicts(traversalGraph);
        
        for(ConcreteRuntimeObjNode node : traversalGraph.values()) {
          printDebug(generalDebug, "      Considering Alloc" + node.alloc + " for traversal");
          
          if (printedAllocs.add(node.alloc) && qualifiesForCaseStatement(node)) {
            printDebug(generalDebug, "++       " + node.alloc + " qualified for case statement");
            
            cFile.println("        case "+node.alloc.getUniqueAllocSiteID()+" : ");
            //Note: this step adds to the toVisit SMFE Queue/Set
            addAllocChecker(taskOrStallSite, var, et, node, "ptr", 0, heaprootNum, state.getID(), toVisit);
            cFile.println("          break;");
          }
        }
        cFile.println("        default: break;");
        cFile.println("      } // end switch on allocsite");
        cFile.println("      break;");
        
      }

    }
    
    cFile.println("        default: break;");
    cFile.println("      } // end switch on traverser state");
    cFile.println("      queueEntry = " + dequeueFromQueueInC + ";");
    cFile.println("      ptr = queueEntry->object;");
    cFile.println("      traverserState = queueEntry->traverserState;");
    cFile.println("    } while(ptr != NULL);");
    cFile.println("  } // end if inVar not null");
   

    if( isStallSite ) {
      cFile.println("  if(atomic_sub_and_test(totalcount,&(record->rcrRecords[0].count))) {");
      cFile.println("    psem_give_tag(record->common.parentsStallSem, record->tag);");
      cFile.println("    BARRIER();");
      cFile.println("  }");
    } else {
      cFile.println("  if(atomic_sub_and_test(totalcount,&(record->rcrRecords["+index+"].count))) {");
      cFile.println("    int flag=LOCKXCHG32(&(record->rcrRecords["+index+"].flag),0);");
      cFile.println("    if(flag) {");
      //we have resolved a heap root...see if this was the last dependence
      cFile.println("      if(atomic_sub_and_test(1, &(record->common.unresolvedDependencies))) workScheduleSubmit((void *)record);");
      cFile.println("    }");
      cFile.println("  }");
    }

    cFile.println("}");
    cFile.flush();
  }
  
  Hashtable<Alloc, ConcreteRuntimeObjNode> createTraversalGraph(EffectsTable et, Graph ptrGraph, TempDescriptor var) {  
    Hashtable<Alloc, ConcreteRuntimeObjNode> created 
            = new Hashtable<Alloc, ConcreteRuntimeObjNode>(); //Pass 0: Create empty graph
    //TODO what if we have more than one way in?! >< i.e. more than 1 temp descriptor...
    Set<Edge> insetVars = ptrGraph.getEdges(var);
    for(Edge invar: insetVars) {
      Alloc rootKey = invar.getSrcAlloc();
      
      if(!created.contains(rootKey)) {
        //null       -> no transitions by reading this object (does not apply to its references
        //bool true  -> this is an inset variable
        ConcreteRuntimeObjNode root = new ConcreteRuntimeObjNode(rootKey, var.getType(), null, true);
        addToTraversalGraphStartingAt(root, et, ptrGraph.getEdges((AllocNode) rootKey), ptrGraph, created);
      }
    }
    
    return created;    
  }
  
  
  private void addToTraversalGraphStartingAt(
      ConcreteRuntimeObjNode                    curr, 
      EffectsTable                              et, 
      MySet<Edge>                               edges,
      Graph                                     ptrGraph, 
      Hashtable<Alloc, ConcreteRuntimeObjNode>  created) {
    CombinedEffects ce;
    
    //Handle Primitives
    for(String field: et.getAllFields(curr.alloc).keySet()) {
      if((ce = et.getCombinedEffects(curr.alloc, field)).isPrimitive);
      curr.primConfRead  |= ce.hasReadConflict;
      curr.primConfWrite |= ce.hasWriteConflict;   
    }
    
    //Handle Object Conflicts
    for(Edge e: edges) {
      //since we're starting from a src, it should match...
      assert e.getSrcAlloc().equals(curr.alloc);
      Alloc dst = e.getDst();
      String field = e.getFieldDesc().getSafeSymbol();
      ce = et.getCombinedEffects(curr.alloc, field);
      ConcreteRuntimeObjNode child;
      
      //if ce is null, then that means we never go down that branch.
      if(ce!=null) {
        boolean isNewChild = !created.containsKey(dst);
        
        if(isNewChild) {
          //false = not inset
          child = new ConcreteRuntimeObjNode(dst, e.getFieldDesc().getType(), ce.transitions, false);  
          created.put(dst, child);
        } else {
          child = created.get(dst);
        }
        
        ObjRef reference = new ObjRef(field, curr, child, ce);
        curr.addReferencee(field, reference);
         
        //update parent flags
        curr.objConfRead   |= ce.hasReadConflict;
        curr.objConfWrite  |= ce.hasWriteConflict;
        
        //Update flags and recurse
        if(ce.hasReadEffect) {
          child.hasPotentialToBeIncorrectDueToConflict |= ce.hasReadConflict;
          child.addReferencer(reference);
          
          if(isNewChild) {
            MySet<Edge> childEdges = ptrGraph.getEdges((AllocNode)dst);
            addToTraversalGraphStartingAt(child, et, childEdges, ptrGraph, created);
          }
        }
      }
    }
  }

  //Note: FlatNode and temp descriptor are what used to be the taint.
  void addAllocChecker(FlatNode fn, TempDescriptor tmp, EffectsTable et, ConcreteRuntimeObjNode node, String prefix, int depth, int heaprootNum, int stateID, Set<SMFEState> toVisit) {
    insertEntriesIntoHashStructure(fn, tmp, node,prefix, depth, heaprootNum);
    
    //Handle conflicts further down. 
    if(node.descendantsConflict()) {
      int pdepth=depth+1;
      cFile.println("{");
      
      //Array Case
      if(node.isArray()) {
        String childPtr = "((struct ___Object___ **)(((char *) &(((struct ArrayObject *)"+ prefix+")->___length___))+sizeof(int)))[i]";
        String currPtr = "arrayElement" + pdepth;
        
        cFile.println("{\n  int i;");
        cFile.println("    struct ___Object___ * "+currPtr+";");
        cFile.println("  for(i = 0; i<((struct ArrayObject *) " + prefix + " )->___length___; i++ ) {");
        
        //There should be only one field, hence we only take the first field in the keyset.
        assert node.referencees.keySet().size() <= 1;
        ObjRefList refsAtParticularField = node.referencees.get(node.referencees.keySet().iterator().next());
        printObjRefSwitchStatement(fn,tmp,et,pdepth,refsAtParticularField,childPtr,currPtr,heaprootNum,stateID,toVisit);
        cFile.println("      }}");
      } else {
      //All other cases
        String currPtr = "myPtr" + pdepth;
        cFile.println("    struct ___Object___ * "+currPtr+";");
        for(String field: node.referencees.keySet()) {
          ObjRefList refsAtParticularField = node.referencees.get(field);
          
          if(refsAtParticularField.hasConflicts()) {
            String childPtr = "((struct "+node.getType().getSafeSymbol()+" *)"+prefix +")->___" + field + "___";
            printObjRefSwitchStatement(fn,tmp,et,pdepth,refsAtParticularField,childPtr,currPtr,heaprootNum, stateID, toVisit);
          }
        }      
      }
      cFile.println("}\n"); //For particular top level case statement. 
    }
  }
  
  //TODO update to include state changes!
  //If state changes branches INTO this object, then it needs its own state. 
  //Possible solution, have a hashtable to keep track of Alloc->PossibleTransition
  //And add to it as we go through the effects. 
  private boolean qualifiesForCaseStatement(ConcreteRuntimeObjNode node) {
    return true;
//    return (          
//        //insetVariable case
//        (node.isInsetVar && (node.descendantsConflict() || node.hasPrimitiveConflicts()) || node.hasDirectObjConflict()) ||
//        //non-inline-able code cases
//        (node.getNumOfReachableParents() != 1 && node.descendantsConflict()) ||
//        //Cases where resumes are possible
//        (node.hasPotentialToBeIncorrectDueToConflict && node.descendantsObjConflict));
  }
    
  //FlatNode and TempDescriptor are what are used to make the taint
  private void insertEntriesIntoHashStructure(FlatNode fn, TempDescriptor tmp, 
      ConcreteRuntimeObjNode curr, String prefix, int depth, int heaprootNum) {

    int index = 0;
    boolean isRblock = (fn instanceof FlatSESEEnterNode);
    if (isRblock) {
      FlatSESEEnterNode fsese = (FlatSESEEnterNode) fn;
      index = fsese.getInVarsForDynamicCoarseConflictResolution().indexOf(tmp);
    }

    String strrcr = isRblock ? "&record->rcrRecords[" + index + "], " : "NULL, ";
    String tasksrc =isRblock ? "(SESEcommon *) record, ":"(SESEcommon *)(((INTPTR)record)|1LL), ";

    // Do call if we need it.
    if (curr.primConfWrite || curr.objConfWrite) {
      assert heaprootNum != -1;
      cFile.append("    int tmpkey" + depth + "=rcr_generateKey(" + prefix + ");\n");
      if (curr.descendantsConflict())
        cFile.append("    int tmpvar" + depth + "=rcr_WTWRITEBINCASE(allHashStructures[" + heaprootNum + "], tmpkey" + depth + ", " + tasksrc + strrcr + index + ");\n");
      else
        cFile.append("    int tmpvar" + depth + "=rcr_WRITEBINCASE(allHashStructures["+ heaprootNum + "], tmpkey" + depth + ", " + tasksrc + strrcr + index + ");\n");
    } else if (curr.primConfRead || curr.objConfRead) {
      assert heaprootNum != -1;
      cFile.append("    int tmpkey" + depth + "=rcr_generateKey(" + prefix + ");\n");
      if (curr.descendantsConflict())
        cFile.append("    int tmpvar" + depth + "=rcr_WTREADBINCASE(allHashStructures[" + heaprootNum + "], tmpkey" + depth + ", " + tasksrc + strrcr + index + ");\n");
      else
        cFile.append("    int tmpvar" + depth + "=rcr_READBINCASE(allHashStructures["+ heaprootNum + "], tmpkey" + depth + ", " + tasksrc + strrcr + index + ");\n");
    }

    if (curr.primConfWrite || curr.objConfWrite || curr.primConfRead || curr.objConfRead) {
      cFile.append("if (!(tmpvar" + depth + "&READYMASK)) totalcount--;\n");
    }
  }

  private void printObjRefSwitchStatement(FlatNode fn,
                                          TempDescriptor tmp,
                                          EffectsTable et, 
                                          int pDepth, 
                                          ArrayList<ObjRef> refsAtParticularField, 
                                          String childPtr,
                                          String currPtr,
                                          int heaprootNum,
                                          int stateID,
                                          Set<SMFEState> toVisit) {
    
    cFile.println("    "+currPtr+"= (struct ___Object___ * ) " + childPtr + ";");
    cFile.println("    if (" + currPtr + " != NULL) { ");
    cFile.println("    switch(" + currPtr + getAllocSiteInC + ") {");
    
    for(ObjRef ref: refsAtParticularField) {
      if(ref.child.descendantsConflict() || ref.child.hasPrimitiveConflicts()) {
        cFile.println("      case "+ref.allocID+":\n      {");
        //The hash insert is here because we don't want to enqueue things unless we know it conflicts. 
        cFile.println("        if (" + queryAndAddToVistedHashtable +"("+ currPtr + ", "+stateID+")) {");
        
        if(ref.child.isTransition()) {
          for(SMFEState s: ref.child.transitions) {
            cFile.println("        " + addToQueueInC + childPtr + ", "+s.getID()+");"); 
          }
        } else if(qualifiesForCaseStatement(ref.child)){
          cFile.println("        " + addToQueueInC + childPtr + ", "+stateID+");"); 
        } else {
          addAllocChecker(fn, tmp, et, ref.child, currPtr, pDepth + 1, heaprootNum, stateID, toVisit);
        }
        
        cFile.println("    }");  //close for queryVistedHashtable
        
        cFile.println("}"); //close for internal case statement
      }
    }
    
    cFile.append("    default:\n" +
    		            "       break;\n"+
    		            "    }}\n"); //internal switch. 
  }

  // decide whether the given SESE doesn't have traversers at all
  public boolean hasEmptyTraversers(FlatSESEEnterNode fsen) {
    boolean hasEmpty = true;

    Set<FlatSESEEnterNode> children = fsen.getChildren();
    for (Iterator iterator = children.iterator(); iterator.hasNext();) {
      FlatSESEEnterNode child = (FlatSESEEnterNode) iterator.next();
      hasEmpty &= child.getInVarsForDynamicCoarseConflictResolution().size() == 0;
    }
    return hasEmpty;
    
  }  

  private void printDebug(boolean guard, String debugStatements) {
    if(guard)
      System.out.println(debugStatements);
  }
  
  /*
  //Walks the connected heaproot groups, coalesces them, and numbers them
  //Special Note: Lookup Table must already be created 
  private void enumerateHeaproots() {
    weaklyConnectedHRCounter = 0;
    num2WeaklyConnectedHRGroup = new ArrayList<WeaklyConectedHRGroup>();
    
    for(Taint t: connectedHRHash.keySet()) {
      if(connectedHRHash.get(t).id == -1) {
        WeaklyConectedHRGroup hg = connectedHRHash.get(t);
        hg.id = weaklyConnectedHRCounter;
        num2WeaklyConnectedHRGroup.add(weaklyConnectedHRCounter, hg);
        weaklyConnectedHRCounter++;
      }
      
      if(t.isRBlockTaint()) {
        int id=connectedHRHash.get(t).id;
        Pair tup=new Pair(t.getVar(),t.getSESE());
        if (weakMap.containsKey(tup)) {
          if (weakMap.get(tup).intValue()!=id) 
            throw new Error("Var/SESE not unique for weak component.");
        } else 
            weakMap.put(tup, new Integer(id));
      }
    }
    
    //output weakly connected groups for verification
    if(generalDebug) {
      System.out.println("==============Weakly Connected HeapRoots==============");
      
      for(int i=0; i < num2WeaklyConnectedHRGroup.size(); i++){
        System.out.println("Heap Group #" + i);
        WeaklyConectedHRGroup hg = num2WeaklyConnectedHRGroup.get(i);
        for(Taint t: hg.connectedHRs) {
          System.out.println("\t" + t);
        }
      }
      
      System.out.println("=======================END LIST=======================");
    }
  }
  */
  

  //This will keep track of a reference
  private class ObjRef {
    CombinedEffects myEffects;
    boolean reachesConflict;
    int allocID;
    String field;
    
    
    //This keeps track of the parent that we need to pass by inorder to get
    //to the conflicting child (if there is one). 
    ConcreteRuntimeObjNode parent;
    ConcreteRuntimeObjNode child;

    public ObjRef(String fieldname, 
                  ConcreteRuntimeObjNode parent,
                  ConcreteRuntimeObjNode ref,
                  CombinedEffects myEffects) {
      field = fieldname;
      allocID = ref.alloc.getUniqueAllocSiteID();
      child = ref;
      this.parent = parent;
      
      this.myEffects = myEffects;
      reachesConflict = false;
    }
    
    public boolean hasConflictsDownThisPath() {
      return child.descendantsConflict() || child.hasPrimitiveConflicts() || myEffects.hasConflict(); 
    }
    
    public boolean hasDirectObjConflict() {
      return myEffects.hasConflict();
    }
    
    public boolean equals(Object other) {
      if(other == null || !(other instanceof ObjRef)) 
        return false;
      
      ObjRef o = (ObjRef) other;
      
      if(o.field == this.field && o.allocID == this.allocID && this.child.equals(o.child))
        return true;
      
      return false;
    }
    
    public int hashCode() {
      return child.alloc.hashCode() ^ field.hashCode();
    }

    public void mergeWith(ObjRef ref) {
      myEffects.mergeWith(ref.myEffects);
    }
  }
  
  
  //Simply rehashes and combines all effects for a AffectedAllocSite + Field.
  private class EffectsTable {
    private Hashtable<Alloc,Hashtable<String,CombinedEffects>> table;

    public EffectsTable(SMFEState state) {
      table = new Hashtable<Alloc,Hashtable<String,CombinedEffects>>();
      Hashtable<String,CombinedEffects> e4a;
      CombinedEffects ce;
      
      for(Effect e: state.getEffectsAllowed()) {
        if((e4a = table.get(e.getAffectedAllocSite())) == null) {
          e4a = new Hashtable<String,CombinedEffects>();
          table.put(e.getAffectedAllocSite(), e4a);
        }
        
        if((ce = e4a.get(e.getField().getSafeSymbol())) == null) {
          ce = new CombinedEffects();
          e4a.put(e.getField().getSafeSymbol(), ce);
        }
        //TODO do something about effects transitions allowed!! :O
        // while building and what not. 
        
        Set<SMFEState> transitions = (state.getTransistionEffects().contains(e))?state.transitionsTo(e):null;
        ce.add(e, state.getConflicts().contains(e),transitions);
      }
    }
    
    public CombinedEffects getCombinedEffects(Alloc curr, String field) {
      return table.get(curr).get(field);
    }
    
    public Hashtable<String, CombinedEffects> getAllFields(Alloc curr) {
      return table.get(curr);
    }
    
    /*
    
    public EffectsTable(Hashtable<Taint, Set<Effect>> effects,
                        Hashtable<Taint, Set<Effect>> conflicts) {
      table = new Hashtable<Alloc, CombinedEffects>();

      // rehash all effects (as a 5-tuple) by their affected allocation site
      for (Taint t : effects.keySet()) {
        Set<Effect> localConflicts = conflicts.get(t);
        for (Effect e : effects.get(t)) {
          BucketOfEffects bucket;
          if ((bucket = table.get(e.getAffectedAllocSite())) == null) {
            bucket = new BucketOfEffects();
            table.put(e.getAffectedAllocSite(), bucket);
          }
          printDebug(verboseDebug, "Added Taint" + t + " Effect " + e + "Conflict Status = " + (localConflicts!=null?localConflicts.contains(e):false)+" localConflicts = "+localConflicts);
          bucket.add(t, e, localConflicts!=null?localConflicts.contains(e):false);
        }
      }
    }

    public EffectsGroup getEffects(AllocSite parentKey, Taint taint) {
      //This would get the proper bucket of effects and then get all the effects
      //for a parent for a specific taint
      try {
        return table.get(parentKey).taint2EffectsGroup.get(taint);
      }
      catch (NullPointerException e) {
        return null;
      }
    }


    // Run Analysis will walk the data structure and figure out the weakly
    // connected heap roots. 
    public void runAnalysis() {
      if(verboseDebug) {
        printoutTable(this); 
      }
      
      for(Alloc key: table.keySet()) {
        BucketOfEffects effects = table.get(key);
        //make sure there are actually conflicts in the bucket
        if(effects.potentiallyConflictingRoots != null && !effects.potentiallyConflictingRoots.isEmpty()){
          for(String field: effects.potentiallyConflictingRoots.keySet()){
            ArrayList<Taint> taints = effects.potentiallyConflictingRoots.get(field);
            //For simplicity, we just create a new group and add everything to it instead of
            //searching through all of them for the largest group and adding everyone in. 
            WeaklyConectedHRGroup group = new WeaklyConectedHRGroup();
            group.add(taints); //This will automatically add the taint to the connectedHRhash
          }
        }
      }
    }
    */
  }
  
  private class ConcreteRuntimeObjNode {
    HashSet<ObjRef>               referencers;
    Hashtable<String, ObjRefList> referencees;
    Alloc alloc;
    TypeDescriptor type;
    Set<SMFEState> transitions;
    
    boolean isInsetVar;
    
    //Accesses BY this node
    boolean primConfRead=false;
    boolean primConfWrite=false;
    boolean objConfRead=false;
    boolean objConfWrite=false;
    
    public boolean descendantsPrimConflict  = false;
    public boolean descendantsObjConflict   = false;
    public boolean hasPotentialToBeIncorrectDueToConflict = false;
    
    public ConcreteRuntimeObjNode(Alloc a, TypeDescriptor type, Set<SMFEState> transitions, boolean isInset) {
      referencers = new HashSet<ObjRef>(4);
      referencees = new Hashtable<String, ObjRefList>(5);
      
      alloc = a;
      isInsetVar = isInset;
      this.type = type;
      
      if(transitions != null && !transitions.isEmpty()) {
        if(this.transitions == null) {
          this.transitions = new MySet<SMFEState>();
          this.transitions.addAll(transitions);
        } else {
          this.transitions.addAll(transitions);
        }
      }
    }

    public void addReferencer(ObjRef refToMe) {
      referencers.add(refToMe);
    }
    
    public void addReferencee(String field, ObjRef refToChild) {
      ObjRefList array;
      
      if((array = referencees.get(field)) == null) {
        array = new ObjRefList();
        referencees.put(field, array);
      }
      
      array.add(refToChild);
    }
    
    public boolean hasDirectObjConflict() {
      return objConfRead || objConfWrite;
    }
    
    public TypeDescriptor getType() {
      return type;
    }

    public boolean isArray() {
      return type.isArray();
    }
    
    public boolean isTransition() {
      return (transitions != null);
    }

    public int getNumOfReachableParents() {
      return referencers.size();
    }

    public boolean hasPrimitiveConflicts() {
      return primConfRead || primConfWrite;
    }
    
    public boolean hasConflict() {
      return objConfRead || objConfWrite || primConfRead || primConfWrite;
    }
    
    public boolean descendantsConflict() {
      return descendantsObjConflict||descendantsPrimConflict;
    }
  }
  
  //Simple extension of the ArrayList to allow it to find if any ObjRefs conflict.
  private class ObjRefList extends ArrayList<ObjRef> {
    private static final long serialVersionUID = 326523675530835596L;
    
    public ObjRefList() {
      super();
    }
    
    public boolean add(ObjRef o){
      if(this.contains(o)) {
        ObjRef other = this.get(this.indexOf(o));
        other.mergeWith(o);
        return false;
      }
      else
        return super.add(o);
    }
    
    public boolean hasConflicts() {
      for(ObjRef r: this) {
        if(r.hasConflictsDownThisPath() || r.child.hasPrimitiveConflicts()) {
          return true;
        }
      }
      
      return false;
    }
  }
  
  /*
  private class EffectsGroup {
    Hashtable<String, CombinedEffects> myObjEffects;
    //In the end, we don't really care what the primitive fields are.
    Hashtable<String, CombinedEffects> primitiveConflictingFields;
    private boolean primConfRead;
    private boolean primConfWrite;
    
    public EffectsGroup() {
      myObjEffects = new Hashtable<String, CombinedEffects>();
      primitiveConflictingFields = new Hashtable<String, CombinedEffects>();
      
      primConfRead  = false;
      primConfWrite = false;
    }
    
    public void add(Effect e, boolean conflict, boolean leadsToTransistion) {
      CombinedEffects effects;
      if ((effects = myObjEffects.get(e.getField().getSymbol())) == null) {
        effects = new CombinedEffects();
        myObjEffects.put(e.getField().getSymbol(), effects);
      }
      
      effects.add(e, conflict, leadsToTransistion);
      
      if (isReallyAPrimitive(e.getField().getType())) {
        effects.add(e, conflict, false);

        primConfRead |= effects.hasReadConflict;
        primConfWrite |= effects.hasWriteConflict;
      }
    }
    
    
    public boolean isEmpty() {
      return myObjEffects.isEmpty() && primitiveConflictingFields.isEmpty();
    }
    
    public boolean hasPrimitiveConflicts(){
      return !primitiveConflictingFields.isEmpty();
    }
    
    public CombinedEffects getPrimEffect(String field) {
      return primitiveConflictingFields.get(field);
    }

    public boolean hasObjectEffects() {
      return !myObjEffects.isEmpty();
    }
    
    public CombinedEffects getObjEffect(String field) {
      return myObjEffects.get(field);
    }
  }
  */
  
  
//Is the combined effects for all effects with the same affectedAllocSite and field
  private class CombinedEffects {
    ArrayList<Effect> originalEffects;
    Set<SMFEState> transitions;
    
    //Note: if isPrimitive, then we automatically assume that it conflicts.
    public boolean isPrimitive;
    
    public boolean hasReadEffect;
    public boolean hasWriteEffect;
    public boolean hasStrongUpdateEffect;
    
    public boolean hasReadConflict;
    public boolean hasWriteConflict;
    public boolean hasStrongUpdateConflict;
    
    public CombinedEffects() {
      originalEffects         = new ArrayList<Effect>();

      isPrimitive             = false;
      
      hasReadEffect           = false;
      hasWriteEffect          = false;
      hasStrongUpdateEffect   = false;
      
      hasReadConflict         = false;
      hasWriteConflict        = false;
      hasStrongUpdateConflict = false;
      
      transitions             = null;
    }
    
    public boolean add(Effect e, boolean conflict, Set<SMFEState> transitions) {
      assert (transitions==null|| e.getType() == Effect.read);
      if(!originalEffects.add(e))
        return false;
      
      //figure out if it's an obj, primitive, or array
      isPrimitive = isReallyAPrimitive(e.getField().getType());
      
      switch(e.getType()) {
      case Effect.read:
        hasReadEffect = true;
        hasReadConflict |= conflict;
        this.transitions = new MySet<SMFEState>();
        this.transitions.addAll(transitions);
        break;
      case Effect.write:
        hasWriteEffect = true;
        hasWriteConflict |= conflict;
        break;
      case Effect.strongupdate:
        hasStrongUpdateEffect = true;
        hasStrongUpdateConflict |= conflict;
        break;
      default:
        System.out.println("RCR ERROR: An Effect Type never seen before has been encountered");
        assert false;
        break;
      }
      
      return true;
    }
    
    public boolean hasConflict() {
      return hasReadConflict || hasWriteConflict || hasStrongUpdateConflict;
    }
    
    public boolean leadsToTransition() {
      return (transitions != null);
    }

    public void mergeWith(CombinedEffects other) {
      for(Effect e: other.originalEffects) {
        if(!originalEffects.contains(e)){
          originalEffects.add(e);
        }
      }
      
      isPrimitive             |= other.isPrimitive;
      
      hasReadEffect           |= other.hasReadEffect;
      hasWriteEffect          |= other.hasWriteEffect;
      hasStrongUpdateEffect   |= other.hasStrongUpdateEffect;
      
      hasReadConflict         |= other.hasReadConflict;
      hasWriteConflict        |= other.hasWriteConflict;
      hasStrongUpdateConflict |= other.hasStrongUpdateConflict;
      
      if(other.transitions != null) {
        if(transitions == null) {
          transitions = other.transitions;
        } else {
          transitions.addAll(other.transitions);
        }
      }
    }
  }
  
  
  
  /*
  private class WeaklyConectedHRGroup {
    HashSet<Taint> connectedHRs;
    int id;
    
    public WeaklyConectedHRGroup() {
      connectedHRs = new HashSet<Taint>();
      id = -1;
    }
    
    public void add(ArrayList<Taint> list) {
      for(Taint t: list) {
        this.add(t);
      }
    }
    
    public void add(Taint t) {
      connectedHRs.add(t);
      WeaklyConectedHRGroup oldGroup = connectedHRHash.get(t);
      connectedHRHash.put(t, this); //put new group into hash
      //If the taint was already in another group, move all its buddies over. 
      if(oldGroup != this && oldGroup != null) {
        Iterator<Taint> it = oldGroup.connectedHRs.iterator();
        Taint relatedTaint;
        
        while(it.hasNext() && (relatedTaint = it.next()) != null) {
          if(!connectedHRs.contains(relatedTaint)){
            this.add(relatedTaint);
          }
        }
      }
    }
  }

  
  //This is a class that stores all the effects for an affected allocation site
  //across ALL taints. The structure is a hashtable of EffectGroups (see above) hashed
  //by a Taint. This way, I can keep EffectsGroups so I can reuse most to all of my old code
  //and allows for easier tracking of effects. In addition, a hashtable (keyed by the string
  //of the field access) keeps track of an ArrayList of taints of SESEblocks that conflict on that
  //field.
  private class BucketOfEffects {
    // This table is used for lookup while creating the traversal.
    Hashtable<Taint, EffectsGroup> taint2EffectsGroup;
    
    //This table is used to help identify weakly connected groups: Contains ONLY 
    //conflicting effects AND is only initialized when needed
    //String stores the field
    Hashtable<String, ArrayList<Taint>> potentiallyConflictingRoots;

    public BucketOfEffects() {
      taint2EffectsGroup = new Hashtable<Taint, EffectsGroup>();
    }

    public void add(Taint t, Effect e, boolean conflict, boolean leadsToTransition) {
      EffectsGroup effectsForGivenTaint;

      if ((effectsForGivenTaint = taint2EffectsGroup.get(t)) == null) {
        effectsForGivenTaint = new EffectsGroup();
        taint2EffectsGroup.put(t, effectsForGivenTaint);
      }

      if (isReallyAPrimitive(e.getField().getType())) {
        if (conflict) {
          effectsForGivenTaint.addPrimitive(e, true);
        }
      } else {
        effectsForGivenTaint.addObjEffect(e, conflict,leadsToTransition);
      }
      
      if(conflict) {
        if(potentiallyConflictingRoots == null) {
          potentiallyConflictingRoots = new Hashtable<String, ArrayList<Taint>>();
        }
        
        ArrayList<Taint> taintsForField = potentiallyConflictingRoots.get(e.getField().getSafeSymbol());
        if(taintsForField == null) {
          taintsForField = new ArrayList<Taint>();
          potentiallyConflictingRoots.put(e.getField().getSafeSymbol(), taintsForField);
        }
        
        if(!taintsForField.contains(t)) {
          taintsForField.add(t);
        }
      }
    }
  }

  
  
  private class TaintAndInternalHeapStructure {
    public Taint t;
    public Hashtable<Integer, ConcreteRuntimeObjNode> nodesInHeap;
    
    public TaintAndInternalHeapStructure(Taint taint, Hashtable<Integer, ConcreteRuntimeObjNode> nodesInHeap) {
      t = taint;
      this.nodesInHeap = nodesInHeap;
    }
  }
  */

  /*
  private class TraversalInfo {
    public FlatNode f;
    public ReachGraph rg;
    public Graph g;
    public TempDescriptor invar;
    
    public TraversalInfo(FlatNode fn, ReachGraph rg1) {
      f = fn;
      rg = rg1;
      invar = null;
    }

    public TraversalInfo(FlatNode fn, ReachGraph rg1, TempDescriptor tempDesc) {
      f = fn;
      rg =rg1;
      invar = tempDesc;
    }

    public TraversalInfo(FlatNode fn, Graph g1) {
      f = fn;
      g = g1;
      invar = null;
    }

    public TraversalInfo(FlatNode fn, Graph g1, TempDescriptor tempDesc) {
      f = fn;
      g =g1;
      invar = tempDesc;
    }
    
    public boolean isStallSite() {
      return !(f instanceof FlatSESEEnterNode);
    }
    
    public boolean isRblock() {
      return (f instanceof FlatSESEEnterNode);
    }
  }
  */
}
