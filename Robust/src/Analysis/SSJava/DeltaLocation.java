package Analysis.SSJava;

public class DeltaLocation extends CompositeLocation {

  private int numDelta;

  public DeltaLocation(CompositeLocation comp, int numDelta) {
    this.numDelta = numDelta;
    this.locTuple = comp.getTuple();
  }

  public int getNumDelta() {
    return numDelta;
  }

  public String toString() {

    String rtr = "";
    for (int i = 0; i < numDelta; i++) {
      rtr += "DELTA[";
    }

    int tupleSize = locTuple.size();
    for (int i = 0; i < tupleSize; i++) {
      Location locElement = locTuple.get(i);
      if (i != 0) {
        rtr += ",";
      }
      rtr += locElement;
    }

    for (int i = 0; i < numDelta; i++) {
      rtr += "]";
    }

    return rtr;
  }

}
