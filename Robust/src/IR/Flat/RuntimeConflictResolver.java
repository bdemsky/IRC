package IR.Flat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import Analysis.Disjoint.*;
import IR.TypeDescriptor;

//TODO: the below may be outdated. 
/* An instance of this class manages all OoOJava coarse-grained runtime conflicts
 * by generating C-code to either rule out the conflict at runtime or resolve one.
 * 
 * How to Use:
 * 1) Instantiate singleton object
 * 2a) Call void traverse(FlatSESEEnterNode rblock, Hashtable<Taint, Set<Effect>> effects, Hashtable<Taint, Set<Effect>> conflicts, ReachGraph rg)
 *    as many times as needed
 * 2b) call String getTraverserInvocation(TempDescriptor invar, String varString, FlatSESEEnterNode sese) to get the name of the traverse method in C
 * 3) Call void close()
 */
public class RuntimeConflictResolver {
  public static final boolean javaDebug = true;
  public static final boolean cSideDebug = true;
  
  private PrintWriter cFile;
  private PrintWriter headerFile;
  private static final String hashAndQueueCFileDir = "oooJava/";
  //This keeps track of taints we've traversed to prevent printing duplicate traverse functions
  //The Integer keeps track of the weakly connected group it's in (used in enumerateHeapRoots)
  private Hashtable<Taint, Integer> doneTaints;

  // initializing variables can be found in printHeader()
  private static final String getAllocSiteInC = "->allocsite";
  private static final String queryAndAddHashTableInC = "hashRCRInsert(";
  private static final String addToQueueInC = "enqueueRCRQueue(";
  private static final String dequeueFromQueueInC = "dequeueRCRQueue()";

  private static final String clearQueue = "resetRCRQueue()";
  // Make hashtable; hashRCRCreate(unsigned int size, double loadfactor)
  private static final String mallocHashTable = "hashRCRCreate(1024, 0.25)";
  private static final String deallocHashTable = "hashRCRDelete()";
  private static final String resetHashTable = "hashRCRreset()";
  
  //TODO find correct strings for these
  private static final String addCheckFromHashStructure = "";
  private static final String putWaitingQueueBlock = "";  //lifting of blocks will be done by hashtable.
  private static final String putIntoAllocQueue = "";
  
  //NOTE: make sure these returned are synced with hashtable
  private static final int noConflict = 0;
  private static final int conflictButTraverserCanContinue = 1;
  private static final int conflictButTraverserCannotContinue = 2;
  
  // hashtable provides fast access to heaproot # lookups
  private Hashtable<Taint, WeaklyConectedHRGroup> connectedHRHash;
  private int traverserIDCounter;

  public RuntimeConflictResolver(String buildir) throws FileNotFoundException {
    String outputFile = buildir + "RuntimeConflictResolver";
    
    cFile = new PrintWriter(new File(outputFile + ".c"));
    headerFile = new PrintWriter(new File(outputFile + ".h"));
    
    cFile.append("#include \"" + hashAndQueueCFileDir + "hashRCR.h\"\n#include \""
        + hashAndQueueCFileDir + "Queue_RCR.h\"\n#include <stdlib.h>\n");
    cFile.append("#include \"classdefs.h\"\n");
    cFile.append("#include \"RuntimeConflictResolver.h\"\n");
    
    headerFile.append("#ifndef __3_RCR_H_\n");
    headerFile.append("#define __3_RCR_H_\n");
    //TODO more closely integrate this by asking generic type from other components? 
    //generic cast struct
    cFile.append("struct genericObjectStruct {int type; int oid; int allocsite; int ___cachedCode___; int ___cachedHash___;};\n");
    
    doneTaints = new Hashtable<Taint, Integer>();
    connectedHRHash = new Hashtable<Taint, WeaklyConectedHRGroup>();
    
    traverserIDCounter = 1;
  }

  //TODO update basic steps.
  /*
   * Basic steps:  
   * 1) Create a hashed Effects Lookup Table (hashed by affected allocsite and then taint). 
   *     1a) Use effects sets to verify if we can access something (reads)
   *     1b) Use conflicts list to mark conflicts 
   * 2) Build runtime representation of data structure
   *     2a) Mark conflicts with 2 flags (one for self, one for references down the line) 
   * 3) Printout via traversing data structure created in previous step.
   * 
   */
  
  //TODO ask YongHun if all these effects/conflicts are global, meaning they include stallsites
  //and all SESEblocks. If so , then we could probably make it where the effects are only parsed
  //ONCE and reused for all rblocks/stallsites. 
  public void traverseSESEBlock(FlatSESEEnterNode rblock, 
      Hashtable<Taint, Set<Effect>> effects,
      Hashtable<Taint, Set<Effect>> conflicts, 
      ReachGraph rg) {
    Set<TempDescriptor> inVars = rblock.getInVarSet();
    
    if (inVars.size() == 0)
      return;
    
    //Builds Effects Table and runs the analysis on them to get weakly connected HRs
    EffectsTable effectsLookupTable = new EffectsTable(effects, conflicts);
    effectsLookupTable.runAnaylsis();
    
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
      
      Taint taint = getProperTaintForFlatSESEEnterNode(rblock, varNode, effects);
      if (taint == null) {
        printDebug(javaDebug, "Null FOR " +varNode.getTempDescriptor().getSafeSymbol() + rblock.toPrettyString());
        return;
      }
      
      //This is to prevent duplicate traversals from being generated 
      if(doneTaints.containsKey(taint) && doneTaints.get(taint) != null)
        return;
      
      doneTaints.put(taint, traverserIDCounter++);
      createConcreteGraph(effectsLookupTable, created, varNode, taint);
      
      //TODO also need to fix location where enumerateHeaproots is done 
      //and perhaps where the methods are print out.
      //Idea: separate printing from traversals by saving the "created" hashtable
      
      if (!created.isEmpty()) {
        //TODO change invocation to new format
        //rblock.addInVarForDynamicCoarseConflictResolution(invar);
        printCMethods(created, invar.getSafeSymbol(), rblock.getPrettyIdentifier(), taint);
      }
    }
  }

  public void traverseStallSite(
      FlatNode enterNode,
      TempDescriptor invar,
      Hashtable<Taint, Set<Effect>> effects,
      Hashtable<Taint, Set<Effect>> conflicts, 
      ReachGraph rg) {
    TypeDescriptor type = invar.getType();
    if(type == null || type.isPrimitive()) {
      return;
    }
    
    Hashtable<AllocSite, ConcreteRuntimeObjNode> created = new Hashtable<AllocSite, ConcreteRuntimeObjNode>();
    VariableNode varNode = rg.getVariableNodeNoMutation(invar);
    Taint taint = getProperTaintForEnterNode(enterNode, varNode, effects);
    EffectsTable effectsLookupTable = new EffectsTable(effects, conflicts);
    
    if (taint == null) {
      printDebug(javaDebug, "Null FOR " +varNode.getTempDescriptor().getSafeSymbol() + enterNode.toString());
      return;
    }        
    
    if(doneTaints.containsKey(taint) && doneTaints.get(taint) != null)
      return;
    
    doneTaints.put(taint, traverserIDCounter++);
    
    createConcreteGraph(effectsLookupTable, created, varNode, taint);
    
    if (!created.isEmpty()) {
      printCMethods(created, invar.getSafeSymbol(), enterNode.toString(), taint);
    }
    
  }

  //TODO replace this with new format of passing in a starting variable and a traverser ID
  public String getTraverserInvocation(TempDescriptor invar, String varString, FlatNode fn) {
    String flatname;
    if(fn instanceof FlatSESEEnterNode) {
      flatname = ((FlatSESEEnterNode) fn).getPrettyIdentifier();
    }
    else {
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
    // Adds Extra supporting methods
    cFile.append("void initializeStructsRCR() { " + mallocHashTable + "; " + clearQueue + "; }");
    cFile.append("void destroyRCR() { " + deallocHashTable + "; }\n");
    
    headerFile.append("void initializeStructsRCR(); \nvoid destroyRCR(); \n");
    headerFile.append("#endif\n");

    cFile.close();
    headerFile.close();
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
    
    //TODO restore debug functionality
//    if(javaDebug) {
//      printLookupTableDebug(table);
//    }

    Iterator<RefEdge> possibleEdges = varNode.iteratorToReferencees();    
    while (possibleEdges.hasNext()) {
      RefEdge edge = possibleEdges.next();
      assert edge != null;

      ConcreteRuntimeObjNode singleRoot = new ConcreteRuntimeObjNode(edge.getDst(), true);
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
          myEffects.addPrimative(e);
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
            child = new ConcreteRuntimeObjNode(childHRN, false);
            created.put(childKey, child);
          }
          else {
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
            }
            else {
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
    if(currEffects.hasPrimativeConflicts()) {
      curr.conflictingPrimitiveFields = currEffects.primativeConflictingFields; 
      //Reminder: primitive conflicts are abstracted to object. 
      curr.hasPotentialToBeIncorrectDueToConflict = true;
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
  private void printCMethods(Hashtable<AllocSite, ConcreteRuntimeObjNode> created, String inVar, String rBlock, Taint taint) {
    //This hash table keeps track of all the case statements generated. Although it may seem a bit much
    //for its purpose, I think it may come in handy later down the road to do it this way. 
    //(i.e. what if we want to eliminate some cases? Or build filter for 1 case)
    Hashtable<AllocSite, StringBuilder> cases = new Hashtable<AllocSite, StringBuilder>();
    
    //TODO fix place of enumeration
    //enumerate heaproots before we start
    enumerateHeaproots();
    
    //Generate C cases 
    for (ConcreteRuntimeObjNode node : created.values()) {
      if (!cases.containsKey(node.allocSite) && (          
          //insetVariable case
          (node.isInsetVar && (node.decendantsConflict() || node.hasPrimitiveConflicts())) ||
          //non-inline-able code cases
          (node.getNumOfReachableParents() != 1 && node.decendantsConflict()) ||
          //Cases where resumes are possible
          (node.hasPotentialToBeIncorrectDueToConflict))) {

        printDebug(javaDebug, node.allocSite + " qualified for case statement");
        addChecker(node, cases, null, "ptr", 0);
      }
    }
    //IMPORTANT: remember to change getTraverserInvocation if you change the line below
    String methodName = "void traverse___" + removeInvalidChars(inVar) + 
                        removeInvalidChars(rBlock) + "___(void * InVar)";
    
    cFile.append(methodName + " {\n");
    headerFile.append(methodName + ";\n");
    
    if(cSideDebug) {
      cFile.append("printf(\"The traverser ran for " + methodName + "\\n\");\n");
    }
    
    if(cases.size() == 0) {
      cFile.append(" return; }");
    } 
    else {
      //Casts the ptr to a genericObjectStruct so we can get to the ptr->allocsite field. 
      cFile.append("struct genericObjectStruct * ptr = (struct genericObjectStruct *) InVar;  if(InVar != NULL) { " + queryAndAddHashTableInC
          + "ptr); do { ");
      
      cFile.append("switch(ptr->allocsite) { ");
      
      for(AllocSite singleCase: cases.keySet())
        cFile.append(cases.get(singleCase));
      
      cFile.append(" default : break; ");
      cFile.append("}} while( (ptr = " + dequeueFromQueueInC + ") != NULL); ");
      
      //Closes the method by clearing the Queue and reseting the hashTable to prevent
      //overhead from freeing and mallocing the structures. 
      cFile.append(clearQueue + "; " + resetHashTable + "; }}\n"); 
    }
    cFile.flush();
  }
  
  /*
   * addChecker creates a case statement for every object that is either an inset variable
   * or has multiple referencers (incoming edges). Else it just prints the checker for that object
   * so that its processing can be pushed up to the referencer node. 
   */
  private void addChecker(ConcreteRuntimeObjNode node, 
                          Hashtable<AllocSite,StringBuilder> cases, 
                          StringBuilder possibleContinuingCase, 
                          String prefix, 
                          int depth) {
    StringBuilder currCase = possibleContinuingCase;
    // We don't need a case statement for things with either 1 incoming or 0 out
    // going edges, because they can be processed when checking the parent. 
    
    if((node.isInsetVar && (node.decendantsConflict() || node.hasPrimitiveConflicts())) ||
       (node.getNumOfReachableParents() != 1 && node.decendantsConflict()) || 
       node.hasPotentialToBeIncorrectDueToConflict) {
      assert prefix.equals("ptr") && !cases.containsKey(node.allocSite);
      currCase = new StringBuilder();
      cases.put(node.allocSite, currCase);
      currCase.append("case " + node.getAllocationSite() + ":\n { ");
    }
    //either currCase is continuing off a parent case or is its own. 
    assert currCase !=null;
    
    //Casts C pointer; depth is used to create unique "myPtr" name for when things are inlined
    String currPtr = "myPtr" + depth;
    
    //Specific Primitives test for invars
    if(node.isInsetVar && node.hasPrimitiveConflicts()) {
//      handlePrimitiveConflict(currCase, prefix, node.conflictingPrimitiveFields, node.allocSite);
      //TODO write code for the following: 
      //checkHashtable for continue
      //If not possible to continue, add all others that must wait on the queue. 
      //if possible continue below.
    }
    
    
    String structType = node.original.getType().getSafeSymbol();
    currCase.append("struct " + structType + " * "+currPtr+"= (struct "+ structType + " * ) " + prefix + "; ");
  
    //Conflicts
    for (ObjRef ref : node.objectRefs) {
      // Will only process edge if there is some sort of conflict with the Child
      if (ref.hasConflictsDownThisPath()) {
        String childPtr = currPtr +"->___" + ref.field + "___";

        // Checks if the child exists and has allocsite matching the conflict
        currCase.append("if(" + childPtr + " != NULL && " + childPtr + getAllocSiteInC + "=="
            + ref.allocSite + ") { ");

        
        //Handles Direct Conflicts and primitive conflicts on child.
        //If there is any conflict on child, check hash
        if(ref.child.hasPrimitiveConflicts() || ref.hasDirectObjConflict()) { 
          currCase.append("int retval = "+ addCheckFromHashStructure + childPtr + ");");
          currCase.append("if(retval == " + conflictButTraverserCannotContinue + ") { \n");
          //If cannot continue, then add all the undetermined references that lead from this child, including self.
          putIntoWaitingQueue();
          
          ConcreteRuntimeObjNode related;
          Iterator<ConcreteRuntimeObjNode> it = ref.child.enqueueToWaitingQueueUponConflict.iterator();
          while(it.hasNext()) {
            related = it.next();
            //TODO finish here
            //TODO probably have a way for the hashtable to keep track of stuff in the waiting Queue;
            putIntoWaitingQueue();
          }
          
          //Else if we can continue continue. 
          currCase.append("} else  {");
        }
          
//        if (ref.hasDirectObjConflict()) {
//          handleObjConflict(currCase, childPtr, node.allocSite, ref);
//        }
        
        //If there are no direct conflicts (determined by static + dynamic), finish check
        if (ref.child.decendantsConflict()) {
          // Checks if we have visited the child before
          currCase.append("if(" + queryAndAddHashTableInC + childPtr + ")) { ");
          if (ref.child.getNumOfReachableParents() == 1 && !ref.child.isInsetVar) {
            addChecker(ref.child, cases, currCase, childPtr, depth + 1);
          }
          else {
            currCase.append(addToQueueInC + childPtr + ");");
          }
          
          currCase.append(" } ");
        }
        //one more brace for the opening if
        if(ref.child.hasPrimitiveConflicts() || ref.hasDirectObjConflict()) {
          currCase.append(" } ");
        }
        
        currCase.append(" } ");
      }
    }

    if((node.isInsetVar && (node.decendantsConflict() || node.hasPrimitiveConflicts())) ||
        (node.getNumOfReachableParents() != 1 && node.decendantsConflict()) || 
        node.hasPotentialToBeIncorrectDueToConflict) {
      currCase.append(" } break; \n");
    }
  }

  private void handleObjConflict(StringBuilder currCase, String childPtr, AllocSite allocSite, ObjRef ref) {
    currCase.append("printf(\"Conflict detected with %p from parent with allocation site %u\\n\"," + childPtr + "," + allocSite.getUniqueAllocSiteID() + ");");
  }
  
  private void handlePrimitiveConflict(StringBuilder currCase, String ptr, ArrayList<String> conflicts, AllocSite allocSite) {
    currCase.append("printf(\"Primitive Conflict detected with %p with alloc site %u\\n\", "+ptr+", "+allocSite.getUniqueAllocSiteID()+"); ");
  }

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

  private void printLookupTableDebug(Hashtable<AllocSite, EffectsGroup> table) {
    System.out.println("==========Table print out============");
      System.out.print("    Key is effect Exists/Conflict\n");
      for(AllocSite allockey: table.keySet()) {
        EffectsGroup eg= table.get(allockey);
        if(eg.hasPrimativeConflicts()) {
          System.out.print("Primitive Conflicts at alloc " + allockey.getUniqueAllocSiteID() +" : ");
          for(String field: eg.primativeConflictingFields) {
            System.out.print(field + " ");
          }
          System.out.println();
        }
        for(String fieldKey: eg.myEffects.keySet()) {
          CombinedObjEffects ce = eg.myEffects.get(fieldKey);
          System.out.println("\nFor allocSite " + allockey.getUniqueAllocSiteID() + " && field " + fieldKey);
          System.out.println("\tread " + ce.hasReadEffect + "/"+ce.hasReadConflict + 
              " write " + ce.hasWriteEffect + "/" + ce.hasWriteConflict + 
              " SU " + ce.hasStrongUpdateEffect + "/" + ce.hasStrongUpdateConflict);
          for(Effect ef: ce.originalEffects) {
            System.out.println("\t" + ef);
          }
        }
      }
      System.out.println("===========End print out=============");
  }

  private void printDebug(boolean guard, String debugStatements) {
    if(guard)
      System.out.println(debugStatements);
  }
  
  //This will print the traverser invocation that takes in a traverserID and 
  //starting ptr
  public void printMasterTraverserInvocation() {
    headerFile.println("\nint traverse(void * startingPtr, int traverserID);");
    cFile.println("\nint traverse(void * startingPtr, int traverserID) {" +
    		"switch(traverserID) { ");
    
    for(Taint t: doneTaints.keySet()) {
      cFile.println("  case: " + doneTaints.get(t));
      if(t.isRBlockTaint()) {
        cFile.println("    " + this.getTraverserInvocation(t.getVar(), "startingPtr", t.getSESE()));
      }
      else if (t.isStallSiteTaint()){
        cFile.println("    " + this.getTraverserInvocation(t.getVar(), "startingPtr", t.getStallSite()));
      } else if(RuntimeConflictResolver.javaDebug) {
        System.out.println("RuntimeConflictResolver encountered a taint that is neither SESE nor stallsite.");
      }
    }
    
    if(RuntimeConflictResolver.cSideDebug) {
      cFile.println("default: printf(\" invalid traverser ID %u was passed in.\n\", traverserID); break;");
    } else {
      cFile.println("default: break;");
    }
    
    cFile.println("}}");
  }
  
  //TODO finish this once waitingqueue side is figured out
  private void putIntoWaitingQueue() {
    //Method looks like this: void put(int allocSiteID, struct WaitingQueue * queue, int effectType, void * resumePtr, int traverserID);  }
  }
  
  private void enumerateHeaproots() {
    int counter = 0;
    for(Taint t: connectedHRHash.keySet()) {
      if(connectedHRHash.get(t).id == -1) {
        connectedHRHash.get(t).id = counter++;
      }
    }
  }
  
  private class EffectsGroup
  {
    Hashtable<String, CombinedObjEffects> myEffects;
    ArrayList<String> primativeConflictingFields;
    
    public EffectsGroup() {
      myEffects = new Hashtable<String, CombinedObjEffects>();
      primativeConflictingFields = new ArrayList<String>();
    }

    public void addPrimative(Effect e) {
      primativeConflictingFields.add(e.getField().toPrettyStringBrief());
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
      return myEffects.isEmpty() && primativeConflictingFields.isEmpty();
    }
    
    public boolean hasPrimativeConflicts(){
      return !primativeConflictingFields.isEmpty();
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
    ArrayList<String> conflictingPrimitiveFields;
    HashSet<ConcreteRuntimeObjNode> parentsWithReadToNode;
    HashSet<ConcreteRuntimeObjNode> parentsThatWillLeadToConflicts;
    //this set keeps track of references down the line that need to be enqueued if traverser is "paused"
    HashSet<ConcreteRuntimeObjNode> enqueueToWaitingQueueUponConflict;
    boolean decendantsPrimConflict;
    boolean decendantsObjConflict;
    boolean hasPotentialToBeIncorrectDueToConflict;
    boolean isInsetVar;
    AllocSite allocSite;
    HeapRegionNode original;

    public ConcreteRuntimeObjNode(HeapRegionNode me, boolean isInVar) {
      objectRefs = new ArrayList<ObjRef>();
      conflictingPrimitiveFields = null;
      parentsThatWillLeadToConflicts = new HashSet<ConcreteRuntimeObjNode>();
      parentsWithReadToNode = new HashSet<ConcreteRuntimeObjNode>();
      enqueueToWaitingQueueUponConflict = new HashSet<ConcreteRuntimeObjNode>();
      allocSite = me.getAllocSite();
      original = me;
      isInsetVar = isInVar;
      decendantsPrimConflict = false;
      decendantsObjConflict = false;
      hasPotentialToBeIncorrectDueToConflict = false;
    }

    public void addReachableParent(ConcreteRuntimeObjNode curr) {
      parentsWithReadToNode.add(curr);
    }
    
    //TODO figure out if getting rid of this hashcode results in correct operation
//    @Override
//    public int hashCode() {
//      // This gets allocsite number
//      return allocSite.hashCodeSpecific();
//    }

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
      return conflictingPrimitiveFields != null;
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
    
    public String toString()
    {
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
          bucket.add(t, e, localConflicts.contains(e));
        }
      }
    }

    public EffectsGroup getEffects(AllocSite parentKey, Taint taint) {
      //This would get the proper bucket of effects and then get all the effects
      //for a parent for a specific taint
      return table.get(parentKey).effects.get(taint);
    }

    // Run Analysis will walk the data structure and figure out the weakly
    // connected heap roots #'s
    public void runAnaylsis() {
      //TODO is there a higher performance way to do this? 
      //walk the structure and put all groups into official groups
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
      
      //Code moved to exterior so we can do the entire set at a time
//      //Walk the official groups and assign each a unique number
//      int counter = 0;
//      for(Taint t: connectedHRHash.keySet()) {
//        if(connectedHRHash.get(t).id == -1) {
//          connectedHRHash.get(t).id = counter++;
//        }
//      }
    }
  }
  
  
  
  private class WeaklyConectedHRGroup {
    HashSet<Taint> connectedHRs;
    int id;
    
    public WeaklyConectedHRGroup() {
      connectedHRs = new HashSet<Taint>();
      id = -1; //this will be later modified
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
    Hashtable<Taint, EffectsGroup> effects;
    
    //This table is used to help identify weakly connected groups: Contains ONLY 
    //conflicting effects AND is only initialized when needed
    Hashtable<String, ArrayList<Taint>> potentiallyConflictingRoots;

    public BucketOfEffects() {
      effects = new Hashtable<Taint, EffectsGroup>();
    }

    public void add(Taint t, Effect e, boolean conflict) {
      EffectsGroup effectsForGivenTaint;

      if ((effectsForGivenTaint = effects.get(t)) == null) {
        effectsForGivenTaint = new EffectsGroup();
        effects.put(t, effectsForGivenTaint);
      }

      if (e.getField().getType().isPrimitive()) {
        if (conflict) {
          effectsForGivenTaint.addPrimative(e);
        }
      } else {
        effectsForGivenTaint.addObjEffect(e, conflict);
      }
      
      if(conflict) {
        if(potentiallyConflictingRoots == null) {
          potentiallyConflictingRoots = new Hashtable<String, ArrayList<Taint>>();
        }
        
        ArrayList<Taint> taintsForField = potentiallyConflictingRoots.get(e.getField());
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
}
