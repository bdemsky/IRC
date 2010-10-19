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
import Analysis.Disjoint.*;
import IR.TypeDescriptor;
import Analysis.OoOJava.OoOJavaAnalysis;

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
  public static final boolean javaDebug = true;
  public static final boolean cSideDebug = false;
  
  private PrintWriter cFile;
  private PrintWriter headerFile;
  private static final String hashAndQueueCFileDir = "oooJava/";
  //This keeps track of taints we've traversed to prevent printing duplicate traverse functions
  //The Integer keeps track of the weakly connected group it's in (used in enumerateHeapRoots)
  private Hashtable<Taint, Integer> doneTaints;
  private Hashtable<Taint, Set<Effect>> globalEffects;
  private Hashtable<Taint, Set<Effect>> globalConflicts;
  private ArrayList<TraversalInfo> toTraverse;

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
  
  //TODO find correct strings for these
  private static final String addCheckFromHashStructure = "checkFromHashStructure(";
  private static final String putWaitingQueueBlock = "putWaitingQueueBlock(";  //lifting of blocks will be done by hashtable.
  private static final String putIntoAllocQueue = "putIntoWaitingQ(";
  private static final int noConflict = 0;
  private static final int conflictButTraverserCanContinue = 1;
  private static final int conflictButTraverserCannotContinue = 2;
  private static final int allocQueueIsNotEmpty = 0;
  
  // Hashtable provides fast access to heaproot # lookups
  private Hashtable<Taint, WeaklyConectedHRGroup> connectedHRHash;
  private ArrayList<WeaklyConectedHRGroup> num2WeaklyConnectedHRGroup;
  private int traverserIDCounter;
  private int weaklyConnectedHRCounter;
  private ArrayList<TaintAndInternalHeapStructure> pendingPrintout;
  private EffectsTable effectsLookupTable;
  private OoOJavaAnalysis oooa;

  public RuntimeConflictResolver(String buildir, OoOJavaAnalysis oooa) throws FileNotFoundException {
    String outputFile = buildir + "RuntimeConflictResolver";
    this.oooa=oooa;

    cFile = new PrintWriter(new File(outputFile + ".c"));
    headerFile = new PrintWriter(new File(outputFile + ".h"));
    
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
  }

  public void setGlobalEffects(Hashtable<Taint, Set<Effect>> effects) {
    globalEffects = effects;
    
    if(javaDebug) {
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
  
  public void addToTraverseToDoList(FlatSESEEnterNode rblock, ReachGraph rg, Hashtable<Taint, Set<Effect>> conflicts) {
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
    // Special Note: The Criteria for executing printCMethod in this loop should match
    // exactly the criteria in buildcode.java to invoke the generated C method(s). 
    for (TempDescriptor invar : inVars) {
      TypeDescriptor type = invar.getType();
      if(type == null || type.isPrimitive()) {
        continue;
      }

      //created stores nodes with specific alloc sites that have been traversed while building
      //internal data structure. It is later traversed sequentially to find inset variables and
      //build output code.
      Hashtable<AllocSite, ConcreteRuntimeObjNode> created = new Hashtable<AllocSite, ConcreteRuntimeObjNode>();
      VariableNode varNode = rg.getVariableNodeNoMutation(invar);
      Taint taint = getProperTaintForFlatSESEEnterNode(rblock, varNode, globalEffects);
      if (taint == null) {
        printDebug(javaDebug, "Null FOR " +varNode.getTempDescriptor().getSafeSymbol() + rblock.toPrettyString());
        return;
      }
      
      //This is to prevent duplicate traversals from being generated 
      if(doneTaints.containsKey(taint))
        return;
      
      doneTaints.put(taint, traverserIDCounter++);
      createConcreteGraph(effectsLookupTable, created, varNode, taint);
      
      
      //This will add the taint to the printout, there will be NO duplicates (checked above)
      if(!created.isEmpty()) {
        rblock.addInVarForDynamicCoarseConflictResolution(invar);
        pendingPrintout.add(new TaintAndInternalHeapStructure(taint, created));
      }
    }
  }
  
  private void traverseStallSite(FlatNode enterNode, TempDescriptor invar, ReachGraph rg) {
    TypeDescriptor type = invar.getType();
    if(type == null || type.isPrimitive()) {
      return;
    }
    Hashtable<AllocSite, ConcreteRuntimeObjNode> created = new Hashtable<AllocSite, ConcreteRuntimeObjNode>();
    VariableNode varNode = rg.getVariableNodeNoMutation(invar);
    Taint taint = getProperTaintForEnterNode(enterNode, varNode, globalEffects);
    
    if (taint == null) {
      printDebug(javaDebug, "Null FOR " +varNode.getTempDescriptor().getSafeSymbol() + enterNode.toString());
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
    
    return "traverse___" + removeInvalidChars(invar.getSafeSymbol()) + 
    removeInvalidChars(flatname) + "___("+varString+");";
  }
  
  public String removeInvalidChars(String in) {
    StringBuilder s = new StringBuilder(in);
    for(int i = 0; i < s.length(); i++) {
      if(s.charAt(i) == ' ' || s.charAt(i) == '.' || s.charAt(i) == '=') {
        s.deleteCharAt(i);
        i--;
      }
    }
    return s.toString();
  }

  public void close() {
    buildEffectsLookupStructure();
    runAllTraverserals();
    
    //prints out all generated code
    for(TaintAndInternalHeapStructure ths: pendingPrintout) {
      printCMethod(ths.nodesInHeap, ths.t);
    }
    
    //Prints out the master traverser Invocation that'll call all other traverser
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
  //SPECIAL NOTE: Only runs after we've taken all the conflicts 
  private void buildEffectsLookupStructure(){
    effectsLookupTable = new EffectsTable(globalEffects, globalConflicts);
    effectsLookupTable.runAnaylsis();
    enumerateHeaproots();
  }

  private void runAllTraverserals() {
    for(TraversalInfo t: toTraverse) {
      printDebug(javaDebug, "Running Traversal a traversal on " + t.f);
      
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
    headerFile.println("void createAndFillMasterHashStructureArray();");
    cFile.println("void createAndFillMasterHashStructureArray() {\n" +
    		"  rcr_createMasterHashTableArray("+weaklyConnectedHRCounter + ");");
    
    for(int i = 0; i < weaklyConnectedHRCounter; i++) {
      cFile.println("  allHashStructures["+i+"] = (HashStructure *) rcr_createHashtable("+num2WeaklyConnectedHRGroup.get(i).connectedHRs.size()+");");
    }
    cFile.println("}");
  }

  private void printMasterTraverserInvocation() {
    headerFile.println("\nint tasktraverse(SESEcommon * record);");
    cFile.println("\nint tasktraverse(SESEcommon * record) {");
    cFile.println("  switch(record->classID) {");
    
    for(Iterator<FlatSESEEnterNode> seseit=oooa.getAllSESEs().iterator();seseit.hasNext();) {
      FlatSESEEnterNode fsen=seseit.next();
      cFile.println(    "    /* "+fsen.getPrettyIdentifier()+" */");
      cFile.println(    "    case "+fsen.getIdentifier()+": {");
      cFile.println(    "      "+fsen.getSESErecordName()+" * rec=("+fsen.getSESErecordName()+" *) record;");
      Vector<TempDescriptor> invars=fsen.getInVarsForDynamicCoarseConflictResolution();
      for(int i=0;i<invars.size();i++) {
	TempDescriptor tmp=invars.get(i);
	cFile.println("      " + this.getTraverserInvocation(tmp, "rec->"+tmp, fsen));
      }
      cFile.println(    "    }");
      cFile.println(    "    break;");
    }

    cFile.println("    default:\n    printf(\"Invalid SESE ID was passed in.\\n\");\n    break;");
    
    cFile.println("  }");
    cFile.println("}");
  }


  //This will print the traverser invocation that takes in a traverserID and 
  //starting ptr
  private void printResumeTraverserInvocation() {
    headerFile.println("\nint traverse(void * startingPtr, int traverserID);");
    cFile.println("\nint traverse(void * startingPtr, int traverserID) {");
    cFile.println(" switch(traverserID) {");
    
    for(Taint t: doneTaints.keySet()) {
      cFile.println("  case " + doneTaints.get(t)+ ":");
      if(t.isRBlockTaint()) {
        cFile.println("    " + this.getTraverserInvocation(t.getVar(), "startingPtr", t.getSESE()));
      } else if (t.isStallSiteTaint()){
        cFile.println("    " + this.getTraverserInvocation(t.getVar(), "startingPtr", t.getStallSite()));
      } else {
        System.out.println("RuntimeConflictResolver encountered a taint that is neither SESE nor stallsite: " + t);
      }
    }
    
    if(RuntimeConflictResolver.cSideDebug) {
      cFile.println("  default:\n    printf(\"Invalid traverser ID %u was passed in.\\n\", traverserID);\n    break;");
    } else {
      cFile.println("  default:\n    break;");
    }
    
    cFile.println(" }");
    cFile.println("}");
  }

  private void createConcreteGraph(
      EffectsTable table,
      Hashtable<AllocSite, ConcreteRuntimeObjNode> created, 
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

      ConcreteRuntimeObjNode singleRoot = new ConcreteRuntimeObjNode(edge.getDst(), true, false);
      AllocSite rootKey = singleRoot.allocSite;

      if (!created.containsKey(rootKey)) {
        created.put(rootKey, singleRoot);
        createHelper(singleRoot, edge.getDst().iteratorToReferencees(), created, table, t);
      }
    }
  }
  
  //This code is the old way of generating an effects lookup table. 
  //The new way is to instantiate an EffectsGroup
  @Deprecated
  private Hashtable<AllocSite, EffectsGroup> generateEffectsLookupTable(
      Taint taint, Hashtable<Taint, 
      Set<Effect>> effects,
      Hashtable<Taint, Set<Effect>> conflicts) {
    if(taint == null)
      return null;
    
    Set<Effect> localEffects = effects.get(taint);
    Set<Effect> localConflicts = conflicts.get(taint);
    
    //Debug Code for manually checking effects
    if(javaDebug) {
      printEffectsAndConflictsSets(taint, localEffects, localConflicts);
    }
    
    if (localEffects == null || localEffects.isEmpty() || localConflicts == null || localConflicts.isEmpty())
      return null;
    
    Hashtable<AllocSite, EffectsGroup> lookupTable = new Hashtable<AllocSite, EffectsGroup>();
    
    for (Effect e : localEffects) {
      boolean conflict = localConflicts.contains(e);
      AllocSite key = e.getAffectedAllocSite();
      EffectsGroup myEffects = lookupTable.get(key);
      
      if(myEffects == null) {
        myEffects = new EffectsGroup();
        lookupTable.put(key, myEffects);
      }
      
      if(e.getField().getType().isPrimitive()) {
        if(conflict) {
          myEffects.addPrimitive(e, true);
        }
      }
      else {
        myEffects.addObjEffect(e, conflict);
      }      
    }
    
    return lookupTable;
  }

  // Plan is to add stuff to the tree depth-first sort of way. That way, we can
  // propagate up conflicts
  private void createHelper(ConcreteRuntimeObjNode curr, 
                            Iterator<RefEdge> edges, 
                            Hashtable<AllocSite, ConcreteRuntimeObjNode> created,
                            EffectsTable table, 
                            Taint taint) {
    assert table != null;
    AllocSite parentKey = curr.allocSite;
    EffectsGroup currEffects = table.getEffects(parentKey, taint); 
    
    if (currEffects == null || currEffects.isEmpty()) 
      return;
    
    //Handle Objects (and primitives if child is new)
    if(currEffects.hasObjectEffects()) {
      while(edges.hasNext()) {
        RefEdge edge = edges.next();
        String field = edge.getField();
        CombinedObjEffects effectsForGivenField = currEffects.getObjEffect(field);
        //If there are no effects, then there's no point in traversing this edge
        if(effectsForGivenField != null) {
          HeapRegionNode childHRN = edge.getDst();
          AllocSite childKey = childHRN.getAllocSite();
          boolean isNewChild = !created.containsKey(childKey);
          ConcreteRuntimeObjNode child; 
          
          if(isNewChild) {
            child = new ConcreteRuntimeObjNode(childHRN, false, curr.isObjectArray());
            created.put(childKey, child);
          } else {
            child = created.get(childKey);
          }
    
          curr.addObjChild(field, child, effectsForGivenField);
          
          if (effectsForGivenField.hasConflict()) {
            child.hasPotentialToBeIncorrectDueToConflict = true;
            propogateObjConflict(curr, child);
          }
          
          if(effectsForGivenField.hasReadEffect) {
            child.addReachableParent(curr);
            
            //If isNewChild, flag propagation will be handled at recursive call
            if(isNewChild) {
              createHelper(child, childHRN.iteratorToReferencees(), created, table, taint);
            } else {
            //This makes sure that all conflicts below the child is propagated up the referencers.
              if(child.decendantsPrimConflict || child.hasPrimitiveConflicts()) {
                propogatePrimConflict(child, child.enqueueToWaitingQueueUponConflict);
              }
              
              if(child.decendantsObjConflict) {
                propogateObjConflict(child, child.enqueueToWaitingQueueUponConflict);
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
      propogatePrimConflict(curr, curr);
    }
  }

  // This will propagate the conflict up the data structure.
  private void propogateObjConflict(ConcreteRuntimeObjNode curr, HashSet<ConcreteRuntimeObjNode> pointsOfAccess) {
    for(ConcreteRuntimeObjNode referencer: curr.parentsWithReadToNode) {
      if(curr.parentsThatWillLeadToConflicts.add(referencer) || //case where referencee has never seen referncer
          (pointsOfAccess != null && referencer.addPossibleWaitingQueueEnqueue(pointsOfAccess))) // case where referencer has never seen possible unresolved referencee below 
      {
        referencer.decendantsObjConflict = true;
        propogateObjConflict(referencer, pointsOfAccess);
      }
    }
  }
  
  private void propogateObjConflict(ConcreteRuntimeObjNode curr, ConcreteRuntimeObjNode pointOfAccess) {
    for(ConcreteRuntimeObjNode referencer: curr.parentsWithReadToNode) {
      if(curr.parentsThatWillLeadToConflicts.add(referencer) || //case where referencee has never seen referncer
          (pointOfAccess != null && referencer.addPossibleWaitingQueueEnqueue(pointOfAccess))) // case where referencer has never seen possible unresolved referencee below 
      {
        referencer.decendantsObjConflict = true;
        propogateObjConflict(referencer, pointOfAccess);
      }
    }
  }
  
  private void propogatePrimConflict(ConcreteRuntimeObjNode curr, HashSet<ConcreteRuntimeObjNode> pointsOfAccess) {
    for(ConcreteRuntimeObjNode referencer: curr.parentsWithReadToNode) {
      if(curr.parentsThatWillLeadToConflicts.add(referencer) || //same cases as above
          (pointsOfAccess != null && referencer.addPossibleWaitingQueueEnqueue(pointsOfAccess))) 
      {
        referencer.decendantsPrimConflict = true;
        propogatePrimConflict(referencer, pointsOfAccess);
      }
    }
  }
  
  private void propogatePrimConflict(ConcreteRuntimeObjNode curr, ConcreteRuntimeObjNode pointOfAccess) {
    for(ConcreteRuntimeObjNode referencer: curr.parentsWithReadToNode) {
      if(curr.parentsThatWillLeadToConflicts.add(referencer) || //same cases as above
          (pointOfAccess != null && referencer.addPossibleWaitingQueueEnqueue(pointOfAccess))) 
      {
        referencer.decendantsPrimConflict = true;
        propogatePrimConflict(referencer, pointOfAccess);
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
   *  and we came across it while checking through it's referencer. Because of this property, 
   *  conflicts will be signaled by the referencer; the only exception is the inset variable which can 
   *  signal a conflict within itself. 
   */
  
  private void printCMethod(Hashtable<AllocSite, ConcreteRuntimeObjNode> created, Taint taint) {
    //This hash table keeps track of all the case statements generated. Although it may seem a bit much
    //for its purpose, I think it may come in handy later down the road to do it this way. 
    //(i.e. what if we want to eliminate some cases? Or build filter for 1 case)
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
    
    Hashtable<AllocSite, StringBuilder> cases = new Hashtable<AllocSite, StringBuilder>();
    
    //Generate C cases 
    for (ConcreteRuntimeObjNode node : created.values()) {
      printDebug(javaDebug, "Considering " + node.allocSite + " for traversal");
      if (!cases.containsKey(node.allocSite) && qualifiesForCaseStatement(node)) {

        printDebug(javaDebug, "+\t" + node.allocSite + " qualified for case statement");
        addChecker(taint, node, cases, null, "ptr", 0);
      }
    }
    //IMPORTANT: remember to change getTraverserInvocation if you change the line below
    String methodName = "void traverse___" + removeInvalidChars(inVar) + 
                        removeInvalidChars(rBlock) + "___(void * InVar)";
    
    cFile.println(methodName + " {");
    headerFile.println(methodName + ";");
    
    if(cSideDebug) {
      cFile.println("printf(\"The traverser ran for " + methodName + "\\n\");");
    }
    
    if(cases.size() == 0) {
      cFile.println(" return; }");
    } 
    else {
      //clears queue and hashtable that keeps track of where we've been. 
      cFile.println(clearQueue + ";\n" + resetVisitedHashTable + ";"); 
      
      //Casts the ptr to a genericObjectStruct so we can get to the ptr->allocsite field. 
      cFile.println("struct genericObjectStruct * ptr = (struct genericObjectStruct *) InVar;\nif (InVar != NULL) {\n " + queryVistedHashtable
          + "(ptr);\n do {");
      
      cFile.println("  switch(ptr->allocsite) {");
      
      for(AllocSite singleCase: cases.keySet())
        cFile.append(cases.get(singleCase));
      
      cFile.println("  default:\n    break; ");
      cFile.println("  }\n } while((ptr = " + dequeueFromQueueInC + ") != NULL);\n}\n}\n");
    }
    cFile.flush();
  }
  
  /*
   * addChecker creates a case statement for every object that is either an inset variable
   * or has multiple referencers (incoming edges). Else it just prints the checker for that object
   * so that its processing can be pushed up to the referencer node. 
   */
  private void addChecker(Taint taint, 
                          ConcreteRuntimeObjNode node, 
                          Hashtable<AllocSite,StringBuilder> cases, 
                          StringBuilder possibleContinuingCase, 
                          String prefix, 
                          int depth) {
    StringBuilder currCase = possibleContinuingCase;
    // We don't need a case statement for things with either 1 incoming or 0 out
    // going edges, because they can be processed when checking the parent. 
    if(qualifiesForCaseStatement(node)) {
      assert prefix.equals("ptr") && !cases.containsKey(node.allocSite);
      currCase = new StringBuilder();
      cases.put(node.allocSite, currCase);
      currCase.append("  case " + node.getAllocationSite() + ": {\n");
    }
    //either currCase is continuing off a parent case or is its own. 
    assert currCase !=null;
    boolean primConfRead=false;
    boolean primConfWrite=false;
    boolean objConfRead=false;
    boolean objConfWrite=false;

    //Primitives Test
    for(String field: node.primitiveConflictingFields.keySet()) {
      CombinedObjEffects effect=node.primitiveConflictingFields.get(field);
      primConfRead|=effect.hasReadConflict;
      primConfWrite|=effect.hasWriteConflict;
    }

    //Object Reference Test
    for(ObjRef ref: node.objectRefs) {
      CombinedObjEffects effect=ref.myEffects;
      objConfRead|=effect.hasReadConflict;
      objConfWrite|=effect.hasWriteConflict;
    }
  
    if (objConfRead) {
      currCase.append("    if(");
      checkWaitingQueue(currCase, taint,  node);
      currCase.append("||");
    }

    //Do call if we need it.
    if(primConfWrite||objConfWrite) {
      int heaprootNum = connectedHRHash.get(taint).id;
      assert heaprootNum != -1;
      int allocSiteID = connectedHRHash.get(taint).getWaitingQueueBucketNum(node);
      int traverserID = doneTaints.get(taint);
      currCase.append("    rcr_WRITEBINCASE(allHashStructures["+heaprootNum+"],"+prefix+","+traverserID+",NULL,NULL)");
    } else if (primConfRead||objConfRead) {
      int heaprootNum = connectedHRHash.get(taint).id;
      assert heaprootNum != -1;
      int allocSiteID = connectedHRHash.get(taint).getWaitingQueueBucketNum(node);
      int traverserID = doneTaints.get(taint);
      currCase.append("    rcr_READBINCASE(allHashStructures["+heaprootNum+"],"+prefix+","+traverserID+",NULL,NULL)");
    }

    if(objConfRead) {
      currCase.append(") {\n");
      putIntoWaitingQueue(currCase, taint, node, prefix);        
      currCase.append("    break;\n");
      currCase.append("    }\n");
    } else if(primConfRead||primConfWrite||objConfWrite) {
      currCase.append(";\n");
    }

    
    //Conflicts
    
    //Array Case
    if(node.isObjectArray() && node.decendantsConflict()) {
      //since each array element will get its own case statement, we just need to enqueue each item into the queue
      //note that the ref would be the actual object and node would be of struct ArrayObject
      
      //This is done with the assumption that an array of object stores pointers. 
      currCase.append("{\n  int i;\n");
      currCase.append("  for(i = 0; i<((struct ArrayObject *) " + prefix + " )->___length___; i++ ) {\n");
      currCase.append("    void * arrayElement = ((INTPTR *)(&(((struct ArrayObject *) " + prefix + " )->___length___) + sizeof(int)))[i];\n");
      currCase.append("    if( arrayElement != NULL && "+ queryVistedHashtable +"(arrayElement)) {\n");
      currCase.append("      " + addToQueueInC + "arrayElement); }}}\n");
      
    } else {
    //All other cases
      for(ObjRef ref: node.objectRefs) {     
        // Will only process edge if there is some sort of conflict with the Child
        if (ref.hasConflictsDownThisPath()) {  
          String childPtr = "((struct "+node.original.getType().getSafeSymbol()+" *)"+prefix +")->___" + ref.field + "___";
          int pdepth=depth+1;
          String currPtr = "myPtr" + pdepth;
          String structType = ref.child.original.getType().getSafeSymbol();
          currCase.append("    struct " + structType + " * "+currPtr+"= (struct "+ structType + " * ) " + childPtr + ";\n");
  
  
          // Checks if the child exists and has allocsite matching the conflict
          currCase.append("    if (" + currPtr + " != NULL && " + currPtr + getAllocSiteInC + "==" + ref.allocSite + ") {\n");
  
          if (ref.child.decendantsConflict() || ref.child.hasPrimitiveConflicts()) {
            // Checks if we have visited the child before
  
            currCase.append("    if (" + queryVistedHashtable +"("+ currPtr + ")) {\n");
            if (ref.child.getNumOfReachableParents() == 1 && !ref.child.isInsetVar) {
              addChecker(taint, ref.child, cases, currCase, currPtr, depth + 1);
            }
            else {
              currCase.append("      " + addToQueueInC + childPtr + ");\n ");
            }
            
            currCase.append("    }\n");
          }
          //one more brace for the opening if
          if(ref.hasDirectObjConflict()) {
            currCase.append("   }\n");
          }
          
          currCase.append("  }\n ");
        }
      }
    }

    if(qualifiesForCaseStatement(node)) {
      currCase.append("  }\n  break;\n");
    }
  }
  
  private boolean qualifiesForCaseStatement(ConcreteRuntimeObjNode node) {
    return (          
        //insetVariable case
        (node.isInsetVar && (node.decendantsConflict() || node.hasPrimitiveConflicts())) ||
        //non-inline-able code cases
        (node.getNumOfReachableParents() != 1 && node.decendantsConflict()) ||
        //Cases where resumes are possible
        (node.hasPotentialToBeIncorrectDueToConflict) && node.decendantsObjConflict) ||
        //Array elements since we have to enqueue them all, we can't in line their checks
        (node.canBeArrayElement() && (node.decendantsConflict() || node.hasPrimitiveConflicts()));
  }

  //This method will touch the waiting queues if necessary.
  //IMPORTANT NOTE: This needs a closing } from the caller and the if is cannot continue
  private void addCheckHashtableAndWaitingQ(StringBuilder currCase, Taint t, ConcreteRuntimeObjNode node, String ptr, int depth) {
    Iterator<ConcreteRuntimeObjNode> it = node.enqueueToWaitingQueueUponConflict.iterator();
    
    currCase.append("    int retval"+depth+" = "+ addCheckFromHashStructure + ptr + ");\n");
    currCase.append("    if (retval"+depth+" == " + conflictButTraverserCannotContinue + " || ");
    checkWaitingQueue(currCase, t,  node);
    currCase.append(") {\n");
    //If cannot continue, then add all the undetermined references that lead from this child, including self.
    //TODO need waitingQueue Side to automatically check the thing infront of it to prevent double adds. 
    putIntoWaitingQueue(currCase, t, node, ptr);  
    
    ConcreteRuntimeObjNode related;
    while(it.hasNext()) {
      related = it.next();
      //TODO maybe ptr won't even be needed since upon resume, the hashtable will remove obj. 
      putIntoWaitingQueue(currCase, t, related, ptr);
    }
  }

  /*
  private void handleObjConflict(StringBuilder currCase, String childPtr, AllocSite allocSite, ObjRef ref) {
    currCase.append("printf(\"Conflict detected with %p from parent with allocation site %u\\n\"," + childPtr + "," + allocSite.getUniqueAllocSiteID() + ");");
  }
  
  private void handlePrimitiveConflict(StringBuilder currCase, String ptr, ArrayList<String> conflicts, AllocSite allocSite) {
    currCase.append("printf(\"Primitive Conflict detected with %p with alloc site %u\\n\", "+ptr+", "+allocSite.getUniqueAllocSiteID()+"); ");
  }
  */
  
  private Taint getProperTaintForFlatSESEEnterNode(FlatSESEEnterNode rblock, VariableNode var,
      Hashtable<Taint, Set<Effect>> effects) {
    Set<Taint> taints = effects.keySet();
    for (Taint t : taints) {
      FlatSESEEnterNode sese = t.getSESE();
      if(sese != null && sese.equals(rblock) && t.getVar().equals(var.getTempDescriptor())) {
        return t;
      }
    }
    return null;
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
  
  private void printEffectsAndConflictsSets(Taint taint, Set<Effect> localEffects,
      Set<Effect> localConflicts) {
    System.out.println("#### List of Effects/Conflicts ####");
    System.out.println("For Taint " + taint);
    System.out.println("Effects");
    if(localEffects != null) {
      for(Effect e: localEffects) {
       System.out.println(e); 
      }
    }
    System.out.println("Conflicts");
    if(localConflicts != null) {
      for(Effect e: localConflicts) {
        System.out.println(e); 
      }
    }
  }

  private void printDebug(boolean guard, String debugStatements) {
    if(guard)
      System.out.println(debugStatements);
  }
  
  //TODO finish this once waitingQueue side is figured out
  private void putIntoWaitingQueue(StringBuilder sb, Taint taint, ConcreteRuntimeObjNode node, String resumePtr ) {
    //Method looks like this: void put(int allocSiteID,  x
    //struct WaitingQueue * queue, //get this from hashtable
    //int effectType, //not so sure we would actually need this one. 
    //void * resumePtr, 
    //int traverserID);  }
    int heaprootNum = connectedHRHash.get(taint).id;
    assert heaprootNum != -1;
    int allocSiteID = connectedHRHash.get(taint).getWaitingQueueBucketNum(node);
    int traverserID = doneTaints.get(taint);
    
    //NOTE if the C-side is changed, this will have to be changed accordingly
    //TODO make sure this matches c-side
    sb.append("      putIntoWaitingQueue("+allocSiteID+", " +
    		"allHashStructures["+ heaprootNum +"]->waitingQueue, " +
    		resumePtr + ", " +
    		traverserID+");\n");
  }
  
  //TODO finish waitingQueue side
  /**
   * Inserts check to see if waitingQueue is occupied.
   * 
   * On C-side, =0 means empty queue, else occupied. 
   */
  private void checkWaitingQueue(StringBuilder sb, Taint taint, ConcreteRuntimeObjNode node) {
    //Method looks like int check(struct WaitingQueue * queue, int allocSiteID)
    assert sb != null && taint !=null;    
    int heaprootNum = connectedHRHash.get(taint).id;
    assert heaprootNum != -1;
    int allocSiteID = connectedHRHash.get(taint).getWaitingQueueBucketNum(node);
    
    sb.append(" (isEmptyForWaitingQ(allHashStructures["+ heaprootNum +"]->waitingQueue, " + allocSiteID + ") == "+ allocQueueIsNotEmpty+")");
  }
  
  private void enumerateHeaproots() {
    //reset numbers jsut in case
    weaklyConnectedHRCounter = 0;
    num2WeaklyConnectedHRGroup = new ArrayList<WeaklyConectedHRGroup>();
    
    for(Taint t: connectedHRHash.keySet()) {
      if(connectedHRHash.get(t).id == -1) {
        WeaklyConectedHRGroup hg = connectedHRHash.get(t);
        hg.id = weaklyConnectedHRCounter;
        num2WeaklyConnectedHRGroup.add(weaklyConnectedHRCounter, hg);
        weaklyConnectedHRCounter++;
      }
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
  }

  private class ConcreteRuntimeObjNode {
    ArrayList<ObjRef> objectRefs;
    Hashtable<String, CombinedObjEffects> primitiveConflictingFields;
    HashSet<ConcreteRuntimeObjNode> parentsWithReadToNode;
    HashSet<ConcreteRuntimeObjNode> parentsThatWillLeadToConflicts;
    //this set keeps track of references down the line that need to be enqueued if traverser is "paused"
    HashSet<ConcreteRuntimeObjNode> enqueueToWaitingQueueUponConflict;
    boolean decendantsPrimConflict;
    boolean decendantsObjConflict;
    boolean hasPotentialToBeIncorrectDueToConflict;
    boolean isInsetVar;
    boolean isArrayElement;
    AllocSite allocSite;
    HeapRegionNode original;

    public ConcreteRuntimeObjNode(HeapRegionNode me, boolean isInVar, boolean isArrayElement) {
      objectRefs = new ArrayList<ObjRef>();
      primitiveConflictingFields = null;
      parentsThatWillLeadToConflicts = new HashSet<ConcreteRuntimeObjNode>();
      parentsWithReadToNode = new HashSet<ConcreteRuntimeObjNode>();
      enqueueToWaitingQueueUponConflict = new HashSet<ConcreteRuntimeObjNode>();
      allocSite = me.getAllocSite();
      original = me;
      isInsetVar = isInVar;
      decendantsPrimConflict = false;
      decendantsObjConflict = false;
      hasPotentialToBeIncorrectDueToConflict = false;
      this.isArrayElement = isArrayElement;
    }

    public void addReachableParent(ConcreteRuntimeObjNode curr) {
      parentsWithReadToNode.add(curr);
    }

    @Override
    public boolean equals(Object obj) {
      return original.equals(obj);
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
      ObjRef ref = new ObjRef(field, child, ce);
      objectRefs.add(ref);
    }
    
    public boolean isObjectArray() {
      return original.getType().isArray() && !original.getType().isPrimitive() && !original.getType().isImmutable();
    }
    
    public boolean canBeArrayElement() {
      return isArrayElement;
    }
    
    public String toString() {
      return "AllocSite=" + getAllocationSite() + " myConflict=" + !parentsThatWillLeadToConflicts.isEmpty() + 
              " decCon="+decendantsObjConflict+ 
              " NumOfConParents=" + getNumOfReachableParents() + " ObjectChildren=" + objectRefs.size();
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
          printDebug(javaDebug, "Added Taint" + t + " Effect " + e + "Conflict Status = " + (localConflicts!=null?localConflicts.contains(e):false)+" localConflicts = "+localConflicts);
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
    public void runAnaylsis() {
      if(javaDebug) {
        printoutTable(this); 
      }
      
      //TODO is there a higher performance way to do this? 
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
      }
      else {
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
        
        while((relatedTaint = it.next()) != null && !connectedHRs.contains(relatedTaint)) {
          this.add(relatedTaint);
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

      if (e.getField().getType().isPrimitive()) {
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
    public Hashtable<AllocSite, ConcreteRuntimeObjNode> nodesInHeap;
    
    public TaintAndInternalHeapStructure(Taint taint, Hashtable<AllocSite, ConcreteRuntimeObjNode> nodesInHeap) {
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
