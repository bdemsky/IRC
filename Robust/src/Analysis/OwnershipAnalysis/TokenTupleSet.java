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

  public TokenTupleSet(TokenTuple tt) {
    this();
    assert tt != null;
    tokenTuples.add(tt);
  }

  public TokenTupleSet(TokenTupleSet tts) {
    assert tts != null;
    // okay to clone, TokenTuple and TokenTupleSet should be canonical
    tokenTuples = (HashSet<TokenTuple>)tts.tokenTuples.clone();
  }


  public TokenTupleSet makeCanonical() {
    return (TokenTupleSet) Canonical.makeCanonical(this);
  }

  public Iterator iterator() {
    return tokenTuples.iterator();
  }

  public boolean isEmpty() {
    return tokenTuples.isEmpty();
  }

  public boolean isSubset(TokenTupleSet ttsIn) {
    assert ttsIn != null;
    return ttsIn.tokenTuples.containsAll(this.tokenTuples);
  }

  public boolean containsTuple(TokenTuple tt) {
    assert tt != null;
    return tokenTuples.contains(tt);
  }


  public TokenTupleSet union(TokenTupleSet ttsIn) {
    assert ttsIn != null;
    TokenTupleSet ttsOut = new TokenTupleSet(this);
    ttsOut.tokenTuples.addAll(ttsIn.tokenTuples);
    return ttsOut.makeCanonical();
  }

  public TokenTupleSet unionUpArity( TokenTupleSet ttsIn ) {
    assert ttsIn != null;
    TokenTupleSet ttsOut = new TokenTupleSet();

    Iterator<TokenTuple> ttItr = this.iterator();
    while( ttItr.hasNext() ) {
      TokenTuple tt = ttItr.next();

      if( ttsIn.containsToken( tt.getToken() ) ) {
	ttsOut.tokenTuples.add(tt.increaseArity());
      } else {
	ttsOut.tokenTuples.add(tt);
      }
    }

    ttItr = ttsIn.iterator();
    while( ttItr.hasNext() ) {
      TokenTuple tt = ttItr.next();

      if( !ttsOut.containsToken(tt.getToken()) ) {
	ttsOut.tokenTuples.add(tt);
      }
    }

    return ttsOut.makeCanonical();    
  }

  public TokenTupleSet add(TokenTuple tt) {
    assert tt != null;
    TokenTupleSet ttsOut = new TokenTupleSet(tt);
    return ttsOut.union(this);
  }


  // this should only be done with a multiple-object heap region's token!
  public TokenTupleSet increaseArity(Integer token) {
    assert token != null;
    TokenTupleSet ttsOut = new TokenTupleSet(this);
    TokenTuple tt
    = new TokenTuple(token, true, TokenTuple.ARITY_ONE).makeCanonical();
    if( ttsOut.tokenTuples.contains(tt) ) {
      ttsOut.tokenTuples.remove(tt);
      ttsOut.tokenTuples.add(
        new TokenTuple(token, true, TokenTuple.ARITY_MANY).makeCanonical()
        );
    }

    return ttsOut.makeCanonical();
  }


  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof TokenTupleSet) ) {
      return false;
    }

    TokenTupleSet tts = (TokenTupleSet) o;
    return tokenTuples.equals(tts.tokenTuples);
  }

  public int hashCode() {
    return tokenTuples.hashCode();
  }


  // this should be a hash table so we can do this by key
  public boolean containsToken(Integer token) {
    assert token != null;

    Iterator itr = tokenTuples.iterator();
    while( itr.hasNext() ) {
      TokenTuple tt = (TokenTuple) itr.next();
      if( token.equals(tt.getToken() ) ) {
	return true;
      }
    }
    return false;
  }


  public TokenTupleSet ageTokens(AllocationSite as) {
    assert as != null;

    TokenTupleSet ttsOut = new TokenTupleSet();

    TokenTuple ttSummary = null;
    boolean foundOldest  = false;

    Iterator itrT = this.iterator();
    while( itrT.hasNext() ) {
      TokenTuple tt = (TokenTuple) itrT.next();

      Integer token = tt.getToken();
      int age = as.getAge(token);

      // summary tokens and tokens not associated with
      // the site should be left alone
      if( age == AllocationSite.AGE_notInThisSite ) {
	ttsOut.tokenTuples.add(tt);

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
	  Integer tokenToChangeTo = as.getIthOldest(age + 1);
	  TokenTuple ttAged       = tt.changeTokenTo(tokenToChangeTo);
	  ttsOut.tokenTuples.add(ttAged);
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
      ttsOut.tokenTuples.add(ttSummary);

    } else if( ttSummary == null &&  foundOldest ) {
      ttsOut.tokenTuples.add(new TokenTuple(as.getSummary(),
                                            true,
                                            TokenTuple.ARITY_ONE).makeCanonical() );

    } else if( ttSummary != null &&  foundOldest ) {
      ttsOut.tokenTuples.add(ttSummary.increaseArity() );
    }

    return ttsOut.makeCanonical();
  }


  public ReachabilitySet rewriteToken( TokenTuple tokenToRewrite,
				       ReachabilitySet replacements,
				       boolean makeChangeSet,
				       Hashtable<TokenTupleSet, TokenTupleSet> forChangeSet ) {
    
    ReachabilitySet rsOut = new ReachabilitySet().makeCanonical();
    
    if( !tokenTuples.contains( tokenToRewrite ) ) {
      rsOut = rsOut.add( this );
      
    } else {
      TokenTupleSet ttsMinusToken = new TokenTupleSet( this );
      ttsMinusToken.tokenTuples.remove( tokenToRewrite );
      
      Iterator<TokenTupleSet> replaceItr = replacements.iterator();
      while( replaceItr.hasNext() ) {
	TokenTupleSet replacement = replaceItr.next();
	TokenTupleSet replaced = new TokenTupleSet( ttsMinusToken );
	replaced = replaced.unionUpArity( replacement );
	rsOut = rsOut.add( replaced );

	if( makeChangeSet ) {
	  assert forChangeSet != null;
	  forChangeSet.put( this, replaced );
	}
      }
    }
    
    return rsOut.makeCanonical();
  }
  

  public String toString() {
    return tokenTuples.toString();
  }
}
