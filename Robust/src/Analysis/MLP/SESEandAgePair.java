package Analysis.MLP;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class SESEandAgePair {

  private FlatSESEEnterNode sese;
  private Integer           age;

  public SESEandAgePair( FlatSESEEnterNode sese,
			 Integer           age ) {
    this.sese = sese;
    this.age  = age;
  }

  public FlatSESEEnterNode getSESE() {
    return sese;
  }

  public Integer getAge() {
    return age;
  }

  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof SESEandAgePair) ) {
      return false;
    }

    SESEandAgePair p = (SESEandAgePair) o;

    return age.equals( p.age  ) &&
          sese.equals( p.sese );
  }

  public int hashCode() {
    return (sese.hashCode() << 2)*(age.hashCode() << 5);
  }


  public String toString() {
    return "SESE_"+
      sese.getPrettyIdentifier()+
      sese.getIdentifier()+
      "_"+
      age;
  }
}
