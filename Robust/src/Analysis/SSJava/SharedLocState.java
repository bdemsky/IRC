package Analysis.SSJava;

import java.util.HashSet;
import java.util.Set;

import IR.Descriptor;

public class SharedLocState {

  Location loc;
  Set<Descriptor> varSet;
  boolean flag;

  public SharedLocState(Location loc) {
    this.loc = loc;
    this.varSet = new HashSet<Descriptor>();
    this.flag = false;
  }

  public void addVar(Descriptor d) {
    varSet.add(d);
  }

  public void removeVar(Descriptor d) {
    varSet.remove(d);
  }

  public Location getLoc() {
    return loc;
  }

  public String toString() {
    return "<" + loc + "," + varSet + "," + flag + ">";
  }

  public void setLoc(Location loc) {
    this.loc = loc;
  }

  public Set<Descriptor> getVarSet() {
    return varSet;
  }

  public void setVarSet(Set<Descriptor> varSet) {
    this.varSet = varSet;
  }

  public boolean isFlag() {
    return flag;
  }

  public void setFlag(boolean flag) {
    this.flag = flag;
  }

}
