package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Family {

  // an element of the family is represented by a pair (basis,set of corresponding nodes)
  Map<Set<Integer>, Set<HNode>> mapFtoGammaF;

  Set<Set<Integer>> Fset;

  public static Set<Integer> EMPTY = new HashSet<Integer>();

  public Family() {
    Fset = new HashSet<Set<Integer>>();
    Fset.add(EMPTY);
    mapFtoGammaF = new HashMap<Set<Integer>, Set<HNode>>();
  }

  public void addFElement(Set<Integer> F) {
    Fset.add(F);
  }

  public Set<HNode> getGamma(Set<Integer> F) {
    if (!mapFtoGammaF.containsKey(F)) {
      mapFtoGammaF.put(F, new HashSet<HNode>());
    }
    return mapFtoGammaF.get(F);
  }

  public void updateGammaF(Set<Integer> F, Set<HNode> gamma) {
    getGamma(F).addAll(gamma);
  }

  public boolean containsF(Set<Integer> F) {
    return Fset.contains(F);
  }

  public int size() {
    return Fset.size();
  }

  public Iterator<Set<Integer>> FIterator() {
    return Fset.iterator();
  }

  public String toString() {

    return mapFtoGammaF.toString();

  }

}
