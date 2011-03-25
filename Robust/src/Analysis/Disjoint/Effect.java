package Analysis.Disjoint;

import IR.FieldDescriptor;
import IR.Flat.TempDescriptor;

public class Effect {

  // operation type
  public static final int read = 1;
  public static final int write = 2;
  public static final int strongupdate = 4;

  // identify an allocation site of affected object
  protected Alloc affectedAllocSite;

  // identify operation type
  protected int type;

  // identify a field
  protected FieldDescriptor field;

  public Effect(Alloc affectedAS, int type, FieldDescriptor field) {
    this.affectedAllocSite = affectedAS;
    this.type = type;
    this.field = field;
  }

  public static boolean isWrite(int effect) {
    return (effect & Effect.write)==Effect.write;
  }

  public Alloc getAffectedAllocSite() {
    return affectedAllocSite;
  }

  public void setAffectedAllocSite(Alloc affectedAllocSite) {
    this.affectedAllocSite = affectedAllocSite;
  }

  public int getType() {
    return type;
  }

  public void setType(int type) {
    this.type = type;
  }

  public FieldDescriptor getField() {
    return field;
  }

  public void setField(FieldDescriptor field) {
    this.field = field;
  }

  public boolean equals(Object o) {

    if (o == null) {
      return false;
    }

    if (!(o instanceof Effect)) {
      return false;
    }

    Effect in = (Effect) o;
    
    if (affectedAllocSite.equals(in.getAffectedAllocSite()) 
        && type == in.getType() 
        && ((field!=null&&field.equals(in.getField()))||
	    (field==null&&in.getField()==null))) {
      return true;
    } else {
      return false;
    }
  }

  public int hashCode() {

    int hash = affectedAllocSite.hashCode();

    hash = hash + type;

    if (field != null) {
      hash = hash ^ field.hashCode();
    }

    return hash;

  }

  public String toString() {
    String s = "(";

    s += affectedAllocSite.toStringBrief();
    s += ", ";
    if (type == read) {
      s += "read";
    } else if (type == write) {
      s += "write";
    } else {
      s += "SU";
    }

    if (field==null) {
      s += ", []";
    } else {
      s += ", " + field.toStringBrief();
    }
    return s + ")";
  }

}
