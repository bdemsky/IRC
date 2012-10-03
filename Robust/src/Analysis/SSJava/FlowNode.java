package Analysis.SSJava;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import IR.Descriptor;
import IR.FieldDescriptor;
import IR.VarDescriptor;

public class FlowNode {

  // descriptor tuple is a unique identifier of the flow node
  private NTuple<Location> locTuple;

  // if the infer node represents the base type of field access,
  // this set contains fields of the base type
  private Set<FlowNode> fieldNodeSet;

  // set true if this node stores a return value
  private boolean isReturn;

  private boolean isDeclarationNode = false;

  private boolean isIntermediate;

  private CompositeLocation compLoc;

  private boolean isSkeleton;

  public boolean isIntermediate() {
    return isIntermediate;
  }

  public void setIntermediate(boolean isIntermediate) {
    this.isIntermediate = isIntermediate;
  }

  public Set<FlowNode> getFieldNodeSet() {
    return fieldNodeSet;
  }

  public FlowNode(NTuple<Location> tuple) {

    this.isSkeleton = false;
    this.isIntermediate = false;

    NTuple<Location> base = null;
    Location loc = null;
    if (tuple.size() > 1) {
      base = tuple.subList(0, tuple.size() - 1);
      loc = tuple.get(tuple.size() - 1);
    } else {
      base = tuple;
    }
    fieldNodeSet = new HashSet<FlowNode>();
    locTuple = new NTuple<Location>();
    if (base != null) {
      locTuple.addAll(base);
    }
    if (loc != null) {
      locTuple.add(loc);
    }

  }

  public void setCompositeLocation(CompositeLocation in) {
    compLoc = in;
  }

  public CompositeLocation getCompositeLocation() {
    return compLoc;
  }

  public void addFieldNode(FlowNode node) {
    fieldNodeSet.add(node);
  }

  public NTuple<Location> getLocTuple() {
    return locTuple;
  }

  public boolean isReturn() {
    return isReturn;
  }

  public void setReturn(boolean isReturn) {
    this.isReturn = isReturn;
  }

  public String toString() {
    String rtr = "[FlowNode]:";
    if (isSkeleton()) {
      rtr += "SKELETON:";
    }
    rtr += ":" + locTuple;
    return rtr;
  }

  public int hashCode() {
    return 7 + locTuple.hashCode();
  }

  public boolean equals(Object obj) {

    if (obj instanceof FlowNode) {
      FlowNode in = (FlowNode) obj;
      if (locTuple.equals(in.getLocTuple())) {
        return true;
      }
    }

    return false;

  }

  public String getID() {
    String id = "";
    for (int i = 0; i < locTuple.size(); i++) {
      id += locTuple.get(i).getSymbol();
    }
    return id;
  }

  public String getPrettyID() {
    String id = "<";
    String property = "";
    for (int i = 0; i < locTuple.size(); i++) {
      if (i != 0) {
        id += ",";
      }
      id += locTuple.get(i).getSymbol();
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

  public void setDeclarationNode() {
    isDeclarationNode = true;
  }

  public boolean isDeclaratonNode() {
    return isDeclarationNode;
  }

  public NTuple<Location> getCurrentLocTuple() {
    if (compLoc == null) {
      return locTuple;
    }
    NTuple<Location> curLocTuple = new NTuple<Location>();
    for (int i = 0; i < compLoc.getSize(); i++) {
      Location locElement = compLoc.get(i);
      curLocTuple.add(locElement);
    }
    return curLocTuple;
  }

  public NTuple<Descriptor> getCurrentDescTuple() {

    if (compLoc == null) {
      return getDescTuple();
    }

    NTuple<Descriptor> curDescTuple = new NTuple<Descriptor>();
    for (int i = 0; i < compLoc.getSize(); i++) {
      Location locElement = compLoc.get(i);
      curDescTuple.add(locElement.getLocDescriptor());
    }
    return curDescTuple;
  }

  public boolean isSkeleton() {
    return isSkeleton;
  }

  public void setSkeleton(boolean isSkeleton) {
    this.isSkeleton = isSkeleton;
  }

  public NTuple<Descriptor> getDescTuple() {
    NTuple<Descriptor> descTuple = new NTuple<Descriptor>();
    for (int i = 0; i < locTuple.size(); i++) {
      descTuple.add(locTuple.get(i).getLocDescriptor());
    }
    return descTuple;
  }

}
