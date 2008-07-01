package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class ReachabilitySet {

    public HashSet<TokenTupleSet> possibleReachabilities;

    public ReachabilitySet() {
	possibleReachabilities = new HashSet<TokenTupleSet>();
    }

    public ReachabilitySet( ReachabilitySet rs ) {
	possibleReachabilities = (HashSet<TokenTupleSet>) rs.possibleReachabilities.clone(); // again, DEEP COPY?!
    }

    public Iterator iterator() {
	return possibleReachabilities.iterator();
    }

    public ReachabilitySet union( ReachabilitySet rsIn ) {
	ReachabilitySet rsOut = new ReachabilitySet( this );
	rsOut.possibleReachabilities.addAll( rsIn.possibleReachabilities );
	return rsOut;
    }

    public ReachabilitySet intersection( ReachabilitySet rsIn ) {
	ReachabilitySet rsOut = new ReachabilitySet();

	Iterator i = this.iterator();
	while( i.hasNext() ) {
	    TokenTupleSet tts = (TokenTupleSet) i.next();
	    if( rsIn.possibleReachabilities.contains( tts ) ) {
		rsOut.possibleReachabilities.add( tts );
	    }
	}

	return rsOut;
    }

    public ChangeTupleSet unionUpArity( ReachabilitySet rsIn ) {
	ChangeTupleSet ctsOut = new ChangeTupleSet();

	Iterator itrO = this.iterator();
	while( itrO.hasNext() ) {
	    TokenTupleSet o = (TokenTupleSet) itrO.next();

	    Iterator itrR = rsIn.iterator();
	    while( itrR.hasNext() ) {
		TokenTupleSet r = (TokenTupleSet) itrR.next();

		TokenTupleSet theUnion = new TokenTupleSet();

		Iterator itrRelement = r.iterator();
		while( itrRelement.hasNext() ) {
		    TokenTuple e = (TokenTuple) itrRelement.next();

		    if( o.contains( e ) ) {
			theUnion.union( new TokenTupleSet( e.increaseArity() ) );
		    }
		}
	    }
	}

	return ctsOut;
    }
}

/*
Set specialUnion( Set O, Set R ) {
  Set C = {}

  foreach o in O {
    foreach r in R {

      Set theUnion = {}

      foreach e in r {
        if o.contains( e ) {
          if e.isSummaryToken() { // wait, stronger condition?
            theUnion.add( e.copy().increaseArity() )
          } else {
            theUnion.add( e.copy() )
          }
        }
      }

      foreach e in o {
        if !theUnion.contains( e ) {
           theUnion.add( e.copy() )
        }
      }

      if !theUnion.isEmpty() {
        C.add( <o, theUnion> )
      }

    }
  }

  return C
}
*/
