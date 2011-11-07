package Analysis.Disjoint;

import IR.*;


public class EdgeKey {
  private Integer srcId;
  private Integer dstId;
  private FieldDescriptor f;

  public EdgeKey(Integer srcId, Integer dstId, FieldDescriptor f) {
    this.srcId = srcId;
    this.dstId = dstId;
    this.f     = f;
  }

  public String toString() {
    return "<"+srcId+", "+f+", "+dstId+">";
  }

  public Integer getSrcId() {
    return srcId;
  }

  public Integer getDstId() {
    return dstId;
  }

  public FieldDescriptor getField() {
    return f;
  }

  public boolean equals(Object o) {
    if(this == o) {
      return true;
    }
    if(o == null) {
      return false;
    }
    if(!(o instanceof EdgeKey)) {
      return false;
    }

    EdgeKey ek = (EdgeKey) o;

    return 
      this.srcId.equals(ek.srcId) &&
      this.dstId.equals(ek.dstId) &&
      this.f.equals(ek.f);
  }

  public int hashCode() {
    return srcId.hashCode() ^ dstId.hashCode() ^ f.hashCode();
  }
}
