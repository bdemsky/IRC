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
/*
 * How to Use:
 * 1) Instantiate object
 * 2) Call void traverse(FlatSESEEnterNode rblock, Hashtable<Taint, Set<Effect>> effects, Hashtable<Taint, Set<Effect>> conflicts, ReachGraph rg)
 *    as many times as needed
 * 3) Call void close()
 */
public class RuntimeConflictResolver {
  public static final String outputFile = "RuntimeConflictResolver.c";
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

  public RuntimeConflictResolver() throws FileNotFoundException {
    out = new PrintWriter(new File(outputFile));
    out.append("#include \"" + hashAndQueueCFileDir + "hashRCR.h\"\n#include \""
        + hashAndQueueCFileDir + "Queue_RCR.h\"\n");
    //TODO Make compromise with defining buildDir
    out.append("#include \"par/classdefs.h\"\n");
    //generic cast struct
    out.append("struct genericObjectStruct {int type; int oid; int allocsite;};\n");
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
      Hashtable<NodeKey, Node> created = new Hashtable<NodeKey, Node>();

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
      Hashtable<NodeKey, Node> created) {

    VariableNode varNode = rg.getVariableNodeNoMutation(invar);
    Hashtable<EffectsKey, EffectsHashPair> table =
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
      NodeKey rootKey = new NodeKey(singleRoot.allocSite);

      if (!created.contains(rootKey)) {
        created.put(rootKey, singleRoot);
        createHelper(singleRoot, edge.getDst().iteratorToReferencees(), created, table);
      }
    }
  }

  private void addChecker(Node node, HashSet<Integer> done, String prefix) {
    // We don't need a case statement for things with either 1 incoming or 0 out
    // going edges.
    if (node.getNumOfReachableParents() != 1 && node.decendentsConflict) {
      assert prefix.equals("ptr");
      out.append("case " + node.getAllocationSite() + ":\n { ");
    }
    
    //Casts pointer
    String structType = node.original.getType().getSafeSymbol();
    out.append("struct " + structType + " * myPtr = (struct "+ structType + " * ) " + prefix + "; ");
    
    for (Reference ref : node.references) {
      // Will only process edge if there is some sort of conflict with the Child
      if (ref.child.decendentsConflict || ref.child.myConflict) {
        String childPtr = "myPtr->___" + ref.field + "___";
        
        // Checks if the child exists and is correct
        out.append("if(" + childPtr + " != NULL && " + childPtr + getAllocSiteInC + "=="
            + ref.allocSite + ") { ");

        // Prints out Conflict of child
        if (ref.child.myConflict)
          handleConflict(childPtr);

        if (ref.child.decendentsConflict) {
          // Checks if we have visited the child before
          out.append("if(!" + queryAndAddHashTableInC + childPtr + ") { ");
          if (ref.child.getNumOfReachableParents() == 1)
            addChecker(ref.child, done, childPtr);
          else
            out.append(addToQueueInC + childPtr + ");");
          
          out.append(" } ");
        }
        out.append(" } ");
      }
    }

    if (node.getNumOfReachableParents() != 1 && node.decendentsConflict)
      out.println(" } break; ");

    done.add(new Integer(node.getAllocationSite()));
  }

  private void handleConflict(String childPtr) {
    out.append("printf(\"Conflict detected at %p with allocation site %u\\n\"," + childPtr + ","
        + childPtr + getAllocSiteInC + ");");
  }

  // I'll assume that I'll be just given a pointer named ptr in my function.
  private void printCMethod(Hashtable<NodeKey, Node> created, String inVar, String rBlock) {
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
          && node.decendentsConflict)
        addChecker(node, done, "ptr");
    }
    out.append(" default : return; ");
    out.append("}} while( (ptr = " + dequeueFromQueueInC + ") != NULL); ");
    out.append(clearQueue + "; " + resetHashTable + "; }}\n");
  }

  // Plan is to add stuff to the tree depth-first sort of way. That way, we can
  // propagate up conflicts
  private void createHelper(Node parent, Iterator<RefEdge> edges, Hashtable<NodeKey, Node> created,
      Hashtable<EffectsKey, EffectsHashPair> table) {
    
    assert table != null;
    while (edges.hasNext()) {
      RefEdge edge = edges.next();
      String field = edge.getField();
      HeapRegionNode childHRN = edge.getDst();
      EffectsKey lookup = new EffectsKey(parent.allocSite, field);
      EffectsHashPair effect = table.get(lookup);
      
      // if there's no effect, we don't traverse this edge.
      if (effect != null) {
        NodeKey key = new NodeKey(childHRN.getAllocSite());
        boolean isNewChild = !created.contains(key);
        Node child;

        if (isNewChild) {
          child = new Node(childHRN, effect.conflict);
          created.put(key, child);
        }
        else {
          child = created.get(key);
          child.myConflict = effect.conflict || child.myConflict;
        }

        parent.addChild(field, child);
        if (effect.conflict)
          propogateConflictFlag(parent);

        if (effect.type == Effect.read && isNewChild)
          createHelper(child, childHRN.iteratorToReferencees(), created, table);
      }
    }
  }

  // This will propagate the conflict up the tree.
  private void propogateConflictFlag(Node node) {
    Node curr = node;

    while (curr != null && curr.decendentsConflict != true) {
      curr.decendentsConflict = true;
      curr = curr.lastParent;
    }
  }

  private Hashtable<EffectsKey, EffectsHashPair> generateHashtable(FlatSESEEnterNode rblock,
      VariableNode var, Hashtable<Taint, Set<Effect>> effects,
      Hashtable<Taint, Set<Effect>> conflicts) {
    // we search effects since conflicts is only a subset of effects
    Taint taint = getProperTaint(rblock, var, effects);
    assert taint != null;

    Set<Effect> localEffects = effects.get(taint);
    Set<Effect> localConflicts = conflicts.get(taint);

    if (localEffects == null || localEffects.isEmpty() || conflicts == null || conflicts.isEmpty())
      return null;

    Hashtable<EffectsKey, EffectsHashPair> table = new Hashtable<EffectsKey, EffectsHashPair>();

    for (Effect e : localEffects) {
      EffectsKey key = new EffectsKey(e);
      EffectsHashPair element = new EffectsHashPair(e, localConflicts.contains(e));
      table.put(key, element);
    }
    
    return table;
  }

  private Taint getProperTaint(FlatSESEEnterNode rblock, VariableNode var,
      Hashtable<Taint, Set<Effect>> effects) {
    Set<Taint> taints = effects.keySet();
    for (Taint t : taints)
      if (t.getSESE().equals(rblock) && t.getVar().equals(var.getTempDescriptor()))
        return t;

    return null;
  }

  private class EffectsKey {
    AllocSite allocsite;
    String field;

    public EffectsKey(AllocSite a, String f) {
      allocsite = a;
      field = f;
    }

    public EffectsKey(Effect e) {
      allocsite = e.getAffectedAllocSite();
      field = e.getField().getSymbol();
    }

    // Hashcode only hashes the object based on AllocationSite and Field
    public int hashCode() {
      return allocsite.hashCode() ^ field.hashCode();
    }

    // Equals ONLY compares object based on AllocationSite and Field
    public boolean equals(Object o) {
      if (o == null)
        return false;

      if (!(o instanceof EffectsKey))
        return false;

      EffectsKey other = (EffectsKey) o;

      return (other.allocsite.equals(this.allocsite) && other.field.equals(this.field));
    }
  }

  private class EffectsHashPair {
    Effect originalEffect;
    int type;
    boolean conflict;

    public EffectsHashPair(Effect e, boolean conflict) {
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

      if (!(o instanceof EffectsHashPair))
        return false;

      EffectsHashPair other = (EffectsHashPair) o;

      return (other.originalEffect.getAffectedAllocSite().equals(
          originalEffect.getAffectedAllocSite()) && other.originalEffect.getField().equals(
          originalEffect.getField()));
    }
    
    public String toString()
    {
      return originalEffect.toString();
    }
  }

  private class Reference {
    String field;
    int allocSite;
    Node child;

    public Reference(String fieldname, Node ref) {
      field = fieldname;
      allocSite = ref.getAllocationSite();
      child = ref;
    }
  }

  private class NodeKey {
    int allocsite;

    public NodeKey(AllocSite site) {
      allocsite = site.hashCodeSpecific();
    }

    public int hashCode() {
      return allocsite;
    }

    public boolean equals(Object obj) {
      if (obj == null)
        return false;

      if (!(obj instanceof NodeKey))
        return false;

      if (((NodeKey) obj).allocsite == allocsite)
        return true;

      return false;
    }

  }

  private class Node {
    ArrayList<Reference> references;
    ArrayList<Node> parents;
    Node lastParent;
    int numOfConflictParents;
    boolean myConflict;
    boolean decendentsConflict;
    AllocSite allocSite;
    HeapRegionNode original;

    public Node(HeapRegionNode me, boolean conflict) {
      references = new ArrayList<Reference>();
      parents = new ArrayList<Node>();
      lastParent = null;
      numOfConflictParents = -1;
      allocSite = me.getAllocSite();
      original = me;
      myConflict = conflict;
      decendentsConflict = false;
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
          if (parent.decendentsConflict)
            numOfConflictParents++;
      }

      return numOfConflictParents;
    }

    public void addChild(String field, Node child) {
      child.lastParent = this;
      Reference ref = new Reference(field, child);
      references.add(ref);
    }
    
    public String toString()
    {
      return "AllocSite=" + getAllocationSite() + " myConflict=" + myConflict + 
              " decCon="+decendentsConflict+ " NumOfParents=" + parents.size()+ 
              " NumOfConParents=" + getNumOfReachableParents() + " children=" + references.size();
    }
  }
}
