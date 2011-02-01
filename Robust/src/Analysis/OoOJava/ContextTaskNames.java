package Analysis.OoOJava;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

// The purpose of a ContextTaskNames is to collect the static and
// dynamic names for tasks that the context needs to track information
// in that context.
//
// For example, a method might have two tasks defined in different
// control branches, either of which may provide a value to a third
// task in the context.  In this case a dynamic task name should be
// saved here so that when the code for the method is generated, a
// local variable that points to a task will be declared.  Other flat
// nodes will have CodePlan objects that will expect the local task
// variable name to exist.
//
// There are two types of contexts: methods and task bodies.  Here we
// don't see the difference because the names of the local variables
// generated from these task names should be the same.


public class ContextTaskNames {

  protected Set<SESEandAgePair> needStaticNameInCode;
  protected Set<TempDescriptor> dynamicVars;


  public ContextTaskNames() {
    needStaticNameInCode = new HashSet<SESEandAgePair>();
    dynamicVars          = new HashSet<TempDescriptor>();
  }


  public void addNeededStaticName( SESEandAgePair p ) {
    needStaticNameInCode.add( p );
  }

  public Set<SESEandAgePair> getNeededStaticNames() {
    return needStaticNameInCode;
  }

  public void addDynamicVar( TempDescriptor td ) {
    dynamicVars.add( td );
  }

  public Set<TempDescriptor> getDynamicVarSet() {
    return dynamicVars;
  }

}
