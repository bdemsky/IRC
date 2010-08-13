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

//TODO fix inaccuracy problem and take advantage of the refEdges
//TODO make it so that methods with no conflicts get no output. 
//TODO Make more efficient by only using ONE hashtable. 

/* An instance of this class manages all OoOJava coarse-grained runtime conflicts
 * by generating C-code to either rule out the conflict at runtime or resolve one.
 * 
 * How to Use:
 * 1) Instantiate singleton object
 * 2) Call void traverse(FlatSESEEnterNode rblock, Hashtable<Taint, Set<Effect>> effects, Hashtable<Taint, Set<Effect>> conflicts, ReachGraph rg)
 *    as many times as needed
 * 3) Call void close()
 */
public class RuntimeConflictResolver {
  public static String outputFile;
  private PrintWriter cFile;
  private PrintWriter headerFile;
  private static final String hashAndQueueCFileDir = "oooJava/";

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

  public RuntimeConflictResolver(String buildir) throws FileNotFoundException {
    outputFile = buildir + "RuntimeConflictResolver";
    
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
  }

  /*
   * Basic steps: 
   * 1) Create pruned data structures from givens 
   *     1a) Use effects sets to verify if we can access something (reads) 
   *     1b) Mark conflicts with 2 flags (one for self, one for references down the line) 
   * 2) build code output structure 
   * 3) printout
   */
  public void traverse(FlatSESEEnterNode rblock, 
      Hashtable<Taint, Set<Effect>> effects,
      Hashtable<Taint, Set<Effect>> conflicts, 
      ReachGraph rg) {
    
    Set<TempDescriptor> inVars = rblock.getInVarSet();
    
    if (inVars.size() == 0)
      return;
    
    // For every non-primative variable, generate unique method
    // Special Note: The Criteria for executing printCMethod in this loop should match
    // exactly the criteria in buildcode.java to invoke the generated C method(s). 
    for (TempDescriptor invar : inVars) {
      TypeDescriptor type = invar.getType();
      if(type == null || type.isPrimitive()) {
        continue;
      }

      Hashtable<AllocSite, ConcreteRuntimeObjNode> created = new Hashtable<AllocSite, ConcreteRuntimeObjNode>();

      createTree(rblock, invar, effects, conflicts, rg, created);
      if (!created.isEmpty()) {
        rblock.addInVarForDynamicCoarseConflictResolution(invar);
        printCMethod(created, invar.getSafeSymbol(), rblock.getPrettyIdentifier());
      }
    }
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

  private void createTree(FlatSESEEnterNode rblock, 
      TempDescriptor invar,
      Hashtable<Taint, Set<Effect>> effects,
      Hashtable<Taint, Set<Effect>> conflicts, 
      ReachGraph rg, 
      Hashtable<AllocSite, ConcreteRuntimeObjNode> created) {

    VariableNode varNode = rg.getVariableNodeNoMutation(invar);
    Hashtable<AllocSite, EffectsGroup> table =
        generateEffectsLookupTable(rblock, varNode, effects, conflicts);
    
    // if table is null that means there's no conflicts, therefore we need not
    // create a traversal
    if (table == null)
      return;

    Iterator<RefEdge> possibleEdges = varNode.iteratorToReferencees();    
    
    while (possibleEdges.hasNext()) {
      RefEdge edge = possibleEdges.next();
      assert edge != null;

      ConcreteRuntimeObjNode singleRoot = new ConcreteRuntimeObjNode(edge.getDst(), false, true);
      AllocSite rootKey = singleRoot.allocSite;

      if (!created.containsKey(rootKey)) {
        created.put(rootKey, singleRoot);
        createHelper(singleRoot, edge.getDst().iteratorToReferencees(), created, table);
      }
    }
  }

  private Hashtable<AllocSite, EffectsGroup> generateEffectsLookupTable(FlatSESEEnterNode rblock,
        VariableNode var, Hashtable<Taint, Set<Effect>> effects,
        Hashtable<Taint, Set<Effect>> conflicts) {
      // we search effects since conflicts is only a subset of effects
      Taint taint = getProperTaint(rblock, var, effects);
      assert taint != null;
    
      Set<Effect> localEffects = effects.get(taint);
      Set<Effect> localConflicts = conflicts.get(taint);
      
      if (localEffects == null || localEffects.isEmpty() || localConflicts == null || localConflicts.isEmpty())
        return null;
      
  //    Debug Code for manually checking effects
  //    System.out.println("For Taint " + taint);
  //    System.out.println("Effects");
  //    for(Effect e: localEffects)
  //    {
  //     System.out.println(e); 
  //    }
  //    
  //    System.out.println("Conflicts");
  //    for(Effect e: localConflicts)
  //    {
  //      System.out.println(e); 
  //    }
      
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
          myEffects.addObj(e, conflict);
        }      
      }
      
      return lookupTable;
    }

  // Plan is to add stuff to the tree depth-first sort of way. That way, we can
  // propagate up conflicts
  private void createHelper(ConcreteRuntimeObjNode curr, Iterator<RefEdge> edges, Hashtable<AllocSite, ConcreteRuntimeObjNode> created,
      Hashtable<AllocSite, EffectsGroup> table) {
    assert table != null;
    
    AllocSite parentKey = curr.allocSite;
    EffectsGroup currEffects = table.get(parentKey);
    
    if (currEffects == null || currEffects.isEmpty()) 
      return;
    
    //Handle Objects
    if(currEffects.hasObjectConflicts()) {
      while(edges.hasNext()) {
        RefEdge edge = edges.next();
        String field = edge.getField();
        EffectPair effect = currEffects.getObjEffect(field); // TODO are you certain there is only one effect to get here?
        //If there is no effect, then there's not point in traversing this edge
        if(effect != null)
        {
          HeapRegionNode childHRN = edge.getDst();
          AllocSite childKey = childHRN.getAllocSite();
          boolean isNewChild = !created.containsKey(childKey);
          ConcreteRuntimeObjNode child; 
    
          if(isNewChild) {
            child = new ConcreteRuntimeObjNode(childHRN, effect.conflict, false);
            created.put(childKey, child);
          }
          else {
            child = created.get(childKey);
          }
    
          curr.addObjChild(field, child, effect.conflict);
          
          if (effect.conflict) {
            propogateObjConflictFlag(child);
          }
          
          if (effect.type == Effect.read && isNewChild) {
            createHelper(child, childHRN.iteratorToReferencees(), created, table);
          }
        }
      }
    }
    
    //Handle primitives
    if(currEffects.hasPrimativeConflicts()) {
      curr.primativeFields = currEffects.primativeConflictingFields; 
      propogatePrimConflictFlag(curr);
    } 
  }

  // This will propagate the conflict up the data structure.
  private void propogateObjConflictFlag(ConcreteRuntimeObjNode in) {
    ConcreteRuntimeObjNode node = in;
    
    while(node.lastReferencer != null) {
      node.lastReferencer.decendantsObjConflict = true;
      if(!node.conflictingParents.add(node.lastReferencer))
        break;
      node = node.lastReferencer;
    }
  }
  
  private void propogatePrimConflictFlag(ConcreteRuntimeObjNode in) {
    ConcreteRuntimeObjNode node = in;
    
    while(node.lastReferencer != null) {
      node.lastReferencer.decendantsPrimConflict = true;
      if(!node.conflictingParents.add(node.lastReferencer))
        break;
      node = node.lastReferencer;
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
  //TODO make empty switch statments just have a return.
  //TODO make check for only 1 case statement (String Builder?)
  //TODO where are all these "temp" variables coming from? 
  private void printCMethod(Hashtable<AllocSite, ConcreteRuntimeObjNode> created, String inVar, String rBlock) {
    HashSet<AllocSite> done = new HashSet<AllocSite>();  
    // note that primitive in-set variables do not generate effects, so we can assume
    // that inVar is an object
    
    //Note: remember to change getTraverserInvocation if you change the line below
    String methodName = "void traverse___" + inVar.replaceAll(" ", "") + rBlock.replaceAll(" ", "") + 
    "___(void * InVar)";
    
    cFile.append(methodName + " {\n");
    headerFile.append(methodName + ";\n");
    
    cFile.append("printf(\"The traverser ran for " + methodName + "\\n\");\n");
    
    //Casts the ptr to a genericObjectSTruct so we can get to the ptr->allocsite field. 
    cFile.append("struct genericObjectStruct * ptr = (struct genericObjectStruct *) InVar;  if(InVar != NULL) { " + queryAndAddHashTableInC
        + "ptr); do { ");
    
    //This part of the code generates the switch statement from all objects in hash. 
    cFile.append("switch(ptr->allocsite) { ");
    for (ConcreteRuntimeObjNode node : created.values()) {
      // If we haven't seen it and it's a node with more than 1 parent
      // Note: a node with 0 parents is a root node (i.e. inset variable)
      if (!done.contains(node.allocSite) && (node.getNumOfReachableParents() != 1 || node.isInsetVar)
          && node.decendantsConflict()) {
        addChecker(node, done, "ptr", 0);
      }
    }
    cFile.append(" default : break; ");
    cFile.append("}} while( (ptr = " + dequeueFromQueueInC + ") != NULL); ");
    
    //Closes the method by clearing the Queue and reseting the hashTable to prevent
    //overhead from freeing and mallocing the structures. 
    cFile.append(clearQueue + "; " + resetHashTable + "; }}\n");
    
    cFile.flush();
  }
  
  public String getTraverserInvocation(TempDescriptor invar, String varString, FlatSESEEnterNode sese) {
    return "traverse___" + invar.getSafeSymbol().replaceAll(" ", "") + 
    sese.getPrettyIdentifier().replaceAll(" ", "") + "___("+varString+");";
  }

  /*
   * addChecker creates a case statement for every object that is either an inset variable
   * or has multiple referencers (incoming edges). Else it just prints the checker for that object
   * so that its processing can be pushed up to the referencer node. 
   */
  private void addChecker(ConcreteRuntimeObjNode node, HashSet<AllocSite> done, String prefix, int depth) {
    // We don't need a case statement for things with either 1 incoming or 0 out
    // going edges, because they can be processed when checking the parent. 
    if ((node.getNumOfReachableParents() != 1 || node.isInsetVar)  && node.decendantsConflict()) {
      assert prefix.equals("ptr");
      cFile.append("case " + node.getAllocationSite() + ":\n { ");
    }
    
    //Specific Primitives test for invars
    if(node.isInsetVar && node.hasPrimativeConflicts())
      handlePrimitiveConflict(prefix, node.primativeFields, node.allocSite);
    
    // TODO orientation
    //Casts C pointer; depth is used to create unique "myPtr" name
    String currPtr = "myPtr" + depth;
    String structType = node.original.getType().getSafeSymbol();
    cFile.append("struct " + structType + " * "+currPtr+"= (struct "+ structType + " * ) " + prefix + "; ");
  
    //Handles Objects
    for (ObjRef ref : node.objectRefs) {
      // Will only process edge if there is some sort of conflict with the Child
      if (ref.child.decendantsConflict()|| ref.child.hasConflicts()) {
        String childPtr = currPtr +"->___" + ref.field + "___";
        
        // Checks if the child exists and has allocsite matching the conflict
        cFile.append("if(" + childPtr + " != NULL && " + childPtr + getAllocSiteInC + "=="
            + ref.allocSite + ") { ");

        // Prints out conflicts of child
        if (ref.conflict)
          handleObjConflict(childPtr, node.allocSite);
       
        if(ref.child.hasPrimativeConflicts())
          handlePrimitiveConflict(childPtr, ref.child.primativeFields, ref.child.allocSite);

        if (ref.child.decendantsConflict()) {
          // Checks if we have visited the child before
          cFile.append("if(!" + queryAndAddHashTableInC + childPtr + ")) { ");
          if (ref.child.getNumOfReachableParents() == 1 && !ref.child.isInsetVar) {
            addChecker(ref.child, done, childPtr, depth + 1);
          }
          else {
            cFile.append(addToQueueInC + childPtr + ");");
          }
          
          cFile.append(" } ");
        }
        cFile.append(" } ");
      }
    }

    if ((node.getNumOfReachableParents() != 1 || node.isInsetVar) && node.decendantsConflict())
      cFile.println(" } break; ");

    done.add(node.allocSite);
  }

  private void handleObjConflict(String childPtr, AllocSite allocSite) {
    cFile.append("printf(\"Conflict detected with %p from parent with allocation site %u\\n\"," + childPtr + "," + allocSite.hashCodeSpecific() + ");");
  }
  
  private void handlePrimitiveConflict(String ptr, ArrayList<String> conflicts, AllocSite allocSite) {
    cFile.append("printf(\"Primitive Conflict detected with %p with alloc site %u\\n\", "+ptr+", "+allocSite.hashCodeSpecific()+"); ");
  }

  private Taint getProperTaint(FlatSESEEnterNode rblock, VariableNode var,
      Hashtable<Taint, Set<Effect>> effects) {
    Set<Taint> taints = effects.keySet();
    
    for (Taint t : taints) {
      FlatSESEEnterNode sese = t.getSESE();
      //Jim says that this case should never happen, but it may
      if( sese == null ) {
          System.out.println( "What should I do with a stall site taint? --Jim");
      }
      if(sese != null && sese.equals(rblock) && t.getVar().equals(var.getTempDescriptor())) {
        return t;
      }
    }
    return null;
  }

  private class EffectsGroup
  {
    Hashtable<String, EffectPair> myEffects;
    ArrayList<String> primativeConflictingFields;
    
    public EffectsGroup() {
      myEffects = new Hashtable<String, EffectPair>();
      primativeConflictingFields = new ArrayList<String>();
    }

    public void addPrimative(Effect e) {
      primativeConflictingFields.add(e.getField().toPrettyStringBrief());
    }
    
    public void addObj(Effect e, boolean conflict) {
      EffectPair effect = new EffectPair(e, conflict);
      myEffects.put(e.getField().getSymbol(), effect);
    }
    
    public boolean isEmpty() {
      return myEffects.isEmpty() && primativeConflictingFields.isEmpty();
    }
    
    public boolean hasPrimativeConflicts(){
      return !primativeConflictingFields.isEmpty();
    }
    
    public boolean hasObjectConflicts() {
      return !myEffects.isEmpty();
    }
    
    public EffectPair getObjEffect(String field) {
      return myEffects.get(field);
    }
  }
  
  private class EffectPair {
    Effect originalEffect;
    int type;
    boolean conflict;

    public EffectPair(Effect e, boolean conflict) {
      originalEffect = e;
      type = e.getType();
      this.conflict = conflict;
    }

    public int hashCode() {
      return originalEffect.hashCode();
    }

    public boolean equals(Object o) {
      if (o == null)
        return false;

      if (!(o instanceof EffectPair))
        return false;

      EffectPair other = (EffectPair) o;

      return (other.originalEffect.getAffectedAllocSite().equals(
          originalEffect.getAffectedAllocSite()) && other.originalEffect.getField().equals(
          originalEffect.getField()));
    }
    
    public String toString()
    {
      return originalEffect.toString();
    }
  }

  private class ObjRef {
    String field;
    int allocSite;
    boolean conflict;
    ConcreteRuntimeObjNode child;

    public ObjRef(String fieldname, ConcreteRuntimeObjNode ref, boolean con) {
      field = fieldname;
      allocSite = ref.getAllocationSite();
      child = ref;
      conflict = con;
    }
  }

  private class ConcreteRuntimeObjNode {
    ArrayList<ObjRef> objectRefs;
    ArrayList<String> primativeFields;
    ArrayList<ConcreteRuntimeObjNode> parents;
    HashSet<ConcreteRuntimeObjNode> conflictingParents;
    ConcreteRuntimeObjNode lastReferencer;
    boolean decendantsPrimConflict;
    boolean decendantsObjConflict;
    boolean isInsetVar;
    AllocSite allocSite;
    HeapRegionNode original;

    public ConcreteRuntimeObjNode(HeapRegionNode me, boolean conflict, boolean isInVar) {
      objectRefs = new ArrayList<ObjRef>();
      parents = new ArrayList<ConcreteRuntimeObjNode>();
      conflictingParents = new HashSet<ConcreteRuntimeObjNode>();
      lastReferencer = null;
      allocSite = me.getAllocSite();
      original = me;
      isInsetVar = isInVar;
      decendantsPrimConflict = false;
      decendantsObjConflict = false;
      primativeFields = null;
    }

    @Override
    public int hashCode() {
      // This gets allocsite number
      return allocSite.hashCodeSpecific();
    }

    @Override
    public boolean equals(Object obj) {
      return original.equals(obj);
    }

    public int getAllocationSite() {
      return allocSite.hashCodeSpecific();
    }

    public int getNumOfReachableParents() {
      return conflictingParents.size();
    }
    
    public boolean hasPrimativeConflicts() {
      return primativeFields != null;
    }
    
    public boolean hasConflicts() {
      return (primativeFields != null) || !conflictingParents.isEmpty();
    }
    
    public boolean decendantsConflict() {
      return decendantsPrimConflict || decendantsObjConflict;
    }

    public void addObjChild(String field, ConcreteRuntimeObjNode child, boolean conflict) {
      child.lastReferencer = this;
      ObjRef ref = new ObjRef(field, child, conflict);
      objectRefs.add(ref);
      child.parents.add(this);
    }
    
    public String toString()
    {
      return "AllocSite=" + getAllocationSite() + " myConflict=" + !conflictingParents.isEmpty() + 
              " decCon="+decendantsObjConflict+ " NumOfParents=" + parents.size()+ 
              " NumOfConParents=" + getNumOfReachableParents() + " ObjectChildren=" + objectRefs.size();
    }
  }
}
