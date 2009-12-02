package Analysis.DisjointAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class ReachSet extends Canonical {

  private HashSet<ReachState> possibleReachabilities;

  public ReachSet() {
    possibleReachabilities = new HashSet<ReachState>();
  }

  public ReachSet(ReachState tts) {
    this();
    assert tts != null;
    possibleReachabilities.add(tts);
  }

  public ReachSet(ReachTuple tt) {
    // can't assert before calling this(), it will
    // do the checking though
    this( new ReachState(tt).makeCanonical() );
  }

  public ReachSet(HashSet<ReachState> possibleReachabilities) {
    assert possibleReachabilities != null;
    this.possibleReachabilities = possibleReachabilities;
  }

  public ReachSet(ReachSet rs) {
    assert rs != null;
    // okay to clone, ReachSet should be canonical
    possibleReachabilities = (HashSet<ReachState>)rs.possibleReachabilities.clone();
  }


  public ReachSet makeCanonical() {
    return (ReachSet) ReachSet.makeCanonical(this);
  }

  public Iterator<ReachState> iterator() {
    return possibleReachabilities.iterator();
  }


  public int size() {
    return possibleReachabilities.size();
  }

  public boolean isEmpty() {
    return possibleReachabilities.isEmpty();
  }

  public boolean contains(ReachState tts) {
    assert tts != null;
    return possibleReachabilities.contains(tts);
  }

  public boolean containsWithZeroes(ReachState tts) {
    assert tts != null;

    if( possibleReachabilities.contains(tts) ) {
      return true;
    }

    Iterator itr = iterator();
    while( itr.hasNext() ) {
      ReachState ttsThis = (ReachTupleSet) itr.next();
      if( ttsThis.containsWithZeroes(tts) ) {
	return true;
      }
    }

    return false;    
  }


  public boolean containsSuperSet(ReachState tts) {
    return containsSuperSet( tts, false );
  }

  public boolean containsStrictSuperSet(ReachState tts) {
    return containsSuperSet( tts, true );
  }

  public boolean containsSuperSet(ReachState tts, boolean strict) {
    assert tts != null;

    if( !strict && possibleReachabilities.contains(tts) ) {
      return true;
    }

    Iterator itr = iterator();
    while( itr.hasNext() ) {
      ReachState ttsThis = (ReachTupleSet) itr.next();
      if( strict ) {
        if( !tts.equals(ttsThis) && tts.isSubset(ttsThis) ) {
          return true;
        }
      } else {
        if( tts.isSubset(ttsThis) ) {
          return true;
        }
      }
    }

    return false;    
  }


  public boolean containsTuple(ReachTuple tt) {
    Iterator itr = iterator();
    while( itr.hasNext() ) {
      ReachState tts = (ReachTupleSet) itr.next();
      if( tts.containsTuple(tt) ) {
	return true;
      }
    }
    return false;
  }

  public boolean containsTupleSetWithBoth(ReachTuple tt1, ReachTuple tt2) {
    Iterator itr = iterator();
    while( itr.hasNext() ) {
      ReachState tts = (ReachTupleSet) itr.next();
      if( tts.containsTuple(tt1) && tts.containsTuple(tt2) ) {
	return true;
      }
    }
    return false;
  }

    public static ReachSet factory(ReachState tts) {
      CanonicalWrapper cw=new CanonicalWrapper(tts);
      if (lookuphash.containsKey(cw))
	  return (ReachSet)lookuphash.get(cw).b;
      ReachSet rs=new ReachSet(tts);
      rs=rs.makeCanonical();
      cw.b=rs;
      lookuphash.put(cw,cw);
      return rs;
  }

    public ReachSet union(ReachState ttsIn) {
	ReachOperation ro=new ReachOperation(this, ttsIn);
	if (unionhash.containsKey(ro)) {
	    return (ReachSet) unionhash.get(ro).c;
	} else {
	    ReachSet rsOut = new ReachSet(this);
	    rsOut.possibleReachabilities.add(ttsIn);
	    ro.c=rsOut=rsOut.makeCanonical();
	    unionhash.put(ro,ro);
	    return rsOut;
	}
    }


  public ReachSet union(ReachSet rsIn) {
      //    assert rsIn != null;
    
      //    assert can.containsKey(this);
      //    assert can.containsKey(rsIn);

    ReachOperation ro=new ReachOperation(this, rsIn);
    if (unionhash.containsKey(ro))
	return (ReachSet) unionhash.get(ro).c;
    else {
	ReachSet rsOut = new ReachSet(this);
	rsOut.possibleReachabilities.addAll(rsIn.possibleReachabilities);
	ro.c=rsOut=rsOut.makeCanonical();
	unionhash.put(ro, ro);
	return rsOut;
    }
  }

  public ReachSet intersection(ReachSet rsIn) {
      //    assert rsIn != null;

    //    assert can.containsKey(this);
    //    assert can.containsKey(rsIn);

    ReachOperation ro=new ReachOperation(this, rsIn);
    if (interhash.containsKey(ro))
	return (ReachSet) interhash.get(ro).c;
    else {
	ReachSet rsOut = new ReachSet();
	Iterator i = this.iterator();
	while( i.hasNext() ) {
	    ReachState tts = (ReachTupleSet) i.next();
	    if( rsIn.possibleReachabilities.contains(tts) ) {
		rsOut.possibleReachabilities.add(tts);
	    }
	}
	ro.c=rsOut=rsOut.makeCanonical();
	interhash.put(ro,ro);
	return rsOut;
    }
  }


  public ReachSet add(ReachState tts) {
    assert tts != null;
    return union(tts);
  }

  public ReachSet remove(ReachState tts) {
    assert tts != null;
    ReachSet rsOut = new ReachSet(this);
    assert rsOut.possibleReachabilities.remove(tts);
    return rsOut.makeCanonical();
  }

  public ReachSet removeTokenAIfTokenB(ReachTuple ttA,
					      ReachTuple ttB) {
    assert ttA != null;
    assert ttB != null;

    ReachSet rsOut = new ReachSet();

    Iterator i = this.iterator();
    while( i.hasNext() ) {
      ReachState tts = (ReachTupleSet) i.next();
      if( tts.containsTuple( ttB ) ) {
	rsOut.possibleReachabilities.add( tts.remove(ttA) );
      } else {
	rsOut.possibleReachabilities.add( tts );
      }
    }    

    return rsOut.makeCanonical();    
  }


  public ReachSet applyChangeSet(ChangeSet C, boolean keepSourceState) {
    assert C != null;

    ReachSet rsOut = new ReachSet();

    Iterator i = this.iterator();
    while( i.hasNext() ) {
      ReachState tts = (ReachTupleSet) i.next();

      boolean changeFound = false;

      Iterator<ChangeTuple> itrC = C.iterator();
      while( itrC.hasNext() ) {
	ChangeTuple c = itrC.next();

	if( tts.equals( c.getSetToMatch() ) ) {
	  rsOut.possibleReachabilities.add( c.getSetToAdd() );
	  changeFound = true;
	}
      }

      if( keepSourceState || !changeFound ) {
	rsOut.possibleReachabilities.add( tts );
      }
    }
    
    return rsOut.makeCanonical();
  }


  public ChangeSet unionUpArityToChangeSet(ReachSet rsIn) {
    assert rsIn != null;

    ChangeSet ctsOut = new ChangeSet();

    Iterator itrO = this.iterator();
    while( itrO.hasNext() ) {
      ReachState o = (ReachTupleSet) itrO.next();

      Iterator itrR = rsIn.iterator();
      while( itrR.hasNext() ) {
	ReachState r = (ReachTupleSet) itrR.next();

	ReachState theUnion = new ReachTupleSet().makeCanonical();

	Iterator itrRelement = r.iterator();
	while( itrRelement.hasNext() ) {
	  ReachTuple ttR = (ReachTuple) itrRelement.next();
	  ReachTuple ttO = o.containsToken(ttR.getToken() );

	  if( ttO != null ) {
	      theUnion = theUnion.union((new ReachState(ttR.unionArity(ttO)).makeCanonical() ) );
	  } else {
	      theUnion = theUnion.union((new ReachState(ttR)).makeCanonical() );
	  }
	}

	Iterator itrOelement = o.iterator();
	while( itrOelement.hasNext() ) {
	  ReachTuple ttO = (ReachTuple) itrOelement.next();
	  ReachTuple ttR = theUnion.containsToken(ttO.getToken() );

	  if( ttR == null ) {
	      theUnion = theUnion.union(new ReachState(ttO).makeCanonical() );
	  }
	}

	if( !theUnion.isEmpty() ) {
	    ctsOut = ctsOut.union((new ChangeSet(new ChangeTuple(o, theUnion) )).makeCanonical() );
	}
      }
    }

    return ctsOut.makeCanonical();
  }


  public ReachSet ageTokens(AllocSite as) {
    assert as != null;

    ReachSet rsOut = new ReachSet();

    Iterator itrS = this.iterator();
    while( itrS.hasNext() ) {
      ReachState tts = (ReachTupleSet) itrS.next();
      rsOut.possibleReachabilities.add(tts.ageTokens(as) );
    }

    return rsOut.makeCanonical();
  }


  public ReachSet unshadowTokens(AllocSite as) {
    assert as != null;

    ReachSet rsOut = new ReachSet();

    Iterator itrS = this.iterator();
    while( itrS.hasNext() ) {
      ReachState tts = (ReachTupleSet) itrS.next();
      rsOut.possibleReachabilities.add(tts.unshadowTokens(as) );
    }

    return rsOut.makeCanonical();
  }


  public ReachSet toShadowTokens(AllocSite as) {
    assert as != null;

    ReachSet rsOut = new ReachSet();

    Iterator itrS = this.iterator();
    while( itrS.hasNext() ) {
      ReachState tts = (ReachTupleSet) itrS.next();
      rsOut.possibleReachabilities.add(tts.toShadowTokens(as) );
    }

    return rsOut.makeCanonical();
  }


  public ReachSet pruneBy(ReachSet rsIn) {
    assert rsIn != null;

    ReachSet rsOut = new ReachSet();

    Iterator itrB = this.iterator();
    while( itrB.hasNext() ) {
      ReachState ttsB = (ReachTupleSet) itrB.next();

      boolean subsetExists = false;

      Iterator itrA = rsIn.iterator();
      while( itrA.hasNext() && !subsetExists ) {
	ReachState ttsA = (ReachTupleSet) itrA.next();

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


  public ReachSet exhaustiveArityCombinations() {
      ReachSet rsOut = (new ReachSet()).makeCanonical();

    int numDimensions = this.possibleReachabilities.size();

    if( numDimensions > 3 ) {
      // for problems that are too big, punt and use less
      // precise arity for reachability information
      ReachState ttsImprecise = new ReachTupleSet().makeCanonical();

      Iterator<ReachState> itrThis = this.iterator();
      while( itrThis.hasNext() ) {
	ReachState ttsUnit = itrThis.next();
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
      ReachState ttsCoordinate = new ReachTupleSet().makeCanonical();
      Iterator<ReachState> ttsItr = this.iterator();
      for( int i = 0; i < numDimensions; ++i ) {
	assert ttsItr.hasNext();
	ReachState ttsUnit = ttsItr.next();
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

    if( !(o instanceof ReachSet) ) {
      return false;
    }

    ReachSet rs = (ReachSet) o;
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
	System.out.println("IF YOU SEE THIS A CANONICAL ReachSet CHANGED");
	Integer x = null;
	x.toString();
      }
    }

    return currentHash;
  }


  public String toStringEscapeNewline( boolean hideSubsetReachability ) {
    String s = "[";

    Iterator<ReachState> i = this.iterator();
    while( i.hasNext() ) {
      ReachState tts = i.next();

      // skip this if there is a superset already
      if( hideSubsetReachability &&
          containsStrictSuperSet( tts ) ) {
        continue;
      }

      s += tts;
      if( i.hasNext() ) {
	s += "\\n";
      }
    }

    s += "]";
    return s;
  }
  

  public String toString() {
    return toString( false );
  }

  public String toString( boolean hideSubsetReachability ) {
    String s = "[";

    Iterator<ReachState> i = this.iterator();
    while( i.hasNext() ) {
      ReachState tts = i.next();

      // skip this if there is a superset already
      if( hideSubsetReachability &&
          containsStrictSuperSet( tts ) ) {
        continue;
      }

      s += tts;
      if( i.hasNext() ) {
	s += "\n";
      }
    }

    s += "]";
    return s;
  }
}
