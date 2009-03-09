package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class MethodContext {

  private Descriptor descMethodOrTask;
  private Set aliasedParameterIndices;

  public MethodContext( Descriptor d ) {
    descMethodOrTask = d;
    aliasedParameterIndices = new HashSet();
  }

  public MethodContext( Descriptor d, Set a ) {
    descMethodOrTask = d;
    aliasedParameterIndices = a;
  }
  
  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !( o instanceof MethodContext) ) {
      return false;
    }

    MethodContext mc = (MethodContext) o;

    return mc.descMethodOrTask.equals( descMethodOrTask ) &&
      mc.aliasedParameterIndices.equals( aliasedParameterIndices );
  }
  
  public int hashCode() {
    return descMethodOrTask.hashCode() ^ 
      aliasedParameterIndices.hashCode();
  }

  public Descriptor getDescriptor() {
    return descMethodOrTask;
  }

  public Set getAliasedParamIndices() {
    return aliasedParameterIndices;
  }


  private String getAliasString() {
    if( aliasedParameterIndices.isEmpty() ) {
      return "";
    }

    String s = "aliased";
    Iterator i = aliasedParameterIndices.iterator();
    while( i.hasNext() ) {
      s += i.next();
      if( i.hasNext() ) {
	s += "a";
      }
    }

    return s;
  }

  public String toString() {
    if( descMethodOrTask instanceof TaskDescriptor ) {
      return descMethodOrTask.getSymbol()+
	     descMethodOrTask.getNum()+
	     getAliasString();

    } else {
      MethodDescriptor md = (MethodDescriptor) descMethodOrTask;
      return md.getClassMethodName()+
	     md.getNum()+
	     getAliasString();
    }
  }
}
