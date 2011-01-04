package IR.Flat;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;
import Util.Tuple;
import Analysis.Disjoint.*;
import Analysis.MLP.CodePlan;
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
  
  private PrintWriter cFile;
  private PrintWriter headerFile;
  private static final String hashAndQueueCFileDir = "oooJava/";
  //This keeps track of taints we've traversed to prevent printing duplicate traverse functions
  //The Integer keeps track of the weakly connected group it's in (used in enumerateHeapRoots)
  private Hashtable<Taint, Integer> doneTaints;
  private Hashtable<Tuple, Integer> idMap=new Hashtable<Tuple,Integer>();
  private Hashtable<Tuple, Integer> weakMap=new Hashtable<Tuple,Integer>();
  private Hashtable<Taint, Set<Effect>> globalEffects;
  private Hashtable<Taint, Set<Effect>> globalConflicts;
  private ArrayList<TraversalInfo> toTraverse;

  public int currentID=1;

  // initializing variables can be found in printHeader()
  private static final String getAllocSiteInC = "->allocsite";
  private static final String queryVistedHashtable = "hashRCRInsert";
  private static final String addToQueueInC = "enqueueRCRQueue(";
  private static final String dequeueFromQueueInC = "dequeueRCRQueue()";
  private static final String clearQueue = "resetRCRQueue()";
  // Make hashtable; hashRCRCreate(unsigned int size, double loadfactor)
  private static final String mallocVisitedHashtable = "hashRCRCreate(128, 0.75)";
  private static final String deallocVisitedHashTable = "hashRCRDelete()";
  private static final String resetVisitedHashTable = "hashRCRreset()";
  
  // Hashtable provides fast access to heaproot # lookups
  private Hashtable<Taint, WeaklyConectedHRGroup> connectedHRHash;
  private ArrayList<WeaklyConectedHRGroup> num2WeaklyConnectedHRGroup;
  private int traverserIDCounter;
  private int weaklyConnectedHRCounter;
  private ArrayList<TaintAndInternalHeapStructure> pendingPrintout;
  private EffectsTable effectsLookupTable;
  private OoOJavaAnalysis oooa;
  private State state;

  public RuntimeConflictResolver(String buildir, OoOJavaAnalysis oooa, State state) throws FileNotFoundException {
    String outputFile = buildir + "RuntimeConflictResolver";
    this.oooa=oooa;
    this.state=state;

    cFile = new CodePrinter(new File(outputFile + ".c"));
    headerFile = new CodePrinter(new File(outputFile + ".h"));
    
    cFile.println("#include \"" + hashAndQueueCFileDir + "hashRCR.h\"\n#include \""
        + hashAndQueueCFileDir + "Queue_RCR.h\"\n#include <stdlib.h>");
    cFile.println("#include \"classdefs.h\"");
    cFile.println("#include \"structdefs.h\"");
    cFile.println("#include \"mlp_runtime.h\"");
    cFile.println("#include \"RuntimeConflictResolver.h\"");
    cFile.println("#include \"hashStructure.h\"");
    
    headerFile.println("#ifndef __3_RCR_H_");
    headerFile.println("#define __3_RCR_H_");
    
    doneTaints = new Hashtable<Taint, Integer>();
    connectedHRHash = new Hashtable<Taint, WeaklyConectedHRGroup>();
    
    traverserIDCounter = 1;
    weaklyConnectedHRCounter = 0;
    pendingPrintout = new ArrayList<TaintAndInternalHeapStructure>();
    toTraverse = new ArrayList<TraversalInfo>();
    globalConflicts = new Hashtable<Taint, Set<Effect>>(); 
    //Note: globalEffects is not instantiated since it'll be passed in whole while conflicts comes in chunks
    
    generalDebug = state.RCR_DEBUG || state.RCR_DEBUG_VERBOSE;
    verboseDebug = state.RCR_DEBUG_VERBOSE;
  }

  public void setGlobalEffects(Hashtable<Taint, Set<Effect>> effects) {
    globalEffects = effects;
    
    if(verboseDebug) {
      System.out.println("============EFFECTS LIST AS PASSED IN============");
      for(Taint t: globalEffects.keySet()) {
        System.out.println("For Taint " + t);
        for(Effect e: globalEffects.get(t)) {
          System.out.println("\t" + e);
        }
      }
      System.out.println("====================END  LIST====================");
    }
  }

  public void init() {
    // Go through the SESE's
    printDebug(generalDebug, "======================SESE's======================");
    for (Iterator<FlatSESEEnterNode> seseit = oooa.getAllSESEs().iterator(); seseit.hasNext();) {
      FlatSESEEnterNode fsen = seseit.next();
      Analysis.OoOJava.ConflictGraph conflictGraph;
      Hashtable<Taint, Set<Effect>> conflicts;
      
//      if (fsen.getParent() != null) {
      FlatSESEEnterNode parentSESE = null;
      if (fsen.getSESEParent().size() > 0) {
         parentSESE = (FlatSESEEnterNode) fsen.getSESEParent().iterator().next();
        conflictGraph = oooa.getConflictGraph(parentSESE);
        
        if(generalDebug) {
          System.out.println(fsen);
          System.out.println(fsen.getIsCallerSESEplaceholder());
          System.out.println(fsen.getParent());
          System.out.println("CG=" + conflictGraph);
        }
      }

//      if (!fsen.getIsCallerSESEplaceholder() && fsen.getParent() != null
//          && (conflictGraph = oooa.getConflictGraph(fsen.getParent())) != null
//          && (conflicts = conflictGraph.getConflictEffectSet(fsen)) != null) {
      if(!fsen.getIsCallerSESEplaceholder() && fsen.getSESEParent().size() > 0
          && (conflictGraph = oooa.getConflictGraph(parentSESE)) != null
          && (conflicts = conflictGraph.getConflictEffectSet(fsen)) != null ){
        FlatMethod fm = fsen.getfmEnclosing();
        ReachGraph rg = oooa.getDisjointAnalysis().getReachGraph(fm.getMethod());
        if (generalDebug)
          rg.writeGraph("RCR_RG_SESE_DEBUG");

        addToTraverseToDoList(fsen, rg, conflicts);
      }
    }
    printDebug(generalDebug, "====================END  LIST====================");
    
    // Go through the stall sites
    for (Iterator<FlatNode> codeit = oooa.getNodesWithPlans().iterator(); codeit.hasNext();) {
      FlatNode fn = codeit.next();
      CodePlan cp = oooa.getCodePlan(fn);
      FlatSESEEnterNode currentSESE = cp.getCurrentSESE();
      
      if(currentSESE.getSESEParent().size()==0){
        continue;
      }
      FlatSESEEnterNode parent=(FlatSESEEnterNode)currentSESE.getSESEParent().iterator().next();
//      Analysis.OoOJava.ConflictGraph graph = oooa.getConflictGraph(currentSESE);
      Analysis.OoOJava.ConflictGraph graph = oooa.getConflictGraph(parent);

      if (graph != null) {
        Set<Analysis.OoOJava.SESELock> seseLockSet = oooa.getLockMappings(graph);
        Set<Analysis.OoOJava.WaitingElement> waitingElementSet =
            graph.getStallSiteWaitingElementSet(fn, seseLockSet);

        if (waitingElementSet.size() > 0) {
          for (Iterator<Analysis.OoOJava.WaitingElement> iterator = waitingElementSet.iterator(); iterator.hasNext();) {
            Analysis.OoOJava.WaitingElement waitingElement =
                (Analysis.OoOJava.WaitingElement) iterator.next();

            Analysis.OoOJava.ConflictGraph conflictGraph = graph;
            Hashtable<Taint, Set<Effect>> conflicts;
            ReachGraph rg = oooa.getDisjointAnalysis().getReachGraph(currentSESE.getmdEnclosing());
            if (generalDebug) {
              rg.writeGraph("RCR_RG_STALLSITE_DEBUG");
            }
            if ((conflictGraph != null) && (conflicts = graph.getConflictEffectSet(fn)) != null
                && (rg != null)) {
              addToTraverseToDoList(fn, waitingElement.getTempDesc(), rg, conflicts);
            }
          }
        }
      }
    }

    buildEffectsLookupStructure();
    runAllTraversals();
  }
  
  /*
   * Basic Strategy:
   * 1) Get global effects and conflicts 
   * 2) Create a hash structure (EffectsTable) to manage effects (hashed by affected Allocsite, then taint, then field)
   *     2a) Use Effects to verify we can access something (reads)
   *     2b) Use conflicts to mark conflicts (read/write/strongupdate)
   *     2c) At second level of hash, store Heaproots that can cause conflicts at the field
   * 3) Walk hash structure to identify and enumerate weakly connected groups and generate waiting queue slots. 
   * 4) Build internal representation of the rgs (pruned)
   * 5) Print c methods by walking internal representation
   */
  
  public void addToTraverseToDoList(FlatSESEEnterNode rblock, ReachGraph rg, 
      Hashtable<Taint, Set<Effect>> conflicts) {
    //Add to todo list
    toTraverse.add(new TraversalInfo(rblock, rg));

    //Add to Global conflicts
    for(Taint t: conflicts.keySet()) {
      if(globalConflicts.containsKey(t)) {
        globalConflicts.get(t).addAll(conflicts.get(t));
      } else {
        globalConflicts.put(t, conflicts.get(t));
      }
    }
  }
  

  public void addToTraverseToDoList(FlatNode fn, TempDescriptor tempDesc, 
      ReachGraph rg, Hashtable<Taint, Set<Effect>> conflicts) {
    toTraverse.add(new TraversalInfo(fn, rg, tempDesc));
    
    for(Taint t: conflicts.keySet()) {
      if(globalConflicts.containsKey(t)) {
        globalConflicts.get(t).addAll(conflicts.get(t));
      } else {
        globalConflicts.put(t, conflicts.get(t));
      }
    }
  }

  private void traverseSESEBlock(FlatSESEEnterNode rblock, ReachGraph rg) {
    Collection<TempDescriptor> inVars = rblock.getInVarSet();
    
    if (inVars.size() == 0)
      return;
    
    // For every non-primitive variable, generate unique method
    for (TempDescriptor invar : inVars) {
      TypeDescriptor type = invar.getType();
      if(isReallyAPrimitive(type)) {
        continue;
      }
      
      //"created" stores nodes with specific alloc sites that have been traversed while building
      //internal data structure. Created is later traversed sequentially to find inset variables and
      //build output code.
      //NOTE: Integer stores Allocation Site ID in hashtable
      Hashtable<Integer, ConcreteRuntimeObjNode> created = new Hashtable<Integer, ConcreteRuntimeObjNode>();
      VariableNode varNode = rg.getVariableNodeNoMutation(invar);
      Taint taint = getProperTaintForFlatSESEEnterNode(rblock, varNode, globalEffects);
      if (taint == null) {
        printDebug(generalDebug, "Null FOR " +varNode.getTempDescriptor().getSafeSymbol() + rblock.toPrettyString());
        continue;
      }
      
      //This is to prevent duplicate traversals from being generated 
      if(doneTaints.containsKey(taint))
        return;
      
      doneTaints.put(taint, traverserIDCounter++);
      createConcreteGraph(effectsLookupTable, created, varNode, taint);
      
      
      //This will add the taint to the printout, there will be NO duplicates (checked above)
      if(!created.isEmpty()) {
        for(Iterator<ConcreteRuntimeObjNode> it=created.values().iterator();it.hasNext();) {
          ConcreteRuntimeObjNode obj=it.next();
          if (obj.hasPrimitiveConflicts() ||
              obj.decendantsConflict()    ||
              obj.hasDirectObjConflict       ) {
            rblock.addInVarForDynamicCoarseConflictResolution(invar);
            break;
          }
        }
        
        pendingPrintout.add(new TaintAndInternalHeapStructure(taint, created));
      }
    }
  }

  //This extends a tempDescriptor's isPrimitive test by also excluding primitive arrays. 
  private boolean isReallyAPrimitive(TypeDescriptor type) {
    return (type.isPrimitive() && !type.isArray());
  }
  
  private void traverseStallSite(FlatNode enterNode, TempDescriptor invar, ReachGraph rg) {
    TypeDescriptor type = invar.getType();
    if(type == null || isReallyAPrimitive(type)) {
      return;
    }
    
    Hashtable<Integer, ConcreteRuntimeObjNode> created = new Hashtable<Integer, ConcreteRuntimeObjNode>();
    VariableNode varNode = rg.getVariableNodeNoMutation(invar);
    Taint taint = getProperTaintForEnterNode(enterNode, varNode, globalEffects);
    
    if (taint == null) {
      printDebug(generalDebug, "Null FOR " +varNode.getTempDescriptor().getSafeSymbol() + enterNode.toString());
      return;
    }        
    
    if(doneTaints.containsKey(taint))
      return;
    
    doneTaints.put(taint, traverserIDCounter++);
    createConcreteGraph(effectsLookupTable, created, varNode, taint);
    
    if (!created.isEmpty()) {
      pendingPrintout.add(new TaintAndInternalHeapStructure(taint, created));
    }
  }
  
  public String getTraverserInvocation(TempDescriptor invar, String varString, FlatNode fn) {
    String flatname;
    if(fn instanceof FlatSESEEnterNode) {
      flatname = ((FlatSESEEnterNode) fn).getPrettyIdentifier();
    } else {
      flatname = fn.toString();
    }
    
    return "traverse___" + invar.getSafeSymbol() + 
    removeInvalidChars(flatname) + "___("+varString+");";
  }

  public int getWeakID(TempDescriptor invar, FlatNode fn) {
    return weakMap.get(new Tuple(invar, fn)).intValue();
  }

  public int getTraverserID(TempDescriptor invar, FlatNode fn) {
    Tuple t=new Tuple(invar, fn);
    if (idMap.containsKey(t))
      return idMap.get(t).intValue();
    int value=currentID++;
    idMap.put(t, new Integer(value));
    return value;
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

  public void close() {
    //prints out all generated code
    for(TaintAndInternalHeapStructure ths: pendingPrintout) {
      printCMethod(ths.nodesInHeap, ths.t);
    }
    
    //Prints out the master traverser Invocation that'll call all other traversers
    //based on traverserID
    printMasterTraverserInvocation();
    printResumeTraverserInvocation();
    
    //TODO this is only temporary, remove when thread local vars implemented. 
    createMasterHashTableArray();
    
    // Adds Extra supporting methods
    cFile.println("void initializeStructsRCR() {\n  " + mallocVisitedHashtable + ";\n  " + clearQueue + ";\n}");
    cFile.println("void destroyRCR() {\n  " + deallocVisitedHashTable + ";\n}");
    
    headerFile.println("void initializeStructsRCR();\nvoid destroyRCR();");
    headerFile.println("#endif\n");

    cFile.close();
    headerFile.close();
  }

  //Builds Effects Table and runs the analysis on them to get weakly connected HRs
  //SPECIAL NOTE: Only runs after we've taken all the conflicts and effects
  private void buildEffectsLookupStructure(){
    effectsLookupTable = new EffectsTable(globalEffects, globalConflicts);
    effectsLookupTable.runAnalysis();
    enumerateHeaproots();
  }

  private void runAllTraversals() {
    for(TraversalInfo t: toTraverse) {
      printDebug(generalDebug, "Running Traversal on " + t.f);
      
      if(t.f instanceof FlatSESEEnterNode) {
        traverseSESEBlock((FlatSESEEnterNode)t.f, t.rg);
      } else {
        if(t.invar == null) {
          System.out.println("RCR ERROR: Attempted to run a stall site traversal with NO INVAR");
        } else {
          traverseStallSite(t.f, t.invar, t.rg);
        }
      }
        
    }
  }

  //TODO: This is only temporary, remove when thread local variables are functional. 
  private void createMasterHashTableArray() {
    headerFile.println("struct Hashtable_rcr ** createAndFillMasterHashStructureArray();");
    cFile.println("struct Hashtable_rcr ** createAndFillMasterHashStructureArray() {");
    cFile.println("  struct Hashtable_rcr **table=rcr_createMasterHashTableArray("+weaklyConnectedHRCounter + ");");
    
    for(int i = 0; i < weaklyConnectedHRCounter; i++) {
      cFile.println("  table["+i+"] = (struct Hashtable_rcr *) rcr_createHashtable("+num2WeaklyConnectedHRGroup.get(i).connectedHRs.size()+");");
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
        
        // FIX IT LATER! Right now, we assume that there is only one parent
        // JCJ ask yong hun what we should do in the multi-parent future!
        FlatSESEEnterNode parentSESE = (FlatSESEEnterNode) fsen.getSESEParent().iterator().next();
        ConflictGraph     graph      = oooa.getConflictGraph(parentSESE);
        // JCJ should ConflictGraph create this id string properly for this code?
        String            id         = tmp + "_sese" + fsen.getPrettyIdentifier();
        ConflictNode      node       = graph.getId2cn().get(id);        
        
      	if (i!=0) {
      	    cFile.println("      if (record->rcrstatus!=0)");
      	}
      	
        if(state.NOSTALLTR && node.IsValidToPrune()){
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
    
    for(Taint t: doneTaints.keySet()) {
      if (t.isStallSiteTaint()){
        cFile.println(    "    case -" + getTraverserID(t.getVar(), t.getStallSite())+ ": {");
        cFile.println(    "      SESEstall * rec=(SESEstall*) record;");
        cFile.println(    "      " + this.getTraverserInvocation(t.getVar(), "rec->___obj___, rec", t.getStallSite())+";");
	cFile.println(    "     record->rcrstatus=0;");
        cFile.println(    "    }");
        cFile.println("    break;");
      }
    }

    cFile.println("    default:\n    printf(\"Invalid SESE ID was passed in: %d.\\n\",record->classID);\n    break;");
    cFile.println("  }");
    cFile.println("}");
  }


  //This will print the traverser invocation that takes in a traverserID and starting ptr
  private void printResumeTraverserInvocation() {
    headerFile.println("\nint traverse(void * startingPtr, SESEcommon * record, int traverserID);");
    cFile.println("\nint traverse(void * startingPtr, SESEcommon *record, int traverserID) {");
    cFile.println("  switch(traverserID) {");
    
    for(Taint t: doneTaints.keySet()) {
      cFile.println("  case " + doneTaints.get(t)+ ":");
      if(t.isRBlockTaint()) {
        cFile.println("    " + this.getTraverserInvocation(t.getVar(), "startingPtr, ("+t.getSESE().getSESErecordName()+" *)record", t.getSESE()));
      } else if (t.isStallSiteTaint()){
        // JCJ either remove this or consider writing a comment explaining what it is commented out for
        cFile.println("/*    " + this.getTraverserInvocation(t.getVar(), "startingPtr, record", t.getStallSite())+"*/");
      } else {
        System.out.println("RuntimeConflictResolver encountered a taint that is neither SESE nor stallsite: " + t);
      }
      cFile.println("    break;");
    }
    
    cFile.println("  default:\n    break;");
    
    cFile.println(" }");
    cFile.println("}");
  }

  private void createConcreteGraph(
      EffectsTable table,
      Hashtable<Integer, ConcreteRuntimeObjNode> created, 
      VariableNode varNode, 
      Taint t) {
    
    // if table is null that means there's no conflicts, therefore we need not
    // create a traversal
    if (table == null)
      return;

    Iterator<RefEdge> possibleEdges = varNode.iteratorToReferencees();
    while (possibleEdges.hasNext()) {
      RefEdge edge = possibleEdges.next();
      assert edge != null;

      ConcreteRuntimeObjNode singleRoot = new ConcreteRuntimeObjNode(edge.getDst(), true);
      int rootKey = singleRoot.allocSite.getUniqueAllocSiteID();

      if (!created.containsKey(rootKey)) {
        created.put(rootKey, singleRoot);
        createHelper(singleRoot, edge.getDst().iteratorToReferencees(), created, table, t);
      }
    }
  }

  // Plan is to add stuff to the tree depth-first sort of way. That way, we can
  // propagate up conflicts
  // JCJ what does this method do, exactly?
  private void createHelper(ConcreteRuntimeObjNode curr, 
                            Iterator<RefEdge> edges, 
                            Hashtable<Integer, ConcreteRuntimeObjNode> created,
                            EffectsTable table, 
                            Taint taint) {
    assert table != null;
    AllocSite parentKey = curr.allocSite;
    EffectsGroup currEffects = table.getEffects(parentKey, taint); 
    
    if (currEffects == null || currEffects.isEmpty()) 
      return;
    
    //Handle Objects (and primitives if child is new) JCJ what does this mean?
    if(currEffects.hasObjectEffects()) {
      while(edges.hasNext()) {
        RefEdge edge = edges.next();
        String field = edge.getField();
        CombinedObjEffects effectsForGivenField = currEffects.getObjEffect(field);
        //If there are no effects, then there's no point in traversing this edge
        if(effectsForGivenField != null) {
          HeapRegionNode childHRN = edge.getDst();
          int childKey = childHRN.getAllocSite().getUniqueAllocSiteID();
          boolean isNewChild = !created.containsKey(childKey);
          ConcreteRuntimeObjNode child; 
          
          if(isNewChild) {
            child = new ConcreteRuntimeObjNode(childHRN, false);
            created.put(childKey, child);
          } else {
            child = created.get(childKey);
          }
    
          curr.addObjChild(field, child, effectsForGivenField);
          
          if (effectsForGivenField.hasConflict()) {
            child.hasPotentialToBeIncorrectDueToConflict |= effectsForGivenField.hasReadConflict;
            propagateObjConflict(curr, child);
          }
          
          if(effectsForGivenField.hasReadEffect) {
            child.addReachableParent(curr);
            
            //If isNewChild, flag propagation will be handled at recursive call
            if(isNewChild) {
              createHelper(child, childHRN.iteratorToReferencees(), created, table, taint);
            } else {
            //This makes sure that all conflicts below the child is propagated up the referencers.
              // JCJ spelling of descencents?
              if(child.decendantsPrimConflict || child.hasPrimitiveConflicts()) {
                propagatePrimConflict(child, child.enqueueToWaitingQueueUponConflict);
              }
              
              if(child.decendantsObjConflict) {
                propagateObjConflict(child, child.enqueueToWaitingQueueUponConflict);
              }
            }
          }
        }
      }
    }
    
    //Handles primitives
    curr.primitiveConflictingFields = currEffects.primitiveConflictingFields; 
    if(currEffects.hasPrimitiveConflicts()) {
      //Reminder: primitive conflicts are abstracted to object. 
      propagatePrimConflict(curr, curr);
    }
  }

  // This will propagate the conflict up the data structure.
  private void propagateObjConflict(ConcreteRuntimeObjNode curr, HashSet<ConcreteRuntimeObjNode> pointsOfAccess) {
    for(ConcreteRuntimeObjNode referencer: curr.parentsWithReadToNode) {
      if(curr.parentsThatWillLeadToConflicts.add(referencer) || //case where referencee has never seen referncer
          (pointsOfAccess != null && referencer.addPossibleWaitingQueueEnqueue(pointsOfAccess))) // case where referencer has never seen possible unresolved referencee below 
      {
        referencer.decendantsObjConflict = true;
        propagateObjConflict(referencer, pointsOfAccess);
      }
    }
  }
  
  private void propagateObjConflict(ConcreteRuntimeObjNode curr, ConcreteRuntimeObjNode pointOfAccess) {
    for(ConcreteRuntimeObjNode referencer: curr.parentsWithReadToNode) {
      if( curr.parentsThatWillLeadToConflicts.add(referencer) || //case where referencee has never seen referncer
          (pointOfAccess != null && referencer.addPossibleWaitingQueueEnqueue(pointOfAccess))) // case where referencer has never seen possible unresolved referencee below 
      {
        referencer.decendantsObjConflict = true;
        propagateObjConflict(referencer, pointOfAccess);
      }
    }
  }
  
  private void propagatePrimConflict(ConcreteRuntimeObjNode curr, HashSet<ConcreteRuntimeObjNode> pointsOfAccess) {
    for(ConcreteRuntimeObjNode referencer: curr.parentsWithReadToNode) {
      if(curr.parentsThatWillLeadToConflicts.add(referencer) || //same cases as above
          (pointsOfAccess != null && referencer.addPossibleWaitingQueueEnqueue(pointsOfAccess))) 
      {
        referencer.decendantsPrimConflict = true;
        propagatePrimConflict(referencer, pointsOfAccess);
      }
    }
  }
  
  private void propagatePrimConflict(ConcreteRuntimeObjNode curr, ConcreteRuntimeObjNode pointOfAccess) {
    for(ConcreteRuntimeObjNode referencer: curr.parentsWithReadToNode) {
      if(curr.parentsThatWillLeadToConflicts.add(referencer) || //same cases as above
          (pointOfAccess != null && referencer.addPossibleWaitingQueueEnqueue(pointOfAccess))) 
      {
        referencer.decendantsPrimConflict = true;
        propagatePrimConflict(referencer, pointOfAccess);
      }
    }
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
  
  private void printCMethod(Hashtable<Integer, ConcreteRuntimeObjNode> created, Taint taint) {
    String inVar = taint.getVar().getSafeSymbol();
    String rBlock;
    
    if(taint.isStallSiteTaint()) {
      rBlock = taint.getStallSite().toString();
    } else if(taint.isRBlockTaint()) {
      rBlock = taint.getSESE().getPrettyIdentifier();
    } else {
      System.out.println("RCR CRITICAL ERROR: TAINT IS NEITHER A STALLSITE NOR SESE! " + taint.toString());
      return;
    }
    
    //This hash table keeps track of all the case statements generated.
    Hashtable<AllocSite, StringBuilder> cases = new Hashtable<AllocSite, StringBuilder>();
    
    //Generate C cases 
    for (ConcreteRuntimeObjNode node : created.values()) {
      printDebug(generalDebug, "Considering " + node.allocSite + " for traversal");
      if (!cases.containsKey(node.allocSite) && qualifiesForCaseStatement(node)) {
        printDebug(generalDebug, "+\t" + node.allocSite + " qualified for case statement");
        addChecker(taint, node, cases, null, "ptr", 0);
      }
    }
    
    String methodName;
    int index=-1;

    if (taint.isStallSiteTaint()) {
      methodName= "void traverse___" + inVar + removeInvalidChars(rBlock) + "___(void * InVar, SESEstall *record)";
    } else {
      methodName= "void traverse___" + inVar + removeInvalidChars(rBlock) + "___(void * InVar, "+taint.getSESE().getSESErecordName() +" *record)";
      FlatSESEEnterNode fsese=taint.getSESE();
      TempDescriptor tmp=taint.getVar();
      index=fsese.getInVarsForDynamicCoarseConflictResolution().indexOf(tmp);
     }

    cFile.println(methodName + " {");
    headerFile.println(methodName + ";");
    
    if(cases.size() == 0) {
      cFile.println(" return;");
    } else {
      cFile.println("    int totalcount=RUNBIAS;");      
      if (taint.isStallSiteTaint()) {
        cFile.println("    record->rcrRecords[0].count=RUNBIAS;");
      } else {
        cFile.println("    record->rcrRecords["+index+"].count=RUNBIAS;");
      }
      
      //clears queue and hashtable that keeps track of where we've been. 
      cFile.println(clearQueue + ";\n" + resetVisitedHashTable + ";"); 
      //generic cast to ___Object___ to access ptr->allocsite field. 
      cFile.println("struct ___Object___ * ptr = (struct ___Object___ *) InVar;\nif (InVar != NULL) {\n " + queryVistedHashtable + "(ptr);\n do {");
      if (taint.isRBlockTaint()) {
      	cFile.println("  if(unlikely(record->common.doneExecuting)) {");
      	cFile.println("    record->common.rcrstatus=0;");
      	cFile.println("    return;");
      	cFile.println("  }");
      }
      cFile.println("  switch(ptr->allocsite) {");
      
      for(AllocSite singleCase: cases.keySet()) {
        cFile.append(cases.get(singleCase));
      }
      
      cFile.println("  default:\n    break; ");
      cFile.println("  }\n } while((ptr = " + dequeueFromQueueInC + ") != NULL);\n}");
      
      if (taint.isStallSiteTaint()) {
        //need to add this
        cFile.println("     if(atomic_sub_and_test(totalcount,&(record->rcrRecords[0].count))) {");
        cFile.println("         psem_give_tag(record->common.parentsStallSem, record->tag);");
        cFile.println("         BARRIER();");
        cFile.println("}");
      } else {
        cFile.println("     if(atomic_sub_and_test(totalcount,&(record->rcrRecords["+index+"].count))) {");
        cFile.println("        int flag=LOCKXCHG32(&(record->rcrRecords["+index+"].flag),0);");
        cFile.println("        if(flag) {");
        //we have resolved a heap root...see if this was the last dependence
        cFile.println("            if(atomic_sub_and_test(1, &(record->common.unresolvedDependencies))) workScheduleSubmit((void *)record);");
        cFile.println("        }");
        cFile.println("     }");
      }
    }
    cFile.println("}");
    cFile.flush();
  }
  
  /*
   * addChecker creates a case statement for every object that is an inset variable, has more
   * than 1 parent && has conflicts, or where resumes are possible (from waiting queue). 
   * See .qualifiesForCaseStatement
   */
  private void addChecker(Taint taint, 
                          ConcreteRuntimeObjNode node, 
                          Hashtable<AllocSite,StringBuilder> cases, 
                          StringBuilder possibleContinuingCase, 
                          String prefix, 
                          int depth) {
    StringBuilder currCase = possibleContinuingCase;
    if(qualifiesForCaseStatement(node)) {
      assert prefix.equals("ptr");
      assert !cases.containsKey(node.allocSite);
      currCase = new StringBuilder();
      cases.put(node.allocSite, currCase);
      currCase.append("  case " + node.getAllocationSite() + ": {\n");
    }
    //either currCase is continuing off a parent case or is its own. 
    assert currCase !=null;
    
    insertEntriesIntoHashStructure(taint, node, prefix, depth, currCase);
    
    //Handle conflicts further down. 
    if(node.decendantsConflict()) {
      int pdepth=depth+1;
      currCase.append("{\n");
      
      //Array Case
      if(node.isArray() && node.decendantsConflict()) {
        String childPtr = "((struct ___Object___ **)(((char *) &(((struct ArrayObject *)"+ prefix+")->___length___))+sizeof(int)))[i]";
        String currPtr = "arrayElement" + pdepth;
        
        currCase.append("{\n  int i;\n");
        currCase.append("    struct ___Object___ * "+currPtr+";\n");
        currCase.append("  for(i = 0; i<((struct ArrayObject *) " + prefix + " )->___length___; i++ ) {\n");
        
        //There should be only one field, hence we only take the first field in the keyset.
        assert node.objectRefs.keySet().size() <= 1;
        ObjRefList refsAtParticularField = node.objectRefs.get(node.objectRefs.keySet().iterator().next());
        printObjRefSwitchStatement(taint,cases,pdepth,currCase,refsAtParticularField,childPtr,currPtr);
        currCase.append("      }}\n");
      } else {
      //All other cases
        String currPtr = "myPtr" + pdepth;
        currCase.append("    struct ___Object___ * "+currPtr+";\n");
        for(String field: node.objectRefs.keySet()) {
          ObjRefList refsAtParticularField = node.objectRefs.get(field);
          
          if(refsAtParticularField.hasConflicts()) {
            String childPtr = "((struct "+node.original.getType().getSafeSymbol()+" *)"+prefix +")->___" + field + "___";
            printObjRefSwitchStatement(taint,cases, pdepth, currCase, refsAtParticularField, childPtr, currPtr);
          }
        }      
      }
      
      currCase.append("}\n"); //For particular top level case statement. 
    }
    if(qualifiesForCaseStatement(node)) {
      currCase.append("  }\n  break;\n");
    }
  }

  private void insertEntriesIntoHashStructure(Taint taint, ConcreteRuntimeObjNode curr,
                                              String prefix, int depth, StringBuilder currCase) {
    boolean primConfRead=false;
    boolean primConfWrite=false;
    boolean objConfRead=false;
    boolean objConfWrite=false;
    boolean descendantConflict=false;

    //Direct Primitives Test
    for(String field: curr.primitiveConflictingFields.keySet()) {
      CombinedObjEffects effect=curr.primitiveConflictingFields.get(field);
      primConfRead|=effect.hasReadConflict;
      primConfWrite|=effect.hasWriteConflict;
    }

    //Direct Object Reference Test
    for(String field: curr.objectRefs.keySet()) {
      for(ObjRef ref: curr.objectRefs.get(field)) {
        CombinedObjEffects effect=ref.myEffects;
        objConfRead|=effect.hasReadConflict;
        objConfWrite|=effect.hasWriteConflict;
      }
    }

    if (objConfRead) {
	descendantConflict=curr.decendantsConflict();
    }

    int index=0;
    if (taint.isRBlockTaint()) {
      FlatSESEEnterNode fsese=taint.getSESE();
      TempDescriptor tmp=taint.getVar();
      index=fsese.getInVarsForDynamicCoarseConflictResolution().indexOf(tmp);
    }

    String strrcr=taint.isRBlockTaint()?"&record->rcrRecords["+index+"], ":"NULL, ";
    String tasksrc=taint.isRBlockTaint()?"(SESEcommon *) record, ":"(SESEcommon *)(((INTPTR)record)|1LL), ";
    
    // JCJ could clean this section up a bit
    //Do call if we need it.
    if(primConfWrite||objConfWrite) {
      int heaprootNum = connectedHRHash.get(taint).id;
      assert heaprootNum != -1;
      int allocSiteID = connectedHRHash.get(taint).getWaitingQueueBucketNum(curr);
      int traverserID = doneTaints.get(taint);
        currCase.append("    int tmpkey"+depth+"=rcr_generateKey("+prefix+");\n");
      if (descendantConflict)
        currCase.append("    int tmpvar"+depth+"=rcr_WTWRITEBINCASE(allHashStructures["+heaprootNum+"], tmpkey"+depth+", "+tasksrc+strrcr+index+");\n");
      else
        currCase.append("    int tmpvar"+depth+"=rcr_WRITEBINCASE(allHashStructures["+heaprootNum+"], tmpkey"+depth+", "+ tasksrc+strrcr+index+");\n");
    } else if (primConfRead||objConfRead) {
      int heaprootNum = connectedHRHash.get(taint).id;
      assert heaprootNum != -1;
      int allocSiteID = connectedHRHash.get(taint).getWaitingQueueBucketNum(curr);
      int traverserID = doneTaints.get(taint);
      currCase.append("    int tmpkey"+depth+"=rcr_generateKey("+prefix+");\n");
      if (descendantConflict)
        currCase.append("    int tmpvar"+depth+"=rcr_WTREADBINCASE(allHashStructures["+heaprootNum+"], tmpkey"+depth+", "+tasksrc+strrcr+index+");\n");
      else
        currCase.append("    int tmpvar"+depth+"=rcr_READBINCASE(allHashStructures["+heaprootNum+"], tmpkey"+depth+", "+tasksrc+strrcr+index+");\n");
    }

    if(primConfWrite||objConfWrite||primConfRead||objConfRead) {
      currCase.append("if (!(tmpvar"+depth+"&READYMASK)) totalcount--;\n");
    }
  }

  private void printObjRefSwitchStatement(Taint taint, 
                                          Hashtable<AllocSite, StringBuilder> cases,
                                          int pDepth, 
                                          StringBuilder currCase, 
                                          ObjRefList refsAtParticularField, 
                                          String childPtr,
                                          String currPtr) {
    
    currCase.append("    "+currPtr+"= (struct ___Object___ * ) " + childPtr + ";\n");
    currCase.append("    if (" + currPtr + " != NULL) { \n");
    currCase.append("    switch(" + currPtr + getAllocSiteInC + ") {\n");
    
    for(ObjRef ref: refsAtParticularField) {
      if(ref.child.decendantsConflict() || ref.child.hasPrimitiveConflicts()) {
        currCase.append("      case "+ref.allocSite+":\n      {\n");
        //The hash insert is here because we don't want to enqueue things unless we know it conflicts. 
        currCase.append("        if (" + queryVistedHashtable +"("+ currPtr + ")) {\n");
        
        if(qualifiesForCaseStatement(ref.child)){
            currCase.append("        " + addToQueueInC + childPtr + ");\n "); 
        } else {
          addChecker(taint, ref.child, cases, currCase, currPtr, pDepth + 1);
        }
        
        currCase.append("    }\n");  //close for queryVistedHashtable
        
        currCase.append("}\n"); //close for internal case statement
      }
    }
    
    currCase.append("    default:\n" +
    		            "       break;\n"+
    		            "    }}\n"); //internal switch. 
  }
  
  private boolean qualifiesForCaseStatement(ConcreteRuntimeObjNode node) {
    return (          
        //insetVariable case
        (node.isInsetVar && (node.decendantsConflict() || node.hasPrimitiveConflicts()) || node.hasDirectObjConflict) ||
        //non-inline-able code cases
        (node.getNumOfReachableParents() != 1 && node.decendantsConflict()) ||
        //Cases where resumes are possible
        (node.hasPotentialToBeIncorrectDueToConflict) && node.decendantsObjConflict);
  }
  
  private Taint getProperTaintForFlatSESEEnterNode(FlatSESEEnterNode rblock, VariableNode var,
                                                   Hashtable<Taint, Set<Effect>> effects) {
    Set<Taint> taints = effects.keySet();
    for (Taint t : taints) {
      FlatSESEEnterNode sese = t.getSESE();
      if(sese != null                                 && 
         sese.equals(rblock)                          && 
         t.getVar().equals( var.getTempDescriptor() )
         ) {
        return t;
      }
    }
    return null;
  }
  
  // decide whether the given SESE doesn't have traversers at all
  public boolean hasEmptyTraversers(FlatSESEEnterNode fsen) {
    boolean hasEmpty = true;

    Set<FlatSESEEnterNode> children = fsen.getSESEChildren();
    for (Iterator iterator = children.iterator(); iterator.hasNext();) {
      FlatSESEEnterNode child = (FlatSESEEnterNode) iterator.next();
      hasEmpty &= child.getInVarsForDynamicCoarseConflictResolution().size() == 0;
    }
    return hasEmpty;
    
  }  
  
  private Taint getProperTaintForEnterNode(FlatNode stallSite, VariableNode var,
      Hashtable<Taint, Set<Effect>> effects) {
    Set<Taint> taints = effects.keySet();
    for (Taint t : taints) {
      FlatNode flatnode = t.getStallSite();
      if(flatnode != null && flatnode.equals(stallSite) && t.getVar().equals(var.getTempDescriptor())) {
        return t;
      }
    }
    return null;
  }

  private void printDebug(boolean guard, String debugStatements) {
    if(guard)
      System.out.println(debugStatements);
  }
  
  // JCJ drop a blurb saying what this method assumes is already set up?
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
        Tuple tup=new Tuple(t.getVar(),t.getSESE());
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
  
  private void printoutTable(EffectsTable table) {
    
    System.out.println("==============EFFECTS TABLE PRINTOUT==============");
    for(AllocSite as: table.table.keySet()) {
      System.out.println("\tFor AllocSite " + as.getUniqueAllocSiteID());
      
      BucketOfEffects boe = table.table.get(as);
      
      if(boe.potentiallyConflictingRoots != null && !boe.potentiallyConflictingRoots.isEmpty()) {
        System.out.println("\t\tPotentially conflicting roots: ");
        for(String key: boe.potentiallyConflictingRoots.keySet()) {
          System.out.println("\t\t-Field: " + key);
          System.out.println("\t\t\t" + boe.potentiallyConflictingRoots.get(key));
        }
      }
      for(Taint t: boe.taint2EffectsGroup.keySet()) {
        System.out.println("\t\t For Taint " + t);
        EffectsGroup eg = boe.taint2EffectsGroup.get(t);
          
        if(eg.hasPrimitiveConflicts()) {
          System.out.print("\t\t\tPrimitive Conflicts at alloc " + as.getUniqueAllocSiteID() +" : ");
          for(String field: eg.primitiveConflictingFields.keySet()) {
            System.out.print(field + " ");
          }
          System.out.println();
        }
        for(String fieldKey: eg.myEffects.keySet()) {
          CombinedObjEffects ce = eg.myEffects.get(fieldKey);
          System.out.println("\n\t\t\tFor allocSite " + as.getUniqueAllocSiteID() + " && field " + fieldKey);
          System.out.println("\t\t\t\tread " + ce.hasReadEffect + "/"+ce.hasReadConflict + 
              " write " + ce.hasWriteEffect + "/" + ce.hasWriteConflict + 
              " SU " + ce.hasStrongUpdateEffect + "/" + ce.hasStrongUpdateConflict);
          for(Effect ef: ce.originalEffects) {
            System.out.println("\t" + ef);
          }
        }
      }
        
    }
    
  }
  
  private class EffectsGroup {
    Hashtable<String, CombinedObjEffects> myEffects;
    Hashtable<String, CombinedObjEffects> primitiveConflictingFields;
    
    public EffectsGroup() {
      myEffects = new Hashtable<String, CombinedObjEffects>();
      primitiveConflictingFields = new Hashtable<String, CombinedObjEffects>();
    }

    public void addPrimitive(Effect e, boolean conflict) {
      CombinedObjEffects effects;
      if((effects = primitiveConflictingFields.get(e.getField().getSymbol())) == null) {
        effects = new CombinedObjEffects();
        primitiveConflictingFields.put(e.getField().getSymbol(), effects);
      }
      effects.add(e, conflict);
    }
    
    public void addObjEffect(Effect e, boolean conflict) {
      CombinedObjEffects effects;
      if((effects = myEffects.get(e.getField().getSymbol())) == null) {
        effects = new CombinedObjEffects();
        myEffects.put(e.getField().getSymbol(), effects);
      }
      effects.add(e, conflict);
    }
    
    public boolean isEmpty() {
      return myEffects.isEmpty() && primitiveConflictingFields.isEmpty();
    }
    
    public boolean hasPrimitiveConflicts(){
      return !primitiveConflictingFields.isEmpty();
    }
    
    public CombinedObjEffects getPrimEffect(String field) {
      return primitiveConflictingFields.get(field);
    }

    public boolean hasObjectEffects() {
      return !myEffects.isEmpty();
    }
    
    public CombinedObjEffects getObjEffect(String field) {
      return myEffects.get(field);
    }
  }
  
  //Is the combined effects for all effects with the same affectedAllocSite and field
  private class CombinedObjEffects {
    ArrayList<Effect> originalEffects;
    
    public boolean hasReadEffect;
    public boolean hasWriteEffect;
    public boolean hasStrongUpdateEffect;
    
    public boolean hasReadConflict;
    public boolean hasWriteConflict;
    public boolean hasStrongUpdateConflict;
    
    
    public CombinedObjEffects() {
      originalEffects = new ArrayList<Effect>();
      
      hasReadEffect = false;
      hasWriteEffect = false;
      hasStrongUpdateEffect = false;
      
      hasReadConflict = false;
      hasWriteConflict = false;
      hasStrongUpdateConflict = false;
    }
    
    public boolean add(Effect e, boolean conflict) {
      if(!originalEffects.add(e))
        return false;
      
      switch(e.getType()) {
      case Effect.read:
        hasReadEffect = true;
        hasReadConflict = conflict;
        break;
      case Effect.write:
        hasWriteEffect = true;
        hasWriteConflict = conflict;
        break;
      case Effect.strongupdate:
        hasStrongUpdateEffect = true;
        hasStrongUpdateConflict = conflict;
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

    public void mergeWith(CombinedObjEffects other) {
      for(Effect e: other.originalEffects) {
        if(!originalEffects.contains(e)){
          originalEffects.add(e);
        }
      }
      
      hasReadEffect |= other.hasReadEffect;
      hasWriteEffect |= other.hasWriteEffect;
      hasStrongUpdateEffect |= other.hasStrongUpdateEffect;
      
      hasReadConflict |= other.hasReadConflict;
      hasWriteConflict |= other.hasWriteConflict;
      hasStrongUpdateConflict |= other.hasStrongUpdateConflict;
    }
  }

  //This will keep track of a reference
  private class ObjRef {
    String field;
    int allocSite;
    CombinedObjEffects myEffects;
    
    //This keeps track of the parent that we need to pass by inorder to get
    //to the conflicting child (if there is one). 
    ConcreteRuntimeObjNode child;

    public ObjRef(String fieldname, 
                  ConcreteRuntimeObjNode ref, 
                  CombinedObjEffects myEffects) {
      field = fieldname;
      allocSite = ref.getAllocationSite();
      child = ref;
      
      this.myEffects = myEffects;
    }
    
    public boolean hasConflictsDownThisPath() {
      return child.decendantsObjConflict || child.decendantsPrimConflict || child.hasPrimitiveConflicts() || myEffects.hasConflict(); 
    }
    
    public boolean hasDirectObjConflict() {
      return myEffects.hasConflict();
    }
    
    public boolean equals(Object other) {
      if(other == null || !(other instanceof ObjRef)) 
        return false;
      
      ObjRef o = (ObjRef) other;
      
      if(o.field == this.field && o.allocSite == this.allocSite && this.child.equals(o.child))
        return true;
      
      return false;
    }
    
    public int hashCode() {
      return child.allocSite.hashCode() ^ field.hashCode();
    }

    public void mergeWith(ObjRef ref) {
      myEffects.mergeWith(ref.myEffects);
    }
  }

  private class ConcreteRuntimeObjNode {
    Hashtable<String, ObjRefList> objectRefs;
    Hashtable<String, CombinedObjEffects> primitiveConflictingFields;
    HashSet<ConcreteRuntimeObjNode> parentsWithReadToNode;
    HashSet<ConcreteRuntimeObjNode> parentsThatWillLeadToConflicts;
    //this set keeps track of references down the line that need to be enqueued if traverser is "paused"
    HashSet<ConcreteRuntimeObjNode> enqueueToWaitingQueueUponConflict;
    boolean decendantsPrimConflict;
    boolean decendantsObjConflict;
    boolean hasDirectObjConflict;
    boolean hasPotentialToBeIncorrectDueToConflict;
    boolean isInsetVar;
    AllocSite allocSite;
    HeapRegionNode original;

    public ConcreteRuntimeObjNode(HeapRegionNode me, boolean isInVar) {
      objectRefs = new Hashtable<String, ObjRefList>(5);
      primitiveConflictingFields = null;
      parentsThatWillLeadToConflicts = new HashSet<ConcreteRuntimeObjNode>();
      parentsWithReadToNode = new HashSet<ConcreteRuntimeObjNode>();
      enqueueToWaitingQueueUponConflict = new HashSet<ConcreteRuntimeObjNode>();
      allocSite = me.getAllocSite();
      original = me;
      isInsetVar = isInVar;
      decendantsPrimConflict = false;
      decendantsObjConflict = false;
      hasDirectObjConflict = false;
      hasPotentialToBeIncorrectDueToConflict = false;
    }

    public void addReachableParent(ConcreteRuntimeObjNode curr) {
      parentsWithReadToNode.add(curr);
    }

    // JCJ why is this here?!?!?!?!
    @Override
    public boolean equals(Object other) {
      if(other == null || !(other instanceof ConcreteRuntimeObjNode)) 
        return false;
      
      return original.equals(((ConcreteRuntimeObjNode)other).original);
    }

    public int getAllocationSite() {
      return allocSite.getUniqueAllocSiteID();
    }

    public int getNumOfReachableParents() {
      return parentsThatWillLeadToConflicts.size();
    }
    
    public boolean hasPrimitiveConflicts() {
      return primitiveConflictingFields != null && !primitiveConflictingFields.isEmpty();
    }
    
    public boolean decendantsConflict() {
      return decendantsPrimConflict || decendantsObjConflict;
    }
    
    //returns true if at least one of the objects in points of access has been added
    public boolean addPossibleWaitingQueueEnqueue(HashSet<ConcreteRuntimeObjNode> pointsOfAccess) {
      boolean addedNew = false;
      for(ConcreteRuntimeObjNode dec: pointsOfAccess) {
        if(dec != null && dec != this){
          addedNew = addedNew || enqueueToWaitingQueueUponConflict.add(dec);
        }
      }
      
      return addedNew;
    }
    
    public boolean addPossibleWaitingQueueEnqueue(ConcreteRuntimeObjNode pointOfAccess) {
      if(pointOfAccess != null && pointOfAccess != this){
        return enqueueToWaitingQueueUponConflict.add(pointOfAccess);
      }
      
      return false;
    }

    public void addObjChild(String field, ConcreteRuntimeObjNode child, CombinedObjEffects ce) {
      printDebug(verboseDebug,this.allocSite.getUniqueAllocSiteID() + " added child at " + child.getAllocationSite());
      hasDirectObjConflict |= ce.hasConflict();
      ObjRef ref = new ObjRef(field, child, ce);
      
      if(objectRefs.containsKey(field)){
        ObjRefList array = objectRefs.get(field);
        
        if(array.contains(ref)) {
          ObjRef other = array.get(array.indexOf(ref));
          other.mergeWith(ref);
          printDebug(verboseDebug,"    Merged with old");
          printDebug(verboseDebug,"    Old="+ other.child.original + "\n    new="+ref.child.original);
        }
        else {
          array.add(ref);
          printDebug(verboseDebug,"    Just added new;\n      Field: " + field);
          printDebug(verboseDebug,"      AllocSite: " + child.getAllocationSite());
          printDebug(verboseDebug,"      Child: "+child.original);
        }
      }
      else {
        ObjRefList array = new ObjRefList(3);
        
        array.add(ref);
        objectRefs.put(field, array);
      }
    }
    
    public boolean isArray() {
      return original.getType().isArray();
    }
    
    public ArrayList<Integer> getReferencedAllocSites() {
      ArrayList<Integer> list = new ArrayList<Integer>();
      
      for(String key: objectRefs.keySet()) {
        for(ObjRef r: objectRefs.get(key)) {
          if( r.hasDirectObjConflict() || 
              (r.child.parentsWithReadToNode.contains(this) && r.hasConflictsDownThisPath())
              ) {
            list.add(r.allocSite);
          }
        }
      }
      
      return list;
    }
    
    public String toString() {
      return "AllocSite=" + getAllocationSite() + " myConflict=" + !parentsThatWillLeadToConflicts.isEmpty() + 
              " decCon="+decendantsObjConflict+ 
              " NumOfConParents=" + getNumOfReachableParents() + " ObjectChildren=" + objectRefs.size();
    }
  }
  
  //Simple extension of the ArrayList to allow it to find if any ObjRefs conflict.
  private class ObjRefList extends ArrayList<ObjRef> {
    private static final long serialVersionUID = 326523675530835596L;
    
    public ObjRefList(int size) {
      super(size);
    }
    
    public boolean add(ObjRef o){
      return super.add(o);
    }
    
    public boolean hasConflicts() {
      for(ObjRef r: this) {
        if(r.hasConflictsDownThisPath() || r.child.hasPrimitiveConflicts())
          return true;
      }
      
      return false;
    }
  }
  
  private class EffectsTable {
    private Hashtable<AllocSite, BucketOfEffects> table;

    public EffectsTable(Hashtable<Taint, Set<Effect>> effects,
        Hashtable<Taint, Set<Effect>> conflicts) {
      table = new Hashtable<AllocSite, BucketOfEffects>();

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
      
      for(AllocSite key: table.keySet()) {
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
  }
  
  private class WeaklyConectedHRGroup {
    HashSet<Taint> connectedHRs;
    //This is to keep track of unique waitingQueue positions for each allocsite. 
    Hashtable<AllocSite, Integer> allocSiteToWaitingQueueMap;
    int waitingQueueCounter;
    int id;
    
    public WeaklyConectedHRGroup() {
      connectedHRs = new HashSet<Taint>();
      id = -1; //this will be later modified
      waitingQueueCounter = 0;
      allocSiteToWaitingQueueMap = new Hashtable<AllocSite, Integer>();
    }
    
    public void add(ArrayList<Taint> list) {
      for(Taint t: list) {
        this.add(t);
      }
    }
    
    public int getWaitingQueueBucketNum(ConcreteRuntimeObjNode node) {
      if(allocSiteToWaitingQueueMap.containsKey(node.allocSite)) {
        return allocSiteToWaitingQueueMap.get(node.allocSite);
      } else {
        allocSiteToWaitingQueueMap.put(node.allocSite, waitingQueueCounter);
        return waitingQueueCounter++;
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

    public void add(Taint t, Effect e, boolean conflict) {
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
        effectsForGivenTaint.addObjEffect(e, conflict);
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
  
  private class TraversalInfo {
    public FlatNode f;
    public ReachGraph rg;
    public TempDescriptor invar;
    
    public TraversalInfo(FlatNode fn, ReachGraph g) {
      f = fn;
      rg =g;
      invar = null;
    }

    public TraversalInfo(FlatNode fn, ReachGraph rg2, TempDescriptor tempDesc) {
      f = fn;
      rg =rg2;
      invar = tempDesc;
    }
  }
}
