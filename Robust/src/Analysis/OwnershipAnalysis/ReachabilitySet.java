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


  public boolean contains(TokenTupleSet tts) {
    assert tts != null;
    return possibleReachabilities.contains(tts);
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


  public ReachabilitySet increaseArity(Integer token) {
    assert token != null;

    HashSet<TokenTupleSet> possibleReachabilitiesNew = new HashSet<TokenTupleSet>();

    Iterator itr = iterator();
    while( itr.hasNext() ) {
      TokenTupleSet tts = (TokenTupleSet) itr.next();
      possibleReachabilitiesNew.add(tts.increaseArity(token) );
    }

    return new ReachabilitySet(possibleReachabilitiesNew).makeCanonical();
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


  public ChangeTupleSet unionUpArityToChangeSet(ReachabilitySet rsIn) {
    assert rsIn != null;

    ChangeTupleSet ctsOut = new ChangeTupleSet();

    Iterator itrO = this.iterator();
    while( itrO.hasNext() ) {
      TokenTupleSet o = (TokenTupleSet) itrO.next();

      Iterator itrR = rsIn.iterator();
      while( itrR.hasNext() ) {
	TokenTupleSet r = (TokenTupleSet) itrR.next();

	TokenTupleSet theUnion = new TokenTupleSet();

	Iterator itrRelement = r.iterator();
	while( itrRelement.hasNext() ) {
	  TokenTuple e = (TokenTuple) itrRelement.next();

	  if( o.containsToken(e.getToken() ) ) {
	    theUnion = theUnion.union(new TokenTupleSet(e.increaseArity() ) ).makeCanonical();
	  } else {
	    theUnion = theUnion.union(new TokenTupleSet(e) ).makeCanonical();
	  }
	}

	Iterator itrOelement = o.iterator();
	while( itrOelement.hasNext() ) {
	  TokenTuple e = (TokenTuple) itrOelement.next();

	  if( !theUnion.containsToken(e.getToken() ) ) {
	    theUnion = theUnion.union(new TokenTupleSet(e) ).makeCanonical();
	  }
	}

	if( !theUnion.isEmpty() ) {
	  ctsOut = ctsOut.union(
	    new ChangeTupleSet(new ChangeTuple(o, theUnion) )
	    );
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
      TokenTupleSet ttsCoordinate = new TokenTupleSet();
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

  public int hashCode() {
    return possibleReachabilities.hashCode();
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
