package IR.Flat;
import IR.TypeDescriptor;

public class FlatNew extends FlatNode {
  TempDescriptor dst;
  TypeDescriptor type;
  TempDescriptor size;
  boolean isglobal;
  String disjointId;

  public FlatNew(TypeDescriptor type, TempDescriptor dst, boolean isglobal) {
    if (type==null)
      throw new Error();
    this.type=type;
    this.dst=dst;
    this.size=null;
    this.isglobal=isglobal;
    this.disjointId=null;
  }

  public void rewriteUse(TempMap t) {
    size=t.tempMap(size);
  }
  public void rewriteDef(TempMap t) {
    dst=t.tempMap(dst);
  }

  public FlatNode clone(TempMap t) {
    return new FlatNew(type, t.tempMap(dst), t.tempMap(size), isglobal, disjointId);
  }

  public FlatNew(TypeDescriptor type, TempDescriptor dst, boolean isglobal, String disjointId) {
    if (type==null)
      throw new Error();
    this.type=type;
    this.dst=dst;
    this.size=null;
    this.isglobal=isglobal;
    this.disjointId=disjointId;
  }

  public FlatNew(TypeDescriptor type, TempDescriptor dst, TempDescriptor size, boolean isglobal) {
    if (type==null)
      throw new Error();
    this.type=type;
    this.dst=dst;
    this.size=size;
    this.isglobal=isglobal;
    this.disjointId=null;
  }

  public FlatNew(TypeDescriptor type, TempDescriptor dst, TempDescriptor size, boolean isglobal, String disjointId) {
    if (type==null)
      throw new Error();
    this.type=type;
    this.dst=dst;
    this.size=size;
    this.isglobal=isglobal;
    this.disjointId=disjointId;
  }

  public boolean isGlobal() {
    return isglobal;
  }

  public boolean isScratch() {
    return isglobal;
  }

  public String getDisjointId() {
    return disjointId;
  }

  public String toString() {
    String str = "FlatNew_"+dst.toString()+"= NEW "+type.toString();

    int numEmptyBrackets = type.getArrayCount();
    if( size != null ) {
      --numEmptyBrackets;
    }
    for( int i = 0; i < numEmptyBrackets; ++i ) {
      str += "[]";
    }    
    if( size != null ) {
      str += "["+size.toString()+"]";
    }    

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
