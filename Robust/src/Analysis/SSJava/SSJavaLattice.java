package Analysis.SSJava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import Util.Lattice;

public class SSJavaLattice<T> extends Lattice<T> {

  Set<T> sharedLocSet;
  public static int seed = 0;

  public SSJavaLattice(T top, T bottom) {
    super(top, bottom);
    sharedLocSet = new HashSet<T>();
  }

  public void setSharedLocSet(Set<T> in) {
    sharedLocSet.addAll(in);
  }

  public Set<T> getSharedLocSet() {
    return sharedLocSet;
  }

  public void addSharedLoc(T loc) {
    sharedLocSet.add(loc);
  }

  public boolean isSharedLoc(T loc) {
    return sharedLocSet.contains(loc);
  }

  public Set<T> getElementSet() {
    Set<T> set = new HashSet<T>();

    Set<T> keySet = getKeySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      T key = (T) iterator.next();
      set.add(key);
      set.addAll(getTable().get(key));
    }

    set.remove(getTopItem());
    set.remove(getBottomItem());
    return set;
  }

  public boolean addRelationHigherToLower(T higher, T lower) {

    System.out.println("add a relation: " + lower + "<" + higher);

    return put(higher, lower);
  }

  public void insertNewLocationAtOneLevelHigher(T lowerLoc, T newLoc) {
    // first identifying which location is connected to the input loc
    Set<T> keySet = getKeySet();
    Set<T> oneLevelHigherLocSet = new HashSet<T>();

    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      T locKey = (T) iterator.next();
      Set<T> conntectedSet = get(locKey);
      for (Iterator iterator2 = conntectedSet.iterator(); iterator2.hasNext();) {
        T connectedLoc = (T) iterator2.next();
        if (connectedLoc.equals(lowerLoc)) {
          oneLevelHigherLocSet.add(locKey);
        }
      }
    }

    put(newLoc);
    addRelationHigherToLower(newLoc, lowerLoc);

    for (Iterator iterator = oneLevelHigherLocSet.iterator(); iterator.hasNext();) {
      T higherLoc = (T) iterator.next();
      // remove an existing edge between the higher loc and the input loc
      get(higherLoc).remove(lowerLoc);
      // add a new edge from the higher loc to the new location
      put(higherLoc, newLoc);
    }

  }

  public Set<T> getPossibleCycleElements(T higherLoc, T lowerLoc) {
    // if a relation of higherloc & lowerloc introduces a new cycle flow,
    // return the set of elements consisting of the cycle
    Set<T> cycleElemetns = new HashSet<T>();

    // if lowerLoc has already been higher than higherLoc, the new relation
    // introduces a cycle to the lattice
    if (lowerLoc.equals(higherLoc)) {
      cycleElemetns.add(lowerLoc);
      cycleElemetns.add(higherLoc);
    } else if (isGreaterThan(lowerLoc, higherLoc)) {
      cycleElemetns.add(lowerLoc);
      cycleElemetns.add(higherLoc);
      getInBetweenElements(lowerLoc, higherLoc, cycleElemetns);
    }
    return cycleElemetns;
  }

  private void getInBetweenElements(T start, T end, Set<T> elementSet) {
    Set<T> connectedSet = get(start);
    for (Iterator iterator = connectedSet.iterator(); iterator.hasNext();) {
      T cur = (T) iterator.next();
      if ((!start.equals(cur)) && (!cur.equals(end)) && isGreaterThan(cur, end)) {
        elementSet.add(cur);
        getInBetweenElements(cur, end, elementSet);
      }
    }
  }

  public void mergeIntoNewLocation(Set<T> cycleSet, T newLoc) {

    // add a new loc
    put(newLoc);

    Set<T> keySet = getKeySet();

    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      T keyElement = (T) iterator.next();
      Set<T> connectedSet = get(keyElement);
      Set<T> removeSet = new HashSet<T>();
      for (Iterator iterator2 = connectedSet.iterator(); iterator2.hasNext();) {
        T cur = (T) iterator2.next();
        if (cycleSet.contains(cur)) {
          removeSet.add(cur);
        }
      }

      if (!removeSet.isEmpty()) {
        // // remove relations of locationElement -> cycle
        connectedSet.removeAll(removeSet);
        // add a new relation of location Element -> shared loc
        connectedSet.add(newLoc);
        getTable().put(keyElement, connectedSet);
      }
    }

    Set<T> newConnectedSet = new HashSet<T>();
    for (Iterator iterator = cycleSet.iterator(); iterator.hasNext();) {
      T cycleElement = (T) iterator.next();
      Set<T> connectedSet = get(cycleElement);
      if (connectedSet != null) {
        newConnectedSet.addAll(connectedSet);
      }
      getTable().remove(cycleElement);
    }
    newConnectedSet.removeAll(cycleSet);
    newConnectedSet.remove(newLoc);

    Set<T> set = getTable().get(newLoc);
    set.addAll(newConnectedSet);

    // clean up lattice
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      T keyElement = (T) iterator.next();
      get(keyElement).removeAll(cycleSet);
    }

    for (Iterator iterator = cycleSet.iterator(); iterator.hasNext();) {
      T cycleElement = (T) iterator.next();
      getTable().remove(cycleElement);
    }

  }

  public void remove(T loc) {

    Set<T> keySet = getKeySet();

    Set<T> inSet = new HashSet<T>();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      T keyElement = (T) iterator.next();
      Set<T> connectedSet = get(keyElement);
      if (connectedSet.contains(loc)) {
        inSet.add(loc);
        connectedSet.remove(loc);
      }
    }

    Set<T> outSet = get(loc);

    for (Iterator iterator = inSet.iterator(); iterator.hasNext();) {
      T in = (T) iterator.next();
      for (Iterator iterator2 = outSet.iterator(); iterator2.hasNext();) {
        T out = (T) iterator2.next();
        put(in, out);
      }
    }

    getTable().remove(loc);

  }

  public void substituteLocation(T oldLoc, T newLoc) {
    // the new location is going to take all relations of the old location
    if (!getKeySet().contains(newLoc)) {
      put(newLoc);
    }

    // consider the set of location s.t. LOC is greater than oldLoc
    Set<T> keySet = getKeySet();
    Set<T> directedConnctedHigherLocSet = new HashSet<T>();

    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      T key = (T) iterator.next();
      Set<T> connectedSet = getTable().get(key);
      if (connectedSet.contains(oldLoc)) {
        directedConnctedHigherLocSet.add(key);
      }
    }

    Set<T> connctedLowerSet = getTable().get(oldLoc);
    Set<T> directedConnctedLowerLocSet = new HashSet<T>();
    if (connctedLowerSet != null) {
      directedConnctedLowerLocSet.addAll(connctedLowerSet);
    }

    for (Iterator iterator = directedConnctedHigherLocSet.iterator(); iterator.hasNext();) {
      T higher = (T) iterator.next();
      if (!higher.equals(newLoc)) {
        addRelationHigherToLower(higher, newLoc);
      }
    }

    for (Iterator iterator = directedConnctedLowerLocSet.iterator(); iterator.hasNext();) {
      T lower = (T) iterator.next();
      if (!lower.equals(newLoc)) {
        addRelationHigherToLower(newLoc, lower);
      }
    }

    getTable().remove(oldLoc);

    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      T key = (T) iterator.next();
      getTable().get(key).remove(oldLoc);
    }

  }

  public void removeRedundantEdges() {

    Set<T> keySet = getTable().keySet();

    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      T src = (T) iterator.next();
      Set<T> connectedSet = getTable().get(src);
      Set<T> toberemovedSet = new HashSet<T>();
      for (Iterator iterator2 = connectedSet.iterator(); iterator2.hasNext();) {
        T dst = (T) iterator2.next();
        Set<T> otherNeighborSet = new HashSet<T>();
        otherNeighborSet.addAll(connectedSet);
        otherNeighborSet.remove(dst);
        for (Iterator iterator3 = otherNeighborSet.iterator(); iterator3.hasNext();) {
          T neighbor = (T) iterator3.next();
          if (isReachable(neighbor, new HashSet<T>(), dst)) {
            toberemovedSet.add(dst);
          }
        }
      }
      if (toberemovedSet.size() > 0) {
        connectedSet.removeAll(toberemovedSet);
      }
    }

  }

  private boolean isReachable(T neighbor, Set<T> visited, T dst) {
    Set<T> connectedSet = getTable().get(neighbor);
    if (connectedSet != null) {
      for (Iterator<T> iterator = connectedSet.iterator(); iterator.hasNext();) {
        T n = iterator.next();
        if (n.equals(dst)) {
          return true;
        }
        if (!visited.contains(n)) {
          visited.add(n);
          if (isReachable(n, visited, dst)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public Map<T, Set<T>> getIncomingElementMap() {
    Map<T, Set<T>> map = new HashMap<T, Set<T>>();

    Set<T> keySet = getKeySet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      T key = (T) iterator.next();

      Set<T> incomingSet = new HashSet<T>();

      for (Iterator iterator2 = keySet.iterator(); iterator2.hasNext();) {
        T in = (T) iterator2.next();
        if (!in.equals(key) && get(in).contains(key)) {
          incomingSet.add(in);
        }
      }
      map.put(key, incomingSet);
    }

    return map;
  }

  public void insertNewLocationBetween(T higher, T lower, T newLoc) {
    Set<T> connectedSet = get(higher);
    connectedSet.remove(lower);
    connectedSet.add(newLoc);

    put(newLoc, lower);
  }

  public void insertNewLocationBetween(T higher, Set<T> lowerSet, T newLoc) {
    // System.out.println("---insert new location=" + newLoc + "   between=" + higher + "<->"
    // + lowerSet);
    Set<T> connectedSet = get(higher);
    if (connectedSet == null) {
      connectedSet = new HashSet<T>();
    } else {
      connectedSet.removeAll(lowerSet);
    }
    connectedSet.add(newLoc);

    for (Iterator iterator = lowerSet.iterator(); iterator.hasNext();) {
      T lower = (T) iterator.next();
      put(newLoc, lower);
    }
  }

  public SSJavaLattice<T> clone() {

    SSJavaLattice<T> clone = new SSJavaLattice<T>(getTopItem(), getBottomItem());
    clone.setTable(getTable());
    clone.setSharedLocSet(getSharedLocSet());
    return clone;
  }

  public int countPaths() {
    T bottom = getBottomItem();

    Map<T, Set<T>> map = getIncomingElementMap();
    Map<T, Integer> countMap = new HashMap<T, Integer>();

    Set<T> visited = new HashSet<T>();
    visited.add(bottom);

    countMap.put(bottom, new Integer(1));
    recur_countPaths(bottom, map, countMap, visited);
    if (countMap.containsKey(getTopItem())) {
      return countMap.get(getTopItem());
    }
    return 0;
  }

  private void recur_countPaths(T cur, Map<T, Set<T>> map, Map<T, Integer> countMap, Set<T> visited) {
    int curCount = countMap.get(cur).intValue();
    Set<T> inSet = map.get(cur);

    for (Iterator iterator = inSet.iterator(); iterator.hasNext();) {
      T in = (T) iterator.next();
      int inCount = 0;
      if (countMap.containsKey(in)) {
        inCount = countMap.get(in).intValue();
      }
      inCount += curCount;
      countMap.put(in, inCount);
      if (visited.containsAll(get(in))) {
        visited.add(in);
        recur_countPaths(in, map, countMap, visited);
      }
    }
  }
}
