package Analysis.MLP;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class VariableSourceToken {

  private FlatSESEEnterNode sese;
  private TempDescriptor    var;
  private Integer           age;

  public VariableSourceToken( FlatSESEEnterNode sese,
			      TempDescriptor    var, 
			      Integer           age ) {
    this.sese = sese;
    this.var  = var;
    this.age  = age;
  }

  public FlatSESEEnterNode getSESE() {
    return sese;
  }

  public TempDescriptor getVar() {
    return var;
  }

  public Integer getAge() {
    return age;
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof VariableSourceToken) ) {
      return false;
    }

    VariableSourceToken vst = (VariableSourceToken) o;

    return sese.equals( vst.sese ) &&
            var.equals( vst.var  ) &&
            age.equals( vst.age  );
  }

  public int hashCode() {
    return (sese.hashCode() << 3) * (var.hashCode() << 2) ^ age.intValue();
  }


  public String toString() {
    return "["+sese+", "+var+", "+age+"]";
  }
}
