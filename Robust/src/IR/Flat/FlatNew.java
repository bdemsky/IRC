package IR.Flat;
import IR.TypeDescriptor;

public class FlatNew extends FlatNode {
  TempDescriptor dst;
  TypeDescriptor type;
  TempDescriptor size;
  boolean isglobal;
  boolean isdisjoint;

  public FlatNew(TypeDescriptor type, TempDescriptor dst, boolean isglobal) {
    this.type=type;
    this.dst=dst;
    this.size=null;
    this.isglobal=isglobal;
    this.isdisjoint=false;
  }

  public FlatNew(TypeDescriptor type, TempDescriptor dst, boolean isglobal, boolean isdisjoint) {
    this.type=type;
    this.dst=dst;
    this.size=null;
    this.isglobal=isglobal;
    this.isdisjoint=isdisjoint;
  }

  public FlatNew(TypeDescriptor type, TempDescriptor dst, TempDescriptor size, boolean isglobal) {
    this.type=type;
    this.dst=dst;
    this.size=size;
    this.isglobal=isglobal;
    this.isdisjoint=false;
  }

  public FlatNew(TypeDescriptor type, TempDescriptor dst, TempDescriptor size, boolean isglobal, boolean isdisjoint) {
    this.type=type;
    this.dst=dst;
    this.size=size;
    this.isglobal=isglobal;
    this.isdisjoint=isdisjoint;
  }

  public boolean isGlobal() {
    return isglobal;
  }

  public boolean isDisjoint() {
    return isdisjoint;
  }

  public String toString() {
    String str = "FlatNew_"+dst.toString()+"= NEW "+type.toString();
    if (size!=null)
      str += "["+size.toString()+"]";
    return str;
  }

  public int kind() {
    return FKind.FlatNew;
  }

  public TempDescriptor [] writesTemps() {
    return new TempDescriptor[] {dst};
  }

  public TempDescriptor [] readsTemps() {
    if (size!=null)
      return new TempDescriptor[] {size};
    else
      return new TempDescriptor[0];
  }

  public TempDescriptor getDst() {
    return dst;
  }

  public TempDescriptor getSize() {
    return size;
  }

  public TypeDescriptor getType() {
    return type;
  }
}
