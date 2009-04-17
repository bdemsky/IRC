package Analysis.MLP;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class VariableSourceToken {

  private TempDescriptor    varLive;
  private FlatSESEEnterNode seseSrc;
  private Integer           seseAge;
  private TempDescriptor    varSrc; 

  public VariableSourceToken( TempDescriptor    varLive, 
                              FlatSESEEnterNode seseSrc,			      
			      Integer           seseAge, 
                              TempDescriptor    varSrc 
                              ) {
    this.varLive = varLive;
    this.seseSrc = seseSrc;
    this.seseAge = seseAge;
    this.varSrc  = varSrc; 
  }

  public TempDescriptor getVarLive() {
    return varLive;
  }

  public FlatSESEEnterNode getSESE() {
    return seseSrc;
  }

  public Integer getAge() {
    return seseAge;
  }

  public TempDescriptor getVarSrc() {
    return varSrc;
  }

  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof VariableSourceToken) ) {
      return false;
    }

    VariableSourceToken vst = (VariableSourceToken) o;

    return seseSrc.equals( vst.seseSrc ) &&
            varSrc.equals( vst.varSrc  ) &&
           seseAge.equals( vst.seseAge ) &&
           varLive.equals( vst.varLive );
  }

  public int hashCode() {
    return (seseSrc.hashCode() << 3) + (varSrc.hashCode() << 4) * (varLive.hashCode() << 2) ^ seseAge.intValue();
  }


  public String toString() {
    return "["+varLive+" -> "+seseSrc.getPrettyIdentifier()+", "+seseAge+", "+varSrc+"]";
  }
}
