package Analysis.OoOJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import Analysis.Disjoint.AllocSite;
import Analysis.Disjoint.Effect;
import Analysis.Disjoint.Taint;
import IR.Flat.FlatNew;
import IR.Flat.FlatNode;
import IR.Flat.FlatSESEEnterNode;
import IR.Flat.TempDescriptor;

public class ConflictNode {

  protected HashSet<ConflictEdge> edgeSet;
  protected HashSet<AllocSite> allocSet;
  protected HashSet<Taint> taintSet;

  protected Hashtable<AllocSite, Set<Effect>> alloc2readEffectSet;
  protected Hashtable<AllocSite, Set<Effect>> alloc2writeEffectSet;
  protected Hashtable<AllocSite, Set<Effect>> alloc2strongUpdateEffectSet;

  protected int nodeType;
  protected String id;
  protected FlatNode stallSite;
  protected TempDescriptor var;
  protected FlatSESEEnterNode fsen;

  public static final int FINE_READ = 0;
  public static final int FINE_WRITE = 1;
  public static final int PARENT_READ = 2;
  public static final int PARENT_WRITE = 3;
  public static final int COARSE = 4;
  public static final int PARENT_COARSE = 5;
  public static final int SCC = 6;

  public static final int INVAR = 0;
  public static final int STALLSITE = 1;

  public ConflictNode(String id, int nodeType, TempDescriptor var, FlatNode stallSite) {
    this(id, var, nodeType);
    this.stallSite = stallSite;
  }

  public ConflictNode(String id, int nodeType, TempDescriptor var, FlatSESEEnterNode fsen) {
    this(id, var, nodeType);
    this.fsen = fsen;
  }

  public ConflictNode(String id, TempDescriptor var, int nodeType) {
    edgeSet = new HashSet<ConflictEdge>();
    // redundant views of access root's
    // allocation sites for efficient retrieval
    allocSet = new HashSet<AllocSite>();
    taintSet = new HashSet<Taint>();

    alloc2readEffectSet = new Hashtable<AllocSite, Set<Effect>>();
    alloc2writeEffectSet = new Hashtable<AllocSite, Set<Effect>>();
    alloc2strongUpdateEffectSet = new Hashtable<AllocSite, Set<Effect>>();

    this.id = id;
    this.nodeType = nodeType;
    this.var = var;
  }

  public void addTaint(Taint t) {
    taintSet.add(t);
  }

  public Taint getTaint(AllocSite as) {
    for (Iterator iterator = taintSet.iterator(); iterator.hasNext();) {
      Taint t = (Taint) iterator.next();
      if (t.getAllocSite().equals(as)) {
        return t;
      }
    }
    return null;
  }

  public void addEffect(AllocSite as, Effect e) {
    if (e.getType() == Effect.read) {
      addReadEffect(as, e);
    } else if (e.getType() == Effect.write) {
      addWriteEffect(as, e);
    } else {
      addStrongUpdateEffect(as, e);
    }
  }

  public void addReadEffect(AllocSite as, Effect e) {
    allocSet.add(as);
    Set<Effect> effectSet = alloc2readEffectSet.get(as);
    if (effectSet == null) {
      effectSet = new HashSet<Effect>();
    }
    effectSet.add(e);

    alloc2readEffectSet.put(as, effectSet);
  }

  public void addWriteEffect(AllocSite as, Effect e) {
    allocSet.add(as);
    Set<Effect> effectSet = alloc2writeEffectSet.get(as);
    if (effectSet == null) {
      effectSet = new HashSet<Effect>();
    }
    effectSet.add(e);

    alloc2writeEffectSet.put(as, effectSet);
  }

  public void addStrongUpdateEffect(AllocSite as, Effect e) {
    allocSet.add(as);
    Set<Effect> effectSet = alloc2strongUpdateEffectSet.get(as);
    if (effectSet == null) {
      effectSet = new HashSet<Effect>();
    }
    effectSet.add(e);

    alloc2strongUpdateEffectSet.put(as, effectSet);
  }

  public Hashtable<AllocSite, Set<Effect>> getReadEffectSet() {
    return alloc2readEffectSet;
  }

  public Hashtable<AllocSite, Set<Effect>> getWriteEffectSet() {
    return alloc2writeEffectSet;
  }

  public Hashtable<AllocSite, Set<Effect>> getStrongUpdateEffectSet() {
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
    for (Iterator iterator = allocSet.iterator(); iterator.hasNext();) {
      AllocSite as = (AllocSite) iterator.next();
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

}
