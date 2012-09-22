package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class BasisSet {

  // an element of the basis set is represented by a pair (HNode,basis)
  Map<Set<Integer>, HNode> map;

  public BasisSet() {
    map = new HashMap<Set<Integer>, HNode>();
  }

  public void addElement(Set<Integer> basis, HNode node) {
    map.put(basis, node);
  }

  public Iterator<Set<Integer>> basisIterator() {
    return map.keySet().iterator();
  }

  public HNode getHNode(Set<Integer> B) {
    return map.get(B);
  }

  public Set<HNode> getHNodeSet() {
    Set<HNode> set = new HashSet<HNode>();
    set.addAll(map.values());
    return set;
  }

  public Set<Set<Integer>> getBasisSetByHNodeSet(Set<HNode> nodeSet) {

    Set<Set<Integer>> rtrSet = new HashSet<Set<Integer>>();

    Set<Set<Integer>> keySet = map.keySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Set<Integer> basisKey = (Set<Integer>) iterator.next();
      HNode node = map.get(basisKey);
      if (nodeSet.contains(node)) {
        rtrSet.add(basisKey);
      }
    }

    return rtrSet;

  }

}
