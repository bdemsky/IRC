package Util;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

public class Lattice<T> {
  private Hashtable<T, Set<T>> table;
  int size;

  public Lattice() {
    table = new Hashtable<T, Set<T>>();
  }

  public boolean put(T key, T value) {
    Set<T> s;
    if (table.containsKey(key)) {
      s = table.get(key);
    } else {
      s = new HashSet<T>();
      table.put(key, s);
    }
    if (!s.contains(value)) {
      size++;
      s.add(value);
      return true;
    } else
      return false;
  }

  public Set<T> get(T key) {
    return table.get(key);
  }
  
  public boolean containsKey(T o) {
    return table.containsKey(o);
  }

  public boolean isGreaterThan(T a, T b) {

    Set<T> neighborSet = get(a);
    System.out.println("neightborSet of " + a + "=" + neighborSet);

    if (neighborSet == null) {
      return false;
    } else if (neighborSet.contains(b)) {
      return true;
    } else {
      boolean reachable = false;
      for (Iterator<T> iterator = neighborSet.iterator(); iterator.hasNext();) {
        T neighbor = iterator.next();
        reachable = reachable || isGreaterThan(neighbor, b);
      }
      return reachable;
    }
  }

  public T getGLB(Set<T> inputSet) {

    Set<T> lowerSet = new HashSet<T>();

    // get lower set of input locations
    for (Iterator<T> iterator = inputSet.iterator(); iterator.hasNext();) {
      T element = iterator.next();
      lowerSet.addAll(getLowerSet(element, new HashSet<T>()));
    }

    // calculate the greatest element of lower set
    // find an element A, where every lower bound B of lowerSet, B<A
    for (Iterator<T> iterator = lowerSet.iterator(); iterator.hasNext();) {
      T lowerElement = iterator.next();
      boolean isGreaterThanAll = true;
      for (Iterator<T> iterator2 = lowerSet.iterator(); iterator2.hasNext();) {
        T e = iterator2.next();
        if (!lowerElement.equals(e)) {
          if (!isGreaterThan(lowerElement, e)) {
            isGreaterThanAll = false;
            break;
          }
        }
      }
      if (isGreaterThanAll) {
        return lowerElement;
      }
    }
    return null;
  }

  public Set<T> getLowerSet(T element, Set<T> lowerSet) {

    Set<T> neighborSet = get(element);
    if (neighborSet != null) {
      lowerSet.addAll(neighborSet);
      for (Iterator<T> iterator = neighborSet.iterator(); iterator.hasNext();) {
        T neighbor = iterator.next();
        lowerSet = getLowerSet(neighbor, lowerSet);
      }
    }
    return lowerSet;
  }

}
