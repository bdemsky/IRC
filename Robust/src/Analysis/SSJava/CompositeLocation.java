package Analysis.SSJava;

import IR.ClassDescriptor;

public class CompositeLocation extends Location {

  private NTuple<Location> locTuple;

  public CompositeLocation(ClassDescriptor cd) {
    super(cd);
  }

  public void addSingleLocation(Location loc) {
    locTuple.addElement(loc);
  }

}
