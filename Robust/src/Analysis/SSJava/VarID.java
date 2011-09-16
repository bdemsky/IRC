package Analysis.SSJava;

import java.util.ArrayList;
import IR.Descriptor;

public class VarID {

  // contains field and var descriptors
  // given the case a.b.f it contains descriptors for a,b, and f
  private ArrayList<Descriptor> var;
  // properties of ID
  private boolean isThis;
  private boolean isGlobal;
  private boolean isTop;

  public VarID(Descriptor var) {
    this.var = new ArrayList<Descriptor>();
    this.var.add(var);
    isThis = false;
    isGlobal = false;
    isTop = false;
  }

  public void addAccess(Descriptor var) {
    this.var.add(var);
  }

  public void setThis() {
    isThis = true;
  }

  public void setGlobal() {
    isGlobal = true;
  }

  public void setTop() {
    isTop = true;
  }

  public String toString() {
    String toReturn = "";
    for (Descriptor d : var)
      toReturn += d.toString() + " ";
    return toReturn;
  }

  public void setReturn() {
    //interim fixes
  }
}