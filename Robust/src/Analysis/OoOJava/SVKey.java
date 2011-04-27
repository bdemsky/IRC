package Analysis.OoOJava;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class SVKey {

  private FlatSESEEnterNode sese;
  private TempDescriptor var;

  public SVKey(FlatSESEEnterNode sese,
               TempDescriptor var) {
    this.sese = sese;
    this.var  = var;
  }

  public FlatSESEEnterNode getSESE() {
    return sese;
  }

  public TempDescriptor getVar() {
    return var;
  }

  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof SVKey) ) {
      return false;
    }

    SVKey k = (SVKey) o;

    return var.equals(k.var) &&
           sese.equals(k.sese);
  }

  public int hashCode() {
    return (sese.hashCode() << 2)*(var.hashCode() << 5);
  }


  public String toString() {
    return "key["+sese.getPrettyIdentifier()+", "+var+"]";
  }
}
