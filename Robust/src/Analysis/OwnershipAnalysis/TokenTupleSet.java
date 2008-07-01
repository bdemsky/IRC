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

    public Iterator iterator() {
	return tokenTuples.iterator();
    }

    public TokenTupleSet union( TokenTupleSet ttsIn ) {
	TokenTupleSet ttsOut = new TokenTupleSet( this );
	ttsOut.tokenTuples.addAll( ttsIn.tokenTuples );
	return ttsOut;
    }

    public boolean contains( TokenTuple tt ) {
	return tokenTuples.contains( tt );
    }

    public String toString() {
	return tokenTuples.toString();
    }
}
