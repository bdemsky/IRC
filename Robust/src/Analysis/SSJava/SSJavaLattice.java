package Analysis.SSJava;

import java.util.HashSet;
import java.util.Set;

import Util.Lattice;

public class SSJavaLattice<T> extends Lattice<T> {
  
  public static final String TOP="_top_";
  public static final String BOTTOM="_bottom_";

  Set<T> spinLocSet;

  public SSJavaLattice(T top, T bottom) {
    super(top, bottom);
    spinLocSet = new HashSet<T>();
  }

  public Set<T> getSpinLocSet() {
    return spinLocSet;
  }

  public void addSpinLoc(T loc) {
    spinLocSet.add(loc);
  }

}
