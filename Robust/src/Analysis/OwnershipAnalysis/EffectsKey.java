package Analysis.OwnershipAnalysis;

import IR.TypeDescriptor;

public class EffectsKey {

  private String fd;
  private TypeDescriptor td;
  private Integer hrnId;
  private String hrnUniqueId;
  private int paramIden;

  public EffectsKey(String fd, TypeDescriptor td, Integer hrnId, String hrnUniqueId, int paramIden) {
    this.fd = fd;
    this.td = td;
    this.hrnId = hrnId;
    this.hrnUniqueId=hrnUniqueId;
    this.paramIden=paramIden;
  }

  public int getParamIden() {
    return paramIden;
  }

  public String getFieldDescriptor() {
    return fd;
  }

  public TypeDescriptor getTypeDescriptor() {
    return td;
  }

  public Integer getHRNId() {
    return hrnId;
  }

  public String getHRNUniqueId() {
    return hrnUniqueId;
  }

  public String toString() {
    return "(" + td + ")" + fd + "#" + hrnId;
  }

  public int hashCode() {

    int hash = 1;

    if (fd != null) {
      hash = hash * 31 + fd.hashCode();
    }

    if (td != null) {
      hash += td.getSymbol().hashCode();
    }

    if (hrnId != null) {
      hash += hrnId.hashCode();
    }

    return hash;

  }

  public boolean equals(Object o) {

    if (o == null) {
      return false;
    }

    if (!(o instanceof EffectsKey)) {
      return false;
    }

    EffectsKey in = (EffectsKey) o;

    if (fd.equals(in.getFieldDescriptor())
        && td.getSymbol().equals(in.getTypeDescriptor().getSymbol())
        && hrnId.equals(in.getHRNId())) {
      return true;
    } else {
      return false;
    }

  }
}
