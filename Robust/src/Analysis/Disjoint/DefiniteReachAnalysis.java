package Analysis.Disjoint;

import java.util.*;

import IR.*;
import IR.Flat.*;
import Util.*;


public class DefiniteReachAnalysis {
  
  // keep a state of definite reachability analysis for
  // every program point
  private Map<FlatNode, DefiniteReachState> fn2state;

  private static PointerMethod pm;

  public static void setPointerMethod( PointerMethod pm ) {
    DefiniteReachAnalysis.pm = pm;
  }

  
  public DefiniteReachAnalysis() {
    // a class-wide initialization
    DefiniteReachState.initBuilders();

    fn2state = new HashMap<FlatNode, DefiniteReachState>();
  }


  public void methodEntry( FlatNode fn, 
                           Set<TempDescriptor> parameters ) {
    DefiniteReachState state = makeIn( fn );
    state.methodEntry( parameters );
    fn2state.put( fn, state );
  }

  public void copy( FlatNode fn, 
                    TempDescriptor x,
                    TempDescriptor y ) {
    DefiniteReachState state = makeIn( fn );
    state.copy( x, y );
    fn2state.put( fn, state ); 
  }

  public void load( FlatNode fn, 
                    TempDescriptor  x,
                    TempDescriptor  y,
                    FieldDescriptor f,
                    Set<EdgeKey> edgeKeysForLoad ) {

    DefiniteReachState state = makeIn( fn );
    state.load( x, y, f, edgeKeysForLoad );
    fn2state.put( fn, state ); 
  }

  public void store( FlatNode fn, 
                     TempDescriptor  x,
                     FieldDescriptor f,
                     TempDescriptor  y,
                     Set<EdgeKey> edgeKeysRemoved,
                     Set<EdgeKey> edgeKeysAdded ) {

    DefiniteReachState state = makeIn( fn );
    state.store( x, f, y, edgeKeysRemoved, edgeKeysAdded );
    fn2state.put( fn, state ); 
  }

  public void newObject( FlatNode fn, 
                         TempDescriptor x ) {
    DefiniteReachState state = makeIn( fn );
    state.newObject( x );
    fn2state.put( fn, state ); 
  }

  public void methodCall( FlatNode fn, 
                          TempDescriptor retVal ) {
    DefiniteReachState state = makeIn( fn );
    if( retVal != null ) {
      state.methodCall( retVal );
    }
    fn2state.put( fn, state ); 
  }

  public void otherStatement( FlatNode fn ) {
    fn2state.put( fn, makeIn( fn ) ); 
  }


  public void writeState( FlatNode fn, String outputName ) {
    makeIn( fn ).writeState( outputName );
  }


  // get the current state for just after the given
  // program point
  public DefiniteReachState get( FlatNode fn ) {
    DefiniteReachState state = fn2state.get( fn );
    if( state == null ) {
      state = new DefiniteReachState();
      fn2state.put( fn, state );
    }
    return state;
  }

  // get the current state for the program point just
  // before the given program point by merging the out
  // states of the predecessor statements
  public DefiniteReachState makeIn( FlatNode fn ) {
    if( pm.numPrev( fn ) == 0 ) {
      return new DefiniteReachState();
    }

    DefiniteReachState stateIn = null;

    for( int i = 0; i < pm.numPrev( fn ); ++i ) {

      if( fn2state.containsKey( pm.getPrev( fn, i ) ) ) {
        if( stateIn == null ) {
          // duplicate the first partial result we find
          stateIn = new DefiniteReachState( get( pm.getPrev( fn, i ) ) );
        } else {
          // merge other partial results into the rest
          stateIn.merge( get( pm.getPrev( fn, i ) ) );
        }
      }
    }
      
    // at least one predecessor was analyzed before this
    assert( stateIn != null );
    return stateIn;
  }
}
