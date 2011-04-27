package Analysis.OoOJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import Analysis.Disjoint.Alloc;
import Analysis.Disjoint.AllocSite;
import Analysis.Disjoint.Effect;
import Analysis.Disjoint.Taint;
import IR.ClassDescriptor;
import IR.Flat.FlatNew;
import IR.Flat.FlatNode;
import IR.Flat.FlatSESEEnterNode;
import IR.Flat.TempDescriptor;

public class ConflictNode {

  protected HashSet<ConflictEdge> edgeSet;
  protected HashSet<Alloc> allocSet;
  protected HashSet<Taint> taintSet;

  protected Hashtable<Alloc, Set<Effect>> alloc2readEffectSet;
  protected Hashtable<Alloc, Set<Effect>> alloc2writeEffectSet;
  protected Hashtable<Alloc, Set<Effect>> alloc2strongUpdateEffectSet;

  protected int nodeType;
  protected String id;
  protected FlatNode stallSite;
  protected TempDescriptor var;
  protected FlatSESEEnterNode fsen;
  protected boolean toBePruned = false;

  protected ClassDescriptor cd;

  public boolean isTobePruned() {
    return toBePruned;
  }

  public void setToBePruned(boolean toBePruned) {
    this.toBePruned = toBePruned;
  }

  public static final int FINE_READ = 0;
  public static final int FINE_WRITE = 1;
  public static final int PARENT_READ = 2;
  public static final int PARENT_WRITE = 3;
  public static final int COARSE = 4;
  public static final int PARENT_COARSE = 5;
  public static final int SCC = 6;

  public static final int INVAR = 0;
  public static final int STALLSITE = 1;

  public ConflictNode(String id, int nodeType, TempDescriptor var, FlatNode stallSite,
                      ClassDescriptor cd) {
    this(id, var, nodeType);
    this.stallSite = stallSite;
    this.cd = cd;
  }

  public ConflictNode(String id, int nodeType, TempDescriptor var, FlatSESEEnterNode fsen) {
    this(id, var, nodeType);
    this.fsen = fsen;
  }

  public ConflictNode(String id, TempDescriptor var, int nodeType) {
    edgeSet = new HashSet<ConflictEdge>();
    // redundant views of access root's
    // allocation sites for efficient retrieval
    allocSet = new HashSet<Alloc>();
    taintSet = new HashSet<Taint>();

    alloc2readEffectSet = new Hashtable<Alloc, Set<Effect>>();
    alloc2writeEffectSet = new Hashtable<Alloc, Set<Effect>>();
    alloc2strongUpdateEffectSet = new Hashtable<Alloc, Set<Effect>>();

    this.id = id;
    this.nodeType = nodeType;
    this.var = var;
  }

  public void addTaint(Taint t) {
    taintSet.add(t);
  }

  public Taint getTaint(Alloc as) {
    for (Iterator iterator = taintSet.iterator(); iterator.hasNext(); ) {
      Taint t = (Taint) iterator.next();
      if (t.getAllocSite().equals(as)) {
        return t;
      }
    }
    return null;
  }

  public void addEffect(Alloc as, Effect e) {
    if (e.getType() == Effect.read) {
      addReadEffect(as, e);
    } else if (e.getType() == Effect.write) {
      addWriteEffect(as, e);
    } else {
      addStrongUpdateEffect(as, e);
    }
  }

  public void addReadEffect(Alloc as, Effect e) {
    allocSet.add(as);
    Set<Effect> effectSet = alloc2readEffectSet.get(as);
    if (effectSet == null) {
      effectSet = new HashSet<Effect>();
    }
    effectSet.add(e);

    alloc2readEffectSet.put(as, effectSet);
  }

  public void addWriteEffect(Alloc as, Effect e) {
    allocSet.add(as);
    Set<Effect> effectSet = alloc2writeEffectSet.get(as);
    if (effectSet == null) {
      effectSet = new HashSet<Effect>();
    }
    effectSet.add(e);

    alloc2writeEffectSet.put(as, effectSet);
  }

  public void addStrongUpdateEffect(Alloc as, Effect e) {
    allocSet.add(as);
    Set<Effect> effectSet = alloc2strongUpdateEffectSet.get(as);
    if (effectSet == null) {
      effectSet = new HashSet<Effect>();
    }
    effectSet.add(e);

    alloc2strongUpdateEffectSet.put(as, effectSet);
  }

  public Hashtable<Alloc, Set<Effect>> getReadEffectSet() {
    return alloc2readEffectSet;
  }

  public Hashtable<Alloc, Set<Effect>> getWriteEffectSet() {
    return alloc2writeEffectSet;
  }

  public Hashtable<Alloc, Set<Effect>> getStrongUpdateEffectSet() {
    return alloc2strongUpdateEffectSet;
  }

  public boolean isInVarNode() {
    if (nodeType == ConflictNode.INVAR) {
      return true;
    }
    return false;
  }

  public boolean isStallSiteNode() {
    return !isInVarNode();
  }

  public Set<FlatNew> getFlatNewSet() {
    Set<FlatNew> fnSet = new HashSet<FlatNew>();
    for (Iterator iterator = allocSet.iterator(); iterator.hasNext(); ) {
      Alloc as = (Alloc) iterator.next();
      FlatNew fn = as.getFlatNew();
      fnSet.add(fn);
    }
    return fnSet;
  }

  public TempDescriptor getVar() {
    return var;
  }

  public Set<ConflictEdge> getEdgeSet() {
    return edgeSet;
  }

  public void addEdge(ConflictEdge edge) {
    edgeSet.add(edge);
  }

  public String getID() {
    return id;
  }

  public FlatNode getStallSiteFlatNode() {
    return stallSite;
  }

  public int getSESEIdentifier() {
    return fsen.getIdentifier();
  }

  public boolean equals(Object o) {

    if (o == null) {
      return false;
    }

    if (!(o instanceof ConflictNode)) {
      return false;
    }

    ConflictNode in = (ConflictNode) o;

    if (id.equals(in.id)) {
      return true;
    } else {
      return false;
    }

  }

  public String toStringAllEffects() {

    String str = "";

    if (!alloc2readEffectSet.isEmpty()) {
      str += "read effect= " + alloc2readEffectSet.toString() + "\n";
    }

    if (!alloc2writeEffectSet.isEmpty()) {
      str += "write effect = " + alloc2writeEffectSet.toString() + "\n";
    }

    if (!alloc2strongUpdateEffectSet.isEmpty()) {
      str += "SU effect = " + alloc2strongUpdateEffectSet.toString() + "\n";
    }

    return str;
  }

  public String toString() {
    return id;
  }

  public boolean IsValidToPrune() {

    for (Iterator iterator = edgeSet.iterator(); iterator.hasNext(); ) {
      ConflictEdge edge = (ConflictEdge) iterator.next();

      if (edge.getVertexU() == edge.getVertexV()) {
        // self-conflict, need to generate traverser
        return false;
      } else {

        if (edge.getVertexU() == this) {
          if (edge.getVertexV().isInVarNode()) {
            // has a conflict with invar, need to generate traverser
            return false;
          }
        } else {
          if (edge.getVertexU().isInVarNode()) {
            // has a conflict with invar, need to generate traverser
            return false;
          }
        }
      }
    }
    return true;
  }

  public String getSourceFileName() {
    return cd.getSourceFileName();
  }

}
