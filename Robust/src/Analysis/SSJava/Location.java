package Analysis.SSJava;

import IR.Descriptor;
import IR.TypeExtension;

public class Location implements TypeExtension {

  public static final int TOP = 1;
  public static final int NORMAL = 2;
  public static final int BOTTOM = 3;

  int type;
  Descriptor d;
  String loc;
  Descriptor locDesc;

  public Location(Descriptor enclosingDesc, Descriptor locDesc) {
    this.d = enclosingDesc;
    this.locDesc = locDesc;
    this.loc = locDesc.getSymbol();
  }

  public Location(Descriptor d, String loc) {
    this.d = d;
    this.loc = loc;

    if (loc.equals(SSJavaAnalysis.TOP)) {
      type = TOP;
    } else if (loc.equals(SSJavaAnalysis.BOTTOM)) {
      type = BOTTOM;
    } else {
      type = NORMAL;
    }

  }

  public Location(Descriptor d, int type) {
    this.d = d;
    this.type = type;
    if (type == TOP) {
      loc = SSJavaAnalysis.TOP;
    } else if (type == BOTTOM) {
      loc = SSJavaAnalysis.BOTTOM;
    }
  }

  public void setLocIdentifier(String s) {
    loc = s;
  }

  public void setLocDescriptor(Descriptor d) {
    locDesc = d;
  }

  public Descriptor getLocDescriptor() {
    return locDesc;
  }

  public void setType(int type) {
    this.type = type;
  }

  public Descriptor getDescriptor() {
    return d;
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

    if (loc.getDescriptor().equals(getDescriptor())) {
      if (loc.getLocIdentifier() == null || getLocIdentifier() == null) {
        if (loc.getType() == getType()) {
          return true;
        }
      } else {
        if (loc.getLocDescriptor() != null && getLocDescriptor() != null
            && loc.getLocDescriptor().equals(getLocDescriptor())) {
          return true;
        } else if (loc.getLocIdentifier().equals(getLocIdentifier())) {
          return true;
        }
      }
    }

    return false;
  }

  public int hashCode() {

    int hash = d.hashCode();
    if (loc != null) {
      hash += loc.hashCode();
    }
    if (locDesc != null) {
      hash += locDesc.hashCode();
    }
    return hash;

  }

  public String toString() {
    return "Loc[" + d.getSymbol() + "." + loc + "]";
  }

  public String getSymbol() {
    return d.getSymbol() + "." + loc;
  }

  public static Location createTopLocation(Descriptor d) {
    Location topLoc = new Location(d, TOP);
    return topLoc;
  }

  public static Location createBottomLocation(Descriptor d) {
    Location bottomLoc = new Location(d, BOTTOM);
    return bottomLoc;
  }

  public boolean isTop() {
    return type == TOP;
  }

}
