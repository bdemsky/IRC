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
}
