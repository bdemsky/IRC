package Analysis.Disjoint;

import java.util.*;
import java.io.*;

import IR.*;
import IR.Flat.*;
import Analysis.OoOJava.*;
import Util.*;


//////////////////////////////////////////////
//
//  BuildStateMachines builds a state machine
//  for every task/stall site and variable pair
//
//  StateMachineForEffects describes an intial
//  state and the effect transtions a DFJ
//  traverser should make from the current state
//  when searching for possible runtime conflicts.
//
//////////////////////////////////////////////

public class BuildStateMachines {

  // map a task or stall site (both a FlatNode) to a variable
  // and then finally to a state machine
  protected Hashtable< FlatNode, Hashtable<TempDescriptor, StateMachineForEffects>> fn2var2smfe;

  // remember all the FlatNode/TempDescriptor pairs that have a state machines
  // for easy retrieval of all machines
  protected Set<Pair<FlatNode, TempDescriptor>> allMachineNamePairs;


  public BuildStateMachines() {
    fn2var2smfe = new Hashtable< FlatNode, Hashtable<TempDescriptor, StateMachineForEffects> >();
    allMachineNamePairs = new HashSet<Pair<FlatNode, TempDescriptor>>();
  }

  public StateMachineForEffects getStateMachine(Pair<FlatNode, TempDescriptor> fnpair) {
    return getStateMachine(fnpair.getFirst(), fnpair.getSecond());
  }

  public StateMachineForEffects getStateMachine(FlatNode fn, TempDescriptor var) {
    Hashtable<TempDescriptor, StateMachineForEffects> var2smfe = fn2var2smfe.get(fn);
    if( var2smfe == null ) {
      var2smfe = new Hashtable<TempDescriptor, StateMachineForEffects>();
      fn2var2smfe.put(fn, var2smfe);
    }

    StateMachineForEffects smfe = var2smfe.get(var);
    if( smfe == null ) {
      smfe = new StateMachineForEffects(fn);
      var2smfe.put(var, smfe);
      allMachineNamePairs.add(new Pair<FlatNode, TempDescriptor>(fn, var) );
    }

    return smfe;
  }


  public Set<Pair<FlatNode, TempDescriptor>> getAllMachineNames() {
    return allMachineNamePairs;
  }


  public void addToStateMachine(Taint t,
                                Effect e,
                                FlatNode currentProgramPoint) {
    FlatNode taskOrStallSite;
    if( t.isStallSiteTaint() ) {
      taskOrStallSite = t.getStallSite();
    } else {
      taskOrStallSite = t.getSESE();
    }

    TempDescriptor var = t.getVar();

    StateMachineForEffects smfe = getStateMachine(taskOrStallSite, var);

    FlatNode whereDefined = t.getWhereDefined();

    smfe.addEffect(whereDefined, e);

    // reads of pointers make a transition
    if( e.getType() == Effect.read &&
        ((e.getField()!=null && e.getField().getType().isPtr())
         ||(e.getField()==null && e.getAffectedAllocSite().getFlatNew().getType().dereference().isPtr()))) {

      smfe.addTransition(whereDefined,
                         currentProgramPoint,
                         e);
    }
  }


  public void writeStateMachines() {
    writeStateMachines("");
  }

  public void writeStateMachines(String prefix) {

    Iterator<FlatNode> fnItr = fn2var2smfe.keySet().iterator();
    while( fnItr.hasNext() ) {
      FlatNode fn = fnItr.next();

      Hashtable<TempDescriptor, StateMachineForEffects>
      var2smfe = fn2var2smfe.get(fn);

      Iterator<TempDescriptor> varItr = var2smfe.keySet().iterator();
      while( varItr.hasNext() ) {
	TempDescriptor var = varItr.next();

	StateMachineForEffects smfe = var2smfe.get(var);

	smfe.writeAsDOT(prefix+"statemachine_"+fn.toString()+var.toString() );
      }
    }
  }
  //TODO JIM! Give me the REAALL number here.
  public int getTotalNumOfWeakGroups() {
    // TODO Auto-generated method stub
    return 1;
  }
}
