package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class ReachabilitySet extends Canonical {

  private HashSet<TokenTupleSet> possibleReachabilities;

  public ReachabilitySet() {
    possibleReachabilities = new HashSet<TokenTupleSet>();
  }

  public ReachabilitySet(TokenTupleSet tts) {
    this();
    assert tts != null;
    possibleReachabilities.add(tts);
  }

  public ReachabilitySet(TokenTuple tt) {
    // can't assert before calling this(), it will
    // do the checking though
    this( new TokenTupleSet(tt).makeCanonical() );
  }

  public ReachabilitySet(HashSet<TokenTupleSet> possibleReachabilities) {
    assert possibleReachabilities != null;
    this.possibleReachabilities = possibleReachabilities;
  }

  public ReachabilitySet(ReachabilitySet rs) {
    assert rs != null;
    // okay to clone, ReachabilitySet should be canonical
    possibleReachabilities = (HashSet<TokenTupleSet>)rs.possibleReachabilities.clone();
  }


  public ReachabilitySet makeCanonical() {
    return (ReachabilitySet) Canonical.makeCanonical(this);
  }

  public Iterator<TokenTupleSet> iterator() {
    return possibleReachabilities.iterator();
  }


  public int size() {
    return possibleReachabilities.size();
  }

  public boolean isEmpty() {
    return possibleReachabilities.isEmpty();
  }

  public boolean contains(TokenTupleSet tts) {
    assert tts != null;
    return possibleReachabilities.contains(tts);
  }

  public boolean containsWithZeroes(TokenTupleSet tts) {
    assert tts != null;

    if( possibleReachabilities.contains(tts) ) {
      return true;
    }

    Iterator itr = iterator();
    while( itr.hasNext() ) {
      TokenTupleSet ttsThis = (TokenTupleSet) itr.next();
      if( ttsThis.containsWithZeroes(tts) ) {
	return true;
      }
    }

    return false;    
  }

  public boolean containsSuperSet(TokenTupleSet tts) {
    assert tts != null;

    if( possibleReachabilities.contains(tts) ) {
      return true;
    }

    Iterator itr = iterator();
    while( itr.hasNext() ) {
      TokenTupleSet ttsThis = (TokenTupleSet) itr.next();
      if( tts.isSubset(ttsThis) ) {
	return true;
      }
    }

    return false;    
  }


  public boolean containsTuple(TokenTuple tt) {
    Iterator itr = iterator();
    while( itr.hasNext() ) {
      TokenTupleSet tts = (TokenTupleSet) itr.next();
      if( tts.containsTuple(tt) ) {
	return true;
      }
    }
    return false;
  }

  public boolean containsTupleSetWithBoth(TokenTuple tt1, TokenTuple tt2) {
    Iterator itr = iterator();
    while( itr.hasNext() ) {
      TokenTupleSet tts = (TokenTupleSet) itr.next();
      if( tts.containsTuple(tt1) && tts.containsTuple(tt2) ) {
	return true;
      }
    }
    return false;
  }

  public ReachabilitySet union(ReachabilitySet rsIn) {
    assert rsIn != null;

    ReachabilitySet rsOut = new ReachabilitySet(this);
    rsOut.possibleReachabilities.addAll(rsIn.possibleReachabilities);
    return rsOut.makeCanonical();
  }

  public ReachabilitySet union(TokenTupleSet ttsIn) {
    assert ttsIn != null;

    ReachabilitySet rsOut = new ReachabilitySet(this);
    rsOut.possibleReachabilities.add(ttsIn);
    return rsOut.makeCanonical();
  }

  public ReachabilitySet intersection(ReachabilitySet rsIn) {
    assert rsIn != null;

    ReachabilitySet rsOut = new ReachabilitySet();

    Iterator i = this.iterator();
    while( i.hasNext() ) {
      TokenTupleSet tts = (TokenTupleSet) i.next();
      if( rsIn.possibleReachabilities.contains(tts) ) {
	rsOut.possibleReachabilities.add(tts);
      }
    }

    return rsOut.makeCanonical();
  }


  public ReachabilitySet add(TokenTupleSet tts) {
    assert tts != null;
    ReachabilitySet rsOut = new ReachabilitySet(tts);
    return rsOut.union(this);
  }

  public ReachabilitySet remove(TokenTupleSet tts) {
    assert tts != null;
    ReachabilitySet rsOut = new ReachabilitySet(tts);
    assert rsOut.possibleReachabilities.remove(tts);
    return rsOut.makeCanonical();
  }


  public ReachabilitySet applyChangeSet(ChangeTupleSet C) {
    assert C != null;

    ReachabilitySet rsOut = new ReachabilitySet();

    Iterator i = this.iterator();
    while( i.hasNext() ) {
      TokenTupleSet tts = (TokenTupleSet) i.next();

      boolean changeFound = false;

      Iterator<ChangeTuple> itrC = C.iterator();
      while( itrC.hasNext() ) {
	ChangeTuple c = itrC.next();

	if( tts.equals( c.getSetToMatch() ) ) {
	  rsOut.possibleReachabilities.add( c.getSetToAdd() );
	  changeFound = true;
	  break;
	}
      }

      if( !changeFound ) {
	rsOut.possibleReachabilities.add( tts );
      }
    }

    return rsOut.makeCanonical();
  }


  public ChangeTupleSet unionUpArityToChangeSet(ReachabilitySet rsIn) {
    assert rsIn != null;

    ChangeTupleSet ctsOut = new ChangeTupleSet();

    Iterator itrO = this.iterator();
    while( itrO.hasNext() ) {
      TokenTupleSet o = (TokenTupleSet) itrO.next();

      Iterator itrR = rsIn.iterator();
      while( itrR.hasNext() ) {
	TokenTupleSet r = (TokenTupleSet) itrR.next();

	TokenTupleSet theUnion = new TokenTupleSet().makeCanonical();

	Iterator itrRelement = r.iterator();
	while( itrRelement.hasNext() ) {
	  TokenTuple ttR = (TokenTuple) itrRelement.next();
	  TokenTuple ttO = o.containsToken(ttR.getToken() );

	  if( ttO != null ) {
	    theUnion = theUnion.union(new TokenTupleSet(ttR.unionArity(ttO) ) ).makeCanonical();
	  } else {
	    theUnion = theUnion.union(new TokenTupleSet(ttR) ).makeCanonical();
	  }
	}

	Iterator itrOelement = o.iterator();
	while( itrOelement.hasNext() ) {
	  TokenTuple ttO = (TokenTuple) itrOelement.next();
	  TokenTuple ttR = theUnion.containsToken(ttO.getToken() );

	  if( ttR == null ) {
	    theUnion = theUnion.union(new TokenTupleSet(ttO) ).makeCanonical();
	  }
	}

	if( !theUnion.isEmpty() ) {
	  ctsOut = ctsOut.union(new ChangeTupleSet(new ChangeTuple(o, theUnion) ) );
	}
      }
    }

    return ctsOut.makeCanonical();
  }


  public ReachabilitySet ageTokens(AllocationSite as) {
    assert as != null;

    ReachabilitySet rsOut = new ReachabilitySet();

    Iterator itrS = this.iterator();
    while( itrS.hasNext() ) {
      TokenTupleSet tts = (TokenTupleSet) itrS.next();
      rsOut.possibleReachabilities.add(tts.ageTokens(as) );
    }

    return rsOut.makeCanonical();
  }


  public ReachabilitySet unshadowTokens(AllocationSite as) {
    assert as != null;

    ReachabilitySet rsOut = new ReachabilitySet();

    Iterator itrS = this.iterator();
    while( itrS.hasNext() ) {
      TokenTupleSet tts = (TokenTupleSet) itrS.next();
      rsOut.possibleReachabilities.add(tts.unshadowTokens(as) );
    }

    return rsOut.makeCanonical();
  }


  public ReachabilitySet toShadowTokens(AllocationSite as) {
    assert as != null;

    ReachabilitySet rsOut = new ReachabilitySet();

    Iterator itrS = this.iterator();
    while( itrS.hasNext() ) {
      TokenTupleSet tts = (TokenTupleSet) itrS.next();
      rsOut.possibleReachabilities.add(tts.toShadowTokens(as) );
    }

    return rsOut.makeCanonical();
  }


  public ReachabilitySet pruneBy(ReachabilitySet rsIn) {
    assert rsIn != null;

    ReachabilitySet rsOut = new ReachabilitySet();

    Iterator itrB = this.iterator();
    while( itrB.hasNext() ) {
      TokenTupleSet ttsB = (TokenTupleSet) itrB.next();

      boolean subsetExists = false;

      Iterator itrA = rsIn.iterator();
      while( itrA.hasNext() && !subsetExists ) {
	TokenTupleSet ttsA = (TokenTupleSet) itrA.next();

	if( ttsA.isSubset(ttsB) ) {
	  subsetExists = true;
	}
      }

      if( subsetExists ) {
	rsOut.possibleReachabilities.add(ttsB);
      }
    }

    return rsOut.makeCanonical();
  }


  public ReachabilitySet exhaustiveArityCombinations() {
    ReachabilitySet rsOut = new ReachabilitySet();

    int numDimensions = this.possibleReachabilities.size();

    if( numDimensions > 1 ) {
      // for problems that are too big, punt and use less
      // precise arity for reachability information
      TokenTupleSet ttsImprecise = new TokenTupleSet().makeCanonical();

      Iterator<TokenTupleSet> itrThis = this.iterator();
      while( itrThis.hasNext() ) {
	TokenTupleSet ttsUnit = itrThis.next();
	ttsImprecise = ttsImprecise.unionUpArity(ttsUnit.makeArityZeroOrMore() );
      }

      //rsOut = this.union( ttsImprecise );
      rsOut = rsOut.union(ttsImprecise);
      return rsOut;
    }

    // add an extra digit to detect termination
    int[] digits = new int[numDimensions+1];

    int minArity = 0;
    int maxArity = 2;

    // start with the minimum possible coordinate
    for( int i = 0; i < numDimensions+1; ++i ) {
      digits[i] = minArity;
    }

    // and stop when the highest-ordered axis rolls above the minimum
    while( digits[numDimensions] == minArity ) {

      // spit out a "coordinate" made from these digits
      TokenTupleSet ttsCoordinate = new TokenTupleSet().makeCanonical();
      Iterator<TokenTupleSet> ttsItr = this.iterator();
      for( int i = 0; i < numDimensions; ++i ) {
	assert ttsItr.hasNext();
	TokenTupleSet ttsUnit = ttsItr.next();
	for( int j = 0; j < digits[i]; ++j ) {
	  ttsCoordinate = ttsCoordinate.unionUpArity(ttsUnit);
	}
      }
      rsOut = rsOut.add(ttsCoordinate.makeCanonical() );

      // increment
      for( int i = 0; i < numDimensions+1; ++i ) {
	digits[i]++;

	if( digits[i] > maxArity ) {
	  // this axis reached its max, so roll it back to min and increment next higher digit
	  digits[i] = minArity;

	} else {
	  // this axis did not reach its max so we just enumerated a new unique coordinate, stop
	  break;
	}
      }
    }

    return rsOut.makeCanonical();
  }


  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ReachabilitySet) ) {
      return false;
    }

    ReachabilitySet rs = (ReachabilitySet) o;
    return possibleReachabilities.equals(rs.possibleReachabilities);
  }


  private boolean oldHashSet = false;
  private int oldHash    = 0;
  public int hashCode() {
    int currentHash = possibleReachabilities.hashCode();

    if( oldHashSet == false ) {
      oldHash = currentHash;
      oldHashSet = true;
    } else {
      if( oldHash != currentHash ) {
	System.out.println("IF YOU SEE THIS A CANONICAL ReachabilitySet CHANGED");
	Integer x = null;
	x.toString();
      }
    }

    return currentHash;
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
