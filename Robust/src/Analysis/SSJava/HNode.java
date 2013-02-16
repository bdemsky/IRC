package Analysis.SSJava;

import IR.Descriptor;

public class HNode {

  private String name;
  private Descriptor desc;

  private boolean isSkeleton;
  private boolean isCombinationNode;
  private boolean isSharedNode;
  private boolean isMergeNode;

  // set true if hnode is the first node of the combination chain
  private boolean isDirectCombinationNode;

  public HNode() {
    this.isSkeleton = false;
    this.isCombinationNode = false;
    this.isSharedNode = false;
    this.isMergeNode = false;
    this.isDirectCombinationNode = false;
  }

  public boolean isMergeNode() {
    return isMergeNode;
  }

  public void setMergeNode(boolean isMergeNode) {
    this.isMergeNode = isMergeNode;
  }

  public HNode(String name) {
    this();
    this.name = name;
  }

  public HNode(Descriptor d) {
    this();
    this.desc = d;
    this.name = d.getSymbol();
  }

  public boolean isSharedNode() {
    return isSharedNode;
  }

  public void setSharedNode(boolean b) {
    this.isSharedNode = b;
  }

  public boolean isSkeleton() {
    return isSkeleton;
  }

  public void setSkeleton(boolean isSkeleton) {
    this.isSkeleton = isSkeleton;
  }

  public boolean isCombinationNode() {
    return isCombinationNode;
  }

  public void setCombinationNode(boolean b) {
    isCombinationNode = b;
  }

  public String getName() {
    return name;
  }

  public boolean isDirectCombinationNode() {
    return isDirectCombinationNode;
  }

  public void setDirectCombinationNode(boolean isDirectCombinationNode) {
    this.isDirectCombinationNode = isDirectCombinationNode;
  }

  public boolean equals(Object o) {
    if (o instanceof HNode) {
      HNode in = (HNode) o;
      if (getName().equals(in.getName())) {
        return true;
      }
    }
    return false;
  }

  public String getNamePropertyString() {
    return toString().substring(1, toString().length() - 1);
  }

  public String toString() {

    String properties = "";

    if (isSharedNode()) {
      properties += "*";
    }

    if (isCombinationNode()) {
      properties += "C";
    }

    if (isSkeleton()) {
      properties += "S";
    }

    if (properties.length() > 0) {
      properties = "(" + properties + ")";
    }

    return "[" + name + properties + "]";
  }

  public Descriptor getDescriptor() {
    return desc;
  }

  public int hashCode() {
    return 7 + name.hashCode();
  }

}
