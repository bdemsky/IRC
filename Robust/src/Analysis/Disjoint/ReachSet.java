package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

///////////////////////////////////////////
//  IMPORTANT
//  This class is an immutable Canonical, so
//
//  0) construct them with a factory pattern
//  to ensure only canonical versions escape
//
//  1) any operation that modifies a Canonical
//  is a static method in the Canonical class
//
//  2) operations that just read this object
//  should be defined here
//
//  3) every Canonical subclass hashCode should
//  throw an error if the hash ever changes
//
///////////////////////////////////////////

// a reach set is a set of reach states

public class ReachSet extends Canonical {

  protected HashSet<ReachState> reachStates;


  public static ReachSet factory() {
    ReachSet out = new ReachSet();
    out = (ReachSet) Canonical.makeCanonical( out );
    return out;
  }

  public static ReachSet factory( ReachState state ) {
    assert state != null;
    assert state.isCanonical();
    ReachSet out = new ReachSet();
    out.reachStates.add( state );
    out = (ReachSet) Canonical.makeCanonical( out );
    return out;
  }

  protected ReachSet() {
    reachStates = new HashSet<ReachState>();
  }


  public Iterator<ReachState> iterator() {
    return reachStates.iterator();
  }

  public int size() {
    return reachStates.size();
  }

  public boolean isEmpty() {
    return reachStates.isEmpty();
  }

  // this should be a hash table so we can do this by key
  public ReachState containsIgnorePreds( ReachState state ) {
    assert state != null;

    Iterator<ReachState> stateItr = this.reachStates.iterator();
    while( stateItr.hasNext() ) {
      ReachState stateThis = stateItr.next();
      if( stateThis.equalsIgnorePreds( state ) ) {
	return stateThis;
      }
    }
    
    return null;
  }

  /*
  public boolean containsWithZeroes( ReachState state ) {
    assert state != null;

    if( reachStates.contains( state ) ) {
      return true;
    }

    Iterator<ReachState> itr = iterator();
    while( itr.hasNext() ) {
      ReachState stateThis = itr.next();
      if( stateThis.containsWithZeroes( state ) ) {
	return true;
      }
    }
    
    return false;    
  }
  */

  public boolean containsSuperSet( ReachState state ) {
    return containsSuperSet( state, false );
  }

  public boolean containsStrictSuperSet( ReachState state ) {
    return containsSuperSet( state, true );
  }

  public boolean containsSuperSet( ReachState state,
                                   boolean    strict ) {
    assert state != null;

    if( !strict && reachStates.contains( state ) ) {
      return true;
    }

    Iterator<ReachState> itr = iterator();
    while( itr.hasNext() ) {
      ReachState stateThis = itr.next();
      if( strict ) {
        if( !state.equals( stateThis ) &&
            state.isSubset( stateThis ) ) {
          return true;
        }
      } else {
        if( state.isSubset( stateThis ) ) {
          return true;
        }
      }
    }
    
    return false;    
  }


  public boolean containsTuple( ReachTuple rt ) {
    Iterator<ReachState> itr = iterator();
    while( itr.hasNext() ) {
      ReachState state = itr.next();
      if( state.containsTuple( rt ) ) {
	return true;
      }
    }
    return false;
  }

  public ReachSet getStatesWithBoth( ReachTuple rt1, 
                                     ReachTuple rt2 ) {

    ReachSet out = new ReachSet();

    Iterator<ReachState> itr = iterator();
    while( itr.hasNext() ) {
      ReachState state = itr.next();
      if( state.containsTuple( rt1 ) &&
          state.containsTuple( rt2 ) ) {
        out.reachStates.add( state );
      }
    }

    out = (ReachSet) Canonical.makeCanonical( out );
    return out;
  }

  // used to assert each state in the set is
  // unique
  public boolean containsNoDuplicates() {
    Vector<ReachState> v = new Vector( reachStates );
    for( int i = 0; i < v.size(); ++i ) {
      ReachState s1 = v.get( i );
      for( int j = i+1; j < v.size(); ++j ) {
        ReachState s2 = v.get( j );
        if( s1.equals( s2 ) ) {
          assert s1.isCanonical();
          assert s2.isCanonical();
          return false;
        }
      }
    }
    return true;
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }
    
    if( !(o instanceof ReachSet) ) {
      return false;
    }

    ReachSet rs = (ReachSet) o;
    return reachStates.equals( rs.reachStates );
  }


  public int hashCodeSpecific() {
    return reachStates.hashCode();
  }


  public String toStringEscNewline( boolean hideSubsetReachability ) {
    String s = "[";

    Iterator<ReachState> i = this.iterator();
    while( i.hasNext() ) {
      ReachState state = i.next();

      // skip this if there is a superset already
      if( hideSubsetReachability &&
          containsStrictSuperSet( state ) ) {
        continue;
      }

      s += state;
      if( i.hasNext() ) {
	s += "\\n";
      }
    }

    s += "]";
    return s;
  }
  

  public String toString() {
    return toString( false );
  }

  public String toString( boolean hideSubsetReachability ) {
    String s = "[";

    Iterator<ReachState> i = this.iterator();
    while( i.hasNext() ) {
      ReachState state = i.next();

      // skip this if there is a superset already
      if( hideSubsetReachability &&
          containsStrictSuperSet( state ) ) {
        continue;
      }

      s += state;
      if( i.hasNext() ) {
	s += "\n";
      }
    }

    s += "]";
    return s;
  }
}
