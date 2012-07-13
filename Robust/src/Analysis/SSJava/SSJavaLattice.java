package Analysis.SSJava;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import Util.Lattice;

public class SSJavaLattice<T> extends Lattice<T> {

  Set<T> sharedLocSet;
  public static int seed = 0;

  public SSJavaLattice(T top, T bottom) {
    super(top, bottom);
    sharedLocSet = new HashSet<T>();
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

  public void mergeIntoSharedLocation(Set<T> cycleSet, T newLoc) {

    // add a new shared loc
    put(newLoc);
    addSharedLoc(newLoc);

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
        // remove relations of locationElement -> cycle
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

  }

  public void substituteLocation(T oldLoc, T newLoc) {
    // the new location is going to take all relations of the old location

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

  }

}
