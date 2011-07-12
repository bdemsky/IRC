package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import IR.Descriptor;
import Util.Pair;

public class SharedStatus {

  // maps location to its current writing var set and flag
  Hashtable<Location, Pair<Set<Descriptor>, Boolean>> mapLocation2Status;

  public SharedStatus() {
    mapLocation2Status = new Hashtable<Location, Pair<Set<Descriptor>, Boolean>>();
  }

  private Pair<Set<Descriptor>, Boolean> getStatus(Location loc) {
    Pair<Set<Descriptor>, Boolean> pair = mapLocation2Status.get(loc);
    if (pair == null) {
      pair = new Pair<Set<Descriptor>, Boolean>(new HashSet<Descriptor>(), new Boolean(false));
      mapLocation2Status.put(loc, pair);
    }
    return pair;
  }

  public void addVar(Location loc, Descriptor d) {
    getStatus(loc).getFirst().add(d);
  }

  public void removeVar(Location loc, Descriptor d) {
    getStatus(loc).getFirst().remove(d);
  }

  public String toString() {
    return mapLocation2Status.toString();
  }

  public Set<Location> getLocationSet() {
    return mapLocation2Status.keySet();
  }

  public void merge(SharedStatus inState) {
    Set<Location> keySet = inState.getLocationSet();
    for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
      Location inLoc = (Location) iterator.next();

      Pair<Set<Descriptor>, Boolean> inPair = inState.getStatus(inLoc);
      Pair<Set<Descriptor>, Boolean> currPair = mapLocation2Status.get(inLoc);

      if (currPair == null) {
        currPair =
            new Pair<Set<Descriptor>, Boolean>(new HashSet<Descriptor>(), new Boolean(false));
        mapLocation2Status.put(inLoc, currPair);
      }
      mergeSet(currPair.getFirst(), inPair.getFirst());
    }
  }

  public void mergeSet(Set<Descriptor> curr, Set<Descriptor> in) {
    if (curr.isEmpty()) {
      // Varset has a special initial value which covers all possible
      // elements
      // For the first time of intersection, we can take all previous set
      curr.addAll(in);
    } else {
      curr.retainAll(in);
    }
  }

  public int hashCode() {
    return mapLocation2Status.hashCode();
  }

  public Hashtable<Location, Pair<Set<Descriptor>, Boolean>> getMap() {
    return mapLocation2Status;
  }

  public boolean equals(Object o) {
    if (!(o instanceof SharedStatus)) {
      return false;
    }
    SharedStatus in = (SharedStatus) o;
    return in.getMap().equals(mapLocation2Status);
  }

  public Set<Descriptor> getVarSet(Location loc) {
    return mapLocation2Status.get(loc).getFirst();
  }

  public void updateFlag(Location loc, boolean b) {
    Pair<Set<Descriptor>, Boolean> pair = mapLocation2Status.get(loc);
    if (pair.getSecond() != b) {
      mapLocation2Status.put(loc,
          new Pair<Set<Descriptor>, Boolean>(pair.getFirst(), Boolean.valueOf(b)));
    }
  }

  public void updateFlag(boolean b) {
    Set<Location> locKeySet = mapLocation2Status.keySet();
    for (Iterator iterator = locKeySet.iterator(); iterator.hasNext();) {
      Location loc = (Location) iterator.next();
      Pair<Set<Descriptor>, Boolean> pair = mapLocation2Status.get(loc);
      mapLocation2Status.put(loc,
          new Pair<Set<Descriptor>, Boolean>(pair.getFirst(), Boolean.valueOf(b)));
    }

  }

  public boolean getFlag(Location loc) {
    return mapLocation2Status.get(loc).getSecond().booleanValue();
  }

  public SharedStatus clone() {
    SharedStatus newState = new SharedStatus();
    newState.mapLocation2Status =
        (Hashtable<Location, Pair<Set<Descriptor>, Boolean>>) mapLocation2Status.clone();
    return newState;
  }
}
