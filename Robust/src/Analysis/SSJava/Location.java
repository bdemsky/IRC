package Analysis.SSJava;

import IR.ClassDescriptor;
import IR.TypeExtension;

public class Location  implements TypeExtension {

  public static final int TOP = 1;
  public static final int NORMAL = 2;
  public static final int BOTTOM = 3;
  public static final int DELTA = 4;

  int type;
  ClassDescriptor cd;
  String loc;

  public Location(ClassDescriptor cd, String loc) {
    this.cd = cd;
    this.loc = loc;
    this.type = NORMAL;
  }

  public Location(ClassDescriptor cd) {
    this.cd = cd;
  }

  public void setType(int type) {
    this.type = type;
  }

  public ClassDescriptor getClassDescriptor() {
    return cd;
  }

  public String getLocIdentifier() {
    return loc;
  }

  public int getType() {
    return this.type;
  }

  public boolean equals(Object o) {
    if (!(o instanceof Location)) {
      return false;
    }

    Location loc = (Location) o;

    if (loc.getClassDescriptor().equals(getClassDescriptor())) {
      if (loc.getLocIdentifier() == null || getLocIdentifier() == null) {
        if (loc.getType() == getType()) {
          return true;
        }
      } else {
        if (loc.getLocIdentifier().equals(getLocIdentifier())) {
          return true;
        }
      }
    }

    return false;
  }

  public int hashCode() {

    int hash = cd.hashCode();
    if (loc != null) {
      hash += loc.hashCode();
    }
    return hash;

  }

  public String toString() {
    return "Loc[" + cd.getSymbol() + "." + loc + "]";
  }

  public static Location createTopLocation(ClassDescriptor cd) {
    Location topLoc = new Location(cd);
    topLoc.setType(TOP);
    topLoc.loc = "_top_";
    return topLoc;
  }

  public static Location createBottomLocation(ClassDescriptor cd) {
    Location bottomLoc = new Location(cd);
    bottomLoc.setType(BOTTOM);
    bottomLoc.loc = "_bottom_";
    return bottomLoc;
  }

  public boolean isTop() {
    return type==TOP;
  }

}
