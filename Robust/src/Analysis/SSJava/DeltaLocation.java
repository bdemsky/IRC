package Analysis.SSJava;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import IR.ClassDescriptor;
import IR.TypeDescriptor;

public class DeltaLocation extends Location {

  private Vector<Location> operandVec;
  private TypeDescriptor refOperand = null;

  public DeltaLocation(ClassDescriptor cd) {
    super(cd);
    operandVec = new Vector<Location>();
  }

  public DeltaLocation(ClassDescriptor cd, Set<Location> set) {
    super(cd);
    operandVec = new Vector<Location>();
    operandVec.addAll(set);
  }

  public DeltaLocation(ClassDescriptor cd, TypeDescriptor refOperand) {
    super(cd);
    this.refOperand = refOperand;
    operandVec = new Vector<Location>();
  }

  public TypeDescriptor getRefLocationId() {
    return this.refOperand;
  }

  public void addDeltaOperand(Location op) {
    operandVec.add(op);
  }

  public List<Location> getDeltaOperandLocationVec() {
    return operandVec;
  }

  public Set<Location> getBaseLocationSet() {

    if (operandVec.size() == 1 && (operandVec.get(0) instanceof DeltaLocation)) {
      // nested delta definition
      DeltaLocation deltaLoc = (DeltaLocation) operandVec.get(0);
      return deltaLoc.getBaseLocationSet();
    } else {
      Set<Location> set = new HashSet<Location>();
      set.addAll(operandVec);
      return set;
    }

  }

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
    int hash = cd.hashCode();
    if (loc != null) {
      hash += operandVec.hashCode();
    }
    return hash;
  }

  public String toString() {
    String rtr = "delta(";

    if (operandVec.size() != 0) {
      int tupleSize = operandVec.size();
      for (int i = 0; i < tupleSize; i++) {
        Location locElement = operandVec.elementAt(i);
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
