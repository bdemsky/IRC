package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class TokenTupleSet extends Canonical {

    private HashSet<TokenTuple> tokenTuples;

    public TokenTupleSet() {
	tokenTuples = new HashSet<TokenTuple>();
    }

    public TokenTupleSet( TokenTuple tt ) {
	this();
	tokenTuples.add( tt );
    }

    public TokenTupleSet( TokenTupleSet tts ) {
	tokenTuples = (HashSet<TokenTuple>) tts.tokenTuples.clone(); //COPY?!
    }

    public TokenTupleSet makeCanonical() {
	return (TokenTupleSet) Canonical.makeCanonical( this );
    }

    public Iterator iterator() {
	return tokenTuples.iterator();
    }

    public TokenTupleSet add( TokenTuple tt ) {
	TokenTupleSet ttsOut = new TokenTupleSet( tt );
	return this.union( ttsOut );
    }

    public TokenTupleSet union( TokenTupleSet ttsIn ) {
	TokenTupleSet ttsOut = new TokenTupleSet( this );
	ttsOut.tokenTuples.addAll( ttsIn.tokenTuples );
	return ttsOut.makeCanonical();
    }

    /*
    public TokenTupleSet unionUpArity( TokenTupleSet ttsIn ) {
	TokenTupleSet ttsOut = new TokenTupleSet();
	
	Iterator itrIn = ttsIn.iterator();
	while( itrIn.hasNext() ) {
	    TokenTuple ttIn = (TokenTuple) itrIn.next();

	    if( this.containsToken( ttIn.getToken() ) ) {	
		ttsOut.tokenTuples.add( ttIn.increaseArity() );
	    } else {
		ttsOut.tokenTuples.add( ttIn );
	    }
	}

	Iterator itrThis = this.iterator();
	while( itrThis.hasNext() ) {
	    TokenTuple ttThis = (TokenTuple) itrThis.next();

	    if( !ttsIn.containsToken( ttThis.getToken() ) ) {
		ttsOut.tokenTuples.add( ttThis );
	    }
	}
	
	return ttsOut.makeCanonical();
    }
    */

    public boolean isEmpty() {
	return tokenTuples.isEmpty();
    }

    public boolean containsTuple( TokenTuple tt ) {
	return tokenTuples.contains( tt );
    }

    // only needs to be done if newSummary is true?  RIGHT?
    public TokenTupleSet increaseArity( Integer token ) {
	TokenTuple tt 
	    = new TokenTuple( token, true, TokenTuple.ARITY_ONE ).makeCanonical();
	if( tokenTuples.contains( tt ) ) {
	    tokenTuples.remove( tt );
	    tokenTuples.add( 
              new TokenTuple( token, true, TokenTuple.ARITY_MANY ).makeCanonical()
			     );
	}
	
	return makeCanonical();
    }

    public boolean equals( Object o ) {
	if( !(o instanceof TokenTupleSet) ) {
	    return false;
	}

	TokenTupleSet tts = (TokenTupleSet) o;
	return tokenTuples.equals( tts.tokenTuples );
    }

    public int hashCode() {
	return tokenTuples.hashCode();
    }

    /*
    public boolean equalWithoutArity( TokenTupleSet ttsIn ) {
	Iterator itrIn = ttsIn.iterator();
	while( itrIn.hasNext() ) {
	    TokenTuple ttIn = (TokenTuple) itrIn.next();

	    if( !this.containsToken( ttIn.getToken() ) )
	    {
		return false;
	    }
	}

	Iterator itrThis = this.iterator();
	while( itrThis.hasNext() ) {
	    TokenTuple ttThis = (TokenTuple) itrThis.next();

	    if( !ttsIn.containsToken( ttThis.getToken() ) )
	    {
		return false;
	    }
	}
	
	return true;
    }
    */

    // this should be a hash table so we can do this by key
    public boolean containsToken( Integer token ) {
	Iterator itr = tokenTuples.iterator();
	while( itr.hasNext() ) {
	    TokenTuple tt = (TokenTuple) itr.next();
	    if( token.equals( tt.getToken() ) ) {
		return true;
	    }
	}
	return false;
    }

    public String toString() {
	return tokenTuples.toString();
    }
}
