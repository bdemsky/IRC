package Analysis.SSJava;

import IR.ClassDescriptor;
import IR.Descriptor;

public class DeltaLocation extends CompositeLocation {

  private Descriptor refOperand = null;
  private int numDelta;

  public DeltaLocation() {
  }

  // public DeltaLocation(Set<Location> set) {
  // locTuple.addAll(set);
  // }

  public DeltaLocation(ClassDescriptor cd, Descriptor refOperand) {
    this.refOperand = refOperand;
  }

  public Descriptor getRefLocationId() {
    return this.refOperand;
  }

  public void addDeltaOperand(Location op) {
    locTuple.addElement(op);
  }

  public NTuple<Location> getDeltaOperandLocationVec() {
    return locTuple;
  }

  // public Set<Location> getBaseLocationSet() {
  //
  // if (operandVec.size() == 1 && (operandVec.get(0) instanceof DeltaLocation))
  // {
  // // nested delta definition
  // DeltaLocation deltaLoc = (DeltaLocation) operandVec.get(0);
  // return deltaLoc.getBaseLocationSet();
  // } else {
  // Set<Location> set = new HashSet<Location>();
  // set.addAll(operandVec);
  // return set;
  // }
  //
  // }

  public boolean equals(Object o) {

    if (!(o instanceof DeltaLocation)) {
      return false;
    }

    DeltaLocation deltaLoc = (DeltaLocation) o;

    if (deltaLoc.getDeltaOperandLocationVec().equals(getDeltaOperandLocationVec())) {
      return true;
    }
    return false;
  }

  public int hashCode() {
    int hash = locTuple.hashCode();
    if (refOperand != null) {
      hash += refOperand.hashCode();
    }
    return hash;
  }

  public String toString() {
    String rtr = "delta(";

    if (locTuple.size() != 0) {
      int tupleSize = locTuple.size();
      for (int i = 0; i < tupleSize; i++) {
        Location locElement = locTuple.get(i);
        if (i != 0) {
          rtr += ",";
        }
        rtr += locElement;
      }
    } else {
      rtr += "LOC_REF";
    }

    rtr += ")";

    return rtr;
  }

}
