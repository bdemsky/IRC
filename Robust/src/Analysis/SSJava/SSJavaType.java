package Analysis.SSJava;

import IR.TypeExtension;

public class SSJavaType implements TypeExtension {

  private boolean isOwned;
  private CompositeLocation compLoc;

  public SSJavaType(boolean isOwned) {
    this.isOwned = isOwned;
  }

  public CompositeLocation getCompLoc() {
    return compLoc;
  }

  public void setCompLoc(CompositeLocation compLoc) {
    this.compLoc = compLoc;
  }

  public boolean isOwned() {
    return isOwned;
  }

  public void setOwned(boolean isOwned) {
    this.isOwned = isOwned;
  }

  public String toString() {
    return compLoc + "::owned=" + isOwned;
  }

}
