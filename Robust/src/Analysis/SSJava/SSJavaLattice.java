package Analysis.SSJava;

import java.util.HashSet;
import java.util.Set;

import Util.Lattice;

public class SSJavaLattice<T> extends Lattice<T> {

  public static final String TOP = "_top_";
  public static final String BOTTOM = "_bottom_";

  Set<T> sharedLocSet;

  public SSJavaLattice(T top, T bottom) {
    super(top, bottom);
    sharedLocSet = new HashSet<T>();
  }

  public Set<T> getSharedLocSet() {
    return sharedLocSet;
  }

  public void addSharedLoc(T loc) {
    sharedLocSet.add(loc);
  }

  public boolean isSharedLoc(T loc) {
    return sharedLocSet.contains(loc);
  }

  public boolean addRelationHigherToLower(T higher, T lower) {

    System.out.println("add a relation: " + lower + "<" + higher);

    return put(higher, lower);
  }

}
