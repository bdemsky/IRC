package Analysis.SSJava;

public class MethodLattice<T> extends SSJavaLattice<T> {

  private T thisLoc;
  private T globalLoc;
  private T returnLoc;

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

  public void setReturnLoc(T returnLoc) {
    this.returnLoc = returnLoc;
  }

  public T getReturnLoc() {
    return returnLoc;
  }

}
