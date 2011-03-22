package Analysis.SSJava;

import java.util.List;
import java.util.Vector;

import IR.ClassDescriptor;

public class DeltaLocation extends Location {

  private Vector<Location> operandVec;

  public DeltaLocation(ClassDescriptor cd) {
    super(cd);
    operandVec = new Vector<Location>();
  }
  
  public void addDeltaOperand(Location op) {
    operandVec.add(op);
  }

  public List<Location> getDeltaOperandLocationVec() {
    return operandVec;
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

    int tupleSize = operandVec.size();
    for (int i = 0; i < tupleSize; i++) {
      Location locElement = operandVec.elementAt(i);
      if (i != 0) {
        rtr += ",";
      }
      rtr += locElement;
    }
    rtr += ")";
    return rtr;
  }

}
