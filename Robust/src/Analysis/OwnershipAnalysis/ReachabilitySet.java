package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class ReachabilitySet extends Canonical {

    private HashSet<TokenTupleSet> possibleReachabilities;

    public ReachabilitySet() {
	possibleReachabilities = new HashSet<TokenTupleSet>();
    }

    public ReachabilitySet( TokenTupleSet tts ) {
	possibleReachabilities = new HashSet<TokenTupleSet>();
	possibleReachabilities.add( tts );
    }

    public ReachabilitySet( TokenTuple tt ) {
	this( new TokenTupleSet( tt ) );
    }

    public ReachabilitySet( ReachabilitySet rs ) {
	possibleReachabilities = (HashSet<TokenTupleSet>) rs.possibleReachabilities.clone(); // again, DEEP COPY?!
    }

    public ReachabilitySet makeCanonical() {
	return (ReachabilitySet) Canonical.makeCanonical( this );
    }

    public Iterator iterator() {
	return possibleReachabilities.iterator();
    }

    public ReachabilitySet union( ReachabilitySet rsIn ) {
	ReachabilitySet rsOut = new ReachabilitySet( this );
	rsOut.possibleReachabilities.addAll( rsIn.possibleReachabilities );
	return rsOut.makeCanonical();
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

	return rsOut.makeCanonical();
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

		    if( o.containsToken( e.getToken() ) ) {
			theUnion = theUnion.union( new TokenTupleSet( e.increaseArity() ) ).makeCanonical();
		    } else {
			theUnion = theUnion.union( new TokenTupleSet( e                 ) ).makeCanonical();
		    }
		}

		Iterator itrOelement = o.iterator();
		while( itrOelement.hasNext() ) {
		    TokenTuple e = (TokenTuple) itrOelement.next();

		    if( !theUnion.containsToken( e.getToken() ) ) {
			theUnion = theUnion.union( new TokenTupleSet( e ) ).makeCanonical();
		    }
		}

		if( !theUnion.isEmpty() ) {
		    ctsOut = ctsOut.union( 
		      new ChangeTupleSet( new ChangeTuple( o, theUnion ) )
				          );
		}
	    }
	}

	return ctsOut.makeCanonical();
    }


    public boolean equals( Object o ) {
	if( !(o instanceof ReachabilitySet) ) {
	    return false;
	}

	ReachabilitySet rs = (ReachabilitySet) o;
	return possibleReachabilities.equals( rs.possibleReachabilities );
    }

    public int hashCode() {
	return possibleReachabilities.hashCode();
    }


    public String toStringEscapeNewline() {
	String s = "[";

	Iterator i = this.iterator();
	while( i.hasNext() ) {
	    s += "\\n  "+i.next();
	}

	s += "]";

	return s;	
    }

    public String toString() {
	String s = "[";

	Iterator i = this.iterator();
	while( i.hasNext() ) {
	    s += "\n  "+i.next();
	}

	s += "\n]";

	return s;	
    }
}
