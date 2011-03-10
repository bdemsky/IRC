package Analysis.Disjoint;

import java.util.*;
import java.io.*;

import IR.*;
import IR.Flat.*;

//////////////////////////////////////////////
//
//  StateMachineForEffects describes an intial
//  state and the effect transtions a DFJ
//  traverser should make from the current state
//  when searching for possible runtime conflicts.
//
//////////////////////////////////////////////

public class StateMachineForEffects {

  // states in the machine are uniquely identified 
  // by a flat node (program point)
  protected Hashtable<FlatNode, SMFEState> fn2state;

  protected SMFEState initialState;


  public StateMachineForEffects( FlatNode fnInitial ) {
    fn2state = new Hashtable<FlatNode, SMFEState>();

    initialState = getState( fnInitial );
  }

  public void addEffect( FlatNode fnState,
                         Effect e ) {

    SMFEState state = getState( fnState );
    state.addEffect( e );
  }

  public void addTransition( FlatNode fnFrom,
                             FlatNode fnTo,
                             Effect e ) {

    assert fn2state.containsKey( fnFrom );
    SMFEState stateFrom = getState( fnFrom );
    SMFEState stateTo   = getState( fnTo );

    stateFrom.addTransition( e, stateTo );
  }

  public SMFEState getIntialState() {
    return initialState;
  }


  protected SMFEState getState( FlatNode fn ) {
    SMFEState state = fn2state.get( fn );
    if( state == null ) {
      state = new SMFEState( fn );
      fn2state.put( fn, state );
    }
    return state;
  }


  public void writeAsDOT( String graphName ) {
    //String graphName = initialState.getID().toString();
    graphName = graphName.replaceAll( "[\\W]", "" );

    try {
      BufferedWriter bw = 
        new BufferedWriter( new FileWriter( graphName+".dot" ) );

      bw.write( "digraph "+graphName+" {\n" );

      Iterator<FlatNode> fnItr = fn2state.keySet().iterator();
      while( fnItr.hasNext() ) {
        SMFEState state = fn2state.get( fnItr.next() );
        bw.write( state.toStringDOT()+"\n" );
      }

      bw.write( "}\n" );
      bw.close();
      
    } catch( IOException e ) {
      throw new Error( "Error writing out DOT graph "+graphName );
    }
  }

}
