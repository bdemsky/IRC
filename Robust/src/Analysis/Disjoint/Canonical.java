package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


////////////////////////////////////////////////
//
//  important note!  The static operations in this class that take
//  canonicals and produce a canonical result sometimes need a
//  "working copy" that IS NOT CANONICAL.  So, even though it isn't
//  perfectly clean, Canonical constructors have been changed from
//  private to protected and they may be used in this file--however,
//  only use a constructor for an object that will mutate during the
//  calculation--use the factory method to obtain everything else!
//  This is CRITICAL!
//
//  What this boils down to is that the only normally constructed
//  object in a canonical operation should be the result out.
//
////////////////////////////////////////////////


abstract public class Canonical {

  // for generating unique canonical values
  private static int canonicalCount = 1;

  // the canon of objects
  private static Hashtable<Canonical, Canonical>
  canon =  new Hashtable<Canonical, Canonical>();



  public static Canonical makeCanonical(Canonical c) {

    if( canon.containsKey(c) ) {
      return canon.get(c);
    }

    c.canonicalValue = canonicalCount;
    ++canonicalCount;
    canon.put(c, c);
    return c;
  }


  // any Canonical with value still 0 is NOT CANONICAL!
  private int canonicalValue = 0;

  public boolean isCanonical() {
    return canonicalValue != 0;
  }

  public int getCanonicalValue() {
    assert isCanonical();
    return canonicalValue;
  }



  abstract public boolean equalsSpecific(Object o);

  final public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof Canonical) ) {
      return false;
    }

    Canonical c = (Canonical) o;

    if( this.canonicalValue == 0 ||
        c.canonicalValue == 0
        ) {
      return equalsSpecific(o);
    }

    return this.canonicalValue == c.canonicalValue;
  }


  // canonical objects should never be modified
  // and therefore have changing hash codes, so
  // use a standard canonical hash code method to
  // enforce this, and define a specific hash code
  // method each specific subclass should implement
  abstract public int hashCodeSpecific();

  private boolean hasHash = false;
  private int oldHash;
  final public int hashCode() {

    // the quick mode
    if( DisjointAnalysis.releaseMode && hasHash ) {
      return oldHash;
    }

    // the safe mode
    int hash = hashCodeSpecific();

    if( hasHash ) {
      if( oldHash != hash ) {
        throw new Error("A CANONICAL HASH CHANGED");
      }
    } else {
      hasHash = true;
      oldHash = hash;
    }

    return hash;
  }


  // mapping of a non-trivial operation to its result
  private static Hashtable<CanonicalOp, Canonical>
  op2result = new Hashtable<CanonicalOp, Canonical>();



  ///////////////////////////////////////////////////////////
  //
  //  Everything below are static methods that implement
  //  "mutating" operations on Canonical objects by returning
  //  the canonical result.  If the op is non-trivial the
  //  canonical result is hashed by its defining CononicalOp
  //
  ///////////////////////////////////////////////////////////


  // not weighty, don't bother with caching
  public static ReachTuple unionUpArity(ReachTuple rt1,
                                        ReachTuple rt2) {
    assert rt1 != null;
    assert rt2 != null;
    assert rt1.isCanonical();
    assert rt2.isCanonical();
    assert rt1.hrnID          == rt2.hrnID;
    assert rt1.isMultiObject  == rt2.isMultiObject;
    assert rt1.isOutOfContext == rt2.isOutOfContext;

    ReachTuple out;

    if( rt1.isMultiObject ) {
      // on two non-ZERO arity multi regions, union arity is always
      // ZERO-OR-MORE
      out = ReachTuple.factory(rt1.hrnID,
                               true,
                               ReachTuple.ARITY_ZEROORMORE,
                               rt1.isOutOfContext);

    } else {
      // a single object region can only be ARITY_ONE (or zero by
      // being absent)
      assert rt1.arity == ReachTuple.ARITY_ONE;
      out = rt1;
    }

    assert out.isCanonical();
    return out;
  }

  // not weighty, no caching
  public static ReachTuple changeHrnIDTo(ReachTuple rt,
                                         Integer hrnIDToChangeTo) {
    assert rt              != null;
    assert hrnIDToChangeTo != null;

    ReachTuple out = ReachTuple.factory(hrnIDToChangeTo,
                                        rt.isMultiObject,
                                        rt.arity,
                                        rt.isOutOfContext
                                        );
    assert out.isCanonical();
    return out;
  }


  public static ReachState attach(ReachState rs,
                                  ExistPredSet preds) {
    assert rs    != null;
    assert preds != null;
    assert rs.isCanonical();
    assert preds.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSTATE_ATTACH_EXISTPREDSET,
                      rs,
                      preds);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachState) result;
    }

    // otherwise, no cached result...
    ReachState out = new ReachState();
    out.reachTuples.addAll(rs.reachTuples);
    out.preds = Canonical.join(rs.preds,
                               preds);

    out = (ReachState) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static ReachState add(ReachState rs,
                               ReachTuple rt) {
    assert rs != null;
    assert rt != null;

    // this is only safe if we are certain the new tuple's
    // ID doesn't already appear in the reach state
    assert rs.containsHrnID(rt.getHrnID(),
                            rt.isOutOfContext() ) == null;

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSTATE_ADD_REACHTUPLE,
                      rs,
                      rt);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachState) result;
    }

    // otherwise, no cached result...
    ReachState out = new ReachState();
    out.reachTuples.addAll(rs.reachTuples);
    out.reachTuples.add(rt);
    out.preds = rs.preds;

    out = (ReachState) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static ReachState unionUpArity(ReachState rs1,
                                        ReachState rs2) {
    assert rs1 != null;
    assert rs2 != null;
    assert rs1.isCanonical();
    assert rs2.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSTATE_UNIONUPARITY_REACHSTATE,
                      rs1,
                      rs2);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachState) result;
    }

    // otherwise, no cached result...
    ReachState out = new ReachState();

    // first add everything from 1, and if it is
    // also in 2 take the union of the tuple arities
    Iterator<ReachTuple> rtItr = rs1.iterator();
    while( rtItr.hasNext() ) {
      ReachTuple rt1 = rtItr.next();
      ReachTuple rt2 = rs2.containsHrnID(rt1.getHrnID(),
                                         rt1.isOutOfContext()
                                         );
      if( rt2 != null ) {
        out.reachTuples.add(unionUpArity(rt1, rt2) );
      } else {
        out.reachTuples.add(rt1);
      }
    }

    // then add everything in 2 that wasn't in 1
    rtItr = rs2.iterator();
    while( rtItr.hasNext() ) {
      ReachTuple rt2 = rtItr.next();
      ReachTuple rt1 = rs1.containsHrnID(rt2.getHrnID(),
                                         rt2.isOutOfContext()
                                         );
      if( rt1 == null ) {
        out.reachTuples.add(rt2);
      }
    }

    out.preds = Canonical.join(rs1.getPreds(),
                               rs2.getPreds()
                               );

    out = (ReachState) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static ReachState addUpArity(ReachState rs,
                                      ReachTuple rt) {
    assert rs != null;
    assert rt != null;

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSTATE_ADDUPARITY_REACHTUPLE,
                      rs,
                      rt);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachState) result;
    }

    // otherwise, no cached result...
    ReachState out;

    // the reason for this add is that we are aware a tuple
    // with the same hrnID might already be in the state, so
    // if it is we should combine properly
    ReachState rtOnly = ReachState.factory(rt);
    out = Canonical.unionUpArity(rs, rtOnly);

    op2result.put(op, out);
    return out;
  }


  public static ReachState remove(ReachState rs, ReachTuple rt) {
    assert rs != null;
    assert rt != null;

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSTATE_REMOVE_REACHTUPLE,
                      rs,
                      rt);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachState) result;
    }

    // otherwise, no cached result...
    ReachState out = new ReachState();
    out.reachTuples.addAll(rs.reachTuples);
    out.reachTuples.remove(rt);
    out.preds = rs.preds;

    out = (ReachState) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static ReachState ageTuplesFrom(ReachState rs,
                                         AllocSite as) {
    assert rs != null;
    assert as != null;
    assert rs.isCanonical();
    assert as.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSTATE_AGETUPLESFROM_ALLOCSITE,
                      rs,
                      as);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachState) result;
    }

    // otherwise, no cached result...
    ReachState out = new ReachState();

    ReachTuple rtSummary = null;
    ReachTuple rtOldest  = null;

    Iterator<ReachTuple> rtItr = rs.iterator();
    while( rtItr.hasNext() ) {
      ReachTuple rt    = rtItr.next();
      Integer hrnID = rt.getHrnID();
      int age   = as.getAgeCategory(hrnID);

      // hrnIDs not associated with
      // the site should be left alone, and
      // those from this site but out-of-context
      if( age == AllocSite.AGE_notInThisSite ||
          rt.isOutOfContext()
          ) {
        out.reachTuples.add(rt);

      } else if( age == AllocSite.AGE_summary ) {
        // remember the summary tuple, but don't add it
        // we may combine it with the oldest tuple
        rtSummary = rt;

      } else if( age == AllocSite.AGE_oldest ) {
        // found an oldest hrnID, again just remember
        // for later
        rtOldest = rt;

      } else {
        assert age == AllocSite.AGE_in_I;

        Integer I = as.getAge(hrnID);
        assert I != null;

        // otherwise, we change this hrnID to the
        // next older hrnID
        Integer hrnIDToChangeTo = as.getIthOldest(I + 1);
        ReachTuple rtAged =
          Canonical.changeHrnIDTo(rt, hrnIDToChangeTo);
        out.reachTuples.add(rtAged);
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
    if( rtSummary != null && rtOldest == null ) {
      out.reachTuples.add(rtSummary);

    } else if( rtSummary == null && rtOldest != null ) {
      out.reachTuples.add(ReachTuple.factory(as.getSummary(),
                                             true,   // multi
                                             rtOldest.getArity(),
                                             false   // out-of-context
                                             )
                          );

    } else if( rtSummary != null && rtOldest != null ) {
      out.reachTuples.add(Canonical.unionUpArity(rtSummary,
                                                 ReachTuple.factory(as.getSummary(),
                                                                    true,    // muli
                                                                    rtOldest.getArity(),
                                                                    false    // out-of-context
                                                                    )
                                                 )
                          );
    }

    out.preds = rs.preds;

    out = (ReachState) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }



  public static ReachSet unionORpreds(ReachSet rs1,
                                      ReachSet rs2) {
    assert rs1 != null;
    assert rs2 != null;
    assert rs1.isCanonical();
    assert rs2.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSET_UNIONORPREDS_REACHSET,
                      rs1,
                      rs2);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = new ReachSet();

    // first add everything from 1, and if it was also in 2
    // take the OR of the predicates
    Iterator<ReachState> stateItr = rs1.iterator();
    while( stateItr.hasNext() ) {
      ReachState state1 = stateItr.next();
      ReachState state2 = rs2.containsIgnorePreds(state1);

      if( state2 != null ) {
        out.reachStates.add(ReachState.factory(state1.reachTuples,
                                               Canonical.join(state1.preds,
                                                              state2.preds
                                                              )
                                               ) );
      } else {
        out.reachStates.add(state1);
      }
    }

    // then add everything in 2 that wasn't in 1
    stateItr = rs2.iterator();
    while( stateItr.hasNext() ) {
      ReachState state2 = stateItr.next();
      ReachState state1 = rs1.containsIgnorePreds(state2);

      if( state1 == null ) {
        out.reachStates.add(state2);
      }
    }

    out = (ReachSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  // NOTE: when taking the intersection of two reach sets it
  // is possible for a reach state to be in both sets, but
  // have different predicates.  Conceptully the best new
  // predicate is an AND of the source predicates, but to
  // avoid eploding states we'll take an overapproximation
  // by preferring the predicates from the state in the FIRST
  // set, so order of arguments matters
  public static ReachSet intersection(ReachSet rs1,
                                      ReachSet rs2) {
    assert rs1 != null;
    assert rs2 != null;
    assert rs1.isCanonical();
    assert rs2.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSET_INTERSECTION_REACHSET,
                      rs1,
                      rs2);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = new ReachSet();
    Iterator<ReachState> itr = rs1.iterator();
    while( itr.hasNext() ) {
      ReachState state1 = (ReachState) itr.next();
      ReachState state2 = rs2.containsIgnorePreds(state1);
      if( state2 != null ) {
        // prefer the predicates on state1, an overapproximation
        // of state1 preds AND state2 preds
        out.reachStates.add(state1);
      }
    }

    out = (ReachSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static ReachSet add(ReachSet rs,
                             ReachState state) {
    return unionORpreds(rs,
                        ReachSet.factory(state)
                        );
  }

  public static ReachSet remove(ReachSet rs,
                                ReachState state) {
    assert rs    != null;
    assert state != null;
    assert rs.isCanonical();
    assert state.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSET_REMOVE_REACHSTATE,
                      rs,
                      state);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = new ReachSet();
    out.reachStates.addAll(rs.reachStates);
    out.reachStates.remove(state);

    out = (ReachSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static ReachSet applyChangeSet(ReachSet rs,
                                        ChangeSet cs,
                                        boolean keepSourceState) {
    assert rs != null;
    assert cs != null;
    assert rs.isCanonical();
    assert cs.isCanonical();

    // this primitive operand stuff is just a way to
    // ensure distinct inputs to a CanonicalOp
    int primOperand;
    if( keepSourceState ) {
      primOperand = 0x1f;
    } else {
      primOperand = 0x2b;
    }

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSET_APPLY_CHANGESET,
                      rs,
                      cs,
                      primOperand);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = new ReachSet();

    Iterator<ReachState> stateItr = rs.iterator();
    while( stateItr.hasNext() ) {
      ReachState stateOrig = stateItr.next();

      boolean changeFound = false;

      Iterator<ChangeTuple> ctItr = cs.iterator();
      while( ctItr.hasNext() ) {
        ChangeTuple ct = ctItr.next();

        if( stateOrig.equalsIgnorePreds(ct.getStateToMatch() ) ) {
          // use the new state, but the original predicates
          ReachState stateNew =
            ReachState.factory(ct.getStateToAdd().reachTuples,
                               stateOrig.preds
                               );
          out.reachStates.add(stateNew);
          changeFound = true;
        }
      }

      if( keepSourceState || !changeFound ) {
        out.reachStates.add(stateOrig);
      }
    }

    out = (ReachSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static ChangeSet unionUpArityToChangeSet(ReachSet rsO,
                                                  ReachSet rsR) {
    assert rsO != null;
    assert rsR != null;
    assert rsO.isCanonical();
    assert rsR.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSET_UNIONTOCHANGESET_REACHSET,
                      rsO,
                      rsR);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ChangeSet) result;
    }

    // otherwise, no cached result...
    ChangeSet out = ChangeSet.factory();

    Iterator<ReachState> itrO = rsO.iterator();
    while( itrO.hasNext() ) {
      ReachState o = itrO.next();

      Iterator<ReachState> itrR = rsR.iterator();
      while( itrR.hasNext() ) {
        ReachState r = itrR.next();

        ReachState theUnion = ReachState.factory();

        Iterator<ReachTuple> itrRelement = r.iterator();
        while( itrRelement.hasNext() ) {
          ReachTuple rtR = itrRelement.next();
          ReachTuple rtO = o.containsHrnID(rtR.getHrnID(),
                                           rtR.isOutOfContext()
                                           );
          if( rtO != null ) {
            theUnion = Canonical.add(theUnion,
                                     Canonical.unionUpArity(rtR,
                                                            rtO
                                                            )
                                     );
          } else {
            theUnion = Canonical.add(theUnion,
                                     rtR
                                     );
          }
        }

        Iterator<ReachTuple> itrOelement = o.iterator();
        while( itrOelement.hasNext() ) {
          ReachTuple rtO = itrOelement.next();
          ReachTuple rtR = theUnion.containsHrnID(rtO.getHrnID(),
                                                  rtO.isOutOfContext()
                                                  );
          if( rtR == null ) {
            theUnion = Canonical.add(theUnion,
                                     rtO
                                     );
          }
        }

        if( !theUnion.isEmpty() ) {
          out =
            Canonical.union(out,
                            ChangeSet.factory(
                              ChangeTuple.factory(o, theUnion)
                              )
                            );
        }
      }
    }

    assert out.isCanonical();
    op2result.put(op, out);
    return out;
  }


  public static ReachSet ageTuplesFrom(ReachSet rs,
                                       AllocSite as) {
    assert rs != null;
    assert as != null;
    assert rs.isCanonical();
    assert as.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSET_AGETUPLESFROM_ALLOCSITE,
                      rs,
                      as);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = new ReachSet();

    Iterator<ReachState> itrS = rs.iterator();
    while( itrS.hasNext() ) {
      ReachState state = itrS.next();
      out.reachStates.add(Canonical.ageTuplesFrom(state, as) );
    }

    out = (ReachSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static ReachSet pruneBy(ReachSet rsO,
                                 ReachSet rsP) {
    assert rsO != null;
    assert rsP != null;
    assert rsO.isCanonical();
    assert rsP.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSET_PRUNEBY_REACHSET,
                      rsO,
                      rsP);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = new ReachSet();

    Iterator<ReachState> itrO = rsO.iterator();
    while( itrO.hasNext() ) {
      ReachState stateO = itrO.next();

      boolean subsetExists = false;

      Iterator<ReachState> itrP = rsP.iterator();
      while( itrP.hasNext() && !subsetExists ) {
        ReachState stateP = itrP.next();

        if( stateP.isSubset(stateO) ) {
          subsetExists = true;
        }
      }

      if( subsetExists ) {
        out.reachStates.add(stateO);
      }
    }

    out = (ReachSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static ChangeSet union(ChangeSet cs1,
                                ChangeSet cs2) {
    assert cs1 != null;
    assert cs2 != null;
    assert cs1.isCanonical();
    assert cs2.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.CHANGESET_UNION_CHANGESET,
                      cs1,
                      cs2);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ChangeSet) result;
    }

    // otherwise, no cached result...
    ChangeSet out = new ChangeSet();
    out.changeTuples.addAll(cs1.changeTuples);
    out.changeTuples.addAll(cs2.changeTuples);

    out = (ChangeSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }

  public static ChangeSet add(ChangeSet cs,
                              ChangeTuple ct) {
    assert cs != null;
    assert ct != null;
    assert cs.isCanonical();
    assert ct.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.CHANGESET_UNION_CHANGETUPLE,
                      cs,
                      ct);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ChangeSet) result;
    }

    // otherwise, no cached result...
    ChangeSet out = new ChangeSet();
    out.changeTuples.addAll(cs.changeTuples);
    out.changeTuples.add(ct);

    out = (ChangeSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }



  public static ExistPredSet join(ExistPredSet eps1,
                                  ExistPredSet eps2) {

    assert eps1 != null;
    assert eps2 != null;
    assert eps1.isCanonical();
    assert eps2.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.EXISTPREDSET_JOIN_EXISTPREDSET,
                      eps1,
                      eps2);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ExistPredSet) result;
    }

    // otherwise, no cached result...
    ExistPredSet out = new ExistPredSet();
    out.preds.addAll(eps1.preds);
    out.preds.addAll(eps2.preds);

    out = (ExistPredSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }

  public static ExistPredSet add(ExistPredSet eps,
                                 ExistPred ep) {


    assert eps != null;
    assert ep  != null;
    assert eps.isCanonical();
    assert ep.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.EXISTPREDSET_ADD_EXISTPRED,
                      eps,
                      ep);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ExistPredSet) result;
    }

    // otherwise, no cached result...
    ExistPredSet out = new ExistPredSet();
    out.preds.addAll(eps.preds);
    out.preds.add(ep);

    out = (ExistPredSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static ReachSet toCallerContext(ReachSet rs,
                                         AllocSite as) {
    assert rs != null;
    assert as != null;
    assert rs.isCanonical();
    assert as.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSET_TOCALLERCONTEXT_ALLOCSITE,
                      rs,
                      as);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = ReachSet.factory();
    Iterator<ReachState> itr = rs.iterator();
    while( itr.hasNext() ) {
      ReachState state = itr.next();
      out = Canonical.unionORpreds(out,
                                   Canonical.toCallerContext(state, as)
                                   );
    }

    assert out.isCanonical();
    op2result.put(op, out);
    return out;
  }


  public static ReachSet toCallerContext(ReachState state,
                                         AllocSite as) {
    assert state != null;
    assert as    != null;
    assert state.isCanonical();
    assert as.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSTATE_TOCALLERCONTEXT_ALLOCSITE,
                      state,
                      as);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = ReachSet.factory();

    // this method returns a ReachSet instead of a ReachState
    // because the companion method, toCallee, translates
    // symbols many-to-one, so state->state
    // but this method does an ~inverse mapping, one-to-many
    // so one state can split into a set of branched states

    // 0    -> -0
    // 1    -> -1
    // 2S   -> -2S
    // 2S*  -> -2S*
    //
    // 0?   -> 0
    // 1?   -> 1
    // 2S?  -> 2S
    //      -> 0?
    //      -> 1?
    //      -> 2S?
    // 2S?* -> {2S*, 2S?*}

    boolean found2Sooc = false;

    ReachState baseState = ReachState.factory();

    Iterator<ReachTuple> itr = state.iterator();
    while( itr.hasNext() ) {
      ReachTuple rt = itr.next();

      int age = as.getAgeCategory(rt.getHrnID() );

      if( age == AllocSite.AGE_notInThisSite ) {
        // things not from the site just go back in
        baseState = Canonical.addUpArity(baseState, rt);

      } else if( age == AllocSite.AGE_summary ) {

        if( rt.isOutOfContext() ) {
          // if its out-of-context, we only deal here with the ZERO-OR-MORE
          // arity, if ARITY-ONE we'll branch the base state after the loop
          if( rt.getArity() == ReachTuple.ARITY_ZEROORMORE ) {
            // add two overly conservative symbols to reach state (PUNTING)

            baseState = Canonical.addUpArity(baseState,
                                             ReachTuple.factory(as.getSummary(),
                                                                true,   // multi
                                                                ReachTuple.ARITY_ZEROORMORE,
                                                                false   // out-of-context
                                                                )
                                             );

            baseState = Canonical.addUpArity(baseState,
                                             ReachTuple.factory(as.getSummary(),
                                                                true,   // multi
                                                                ReachTuple.ARITY_ZEROORMORE,
                                                                true    // out-of-context
                                                                )
                                             );
          } else {
            assert rt.getArity() == ReachTuple.ARITY_ONE;
            found2Sooc = true;
          }

        } else {
          // the in-context just becomes shadow
          baseState = Canonical.addUpArity(baseState,
                                           ReachTuple.factory(as.getSummaryShadow(),
                                                              true,   // multi
                                                              rt.getArity(),
                                                              false    // out-of-context
                                                              )
                                           );
        }


      } else {
        // otherwise age is in range [0, k]
        Integer I = as.getAge(rt.getHrnID() );
        assert I != null;
        assert !rt.isMultiObject();
        assert rt.getArity() == ReachTuple.ARITY_ONE;

        if( rt.isOutOfContext() ) {
          // becomes the in-context version
          baseState = Canonical.addUpArity(baseState,
                                           ReachTuple.factory(rt.getHrnID(),
                                                              false,   // multi
                                                              ReachTuple.ARITY_ONE,
                                                              false    // out-of-context
                                                              )
                                           );

        } else {
          // otherwise the ith symbol becomes shadowed
          baseState = Canonical.addUpArity(baseState,
                                           ReachTuple.factory(-rt.getHrnID(),
                                                              false,   // multi
                                                              ReachTuple.ARITY_ONE,
                                                              false    // out-of-context
                                                              )
                                           );
        }
      }
    }

    // now either make branches if we have 2S?, or
    // the current baseState is the only state we need
    if( found2Sooc ) {
      // make a branch with every possibility of the one-to-many
      // mapping for 2S? appended to the baseState
      out = Canonical.add(out,
                          Canonical.addUpArity(baseState,
                                               ReachTuple.factory(as.getSummary(),
                                                                  true,    // multi
                                                                  ReachTuple.ARITY_ONE,
                                                                  false     // out-of-context
                                                                  )
                                               )
                          );

      out = Canonical.add(out,
                          Canonical.addUpArity(baseState,
                                               ReachTuple.factory(as.getSummary(),
                                                                  true,    // multi
                                                                  ReachTuple.ARITY_ONE,
                                                                  true     // out-of-context
                                                                  )
                                               )
                          );

      for( int i = 0; i < as.getAllocationDepth(); ++i ) {
        out = Canonical.add(out,
                            Canonical.addUpArity(baseState,
                                                 ReachTuple.factory(as.getIthOldest(i),
                                                                    false,    // multi
                                                                    ReachTuple.ARITY_ONE,
                                                                    true     // out-of-context
                                                                    )
                                                 )
                            );
      }

    } else {
      // just use current baseState
      out = Canonical.add(out,
                          baseState);
    }


    assert out.isCanonical();
    op2result.put(op, out);
    return out;
  }


  public static ReachSet unshadow(ReachSet rs,
                                  AllocSite as) {
    assert rs != null;
    assert as != null;
    assert rs.isCanonical();
    assert as.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSET_UNSHADOW_ALLOCSITE,
                      rs,
                      as);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = ReachSet.factory();
    Iterator<ReachState> itr = rs.iterator();
    while( itr.hasNext() ) {
      ReachState state = itr.next();
      out = Canonical.add(out,
                          Canonical.unshadow(state, as)
                          );
    }

    assert out.isCanonical();
    op2result.put(op, out);
    return out;
  }

  public static ReachState unshadow(ReachState state,
                                    AllocSite as) {
    assert state != null;
    assert as    != null;
    assert state.isCanonical();
    assert as.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSTATE_UNSHADOW_ALLOCSITE,
                      state,
                      as);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachState) result;
    }

    // this is the current mapping, where 0, 1, 2S were allocated
    // in the current context, 0?, 1? and 2S? were allocated in a
    // previous context, and we're translating to a future context
    //
    // -0   -> 0
    // -1   -> 1
    // -2S  -> 2S

    // otherwise, no cached result...
    ReachState out = ReachState.factory();
    Iterator<ReachTuple> itr = state.iterator();
    while( itr.hasNext() ) {
      ReachTuple rt = itr.next();

      int age = as.getShadowAgeCategory(rt.getHrnID() );

      if( age == AllocSite.SHADOWAGE_notInThisSite ) {
        // things not from the site just go back in
        out = Canonical.addUpArity(out, rt);

      } else {
        assert !rt.isOutOfContext();

        // otherwise unshadow it
        out = Canonical.addUpArity(out,
                                   ReachTuple.factory(-rt.getHrnID(),
                                                      rt.isMultiObject(),
                                                      rt.getArity(),
                                                      false
                                                      )
                                   );
      }
    }

    out = Canonical.attach(out,
                           state.getPreds()
                           );

    assert out.isCanonical();
    op2result.put(op, out);
    return out;
  }



  public static ReachState changePredsTo(ReachState rs,
                                         ExistPredSet preds) {
    assert rs != null;
    assert rs.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSTATE_CHANGEPREDSTO_EXISTPREDSET,
                      rs,
                      preds);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachState) result;
    }

    // otherwise, no cached result...
    ReachState out = new ReachState();

    // just remake state with the true predicate attached
    out.reachTuples.addAll(rs.reachTuples);
    out.preds = preds;

    out = (ReachState) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static ReachSet changePredsTo(ReachSet rs,
                                       ExistPredSet preds) {
    assert rs != null;
    assert rs.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.REACHSET_CHANGEPREDSTO_EXISTPREDSET,
                      rs,
                      preds);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = ReachSet.factory();
    Iterator<ReachState> itr = rs.iterator();
    while( itr.hasNext() ) {
      ReachState state = itr.next();
      out = Canonical.add(out,
                          Canonical.changePredsTo(state,
                                                  preds
                                                  )
                          );
    }

    out = (ReachSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static Taint attach(Taint t,
                             ExistPredSet preds) {
    assert t     != null;
    assert preds != null;
    assert t.isCanonical();
    assert preds.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.TAINT_ATTACH_EXISTPREDSET,
                      t,
                      preds);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (Taint) result;
    }

    // otherwise, no cached result...
    Taint out = new Taint(t);
    out.preds = Canonical.join(t.preds,
                               preds);

    out = (Taint) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }

  public static TaintSet add(TaintSet ts,
                             Taint t) {
    assert ts != null;
    assert t  != null;
    assert ts.isCanonical();
    assert t.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.TAINTSET_ADD_TAINT,
                      ts,
                      t);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (TaintSet) result;
    }

    // otherwise, no cached result...
    TaintSet out = new TaintSet();
    out.taints.addAll(ts.taints);
    out.taints.add(t);

    out = (TaintSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static TaintSet addPTR(TaintSet ts,
                                Taint t) {
    return add(ts, t);
  }

  public static TaintSet union(TaintSet ts1,
                               TaintSet ts2) {
    assert ts1 != null;
    assert ts2 != null;
    assert ts1.isCanonical();
    assert ts2.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.TAINTSET_UNION_TAINTSET,
                      ts1,
                      ts2);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (TaintSet) result;
    }

    // otherwise, no cached result...
    TaintSet out = new TaintSet();

    // first add everything from 1, and if it was also in 2
    // take the OR of the predicates
    Iterator<Taint> tItr = ts1.iterator();
    while( tItr.hasNext() ) {
      Taint t1 = tItr.next();
      Taint t2 = ts2.containsIgnorePreds(t1);

      if( t2 != null ) {
        Taint tNew = new Taint(t1);
        tNew.preds = Canonical.join(t1.preds,
                                    t2.preds
                                    );
        tNew = (Taint) makeCanonical(tNew);
        out.taints.add(tNew);
      } else {
        out.taints.add(t1);
      }
    }

    // then add everything in 2 that wasn't in 1
    tItr = ts2.iterator();
    while( tItr.hasNext() ) {
      Taint t2 = tItr.next();
      Taint t1 = ts1.containsIgnorePreds(t2);

      if( t1 == null ) {
        out.taints.add(t2);
      }
    }

    out = (TaintSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }

  public static TaintSet unionPTR(TaintSet ts1,
                                  TaintSet ts2) {
    assert ts1 != null;
    assert ts2 != null;
    assert ts1.isCanonical();
    assert ts2.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.TAINTSET_UNION_TAINTSET,
                      ts1,
                      ts2);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (TaintSet) result;
    }

    // otherwise, no cached result...
    TaintSet out = new TaintSet();

    out.taints.addAll(ts1.taints);
    out.taints.addAll(ts2.taints);
    out= (TaintSet) Canonical.makeCanonical(out);
    op2result.put(op, out);
    return out;
  }

  public static TaintSet unionORpreds(TaintSet ts1,
                                      TaintSet ts2) {
    assert ts1 != null;
    assert ts2 != null;
    assert ts1.isCanonical();
    assert ts2.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.TAINTSET_UNIONORPREDS_TAINTSET,
                      ts1,
                      ts2);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (TaintSet) result;
    }

    // otherwise, no cached result...
    TaintSet out = new TaintSet();

    // first add everything from 1, and if it was also in 2
    // take the OR of the predicates
    Iterator<Taint> tItr = ts1.iterator();
    while( tItr.hasNext() ) {
      Taint t1 = tItr.next();
      Taint t2 = ts2.containsIgnorePreds(t1);

      if( t2 != null ) {
        Taint tNew = new Taint(t1);
        tNew.preds = Canonical.join(t1.preds,
                                    t2.preds
                                    );
        tNew = (Taint) makeCanonical(tNew);
        out.taints.add(tNew);
      } else {
        out.taints.add(t1);
      }
    }

    // then add everything in 2 that wasn't in 1
    tItr = ts2.iterator();
    while( tItr.hasNext() ) {
      Taint t2 = tItr.next();
      Taint t1 = ts1.containsIgnorePreds(t2);

      if( t1 == null ) {
        out.taints.add(t2);
      }
    }

    out = (TaintSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  // BOO, HISS! SESE (rblock) operand does NOT extend
  // Canonical, so we can't cache this op by its
  // canonical arguments--THINK ABOUT A BETTER WAY!
  public static TaintSet removeInContextTaints(TaintSet ts,
                                               FlatSESEEnterNode sese) {
    assert ts != null;
    assert ts.isCanonical();
    assert sese != null;

    // NEVER a cached result... (cry)
    TaintSet out = new TaintSet();

    Iterator<Taint> tItr = ts.iterator();
    while( tItr.hasNext() ) {
      Taint t = tItr.next();

      // what is allowed through?  stall site taints always
      // go through, anything where rblock doesn't match is
      // unaffected, and if the taint has a non-empty predicate
      // it is out of context so it should go through, too
      if( t.getSESE() == null ||
          !t.getSESE().equals(sese) ||
          !t.getPreds().isEmpty()
          ) {
        out.taints.add(t);
      }
    }

    out = (TaintSet) makeCanonical(out);
    //op2result.put( op, out ); CRY CRY
    return out;
  }

  // BOO, HISS! SESE (rblock) operand does NOT extend
  // Canonical, so we can't cache this op by its
  // canonical arguments--THINK ABOUT A BETTER WAY!
  public static TaintSet removeSESETaints(TaintSet ts,
                                          Set<FlatSESEEnterNode> seseSet) {
    assert ts != null;
    assert ts.isCanonical();

    // NEVER a cached result... (cry)
    TaintSet out = new TaintSet();

    Iterator<Taint> tItr = ts.iterator();
    while( tItr.hasNext() ) {
      Taint t = tItr.next();

      // what is allowed through?  stall site taints always
      // go through, anything where rblock doesn't match is
      // unaffected, and if the taint has a non-empty predicate
      // it is out of context so it should go through, too
      if( t.getSESE() == null ||
          !seseSet.contains(t)) {
        out.taints.add(t);
      }
    }

    out = (TaintSet) makeCanonical(out);
    //op2result.put( op, out ); CRY CRY
    return out;
  }

  public static TaintSet removeInContextTaintsNP(TaintSet ts,
                                                 FlatSESEEnterNode sese) {
    assert ts != null;
    assert ts.isCanonical();

    // NEVER a cached result... (cry)
    TaintSet out = new TaintSet();

    Iterator<Taint> tItr = ts.iterator();
    while( tItr.hasNext() ) {
      Taint t = tItr.next();

      // what is allowed through?  stall site taints always
      // go through, anything where rblock doesn't match is
      // unaffected, and if the taint has a non-empty predicate
      // it is out of context so it should go through, too
      if( t.getSESE()!=null && t.getSESE()!=sese) {
        out.taints.add(t);
      }
    }

    out = (TaintSet) makeCanonical(out);
    return out;
  }

  public static TaintSet removeStallSiteTaints(TaintSet ts) {
    assert ts != null;
    assert ts.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.TAINTSET_REMOVESTALLSITETAINTS,
                      ts,
                      ts);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (TaintSet) result;
    }

    // otherwise, no cached result...
    TaintSet out = new TaintSet();

    Iterator<Taint> tItr = ts.iterator();
    while( tItr.hasNext() ) {
      Taint t = tItr.next();

      // only take non-stall site taints onward
      if( t.getStallSite() == null ) {
        out.taints.add(t);
      }
    }

    out = (TaintSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static Taint changePredsTo(Taint t,
                                    ExistPredSet preds) {
    assert t != null;
    assert t.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.TAINT_CHANGEPREDSTO_EXISTPREDSET,
                      t,
                      preds);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (Taint) result;
    }

    // otherwise, no cached result...
    Taint out = new Taint(t.sese,
                          t.stallSite,
                          t.var,
                          t.allocSite,
                          t.fnDefined,
                          preds
                          );

    out = (Taint) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }


  public static TaintSet changePredsTo(TaintSet ts,
                                       ExistPredSet preds) {
    assert ts != null;
    assert ts.isCanonical();

    CanonicalOp op =
      new CanonicalOp(CanonicalOp.TAINTSET_CHANGEPREDSTO_EXISTPREDSET,
                      ts,
                      preds);

    Canonical result = op2result.get(op);
    if( result != null ) {
      return (TaintSet) result;
    }

    // otherwise, no cached result...
    TaintSet out = TaintSet.factory();
    Iterator<Taint> itr = ts.iterator();
    while( itr.hasNext() ) {
      Taint t = itr.next();
      out = Canonical.add(out,
                          Canonical.changePredsTo(t, preds)
                          );
    }

    out = (TaintSet) makeCanonical(out);
    op2result.put(op, out);
    return out;
  }



  // BOO, HISS! FlatNode operand does NOT extend
  // Canonical, so we can't cache this op by its
  // canonical arguments--THINK ABOUT A BETTER WAY!
  public static Taint changeWhereDefined(Taint t,
                                         FlatNode pp) {
    assert t != null;
    assert t.isCanonical();

    // never a cached result...
    Taint out = new Taint(t.sese,
                          t.stallSite,
                          t.var,
                          t.allocSite,
                          pp,
                          t.preds
                          );

    out = (Taint) makeCanonical(out);
    //op2result.put( op, out ); CRY CRY
    return out;
  }

  // BOO, HISS! FlatNode operand does NOT extend
  // Canonical, so we can't cache this op by its
  // canonical arguments--THINK ABOUT A BETTER WAY!
  public static TaintSet changeWhereDefined(TaintSet ts,
                                            FlatNode pp) {
    assert ts != null;
    assert ts.isCanonical();

    // never a cached result...
    TaintSet out = TaintSet.factory();
    Iterator<Taint> itr = ts.iterator();
    while( itr.hasNext() ) {
      Taint t = itr.next();
      out = Canonical.add(out,
                          Canonical.changeWhereDefined(t, pp)
                          );
    }

    out = (TaintSet) makeCanonical(out);
    //op2result.put( op, out ); CRY CRY
    return out;
  }

}
