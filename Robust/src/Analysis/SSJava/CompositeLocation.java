package Analysis.SSJava;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import IR.ClassDescriptor;

public class CompositeLocation extends Location {

  private NTuple<Location> locTuple;
  private Hashtable<ClassDescriptor, Location> cd2loc;
  private int size;

  public CompositeLocation(ClassDescriptor cd) {
    super(cd);
    locTuple = new NTuple<Location>();
    cd2loc = new Hashtable<ClassDescriptor, Location>();
    size = 0;
  }

  public NTuple<Location> getTuple() {
    return locTuple;
  }

  public int getTupleSize() {
    return size;
  }

  public void addLocation(Location loc) {
    locTuple.addElement(loc);

    if (loc instanceof DeltaLocation) {
      DeltaLocation deltaLoc = (DeltaLocation) loc;
      for (Iterator iterator = deltaLoc.getDeltaOperandLocationVec().iterator(); iterator.hasNext();) {
        Location opLoc = (Location) iterator.next();
        cd2loc.put(opLoc.getClassDescriptor(), opLoc);
        size++;
      }
    } else {
      cd2loc.put(loc.getClassDescriptor(), loc);
      size += 1;
    }
  }

  public Map<ClassDescriptor, Location> getCd2Loc() {
    return cd2loc;
  }

  public Location getLocation(ClassDescriptor cd) {
    return cd2loc.get(cd);
  }

  public Set<Location> getBaseLocationSet() {

    Set<Location> baseLocationSet = new HashSet<Location>();

    int tupleSize = locTuple.size();
    for (int i = 0; i < tupleSize; i++) {
      Location locElement = locTuple.at(i);

      if (locElement instanceof DeltaLocation) {
        baseLocationSet.addAll(((DeltaLocation) locElement).getDeltaOperandLocationVec());
      } else {
        baseLocationSet.add(locElement);
      }
    }
    return baseLocationSet;
  }

  public String toString() {
    String rtr = "CompLoc[";

    int tupleSize = locTuple.size();
    for (int i = 0; i < tupleSize; i++) {
      Location locElement = locTuple.at(i);
      if (i != 0) {
        rtr += ",";
      }
      rtr += locElement;
    }
    rtr += "]";

    return rtr;
  }

  public boolean equals(Object o) {

    if (!(o instanceof CompositeLocation)) {
      return false;
    }

    CompositeLocation compLoc = (CompositeLocation) o;

    if (compLoc.getClassDescriptor().equals(getClassDescriptor())
        && compLoc.getTuple().equals(getTuple())) {
      return true;
    } else {
      return false;
    }

  }

  public int hashCode() {

    int hashCode = getClassDescriptor().hashCode();
    return hashCode + locTuple.hashCode();

  }

}
