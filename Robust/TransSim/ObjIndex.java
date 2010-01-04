public class ObjIndex {
  int object;
  int index;
  public ObjIndex(int object, int index) {
    this.object=object;
    this.index=index;
  }

  public boolean equals(Object o) {
    ObjIndex other=(ObjIndex)o;
    return (other.object==object)&&(other.index==index);
  }

  public int hashCode() {
    return object^index;
  }
}