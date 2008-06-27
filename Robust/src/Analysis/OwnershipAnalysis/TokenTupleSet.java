package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class TokenTupleSet {

    public HashSet<TokenTuple> tokenTuples;

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

    public TokenTupleSet union( TokenTupleSet ttsIn ) {
	TokenTupleSet ttsOut = new TokenTupleSet( this );
	ttsOut.tokenTuples.addAll( ttsIn.tokenTuples );
	/*
	Iterator i = ttsIn.tokenTuples.iterator();
	while( i.hasNext() ) {
	    ttsOut.tokenTuples.add( (TokenTuple) i.next() );
	}
	*/

	return ttsOut;
    }

    public String toString() {
	return tokenTuples.toString();
    }
}
