package Analysis.SSJava;

import IR.ClassDescriptor;
import IR.Descriptor;

public class LocationDescriptor extends Descriptor {

  ClassDescriptor enclosingDesc;

  public LocationDescriptor(String name) {
    super(name);
  }

  public void setEnclosingClassDesc(ClassDescriptor en) {
    enclosingDesc = en;
  }

  public ClassDescriptor getEnclosingClassDesc() {
    return enclosingDesc;
  }

}
