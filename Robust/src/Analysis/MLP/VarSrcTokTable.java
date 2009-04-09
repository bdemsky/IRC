package Analysis.MLP;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class VarSrcTokTable {
  
  // be able to grab the VariableSourceToken triples from the
  // table by the sese, by the variable, or by a key that is
  // an sese/variable pair
  private Hashtable< FlatSESEEnterNode, Set<VariableSourceToken> > sese2vst;
  private Hashtable< TempDescriptor,    Set<VariableSourceToken> >  var2vst;
  private Hashtable< SVKey,             Set<VariableSourceToken> >   sv2vst;

  public VarSrcTokTable() {
    sese2vst = new Hashtable< FlatSESEEnterNode, Set<VariableSourceToken> >();
    var2vst  = new Hashtable< TempDescriptor,    Set<VariableSourceToken> >();
    sv2vst   = new Hashtable< SVKey,             Set<VariableSourceToken> >();
  }


  public void add( VariableSourceToken vst ) {

  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof VariableSourceToken) ) {
      return false;
    }

    VariableSourceToken vst = (VariableSourceToken) o;

    return var.equals( vst.var ) &&
           age.equals( vst.age );
  }

  public int hashCode() {
    return (var.hashCode() << 2) ^ age.intValue();
  }


  public String toString() {
    return "["+var+", "+age+"]";
  }
}
