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

  public Location(Descriptor d, String loc) {
    this.d = d;
    this.loc = loc;
    this.type = NORMAL;
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
        if (loc.getLocIdentifier().equals(getLocIdentifier())) {
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
