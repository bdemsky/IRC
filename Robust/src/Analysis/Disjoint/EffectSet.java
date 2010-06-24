package Analysis.Disjoint;

import java.util.HashSet;
import java.util.Hashtable;

import IR.Flat.TempDescriptor;

public class EffectSet {

  private Hashtable<Integer, HashSet<Effect>> methodEffectSet;
  private Hashtable<TempDescriptor, HashSet<Effect>> rblockEffectSet;

  public EffectSet() {
    methodEffectSet = new Hashtable<Integer, HashSet<Effect>>();
  }

  public void addMethodEffect(Integer paramIdx, Effect e) {
    HashSet<Effect> effectSet = methodEffectSet.get(paramIdx);
    if (effectSet == null) {
      effectSet = new HashSet<Effect>();
    }
    effectSet.add(e);
    methodEffectSet.put(paramIdx, effectSet);
  }

  public String toString() {
    if (methodEffectSet != null) {
      return methodEffectSet.toString();
    } else {
      return rblockEffectSet.toString();
    }
  }

}
