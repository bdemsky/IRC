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

//TODO make it so that methods with no conflicts get no output. 

//TODO Make more efficent by only using ONE hashtable. 

/*
 * How to Use:
 * 1) Instantiate object
 * 2) Call void traverse(FlatSESEEnterNode rblock, Hashtable<Taint, Set<Effect>> effects, Hashtable<Taint, Set<Effect>> conflicts, ReachGraph rg)
 *    as many times as needed
 * 3) Call void close()
 */
public class RuntimeConflictResolver {
  public static String outputFile;
  private PrintWriter out;
  private static final String hashAndQueueCFileDir = "";

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
    outputFile = buildir + "RuntimeConflictResolver.c";
    out = new PrintWriter(new File(outputFile));
    out.append("#include \"" + hashAndQueueCFileDir + "hashRCR.h\"\n#include \""
        + hashAndQueueCFileDir + "Queue_RCR.h\"\n");
    //TODO Make compromise with defining buildDir
    out.append("#include \""+hashAndQueueCFileDir+"classdefs.h\"\n");
    
    //TODO more closely integrate this by asking generic type from other components? 
    //generic cast struct
    out.append("struct genericObjectStruct {int type; int oid; int allocsite; int ___cachedCode___; int ___cachedHash___;};\n");
  }

  /*
   * Basic steps: 1) Create pruned data structures from givens 1a) Use effects
   * sets to verify if we can access something (reads) 1b) Mark conflicts with 2
   * flags (one for self, one for children) 2)build code output structure 3)
   * printout
   */
  public void traverse(FlatSESEEnterNode rblock, Hashtable<Taint, Set<Effect>> effects,
      Hashtable<Taint, Set<Effect>> conflicts, ReachGraph rg) {
    Set<TempDescriptor> inVars = rblock.getInVarSet();

    // Is this even needed?
    if (inVars.size() == 0)
      return;

//    System.out.println("\n##Effects Set");
//    for(Taint key: effects.keySet())
//    {
//      System.out.println(key);
//      System.out.println(effects.get(key));
//    }
//    
//    System.out.println("##Conflicts Set:");
//    for(Taint key: conflicts.keySet())
//    {
//      System.out.println(key);
//      System.out.println(conflicts.get(key));
//    }
    
    // For every inVariable, generate unique method
    for (TempDescriptor invar : inVars) {
      Hashtable<AllocSiteKey, Node> created = new Hashtable<AllocSiteKey, Node>();

      createTree(rblock, invar, effects, conflicts, rg, created);
      if (!created.isEmpty()) {
        printCMethod(created, invar.getSymbol(), rblock.getSESErecordName());
      }
    }
  }

  public void close() {
    // appends file
    out.append("void initializeStructsRCR() { " + mallocHashTable + "; " + clearQueue + "; }");
    out.append("void destroyRCR() { " + deallocHashTable + "; }\n");

    out.close();
  }

  private void createTree(FlatSESEEnterNode rblock, 
      TempDescriptor invar,
      Hashtable<Taint, Set<Effect>> effects,
      Hashtable<Taint, Set<Effect>> conflicts, 
      ReachGraph rg, 
      Hashtable<AllocSiteKey, Node> created) {

    VariableNode varNode = rg.getVariableNodeNoMutation(invar);
    Hashtable<AllocSiteKey, EffectsGroup> table =
        generateHashtable(rblock, varNode, effects, conflicts);
    
    // if table is null that means there's no conflicts, therefore we need not
    // create a traversal
    if (table == null)
      return;

    Iterator<RefEdge> possibleEdges = varNode.iteratorToReferencees();

    while (possibleEdges.hasNext()) {
      RefEdge edge = possibleEdges.next();
      assert edge != null;

      // always assumed to be a conflict on the root variables.
      Node singleRoot = new Node(edge.getDst(), true);
      AllocSiteKey rootKey = new AllocSiteKey(singleRoot.allocSite);

      if (!created.contains(rootKey)) {
        created.put(rootKey, singleRoot);
        createHelper(singleRoot, edge.getDst().iteratorToReferencees(), created, table);
      }
    }
  }

  // Plan is to add stuff to the tree depth-first sort of way. That way, we can
  // propagate up conflicts
  private void createHelper(Node curr, Iterator<RefEdge> edges, Hashtable<AllocSiteKey, Node> created,
      Hashtable<AllocSiteKey, EffectsGroup> table) {
    
    assert table != null;
    
    AllocSiteKey parentKey = new AllocSiteKey(curr.allocSite);
    EffectsGroup parentEffects = table.get(parentKey);
    
    if (parentEffects == null || parentEffects.isEmpty()) 
      return;
    
    //Handle Objects
    if(parentEffects.hasObjectConflicts()) {
      while(edges.hasNext()) {
        RefEdge edge = edges.next();
        String field = edge.getField();
        EffectPair effect = parentEffects.getObjEffect(field);
        //If there is no effect, then there's not point in traversing this edge
        if(effect != null)
        {
          HeapRegionNode childHRN = edge.getDst();
          AllocSiteKey childKey = new AllocSiteKey(childHRN.getAllocSite());
          boolean isNewChild = !created.contains(childKey);
          Node child;
    
          if(isNewChild) {
            child = new Node(childHRN, effect.conflict);
            created.put(childKey, child);
          }
          else {
            child = created.get(childKey);
            child.myObjConflict = effect.conflict || child.myObjConflict;
          }
    
          curr.addObjChild(field, child);
          if (effect.conflict)
            propogateObjConflictFlag(curr);
    
          if (effect.type == Effect.read && isNewChild)
            createHelper(child, childHRN.iteratorToReferencees(), created, table);
        }
      }
    }
    
    //Handle primitives
    if(parentEffects.hasPrimativeConflicts()) {
      curr.primativeRefs = parentEffects.primativeConflicts;
      propogatePrimConflictFlag(curr.lastParent);
    } 
  }

  private Hashtable<AllocSiteKey, EffectsGroup> generateHashtable(FlatSESEEnterNode rblock,
      VariableNode var, Hashtable<Taint, Set<Effect>> effects,
      Hashtable<Taint, Set<Effect>> conflicts) {
    // we search effects since conflicts is only a subset of effects
    Taint taint = getProperTaint(rblock, var, effects);
    assert taint != null;
  
    Set<Effect> localEffects = effects.get(taint);
    Set<Effect> localConflicts = conflicts.get(taint);
    
    if (localEffects == null || localEffects.isEmpty() || localConflicts == null || localConflicts.isEmpty())
      return null;
  
    Hashtable<AllocSiteKey, EffectsGroup> table = new Hashtable<AllocSiteKey, EffectsGroup>();
    
    for (Effect e : localEffects) {
      boolean conflict = localConflicts.contains(e);
      AllocSiteKey key = new AllocSiteKey(e);
      EffectsGroup myEffects = table.get(key);
      
      if(myEffects == null) {
        myEffects = new EffectsGroup();
        table.put(key, myEffects);
      }
      
      if(e.getField().getType().isPrimitive()) {
        if(conflict)
          myEffects.addPrimative(e);
      }
      else {
        myEffects.addObj(e, conflict);
      }      
    }
    
    return table;
  }

  // This will propagate the conflict up the tree.
  private void propogateObjConflictFlag(Node node) {
    Node curr = node;
  
    while (curr != null && curr.decendantsObjConflict != true) {
      curr.decendantsObjConflict = true;
      curr = curr.lastParent;
    }
  }
  
  private void propogatePrimConflictFlag(Node node) {
    Node curr = node;
    
    while (curr != null && curr.decendantsPrimConflict != true) {
      curr.decendantsPrimConflict = true;
      curr = curr.lastParent;
    }
  }

  // I'll assume that I'll be just given a pointer named ptr in my function.
  private void printCMethod(Hashtable<AllocSiteKey, Node> created, String inVar, String rBlock) {
    HashSet<Integer> done = new HashSet<Integer>();
  
    out.append("void traverse___" + inVar.replaceAll(" ", "") + rBlock.replaceAll(" ", "") + 
        "___(void * InVar) {\n");
    out.append("struct genericObjectStruct * ptr = (struct genericObjectStruct *) InVar;  if(InVar != NULL) { " + queryAndAddHashTableInC
        + "ptr); do { ");
    //Add double cast to here 
    out.append("switch(ptr->allocsite) { ");
    for (Node node : created.values()) {
      // If we haven't seen it and it's a node with more than 1 parent
      // Note: a node with 0 parents is a root node (i.e. inset variable)
      if (!done.contains(new Integer(node.getAllocationSite())) && node.numOfConflictParents != 1
          && node.decendantsConflict())
        addChecker(node, done, "ptr", 0);
    }
    out.append(" default : break; ");
    out.append("}} while( (ptr = " + dequeueFromQueueInC + ") != NULL); ");
    out.append(clearQueue + "; " + resetHashTable + "; }}\n");
  }

  private void addChecker(Node node, HashSet<Integer> done, String prefix, int depth) {
    // We don't need a case statement for things with either 1 incoming or 0 out
    // going edges.
    if (node.getNumOfReachableParents() != 1 && node.decendantsConflict()) {
      assert prefix.equals("ptr");
      out.append("case " + node.getAllocationSite() + ":\n { ");
    }
    
    //TODO make a test case for this
    //Specific Primitives test for invars
    if(node.getNumOfReachableParents() == 0 && node.hasPrimativeConflicts())
      handlePrimitiveConflict(prefix, node.primativeRefs);
    
    //Casts C pointer; depth is used to create unique "myPtr" name
    String currPtr = "myPtr" + depth;
    String structType = node.original.getType().getSafeSymbol();
    out.append("struct " + structType + " * "+currPtr+"= (struct "+ structType + " * ) " + prefix + "; ");
  
    //Handles Objects
    for (ObjRefs ref : node.objectRefs) {
      // Will only process edge if there is some sort of conflict with the Child
      if (ref.child.decendantsConflict()|| ref.child.hasConflicts()) {
        String childPtr = currPtr +"->___" + ref.field + "___";
        
        // Checks if the child exists and is correct
        out.append("if(" + childPtr + " != NULL && " + childPtr + getAllocSiteInC + "=="
            + ref.allocSite + ") { ");

        // Prints out conflicts of child
        if (ref.child.myObjConflict)
          handleObjConflict(childPtr, node.allocSite);
       
        if(ref.child.hasPrimativeConflicts())
          handlePrimitiveConflict(childPtr, ref.child.primativeRefs);

        if (ref.child.decendantsConflict()) {
          // Checks if we have visited the child before
          out.append("if(!" + queryAndAddHashTableInC + childPtr + ")) { ");
          if (ref.child.getNumOfReachableParents() == 1) {
            addChecker(ref.child, done, childPtr, depth + 1);
          }
          else {
            out.append(addToQueueInC + childPtr + ");");
            }
          
          out.append(" } ");
        }
        out.append(" } ");
      }
    }

    if (node.getNumOfReachableParents() != 1 && node.decendantsConflict())
      out.println(" } break; ");

    done.add(new Integer(node.getAllocationSite()));
  }

  private void handleObjConflict(String childPtr, AllocSite allocSite) {
    out.append("printf(\"Conflict detected with %p from parent with allocation site %u\\n\"," + childPtr + "," + allocSite + ");");
  }
  
  private void handlePrimitiveConflict(String ptr, ArrayList<String> conflicts) {
    out.append("printf(\"Primitive Conflict detected with %p\\n\", "+ptr+"); ");
  }

  private Taint getProperTaint(FlatSESEEnterNode rblock, VariableNode var,
      Hashtable<Taint, Set<Effect>> effects) {
    Set<Taint> taints = effects.keySet();
    for (Taint t : taints)
      if (t.getSESE().equals(rblock) && t.getVar().equals(var.getTempDescriptor()))
        return t;

    return null;
  }

  private class EffectsGroup
  {
    Hashtable<String, EffectPair> myEffects;
    ArrayList<String> primativeConflicts;
    
    public EffectsGroup() {
      myEffects = new Hashtable<String, EffectPair>();
      primativeConflicts = new ArrayList<String>();
    }

    public void addPrimative(Effect e) {
      primativeConflicts.add(e.getField().toPrettyStringBrief());
    }
    
    public void addObj(Effect e, boolean conflict) {
      EffectPair effect = new EffectPair(e, conflict);
      myEffects.put(e.getField().getSymbol(), effect);
    }
    
    public boolean isEmpty() {
      return myEffects.isEmpty() && primativeConflicts.isEmpty();
    }
    
    public boolean hasPrimativeConflicts(){
      return !primativeConflicts.isEmpty();
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

  private class ObjRefs {
    String field;
    int allocSite;
    Node child;

    public ObjRefs(String fieldname, Node ref) {
      field = fieldname;
      allocSite = ref.getAllocationSite();
      child = ref;
    }
  }

  private class AllocSiteKey {
    int allocsite;

    public AllocSiteKey(AllocSite site) {
      allocsite = site.hashCodeSpecific();
    }
    
    public AllocSiteKey(Effect e) {
      allocsite = e.getAffectedAllocSite().hashCodeSpecific();
    }

    public int hashCode() {
      return allocsite;
    }

    public boolean equals(Object obj) {
      if (obj == null)
        return false;

      if (!(obj instanceof AllocSiteKey))
        return false;

      if (((AllocSiteKey) obj).allocsite == allocsite)
        return true;

      return false;
    }

  }

  private class Node {
    ArrayList<ObjRefs> objectRefs;
    ArrayList<String> primativeRefs;
    ArrayList<Node> parents;
    Node lastParent;
    int numOfConflictParents;
    boolean myObjConflict;
    boolean decendantsPrimConflict;
    boolean decendantsObjConflict;
    AllocSite allocSite;
    HeapRegionNode original;

    public Node(HeapRegionNode me, boolean conflict) {
      objectRefs = new ArrayList<ObjRefs>();
      parents = new ArrayList<Node>();
      lastParent = null;
      numOfConflictParents = -1;
      allocSite = me.getAllocSite();
      original = me;
      myObjConflict = conflict;
      decendantsPrimConflict = false;
      decendantsObjConflict = false;
      primativeRefs = null;
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
      if (numOfConflictParents == -1) {
        numOfConflictParents = 0;
        for (Node parent : parents)
          if (parent.decendantsConflict())
            numOfConflictParents++;
      }

      return numOfConflictParents;
    }
    
    public boolean hasPrimativeConflicts() {
      return primativeRefs != null;
    }
    
    public boolean hasConflicts() {
      return (primativeRefs != null) || myObjConflict;
    }
    
    public boolean decendantsConflict() {
      return decendantsPrimConflict || decendantsObjConflict;
    }

    public void addObjChild(String field, Node child) {
      child.lastParent = this;
      ObjRefs ref = new ObjRefs(field, child);
      objectRefs.add(ref);
      child.parents.add(this);
    }
    
    public String toString()
    {
      return "AllocSite=" + getAllocationSite() + " myConflict=" + myObjConflict + 
              " decCon="+decendantsObjConflict+ " NumOfParents=" + parents.size()+ 
              " NumOfConParents=" + getNumOfReachableParents() + " ObjectChildren=" + objectRefs.size();
    }
  }
}
