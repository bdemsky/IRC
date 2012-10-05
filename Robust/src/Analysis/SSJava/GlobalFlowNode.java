package Analysis.SSJava;

import IR.Descriptor;

public class GlobalFlowNode {

  NTuple<Location> locTuple;
  CompositeLocation compLoc;

  public GlobalFlowNode(NTuple<Location> in) {
    locTuple = in;
  }

  public int hashCode() {
    return locTuple.hashCode();
  }

  public NTuple<Location> getLocTuple() {
    return locTuple;
  }

  public boolean equals(Object obj) {

    if (obj instanceof GlobalFlowNode) {
      GlobalFlowNode in = (GlobalFlowNode) obj;
      if (locTuple.equals(in.getLocTuple())) {
        return true;
      }
    }

    return false;

  }

  public String toString() {
    return locTuple.toString();
  }

  public NTuple<Descriptor> getDescTuple() {
    NTuple<Descriptor> descTuple = new NTuple<Descriptor>();

    for (int i = 0; i < locTuple.size(); i++) {
      descTuple.add(locTuple.get(i).getLocDescriptor());
    }

    return descTuple;
  }

  public String getID() {

    NTuple<Descriptor> descTuple = getDescTuple();
    String id = "";
    for (int i = 0; i < descTuple.size(); i++) {
      id += descTuple.get(i).getSymbol();
    }
    return id;
  }

  public String getPrettyID() {

    NTuple<Descriptor> descTuple = getDescTuple();

    String id = "<";
    String property = "";
    for (int i = 0; i < descTuple.size(); i++) {
      if (i == 0) {
        id += locTuple.get(i);
      } else {
        id += ",";
        id += descTuple.get(i).getSymbol();
      }
    }
    id += ">";

    if (compLoc != null) {
      id += " " + compLoc;
    }

    // if (isReturn()) {
    // property += "R";
    // }
    //
    // if (isSkeleton()) {
    // property += "S";
    // }

    if (property.length() > 0) {
      property = " [" + property + "]";
    }

    return id + property;
  }

}
