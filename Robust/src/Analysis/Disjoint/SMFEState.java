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

  // the given effect allows a transition to a
  // set of new states
  protected Hashtable< Effect, Set<SMFEState> > e2states;

  // useful for knowing when a state can be inlined during
  // code gen
  protected int refCount;


  
  public SMFEState( FlatNode fnWhereDefined ) {

    this.id        = fnWhereDefined.nodeid;
    this.iHashCode = fnWhereDefined.hashCode();

    effects  = new HashSet<Effect>();
    e2states = new Hashtable< Effect, Set<SMFEState> >();
    refCount = 0;
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
    states.add( stateTo );

    ++stateTo.refCount;
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

  // some subset of the above effects may transition to
  // other states
  public Set<SMFEState> transitionsTo( Effect e ) {
    Set<SMFEState> statesOut = e2states.get( e );
    if( statesOut == null ) {
      statesOut = new HashSet<SMFEState>();
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
    String s = "  "+id+"[shape=box,label=\"";

    if( effects.size() == 1 ) {
      s += effects.iterator().next().toString();

    } else if( effects.size() > 1 ) {

      Iterator<Effect> eItr = effects.iterator();
      while( eItr.hasNext() ) {
        Effect e = eItr.next();
        s += e.toString();

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
          "[label=\""+e+"\"];";
      }
    }

    return s;
  }

}
