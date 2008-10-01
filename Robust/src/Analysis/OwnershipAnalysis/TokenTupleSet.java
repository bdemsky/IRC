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


  public TokenTupleSet union(TokenTuple ttIn) {
    assert ttIn != null;
    TokenTupleSet ttsOut = new TokenTupleSet(this);
    ttsOut.tokenTuples.add(ttIn);
    return ttsOut.makeCanonical();
  }

  public TokenTupleSet union(TokenTupleSet ttsIn) {
    assert ttsIn != null;
    TokenTupleSet ttsOut = new TokenTupleSet(this);
    ttsOut.tokenTuples.addAll(ttsIn.tokenTuples);
    return ttsOut.makeCanonical();
  }


  public TokenTupleSet unionUpArity(TokenTupleSet ttsIn) {
    assert ttsIn != null;
    TokenTupleSet ttsOut = new TokenTupleSet();

    Iterator<TokenTuple> ttItr = this.iterator();
    while( ttItr.hasNext() ) {
      TokenTuple ttThis = ttItr.next();
      TokenTuple ttIn   = ttsIn.containsToken( ttThis.getToken() );

      if( ttIn != null ) {
	ttsOut.tokenTuples.add( ttThis.unionArity( ttIn ) );
      } else {
	ttsOut.tokenTuples.add( ttThis );
      }
    }

    ttItr = ttsIn.iterator();
    while( ttItr.hasNext() ) {
      TokenTuple ttIn   = ttItr.next();
      TokenTuple ttThis = ttsOut.containsToken( ttIn.getToken() );

      if( ttThis == null ) {
	ttsOut.tokenTuples.add( ttIn );
      }
    }

    return ttsOut.makeCanonical();
  }


  public TokenTupleSet add(TokenTuple tt) {
    assert tt != null;
    TokenTupleSet ttsOut = new TokenTupleSet(tt);
    return ttsOut.union(this);
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



  private boolean oldHashSet = false;
  private int     oldHash    = 0;
  public int hashCode() {
    int currentHash = tokenTuples.hashCode();

    if( oldHashSet == false ) {
      oldHash = currentHash;
      oldHashSet = true;
    } else {
      if( oldHash != currentHash ) {
	System.out.println( "IF YOU SEE THIS A CANONICAL TokenTupleSet CHANGED" );
	Integer x = null;
	x.toString();
      }
    }

    return currentHash;
  }


  // this should be a hash table so we can do this by key
  public TokenTuple containsToken(Integer token) {
    assert token != null;

    Iterator itr = tokenTuples.iterator();
    while( itr.hasNext() ) {
      TokenTuple tt = (TokenTuple) itr.next();
      if( token.equals(tt.getToken() ) ) {
	return tt;
      }
    }
    return null;
  }


  public TokenTupleSet ageTokens(AllocationSite as) {
    assert as != null;

    TokenTupleSet ttsOut = new TokenTupleSet();

    TokenTuple ttSummary = null;
    TokenTuple ttOldest  = null;

    Iterator itrT = this.iterator();
    while( itrT.hasNext() ) {
      TokenTuple tt = (TokenTuple) itrT.next();

      Integer token = tt.getToken();
      int age = as.getAgeCategory(token);

      // tokens not associated with
      // the site should be left alone
      if( age == AllocationSite.AGE_notInThisSite ) {
	ttsOut.tokenTuples.add(tt);

      } else if( age == AllocationSite.AGE_summary ) {
	// remember the summary tuple, but don't add it
	// we may combine it with the oldest tuple
	ttSummary = tt;

      } else if( age == AllocationSite.AGE_oldest ) {
	// found an oldest token, again just remember
	// for later
	ttOldest = tt;

      } else {
	assert age == AllocationSite.AGE_in_I;

	Integer I = as.getAge(token);
	assert I != null;

	// otherwise, we change this token to the
	// next older token
	Integer tokenToChangeTo = as.getIthOldest(I + 1);
	TokenTuple ttAged       = tt.changeTokenTo(tokenToChangeTo);
	ttsOut.tokenTuples.add(ttAged);
      }
    }

    // there are four cases to consider here
    // 1. we found a summary tuple and no oldest tuple
    //    Here we just pass the summary unchanged
    // 2. we found an oldest tuple, no summary
    //    Make a new, arity-one summary tuple
    // 3. we found both a summary and an oldest
    //    Merge them by arity
    // 4. (not handled) we found neither, do nothing
    if       ( ttSummary != null && ttOldest == null ) {
      ttsOut.tokenTuples.add(ttSummary);

    } else if( ttSummary == null && ttOldest != null ) {
      ttsOut.tokenTuples.add(new TokenTuple(as.getSummary(),
                                            true,
                                            ttOldest.getArity() 
					   ).makeCanonical() 
			     );

    } else if( ttSummary != null && ttOldest != null ) {
      ttsOut.tokenTuples.add(ttSummary.unionArity(new TokenTuple(as.getSummary(),
								 true,
								 ttOldest.getArity() 
								 ).makeCanonical()
						  )
			     );
    }

    return ttsOut.makeCanonical();
  }


  public TokenTupleSet unshadowTokens(AllocationSite as) {
    assert as != null;

    TokenTupleSet ttsOut = new TokenTupleSet();

    TokenTuple ttSummary       = null;
    TokenTuple ttShadowSummary = null;

    Iterator itrT = this.iterator();
    while( itrT.hasNext() ) {
      TokenTuple tt = (TokenTuple) itrT.next();

      Integer token = tt.getToken();
      int shadowAge = as.getShadowAgeCategory(token);

      if( shadowAge == AllocationSite.AGE_summary ) {
	// remember the summary tuple, but don't add it
	// we may combine it with the oldest tuple
	ttSummary = tt;

      } else if( shadowAge == AllocationSite.SHADOWAGE_notInThisSite ) {
	ttsOut.tokenTuples.add(tt);

      } else if( shadowAge == AllocationSite.SHADOWAGE_summary ) {
	// found the shadow summary token, again just remember
	// for later
	ttShadowSummary = tt;

      } else if( shadowAge == AllocationSite.SHADOWAGE_oldest ) {
	Integer tokenToChangeTo = as.getOldest();
	TokenTuple ttNormal = tt.changeTokenTo(tokenToChangeTo);
	ttsOut.tokenTuples.add(ttNormal);

      } else {
	assert shadowAge == AllocationSite.SHADOWAGE_in_I;

	Integer I = as.getShadowAge(token);
	assert I != null;

	Integer tokenToChangeTo = as.getIthOldest(-I);
	TokenTuple ttNormal = tt.changeTokenTo(tokenToChangeTo);
	ttsOut.tokenTuples.add(ttNormal);
      }
    }

    if       ( ttSummary != null && ttShadowSummary == null ) {
      ttsOut.tokenTuples.add(ttSummary);

    } else if( ttSummary == null && ttShadowSummary != null ) {
      ttsOut.tokenTuples.add( new TokenTuple(as.getSummary(),
					     true,
					     ttShadowSummary.getArity()
					     ).makeCanonical()
			      );

    } else if( ttSummary != null && ttShadowSummary != null ) {
      ttsOut.tokenTuples.add(ttSummary.unionArity( new TokenTuple(as.getSummary(),
								  true,
								  ttShadowSummary.getArity()
								  ).makeCanonical()
						   )
			     );
    }

    return ttsOut.makeCanonical();
  }


  public TokenTupleSet toShadowTokens(AllocationSite as) {
    assert as != null;

    TokenTupleSet ttsOut = new TokenTupleSet().makeCanonical();

    Iterator itrT = this.iterator();
    while( itrT.hasNext() ) {
      TokenTuple tt = (TokenTuple) itrT.next();

      Integer token = tt.getToken();
      int age = as.getAgeCategory(token);

      // summary tokens and tokens not associated with
      // the site should be left alone
      if( age == AllocationSite.AGE_notInThisSite ) {
	ttsOut = ttsOut.union(tt);

      } else if( age == AllocationSite.AGE_summary ) {
	ttsOut = ttsOut.union(tt.changeTokenTo(as.getSummaryShadow() ));

      } else if( age == AllocationSite.AGE_oldest ) {
	ttsOut = ttsOut.union(tt.changeTokenTo(as.getOldestShadow() ));

      } else {
	assert age == AllocationSite.AGE_in_I;

	Integer I = as.getAge(token);
	assert I != null;

	ttsOut = ttsOut.union(tt.changeTokenTo(as.getIthOldestShadow(I) ));
      }
    }

    return ttsOut.makeCanonical();
  }


  public ReachabilitySet rewriteToken(TokenTuple tokenToRewrite,
                                      ReachabilitySet replacements,
                                      boolean makeChangeSet,
                                      Hashtable<TokenTupleSet, HashSet<TokenTupleSet> > forChangeSet) {

    ReachabilitySet rsOut = new ReachabilitySet().makeCanonical();

    if( !tokenTuples.contains(tokenToRewrite) ) {
      rsOut = rsOut.add(this);

    } else {
      TokenTupleSet ttsMinusToken = new TokenTupleSet(this);
      ttsMinusToken.tokenTuples.remove(tokenToRewrite);

      Iterator<TokenTupleSet> replaceItr = replacements.iterator();
      while( replaceItr.hasNext() ) {
	TokenTupleSet replacement = replaceItr.next();
	TokenTupleSet replaced = new TokenTupleSet(ttsMinusToken).makeCanonical();
	replaced = replaced.unionUpArity(replacement);
	rsOut = rsOut.add(replaced);

	if( makeChangeSet ) {
	  assert forChangeSet != null;
	  
	  if( forChangeSet.get(this) == null ) {
	    forChangeSet.put(this, new HashSet<TokenTupleSet>() );
	  }
	  
	  forChangeSet.get(this).add(replaced);
	}
      }
    }

    return rsOut.makeCanonical();
  }


  public TokenTupleSet makeArityZeroOrMore() {
    TokenTupleSet ttsOut = new TokenTupleSet().makeCanonical();

    Iterator<TokenTuple> itrThis = this.iterator();
    while( itrThis.hasNext() ) {
      TokenTuple tt = itrThis.next();

      ttsOut.tokenTuples.add( new TokenTuple( tt.getToken(),
					     tt.isMultiObject(),
					     TokenTuple.ARITY_ZEROORMORE 
					    ).makeCanonical()
			      );
    }

    return ttsOut.makeCanonical();
  }
 
  public String toString() {
    return tokenTuples.toString();
  }
}
