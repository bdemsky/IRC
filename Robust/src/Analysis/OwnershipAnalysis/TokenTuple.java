package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


// a token touple is a pair that indicates a
// heap region node and an arity
public class TokenTuple
{
    private Integer token;
    private boolean isNewSummary;

    // only summary tokens should have ARITY_MANY?
    public static final int ARITY_ONE  = 1;
    public static final int ARITY_MANY = 2;
    private int arity;

    public TokenTuple( HeapRegionNode hrn ) {
	token        = hrn.getID();
	isNewSummary = hrn.isNewSummary();
	arity        = ARITY_ONE;
    }

    public TokenTuple( Integer token,
		       boolean isNewSummary,
		       int     arity ) {
	this.token        = token;
	this.isNewSummary = isNewSummary;
	this.arity        = arity;
    }

    public Integer getToken() { return token; }
    public int     getArity() {	return arity; }

    public void increaseArity() {
	if( isNewSummary ) {
	    arity = ARITY_MANY;
	}
    }

    public boolean equals( TokenTuple tt ) {
	return token.equals( tt.getToken() ) &&
   	       arity ==      tt.getArity();
    }

    public TokenTuple copy() {
	return new TokenTuple( token,
			       isNewSummary,
			       arity );
    }
}
