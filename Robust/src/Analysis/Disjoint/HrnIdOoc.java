package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

// a heap region node has an integer ID, but heap regions can
// also have reach tuples with the same ID but out-of-context
// so 17 and 17? mean something different in reachability states
public class HrnIdOoc {
  protected Integer id;
  protected Boolean ooc;

  public HrnIdOoc(Integer id, Boolean ooc) {
    this.id  = id;
    this.ooc = ooc;
  }

  public Integer getId() {
    return id;
  }

  public Boolean getOoc() {
    return ooc;
  }

  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof HrnIdOoc) ) {
      return false;
    }

    HrnIdOoc hio = (HrnIdOoc) o;

    return
      id.equals(hio.id)  &&
      ooc.equals(hio.ooc);
  }

  public int hashCode() {
    int hash = id.intValue();
    if( ooc.booleanValue() ) {
      hash = ~hash;
    }
    return hash;
  }

  public String toString() {
    String s = id.toString();

    if( ooc ) {
      s += "?";
    }

    return s;
  }
}
