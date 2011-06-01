package Analysis.SSJava;

public class MethodLattice<T> extends SSJavaLattice<T> {

  private T thisLoc;
  private T globalLoc;

  public MethodLattice(T top, T bottom) {
    super(top, bottom);
  }

  public void setThisLoc(T thisLoc) {
    this.thisLoc = thisLoc;
  }

  public T getThisLoc() {
    return thisLoc;
  }

  public void setGlobalLoc(T globalLoc) {
    this.globalLoc = globalLoc;
  }

  public T getGlobalLoc() {
    return globalLoc;
  }

}
