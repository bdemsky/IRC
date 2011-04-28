package Analysis.Disjoint;

import java.util.*;

import IR.*;
import IR.Flat.*;

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

  // for debugging purposes, keep the compilation
  // unit and line number of this effect--only if state
  // is non-null later
  protected int             lineNumber;
  protected ClassDescriptor compilationUnit;
  protected static Hashtable<FlatNode, ClassDescriptor> fn2cd =
    new Hashtable<FlatNode, ClassDescriptor>();


  public Effect(Alloc affectedAS, int type, FieldDescriptor field, FlatNode currentProgramPoint) {
    this.affectedAllocSite = affectedAS;
    this.type = type;
    this.field = field;


    // NOTE: this line number+compilation unit is collected for debugging,
    // so we don't want to spend time on this unless OOODEBUG or some new
    // option controls it.  Disjoint and Pointer analysis use this currently.
    lineNumber      = -1;
    compilationUnit = null;

    // find the class the current program point belongs to
    if( currentProgramPoint == null ) {
      return;
    }
    Set<FlatNode> visited = new HashSet<FlatNode>();
    Set<FlatNode> toVisit = new HashSet<FlatNode>();
    toVisit.add( currentProgramPoint );
    
    while( !toVisit.isEmpty() ) {
      FlatNode fn = toVisit.iterator().next();
      toVisit.remove( fn );
      visited.add( fn );

      // when we find a flat method, remember every node we visited
      // belongs to that compilation unit
      if( fn instanceof FlatMethod ) {
        MethodDescriptor md = ((FlatMethod)fn).getMethod();
        if( md != null ) {
          ClassDescriptor cd = md.getClassDesc();
          if( cd != null ) {
            fn2cd.put( fn, cd );
          }
        }
      }

      if( fn2cd.containsKey( fn ) ) {
        compilationUnit = fn2cd.get( fn );

        for( FlatNode fnKnown: visited ) {
          fn2cd.put( fnKnown, compilationUnit );
        }
        
        lineNumber = currentProgramPoint.getNumLine();
        break;
      }

      for( int i = 0; i < fn.numPrev(); ++i ) {
        FlatNode prev = fn.getPrev( i );
        if( !visited.contains( prev ) ) {
          toVisit.add( prev );
        }
      }
    }
  }

  public static boolean isWrite(int effect) {
    return (effect & Effect.write)==Effect.write;
  }

  public boolean isWrite() {
    return type==write;
  }

  public boolean isRead() {
    return type==read;
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

    if( compilationUnit != null ) {
      s += ", "+compilationUnit.getSymbol()+":"+lineNumber;
    }
    
    return s + ")";
  }

}
