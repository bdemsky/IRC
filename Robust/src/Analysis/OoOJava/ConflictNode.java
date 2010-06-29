package Analysis.OoOJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import Analysis.Disjoint.AllocSite;
import Analysis.Disjoint.Effect;

public class ConflictNode {

  protected HashSet<ConflictEdge> edgeSet;

  protected Hashtable<AllocSite, Set<Effect>> alloc2readEffectSet;
  protected Hashtable<AllocSite, Set<Effect>> alloc2writeEffectSet;
  protected Hashtable<AllocSite, Set<Effect>> alloc2strongUpdateEffectSet;

  protected int nodeType;
  protected int type;
  protected String id;

  public static final int FINE_READ = 0;
  public static final int FINE_WRITE = 1;
  public static final int PARENT_READ = 2;
  public static final int PARENT_WRITE = 3;
  public static final int COARSE = 4;
  public static final int PARENT_COARSE = 5;
  public static final int SCC = 6;

  public static final int INVAR = 0;
  public static final int STALLSITE = 1;

  public ConflictNode(String id, int nodeType) {
    edgeSet = new HashSet<ConflictEdge>();

    alloc2readEffectSet = new Hashtable<AllocSite, Set<Effect>>();
    alloc2writeEffectSet = new Hashtable<AllocSite, Set<Effect>>();
    alloc2strongUpdateEffectSet = new Hashtable<AllocSite, Set<Effect>>();

    this.id = id;
    this.nodeType = nodeType;
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
    Set<Effect> effectSet = alloc2readEffectSet.get(as);
    if (effectSet == null) {
      effectSet = new HashSet<Effect>();
    }
    effectSet.add(e);

    alloc2readEffectSet.put(as, effectSet);
  }

  public void addWriteEffect(AllocSite as, Effect e) {
    Set<Effect> effectSet = alloc2writeEffectSet.get(as);
    if (effectSet == null) {
      effectSet = new HashSet<Effect>();
    }
    effectSet.add(e);

    alloc2writeEffectSet.put(as, effectSet);
  }

  public void addStrongUpdateEffect(AllocSite as, Effect e) {
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

  public Set<ConflictEdge> getEdgeSet() {
    return edgeSet;
  }

  public void addEdge(ConflictEdge edge) {
    edgeSet.add(edge);
  }

  public int getType() {
    return type;
  }

  public String getID() {
    return id;
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

  public String toString() {

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

}
