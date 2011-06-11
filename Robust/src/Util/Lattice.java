package Util;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

public class Lattice<T> {

  private Hashtable<T, Set<T>> table;
  int size;

  private T top;
  private T bottom;

  public Lattice(T top, T bottom) {
    table = new Hashtable<T, Set<T>>();
    this.top = top;
    this.bottom = bottom;

    table.put(top, new HashSet<T>());

  }

  public T getTopItem() {
    return top;
  }

  public T getBottomItem() {
    return bottom;
  }

  public Set<T> getKeySet() {
    return table.keySet();
  }

  public boolean put(T key) {
    if (table.containsKey(key)) {
      return false;
    } else {
      // new key, need to be connected with top/bottom
      size++;
      table.get(top).add(key);
      Set<T> neightborSet = new HashSet<T>();
      neightborSet.add(bottom);
      table.put(key, neightborSet);
      return true;
    }
  }

  public boolean put(T key, T value) {
    Set<T> s;

    Set<T> topNeighbor = table.get(top);

    if (table.containsKey(key)) {
      s = table.get(key);
    } else {
      // new key, need to be connected with top
      topNeighbor.add(key);

      s = new HashSet<T>();
      table.put(key, s);
    }
    if (value != null && !s.contains(value)) {
      size++;
      s.add(value);

      if (!table.containsKey(value)) {
        Set<T> lowerNeighbor = new HashSet<T>();
        lowerNeighbor.add(bottom);
        table.put(value, lowerNeighbor);
      }

      // if value is already connected with top, it is no longer to be
      topNeighbor.remove(value);

      // if key is already connected with bottom,, it is no longer to be
      table.get(key).remove(getBottomItem());

      return true;
    } else
      return false;
  }

  public boolean isIntroducingCycle(T key) {

    Set<T> reachableSet = new HashSet<T>();
    Set<T> neighborSet = get(key);

    if (neighborSet == null) {
      return false;
    } else {
      reachableSet.addAll(neighborSet);
    }

    int oldReachableSize;
    do {
      oldReachableSize = reachableSet.size();
      Set<T> nextLevelNeighbors = new HashSet<T>();
      for (Iterator<T> iterator = neighborSet.iterator(); iterator.hasNext();) {
        T element = iterator.next();
        Set<T> neighbors = get(element);
        if (neighbors != null) {
          nextLevelNeighbors.addAll(neighbors);
          reachableSet.addAll(neighbors);
        }

        if (reachableSet.contains(key)) {
          // found cycle
          return true;
        }
      }
      neighborSet = nextLevelNeighbors;
    } while (oldReachableSize != reachableSet.size());

    return false;
  }

  public Set<T> get(T key) {
    return table.get(key);
  }

  public boolean containsKey(T o) {
    return table.containsKey(o);
  }

  public boolean isComparable(T a, T b) {

    Set<T> neighborSet = get(a);

    if (neighborSet == null) {
      return false;
    } else if (neighborSet.contains(b)) {
      return true;
    } else {
      boolean reachable = false;
      for (Iterator<T> iterator = neighborSet.iterator(); iterator.hasNext();) {
        T neighbor = iterator.next();
        reachable = reachable || isComparable(neighbor, b);
      }
      return reachable;
    }

  }

  public boolean isGreaterThan(T a, T b) {

    if (a.equals(top)) {
      if (b.equals(top)) {
        return false;
      }
      return true;
    }
    if (b.equals(top)) {
      return false;
    }
    if (a.equals(bottom)) {
      return false;
    }
    if (b.equals(bottom)) {
      return true;
    }

    Set<T> neighborSet = get(a);

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
      lowerSet.add(element);
    }

    // an element of lower bound should be lower than every input set
    Set<T> toberemoved = new HashSet<T>();
    for (Iterator<T> inputIterator = inputSet.iterator(); inputIterator.hasNext();) {
      T inputElement = inputIterator.next();

      for (Iterator iterator = lowerSet.iterator(); iterator.hasNext();) {
        T lowerElement = (T) iterator.next();
        if (!inputElement.equals(lowerElement)) {
          if (!isGreaterThan(inputElement, lowerElement)) {
            toberemoved.add(lowerElement);
          }
        }
      }
    }
    lowerSet.removeAll(toberemoved);

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

  public Set<Pair<T, T>> getOrderingPairSet() {
    // return the set of pairs in the lattice

    Set<Pair<T, T>> set = new HashSet<Pair<T, T>>();

    Set<T> visited = new HashSet<T>();
    Set<T> needtovisit = new HashSet<T>();
    needtovisit.add(top);

    while (!needtovisit.isEmpty()) {
      T key = needtovisit.iterator().next();
      Set<T> lowerSet = table.get(key);
      if (lowerSet != null) {
        for (Iterator iterator = lowerSet.iterator(); iterator.hasNext();) {
          T lowerItem = (T) iterator.next();
          set.add(new Pair(key, lowerItem));
          if (!visited.contains(key)) {
            needtovisit.add(lowerItem);
          }
        }
      }
      visited.add(key);
      needtovisit.remove(key);
    }
    return set;
  }

}
