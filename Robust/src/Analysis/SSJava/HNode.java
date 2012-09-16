package Analysis.SSJava;

import IR.Descriptor;

public class HNode {

  private String name;
  private Descriptor desc;

  private boolean isSkeleton;
  private boolean isCombinationNode;
  private boolean isSharedNode;

  public HNode() {
    this.isSkeleton = false;
    this.isCombinationNode = false;
    this.isSharedNode = false;
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

  public boolean equals(Object o) {
    if (o instanceof HNode) {
      HNode in = (HNode) o;
      if (getName().equals(in.getName())) {
        return true;
      }
    }
    return false;
  }

  public String toString() {
    String isShared = "";
    if (isSharedNode()) {
      isShared = "*";
    }
    return "[Node::" + name + isShared + "]";
  }

  public Descriptor getDescriptor() {
    return desc;
  }

  public int hashCode() {
    return 7 + name.hashCode();
  }

}
