package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class TokenTupleSet {

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

    public Iterator iterator() {
	return tokenTuples.iterator();
    }

    public TokenTupleSet union( TokenTupleSet ttsIn ) {
	TokenTupleSet ttsOut = new TokenTupleSet( this );
	ttsOut.tokenTuples.addAll( ttsIn.tokenTuples );
	return ttsOut;
    }

    public boolean isEmpty() {
	return tokenTuples.isEmpty();
    }

    public boolean containsTuple( TokenTuple tt ) {
	return tokenTuples.contains( tt );
    }

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
