package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class ReachabilitySet extends Canonical {

    private HashSet<TokenTupleSet> possibleReachabilities;

    public ReachabilitySet() {
	possibleReachabilities = new HashSet<TokenTupleSet>();
	//TokenTupleSet ttsEmpty = new TokenTupleSet().makeCanonical();
	//possibleReachabilities.add( ttsEmpty );	
    }

    public ReachabilitySet( TokenTupleSet tts ) {
	this();
	assert tts != null;
	possibleReachabilities.add( tts );
    }

    public ReachabilitySet( TokenTuple tt ) {
	this( new TokenTupleSet( tt ).makeCanonical() );
    }

    public ReachabilitySet( HashSet<TokenTupleSet> possibleReachabilities ) {
	this.possibleReachabilities = possibleReachabilities;
    }

    public ReachabilitySet( ReachabilitySet rs ) {
	assert rs != null;
	possibleReachabilities = (HashSet<TokenTupleSet>) rs.possibleReachabilities.clone(); // again, DEEP COPY?!
    }

    public ReachabilitySet makeCanonical() {
	return (ReachabilitySet) Canonical.makeCanonical( this );
    }

    public boolean contains( TokenTupleSet tts ) {
	assert tts != null;
	return possibleReachabilities.contains( tts );
    }

    public ReachabilitySet add( TokenTupleSet tts ) {
	ReachabilitySet rsOut = new ReachabilitySet( tts );
	return this.union( rsOut );
    }

    public ReachabilitySet increaseArity( Integer token ) {
	assert token != null;

	HashSet<TokenTupleSet> possibleReachabilitiesNew = new HashSet<TokenTupleSet>();

	Iterator itr = iterator();
	while( itr.hasNext() ) {
	    TokenTupleSet tts = (TokenTupleSet) itr.next();
	    possibleReachabilitiesNew.add( tts.increaseArity( token ) );
	}

	return new ReachabilitySet( possibleReachabilitiesNew ).makeCanonical(); 
    }

    public Iterator iterator() {
	return possibleReachabilities.iterator();
    }

    public ReachabilitySet union( ReachabilitySet rsIn ) {
	assert rsIn != null;

	ReachabilitySet rsOut = new ReachabilitySet( this );
	rsOut.possibleReachabilities.addAll( rsIn.possibleReachabilities );
	return rsOut.makeCanonical();
    }

    public ReachabilitySet union( TokenTupleSet ttsIn ) {
	assert ttsIn != null;

	ReachabilitySet rsOut = new ReachabilitySet( this );
	rsOut.possibleReachabilities.add( ttsIn );
	return rsOut.makeCanonical();
    }

    public ReachabilitySet intersection( ReachabilitySet rsIn ) {
	assert rsIn != null;

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
    
    /*
    public ReachabilitySet unionUpArity( ReachabilitySet rsIn ) {
	assert rsIn != null;

	ReachabilitySet rsOut = new ReachabilitySet();
	Iterator itrIn;
	Iterator itrThis;	

	itrIn = rsIn.iterator();
	while( itrIn.hasNext() ) {
	    TokenTupleSet ttsIn = (TokenTupleSet) itrIn.next();

	    boolean foundEqual = false;

	    itrThis = this.iterator();
	    while( itrThis.hasNext() ) {
		TokenTupleSet ttsThis = (TokenTupleSet) itrThis.next();

		if( ttsIn.equalWithoutArity( ttsThis ) ) {
		    rsOut.possibleReachabilities.add( ttsIn.unionUpArity( ttsThis ) );
		    foundEqual = true;
		    continue;
		}
	    }

	    if( !foundEqual ) {
		rsOut.possibleReachabilities.add( ttsIn );
	    }
	}

	itrThis = this.iterator();
	while( itrThis.hasNext() ) {
	    TokenTupleSet ttsThis = (TokenTupleSet) itrThis.next();

	    boolean foundEqual = false;

	    itrIn = rsIn.iterator();
	    while( itrIn.hasNext() ) {
		TokenTupleSet ttsIn = (TokenTupleSet) itrIn.next();

		if( ttsThis.equalWithoutArity( ttsIn ) ) {
		    foundEqual = true;
		    continue;
		}
	    }

	    if( !foundEqual ) {
		rsOut.possibleReachabilities.add( ttsThis );
	    }
	}

	return rsOut.makeCanonical();
    }  
    */

    public ChangeTupleSet unionUpArityToChangeSet( ReachabilitySet rsIn ) {
	assert rsIn != null;

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


    public ReachabilitySet ageTokens( AllocationSite as ) {
	ReachabilitySet rsOut = new ReachabilitySet();

	Iterator itrS = this.iterator();
	while( itrS.hasNext() ) {
	    TokenTupleSet tts = (TokenTupleSet) itrS.next();
	    rsOut.possibleReachabilities.add( tts.ageTokens( as ) );
	}

	return rsOut.makeCanonical();
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
	    s += i.next();
	    if( i.hasNext() ) {
		s += "\\n";
	    }
	}

	s += "]";
	return s;	
    }

    public String toString() {
	String s = "[";

	Iterator i = this.iterator();
	while( i.hasNext() ) {
	    s += i.next();
	    if( i.hasNext() ) {
		s += "\n";
	    }
	}

	s += "]";
	return s;	
    }
}
