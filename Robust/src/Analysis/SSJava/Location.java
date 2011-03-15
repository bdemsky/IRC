package Analysis.SSJava;

import IR.ClassDescriptor;

public class Location {

  public static final int TOP = 1;
  public static final int NORMAL = 2;
  public static final int BOTTOM = 3;

  private int type;
  private ClassDescriptor cd;
  private String loc;

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

}
