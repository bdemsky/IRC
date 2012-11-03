package Analysis.SSJava;

import java.util.HashSet;
import java.util.Set;

import IR.ClassDescriptor;
import IR.Descriptor;
import IR.FieldDescriptor;
import IR.VarDescriptor;

public class FlowNode {

  // descriptor tuple is a unique identifier of the flow node
  protected NTuple<Descriptor> descTuple;

  // if the infer node represents the base type of field access,
  // this set contains fields of the base type
  private Set<FlowNode> fieldNodeSet;

  // set true if this node stores a return value
  private boolean isReturn;

  private boolean isDeclarationNode = false;

  private boolean isIntermediate;

  private CompositeLocation compLoc;

  private boolean isSkeleton;

  private boolean isFormHolder = false;

  public boolean isIntermediate() {
    return isIntermediate;
  }

  public void setIntermediate(boolean isIntermediate) {
    this.isIntermediate = isIntermediate;
  }

  public void setFormHolder(boolean in) {
    isFormHolder = in;
  }

  public boolean isFromHolder() {
    return isFormHolder;
  }

  public Set<FlowNode> getFieldNodeSet() {
    return fieldNodeSet;
  }

  public FlowNode(NTuple<Descriptor> tuple) {

    this.isSkeleton = false;
    this.isIntermediate = false;

    NTuple<Descriptor> base = null;
    Descriptor desc = null;
    if (tuple.size() > 1) {
      base = tuple.subList(0, tuple.size() - 1);
      desc = tuple.get(tuple.size() - 1);
    } else {
      base = tuple;
    }
    fieldNodeSet = new HashSet<FlowNode>();
    descTuple = new NTuple<Descriptor>();
    if (base != null) {
      descTuple.addAll(base);
    }
    if (desc != null) {
      descTuple.add(desc);
    }

  }

  public void setCompositeLocation(CompositeLocation in) {
    System.out.println("$$$set compLoc=" + in);
    compLoc = in;
  }

  public CompositeLocation getCompositeLocation() {
    return compLoc;
  }

  public void addFieldNode(FlowNode node) {
    fieldNodeSet.add(node);
  }

  public NTuple<Descriptor> getDescTuple() {
    return descTuple;
  }

  public Descriptor getOwnDescriptor() {
    return descTuple.get(descTuple.size() - 1);
  }

  public boolean isPrimitiveType() {
    Descriptor desc = descTuple.get(descTuple.size() - 1);
    if (desc instanceof VarDescriptor) {
      return ((VarDescriptor) desc).getType().isPrimitive();
    } else if (desc instanceof FieldDescriptor) {
      return ((FieldDescriptor) desc).getType().isPrimitive();
    }
    return false;
  }

  public String toString() {
    String rtr = "[FlowNode]:";
    if (isSkeleton()) {
      rtr += "SKELETON:";
    }
    rtr += ":" + descTuple;
    return rtr;
  }

  public int hashCode() {
    return 7 + descTuple.hashCode();
  }

  public boolean equals(Object obj) {

    if (obj instanceof FlowNode) {
      FlowNode in = (FlowNode) obj;
      if (descTuple.equals(in.getDescTuple())) {
        return true;
      }
    }

    return false;

  }

  public String getID() {
    String id = "";
    for (int i = 0; i < descTuple.size(); i++) {
      id += descTuple.get(i).getSymbol();
    }
    return id;
  }

  public String getPrettyID() {
    String id = "<";
    String property = "";
    for (int i = 0; i < descTuple.size(); i++) {
      if (i != 0) {
        id += ",";
      }
      id += descTuple.get(i).getSymbol();
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

  public NTuple<Descriptor> getCurrentDescTuple() {

    if (compLoc == null) {
      return descTuple;
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

}
