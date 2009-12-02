package Analysis.DisjointAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class ReachState extends Canonical {

  private HashSet<ReachTuple> tokenTuples;


  public ReachState() {
    tokenTuples = new HashSet<ReachTuple>();
  }

  public ReachState(ReachTuple tt) {
    this();
    assert tt != null;
    tokenTuples.add(tt);
  }

  public ReachState(ReachTupleSet tts) {
    assert tts != null;
    // okay to clone, ReachTuple and ReachState should be canonical
    tokenTuples = (HashSet<ReachTuple>)tts.tokenTuples.clone();
  }


  public ReachState makeCanonical() {
    return (ReachState) Canonical.makeCanonical(this);
  }

  public Iterator iterator() {
    return tokenTuples.iterator();
  }

  public boolean isEmpty() {
    return tokenTuples.isEmpty();
  }

  public boolean isSubset(ReachState ttsIn) {
    assert ttsIn != null;
    return ttsIn.tokenTuples.containsAll(this.tokenTuples);
  }

  public boolean containsTuple(ReachTuple tt) {
    assert tt != null;
    return tokenTuples.contains(tt);
  }

  public boolean containsBoth(ReachTuple tt1, ReachTuple tt2) {
    return containsTuple(tt1) && containsTuple(tt2);
  }

  public boolean containsWithZeroes(ReachState tts) {
    assert tts != null;

    // first establish that every token tuple from tts is
    // also in this set
    Iterator<ReachTuple> ttItrIn = tts.iterator();
    while( ttItrIn.hasNext() ) {
      ReachTuple ttIn   = ttItrIn.next();
      ReachTuple ttThis = this.containsToken(ttIn.getToken() );

      if( ttThis == null ) {
	return false;
      }
    }    
    
    // then establish that anything in this set that is
    // not in tts is a zero-arity token tuple, which is okay    
    Iterator<ReachTuple> ttItrThis = this.iterator();
    while( ttItrThis.hasNext() ) {
      ReachTuple ttThis = ttItrThis.next();
      ReachTuple ttIn   = tts.containsToken(ttThis.getToken() );

      if( ttIn == null && 
	  ttThis.getArity() != ReachTuple.ARITY_ZEROORMORE ) {
	return false;
      }
    }    

    // if so this set contains tts with zeroes
    return true;
  }

  public ReachState union(ReachTuple ttIn) {
    assert ttIn != null;
    ReachOperation ro=new ReachOperation(this, ttIn);
    if (unionhash.containsKey(ro))
	return (ReachState) unionhash.get(ro).c;
    else {
	ReachState ttsOut = new ReachTupleSet(this);
	ttsOut.tokenTuples.add(ttIn);
	ro.c=ttsOut=ttsOut.makeCanonical();
	unionhash.put(ro,ro);
	return ttsOut;
    }
  }

  public ReachState union(ReachTupleSet ttsIn) {
    assert ttsIn != null;
    ReachOperation ro=new ReachOperation(this, ttsIn);
    if (unionhash.containsKey(ro)) {
	return (ReachState) unionhash.get(ro).c;
    } else {
	ReachState ttsOut = new ReachTupleSet(this);
	ttsOut.tokenTuples.addAll(ttsIn.tokenTuples);
	ro.c=ttsOut=ttsOut.makeCanonical();
	unionhash.put(ro,ro);
	return ttsOut;
    }
  }


  public ReachState unionUpArity(ReachTupleSet ttsIn) {
    assert ttsIn != null;
    ReachState ttsOut = new ReachTupleSet();

    Iterator<ReachTuple> ttItr = this.iterator();
    while( ttItr.hasNext() ) {
      ReachTuple ttThis = ttItr.next();
      ReachTuple ttIn   = ttsIn.containsToken(ttThis.getToken() );

      if( ttIn != null ) {
	ttsOut.tokenTuples.add(ttThis.unionArity(ttIn) );
      } else {
	ttsOut.tokenTuples.add(ttThis);
      }
    }

    ttItr = ttsIn.iterator();
    while( ttItr.hasNext() ) {
      ReachTuple ttIn   = ttItr.next();
      ReachTuple ttThis = ttsOut.containsToken(ttIn.getToken() );

      if( ttThis == null ) {
	ttsOut.tokenTuples.add(ttIn);
      }
    }

    return ttsOut.makeCanonical();
  }


  public ReachState add(ReachTuple tt) {
    assert tt != null;
    return this.union(tt);
  }


  public ReachState remove(ReachTuple tt) {
    assert tt != null;
    ReachState ttsOut = new ReachTupleSet(this);
    ttsOut.tokenTuples.remove(tt);
    return ttsOut.makeCanonical();
  }


  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ReachState) ) {
      return false;
    }

    ReachState tts = (ReachTupleSet) o;
    return tokenTuples.equals(tts.tokenTuples);
  }

    boolean hashcodecomputed=false;
    int ourhashcode=0;


  public int hashCode() {
      if (hashcodecomputed)
	  return ourhashcode;
      else {
	  ourhashcode=tokenTuples.hashCode();
	  hashcodecomputed=true;
	  return ourhashcode;
      }
  }


  // this should be a hash table so we can do this by key
  public ReachTuple containsToken(Integer token) {
    assert token != null;

    Iterator itr = tokenTuples.iterator();
    while( itr.hasNext() ) {
      ReachTuple tt = (ReachTuple) itr.next();
      if( token.equals(tt.getToken() ) ) {
	return tt;
      }
    }
    return null;
  }


  public ReachState ageTokens(AllocSite as) {
    assert as != null;

    ReachState ttsOut = new ReachTupleSet();

    ReachTuple ttSummary = null;
    ReachTuple ttOldest  = null;

    Iterator itrT = this.iterator();
    while( itrT.hasNext() ) {
      ReachTuple tt = (ReachTuple) itrT.next();

      Integer token = tt.getToken();
      int age = as.getAgeCategory(token);

      // tokens not associated with
      // the site should be left alone
      if( age == AllocSite.AGE_notInThisSite ) {
	ttsOut.tokenTuples.add(tt);

      } else if( age == AllocSite.AGE_summary ) {
	// remember the summary tuple, but don't add it
	// we may combine it with the oldest tuple
	ttSummary = tt;

      } else if( age == AllocSite.AGE_oldest ) {
	// found an oldest token, again just remember
	// for later
	ttOldest = tt;

      } else {
	assert age == AllocSite.AGE_in_I;

	Integer I = as.getAge(token);
	assert I != null;

	// otherwise, we change this token to the
	// next older token
	Integer tokenToChangeTo = as.getIthOldest(I + 1);
	ReachTuple ttAged       = tt.changeTokenTo(tokenToChangeTo);
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
      ttsOut.tokenTuples.add(new ReachTuple(as.getSummary(),
                                            true,
                                            ttOldest.getArity()
                                            ).makeCanonical()
                             );

    } else if( ttSummary != null && ttOldest != null ) {
      ttsOut.tokenTuples.add(ttSummary.unionArity(new ReachTuple(as.getSummary(),
                                                                 true,
                                                                 ttOldest.getArity()
                                                                 ).makeCanonical()
                                                  )
                             );
    }

    return ttsOut.makeCanonical();
  }


  public ReachState unshadowTokens(AllocSite as) {
    assert as != null;

    ReachState ttsOut = new ReachTupleSet();

    ReachTuple ttSummary       = null;
    ReachTuple ttShadowSummary = null;

    Iterator itrT = this.iterator();
    while( itrT.hasNext() ) {
      ReachTuple tt = (ReachTuple) itrT.next();

      Integer token = tt.getToken();
      int shadowAge = as.getShadowAgeCategory(token);

      if( shadowAge == AllocSite.AGE_summary ) {
	// remember the summary tuple, but don't add it
	// we may combine it with the oldest tuple
	ttSummary = tt;

      } else if( shadowAge == AllocSite.SHADOWAGE_notInThisSite ) {
	ttsOut.tokenTuples.add(tt);

      } else if( shadowAge == AllocSite.SHADOWAGE_summary ) {
	// found the shadow summary token, again just remember
	// for later
	ttShadowSummary = tt;

      } else if( shadowAge == AllocSite.SHADOWAGE_oldest ) {
	Integer tokenToChangeTo = as.getOldest();
	ReachTuple ttNormal = tt.changeTokenTo(tokenToChangeTo);
	ttsOut.tokenTuples.add(ttNormal);

      } else {
	assert shadowAge == AllocSite.SHADOWAGE_in_I;

	Integer I = as.getShadowAge(token);
	assert I != null;

	Integer tokenToChangeTo = as.getIthOldest(-I);
	ReachTuple ttNormal = tt.changeTokenTo(tokenToChangeTo);
	ttsOut.tokenTuples.add(ttNormal);
      }
    }

    if       ( ttSummary != null && ttShadowSummary == null ) {
      ttsOut.tokenTuples.add(ttSummary);

    } else if( ttSummary == null && ttShadowSummary != null ) {
      ttsOut.tokenTuples.add(new ReachTuple(as.getSummary(),
                                            true,
                                            ttShadowSummary.getArity()
                                            ).makeCanonical()
                             );

    } else if( ttSummary != null && ttShadowSummary != null ) {
      ttsOut.tokenTuples.add(ttSummary.unionArity(new ReachTuple(as.getSummary(),
                                                                 true,
                                                                 ttShadowSummary.getArity()
                                                                 ).makeCanonical()
                                                  )
                             );
    }

    return ttsOut.makeCanonical();
  }


  public ReachState toShadowTokens(AllocSite as) {
    assert as != null;

    ReachState ttsOut = new ReachTupleSet().makeCanonical();

    Iterator itrT = this.iterator();
    while( itrT.hasNext() ) {
      ReachTuple tt = (ReachTuple) itrT.next();

      Integer token = tt.getToken();
      int age = as.getAgeCategory(token);

      // summary tokens and tokens not associated with
      // the site should be left alone
      if( age == AllocSite.AGE_notInThisSite ) {
	ttsOut = ttsOut.union(tt);

      } else if( age == AllocSite.AGE_summary ) {
	ttsOut = ttsOut.union(tt.changeTokenTo(as.getSummaryShadow() ));

      } else if( age == AllocSite.AGE_oldest ) {
	ttsOut = ttsOut.union(tt.changeTokenTo(as.getOldestShadow() ));

      } else {
	assert age == AllocSite.AGE_in_I;

	Integer I = as.getAge(token);
	assert I != null;

	ttsOut = ttsOut.union(tt.changeTokenTo(as.getIthOldestShadow(I) ));
      }
    }

    return ttsOut.makeCanonical();
  }


  public ReachSet rewriteToken(ReachTuple tokenToRewrite,
                                      ReachSet replacements,
                                      boolean makeChangeSet,
                                      Hashtable<ReachState, HashSet<ReachTupleSet> > forChangeSet) {

    ReachSet rsOut = new ReachSet().makeCanonical();

    if( !tokenTuples.contains(tokenToRewrite) ) {
      rsOut = rsOut.add(this);

    } else {
      ReachState ttsMinusToken = new ReachTupleSet(this);
      ttsMinusToken.tokenTuples.remove(tokenToRewrite);

      Iterator<ReachState> replaceItr = replacements.iterator();
      while( replaceItr.hasNext() ) {
	ReachState replacement = replaceItr.next();
	ReachState replaced = new ReachTupleSet(ttsMinusToken).makeCanonical();
	replaced = replaced.unionUpArity(replacement);
	rsOut = rsOut.add(replaced);

	if( makeChangeSet ) {
	  assert forChangeSet != null;

	  if( forChangeSet.get(this) == null ) {
	    forChangeSet.put(this, new HashSet<ReachState>() );
	  }

	  forChangeSet.get(this).add(replaced);
	}
      }
    }

    return rsOut.makeCanonical();
  }


  public ReachState makeArityZeroOrMore() {
    ReachState ttsOut = new ReachTupleSet().makeCanonical();

    Iterator<ReachTuple> itrThis = this.iterator();
    while( itrThis.hasNext() ) {
      ReachTuple tt = itrThis.next();

      ttsOut = ttsOut.union(new ReachTuple(tt.getToken(),
                                           tt.isMultiObject(),
                                           ReachTuple.ARITY_ZEROORMORE
                                           ).makeCanonical()
                            );
    }

    return ttsOut.makeCanonical();
  }

  public String toString() {
    return tokenTuples.toString();
  }
}
