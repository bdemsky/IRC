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

  // uniquely identifies this state
  protected FlatNode id;

  // all possible effects in this state
  protected Set<Effect> effects;

  // the given effect allows a transition to a
  // set of new states
  protected Hashtable< Effect, Set<SMFEState> > e2states;

  
  public SMFEState( FlatNode id ) {
    this.id = id;
    effects  = new HashSet<Effect>();
    e2states = new Hashtable< Effect, Set<SMFEState> >();
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
  }

  public FlatNode getID() {
    return id;
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof SMFEState) ) {
      return false;
    }

    SMFEState state = (SMFEState) o;

    return id.equals( state.id );
  }

  public int hashCode() {
    return id.hashCode();
  }


  public String toStringDOT() {
    
    // first create the state as a node in DOT graph
    String s = "  "+id.nodeid+
      "[shape=box,label=\"";

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
          id.nodeid+" -> "+state.id.nodeid+
          "[label=\""+e+"\"];";
      }
    }

    return s;
  }
}
