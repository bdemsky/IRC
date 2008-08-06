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

    /*
    public TokenTupleSet add( TokenTuple tt ) {
	TokenTupleSet ttsOut = new TokenTupleSet( tt );
	return this.union( ttsOut );
    }
    */

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

    public boolean isSubset( TokenTupleSet ttsIn ) {
	assert ttsIn != null;
	return ttsIn.tokenTuples.containsAll( this.tokenTuples );
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
	if( o == null ) {
	    return false;
	}

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

    public TokenTupleSet ageTokens( AllocationSite as ) {
	TokenTupleSet ttsOut = new TokenTupleSet();

	TokenTuple ttSummary = null;
	boolean foundOldest  = false;

	Iterator itrT = this.iterator();
	while( itrT.hasNext() ) {
	    TokenTuple tt = (TokenTuple) itrT.next();

	    Integer token = tt.getToken();
	    int age = as.getAge( token );

	    // summary tokens and tokens not associated with
	    // the site should be left alone
	    if( age == AllocationSite.AGE_notInThisSite ) {
		ttsOut.tokenTuples.add( tt );

	    } else {
		if( age == AllocationSite.AGE_summary ) {
		    // remember the summary tuple, but don't add it
		    // we may combine it with the oldest tuple
		    ttSummary = tt;

		} else if( age == AllocationSite.AGE_oldest ) {
		    // found an oldest token, again just remember
		    // for later
		    foundOldest = true;

		} else {
		    // otherwise, we change this token to the
		    // next older token
		    Integer tokenToChangeTo = as.getIthOldest( age + 1 );		   
		    TokenTuple ttAged       = tt.changeTokenTo( tokenToChangeTo );
		    ttsOut.tokenTuples.add( ttAged );
		}

	    }
	}

	// there are four cases to consider here
	// 1. we found a summary tuple and no oldest tuple
	//    Here we just pass the summary unchanged
	// 2. we found an oldest tuple, no summary
	//    Make a new, arity-one summary tuple
	// 3. we found both a summary and an oldest
	//    Merge them by increasing arity of summary
	// 4. (not handled) we found neither, do nothing
	if       ( ttSummary != null && !foundOldest ) {
	    ttsOut.tokenTuples.add( ttSummary );

	} else if( ttSummary == null &&  foundOldest ) {
	    ttsOut.tokenTuples.add( new TokenTuple( as.getSummary(),
					true,
					TokenTuple.ARITY_ONE ).makeCanonical() );	   
	
	} else if( ttSummary != null &&  foundOldest ) {
	    ttsOut.tokenTuples.add( ttSummary.increaseArity() );
	}

	return ttsOut.makeCanonical();
    }

    public String toString() {
	return tokenTuples.toString();
    }
}
