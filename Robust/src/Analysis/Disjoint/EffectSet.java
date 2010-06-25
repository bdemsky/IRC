package Analysis.Disjoint;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.Iterator;
import IR.Flat.TempDescriptor;

public class EffectSet {

  private Hashtable<Taint, HashSet<Effect>> taint2effects;

  public EffectSet() {
    taint2effects = new Hashtable<Taint, HashSet<Effect>>();
  }

  public void addEffect(Taint t, Effect e) {
    HashSet<Effect> effectSet = taint2effects.get(t);
    if (effectSet == null) {
      effectSet = new HashSet<Effect>();
    }
    effectSet.add(e);
    taint2effects.put(t, effectSet);
  }

  public Set<Effect> getEffects(Taint t) {
    return taint2effects.get(t);
  }

  public Iterator getAllEffectPairs() {
    return taint2effects.entrySet().iterator();
  }

  public String toString() {
    return taint2effects.toString();    
  }
}
