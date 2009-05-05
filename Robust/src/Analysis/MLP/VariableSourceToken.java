package Analysis.MLP;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class VariableSourceToken {

  private Set<TempDescriptor> refVars;
  private FlatSESEEnterNode   sese;
  private Integer             seseAge;
  private TempDescriptor      addrVar; 

  public VariableSourceToken( Set<TempDescriptor> refVars, 
                              FlatSESEEnterNode   sese,			      
			      Integer             seseAge, 
                              TempDescriptor      addrVar 
                              ) {
    this.refVars = refVars;
    this.sese    = sese;
    this.seseAge = seseAge;
    this.addrVar = addrVar; 
  }

  public Set<TempDescriptor> getRefVars() {
    return refVars;
  }

  public FlatSESEEnterNode getSESE() {
    return sese;
  }

  public Integer getAge() {
    return seseAge;
  }

  public TempDescriptor getAddrVar() {
    return addrVar;
  }

  public VariableSourceToken copy() {
    Set<TempDescriptor> refVarsCopy = new HashSet<TempDescriptor>();

    Iterator<TempDescriptor> rvItr = refVars.iterator();
    while( rvItr.hasNext() ) {
      refVarsCopy.add( rvItr.next() );
    }

    return new VariableSourceToken( refVarsCopy,
                                    sese,
                                    new Integer( seseAge ),
                                    addrVar );
  }

  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof VariableSourceToken) ) {
      return false;
    }

    VariableSourceToken vst = (VariableSourceToken) o;

    // the reference vars have no bearing on equality
    return    sese.equals( vst.sese    ) &&
           addrVar.equals( vst.addrVar ) &&
           seseAge.equals( vst.seseAge );
  }

  public int hashCode() {
    // the reference vars have no bearing on hashCode
    return (sese.hashCode() << 3) * (addrVar.hashCode() << 4) ^ seseAge.intValue();
  }


  public String toString() {
    return refVars+" ref "+addrVar+"@"+sese.getPrettyIdentifier()+"("+seseAge+")";
  }
}
