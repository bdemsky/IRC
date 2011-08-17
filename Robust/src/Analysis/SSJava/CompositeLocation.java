package Analysis.SSJava;

import IR.TypeExtension;

public class CompositeLocation implements TypeExtension {

  protected NTuple<Location> locTuple;

  public CompositeLocation() {
    locTuple = new NTuple<Location>();
  }

  public CompositeLocation(Location loc) {
    locTuple = new NTuple<Location>();
    locTuple.add(loc);
  }

  public NTuple<Location> getTuple() {
    return locTuple;
  }

  public int getSize() {
    return locTuple.size();
  }

  public void addLocation(Location loc) {
    locTuple.add(loc);
  }

  public Location get(int idx) {
    return locTuple.get(idx);
  }

  public boolean isEmpty() {
    return locTuple.size() == 0;
  }

  public boolean startsWith(CompositeLocation prefix) {
    // tests if this composite location starts with the prefix

    for (int i = 0; i < prefix.getSize(); i++) {
      if (!prefix.get(i).equals(get(i))) {
        return false;
      }
    }
    return true;

  }

  public String toString() {

    String rtr = "CompLoc[";

    int tupleSize = locTuple.size();
    for (int i = 0; i < tupleSize; i++) {
      Location locElement = locTuple.get(i);
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

    if (compLoc.getTuple().equals(getTuple())) {
      return true;
    } else {
      return false;
    }

  }

  public int hashCode() {

    return locTuple.hashCode();

  }

  public CompositeLocation clone() {
    CompositeLocation clone = new CompositeLocation();
    clone.getTuple().addAll(locTuple);
    return clone;
  }

}
