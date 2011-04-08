package Analysis.Disjoint;

import java.util.*;
import java.io.*;

import IR.*;
import IR.Flat.*;

//////////////////////////////////////////////
//
//  SMFEState is part of a 
//  (S)tate (M)achine (F)or (E)ffects.
//
//  StateMachineForEffects describes an intial
//  state and the effect transtions a DFJ
//  traverser should make from the current state
//  when searching for possible runtime conflicts.
//
//////////////////////////////////////////////

public class SMFEState {

  //  #####################
  //  ## NOTE NOTE NOTE!!!!
  //  #####################
  //  When every state corresponds to exactly one
  //  FlatNode (whereDefined attribute) then we can
  //  use the FlatNode's id as an ID.  BUT BUT BUT, if
  //  we merge nodes together in the future for
  //  optimizations and whatnot, we need an alternate
  //  system of unique IDs

  // uniquely identifies this state  
  protected int id;
  protected int iHashCode;

  // all possible effects in this state
  protected Set<Effect> effects;
  
  //TODO Jim! get me the list of conflicts!
  protected Set<Effect> conflicts;

  // the given effect allows a transition to a
  // set of new states
  protected Hashtable< Effect, Set<SMFEState> > e2states;

  // useful for knowing when a state can be inlined during
  // code gen
  protected int refCount;

  protected FlatNode whereDefined;
  
  public SMFEState( FlatNode fnWhereDefined, int id ) {
    this.id         = id;
    this.iHashCode  = fnWhereDefined.hashCode();
    this.whereDefined=fnWhereDefined;
    
    effects         = new HashSet<Effect>();
    conflicts       = new HashSet<Effect>();
    e2states        = new Hashtable< Effect, Set<SMFEState> >();
    refCount        = 0;
  }

  public void addEffect( Effect e ) {
    effects.add( e );
  }

  // the given effect allows the transition to the new state
  public void addTransition( Effect    effect,
                             SMFEState stateTo
                             ) {

    Set<SMFEState> states = e2states.get( effect );
    if( states == null ) {
      states = new HashSet<SMFEState>();
      e2states.put( effect, states );
    }
    if (!states.contains(stateTo)) {
      states.add( stateTo );
      stateTo.refCount++;
    }
  }


  public int getID() {
    return id;
  }

  // once you get your hands on an SMFEState in the
  // RuntimeConflictResolver side of things, this is how you
  // find out what effects are possible in this state
  public Set<Effect> getEffectsAllowed() {
    return effects;
  }
  
  public void addConflict(Effect e) {
    conflicts.add(e);
  }

  public Set<Effect> getConflicts() {
    return conflicts;
  }
  
  public Set<Effect> getTransitionEffects() {
    return this.e2states.keySet();
  }

  // some subset of the above effects may transition to
  // other states
  public Set<SMFEState> transitionsTo( Effect e ) {
    Set<SMFEState> statesOut = e2states.get( e );
    if( statesOut == null ) {
      statesOut = new HashSet<SMFEState>();
    }
    return statesOut;
  }

  // some subset of the above effects may transition to
  // other states
  public Set<SMFEState> transitionsTo() {
    Set<SMFEState> statesOut = new HashSet<SMFEState>();
    for(Map.Entry<Effect, Set<SMFEState>> entry:e2states.entrySet()) {
      statesOut.addAll(entry.getValue());
    }
    return statesOut;
  }

  public int getRefCount() {
    return refCount;
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof SMFEState) ) {
      return false;
    }

    SMFEState state = (SMFEState) o;

    return id == state.id;
  }

  public int hashCode() {
    return iHashCode;
  }


  public String toStringDOT() {
    
    // first create the state as a node in DOT graph
    String s = "  "+id+"[shape=box,";
    if (conflicts.size()>0 ) {
      s+="peripheries=2,";
    }
    s+="label=\"";

    if( effects.size() >= 1 ) {

      Iterator<Effect> eItr = effects.iterator();
      while( eItr.hasNext() ) {
        Effect e = eItr.next();
	if (conflicts.contains(e)) {
	  s += "["+e.toString()+"]";
	} else {
	  s += e.toString();
	}
        if( eItr.hasNext() ) {
          s += "\\n";
        }
      }
    }

    s += "\"];";

    // then each transition is an edge
    Iterator<Effect> eItr = e2states.keySet().iterator();
    while( eItr.hasNext() ) {
      Effect         e      = eItr.next();
      Set<SMFEState> states = e2states.get( e );

      Iterator<SMFEState> sItr = states.iterator();
      while( sItr.hasNext() ) {
        SMFEState state = sItr.next();

        s += "\n  "+
          id+" -> "+state.id+
          "[label=\""+e+", RC="+refCount+"\"";
	if (conflicts.contains(e))
	  s+=",style=dashed";
	s+="];";
      }
    }

    return s;
  }

}
