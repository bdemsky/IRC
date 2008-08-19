package IR.Tree;

import IR.*;

public class TagEffect {
  TagVarDescriptor tag;
  boolean status;
  String name;

  public TagEffect(String tag, boolean status) {
    this.name=tag;
    this.status=status;
  }

  public void setTag(TagVarDescriptor tag) {
    this.tag=tag;
  }

  public TagVarDescriptor getTag() {
    return tag;
  }

  public String getName() {
    return name;
  }

  public boolean getStatus() {
    return status;
  }

  public String printNode(int indent) {
    if (status)
      return name;
    else
      return "!"+name;
  }
}
