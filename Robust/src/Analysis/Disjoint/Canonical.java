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
  


 public static Canonical makeCanonical( Canonical c ) {

    if( canon.containsKey( c ) ) {
      return canon.get( c );
    }
    
    c.canonicalValue = canonicalCount;
    ++canonicalCount;
    canon.put( c, c );
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

  
  // canonical objects should never be modified
  // and therefore have changing hash codes, so
  // use a standard canonical hash code method to
  // enforce this, and define a specific hash code
  // method each specific subclass should implement
  abstract public int hashCodeSpecific();

  private boolean hasHash = false;
  private int     oldHash;
  final public int hashCode() {
    int hash = hashCodeSpecific();

    if( hasHash ) {
      if( oldHash != hash ) {
        throw new Error( "A CANONICAL HASH CHANGED" );
      }
    } else {
      hasHash = true;
      oldHash = hash;
    }
    
    return hash;
  }




  // mapping of a non-trivial operation to its result
  private static    Hashtable<CanonicalOp, Canonical> 
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
  public static ReachTuple unionArity( ReachTuple rt1,
                                       ReachTuple rt2 ) {
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
      out = ReachTuple.factory( rt1.hrnID, 
                                true, 
                                ReachTuple.ARITY_ZEROORMORE,
                                rt1.isOutOfContext );
      
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
  public static ReachTuple changeHrnIDTo( ReachTuple rt,
                                          Integer    hrnIDToChangeTo ) {
    assert rt              != null;
    assert hrnIDToChangeTo != null;

    ReachTuple out = ReachTuple.factory( hrnIDToChangeTo,
                                         rt.isMultiObject,
                                         rt.arity,
                                         rt.isOutOfContext
                                         );
    assert out.isCanonical();
    return out;
  }


  public static ReachState attach( ReachState   rs,
                                   ExistPredSet preds ) {
    assert rs    != null;
    assert preds != null;
    assert rs.isCanonical();
    assert preds.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSTATE_ATTACH_EXISTPREDSET,
                       rs, 
                       preds );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachState) result;
    }
    
    // otherwise, no cached result...
    ReachState out = new ReachState();
    out.reachTuples.addAll( rs.reachTuples );
    out.preds = Canonical.join( rs.preds,
                                preds );

    out = (ReachState) makeCanonical( out );
    op2result.put( op, out );
    return out;
  }


  public static ReachState union( ReachState rs1,
                                  ReachState rs2 ) {
    assert rs1 != null;
    assert rs2 != null;
    assert rs1.isCanonical();
    assert rs2.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSTATE_UNION_REACHSTATE,
                              rs1, 
                              rs2 );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachState) result;
    }

    // otherwise, no cached result...
    ReachState out = new ReachState();
    out.reachTuples.addAll( rs1.reachTuples );
    out.reachTuples.addAll( rs2.reachTuples );
    out.preds = Canonical.join( rs1.getPreds(),
                                rs2.getPreds()
                                );

    out = (ReachState) makeCanonical( out );
    op2result.put( op, out );
    return out;
  }

  // this is just a convenience version of above
  public static ReachState union( ReachState rs,
                                  ReachTuple rt ) {
    assert rs != null;
    assert rt != null;

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSTATE_UNION_REACHTUPLE,
                       rs, 
                       rt );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachState) result;
    }

    // otherwise, no cached result...
    ReachState out = new ReachState();
    out.reachTuples.addAll( rs.reachTuples );
    out.reachTuples.add( rt );
    out.preds = rs.preds;

    out = (ReachState) makeCanonical( out );
    op2result.put( op, out );
    return out;
  }
  

  public static ReachState unionUpArity( ReachState rs1,
                                         ReachState rs2 ) {
    assert rs1 != null;
    assert rs2 != null;
    assert rs1.isCanonical();
    assert rs2.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSTATE_UNIONUPARITY_REACHSTATE,
                       rs1, 
                       rs2 );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachState) result;
    }
    
    // otherwise, no cached result...
    ReachState out = new ReachState();

    Iterator<ReachTuple> rtItr = rs1.iterator();
    while( rtItr.hasNext() ) {
      ReachTuple rt1 = rtItr.next();
      ReachTuple rt2 = rs2.containsHrnID( rt1.getHrnID(),
                                          rt1.isOutOfContext() 
                                          );
      if( rt2 != null ) {
	out.reachTuples.add( unionArity( rt1, rt2 ) );
      } else {
	out.reachTuples.add( rt1 );
      }
    }

    rtItr = rs2.iterator();
    while( rtItr.hasNext() ) {
      ReachTuple rt2 = rtItr.next();
      ReachTuple rto = out.containsHrnID( rt2.getHrnID(),
                                          rt2.isOutOfContext()
                                          );
      if( rto == null ) {
	out.reachTuples.add( rto );
      }
    }

    out.preds = Canonical.join( rs1.getPreds(),
                                rs2.getPreds()
                                );
    
    out = (ReachState) makeCanonical( out );
    op2result.put( op, out );
    return out;
  }

  public static ReachState add( ReachState rs, ReachTuple rt ) {   
    return union( rs, rt );
  }
  
  public static ReachState remove( ReachState rs, ReachTuple rt ) {
    assert rs != null;
    assert rt != null;

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSTATE_REMOVE_REACHTUPLE,
                       rs, 
                       rt );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachState) result;
    }

    // otherwise, no cached result...    
    ReachState out = new ReachState();
    out.reachTuples.addAll( rs.reachTuples );
    out.reachTuples.remove( rt );
    out.preds = rs.preds;

    out = (ReachState) makeCanonical( out );
    op2result.put( op, out );
    return out;
  }
  
  
  public static ReachState ageTuplesFrom( ReachState rs, 
                                          AllocSite  as ) {
    assert rs != null;
    assert as != null;
    assert rs.isCanonical();
    assert as.isCanonical();
    
    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSTATE_AGETUPLESFROM_ALLOCSITE,
                       rs, 
                       as );
    
    Canonical result = op2result.get( op );
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
      Integer    hrnID = rt.getHrnID();
      int        age   = as.getAgeCategory( hrnID );

      // hrnIDs not associated with
      // the site should be left alone, and
      // those from this site but out-of-context
      if( age == AllocSite.AGE_notInThisSite ||
          rt.isOutOfContext()
          ) {
	out.reachTuples.add( rt );

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

	Integer I = as.getAge( hrnID );
	assert I != null;

	// otherwise, we change this hrnID to the
	// next older hrnID
	Integer hrnIDToChangeTo = as.getIthOldest( I + 1 );
	ReachTuple rtAged =
          Canonical.changeHrnIDTo( rt, hrnIDToChangeTo );
	out.reachTuples.add( rtAged );
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
      out.reachTuples.add( rtSummary );

    } else if( rtSummary == null && rtOldest != null ) {
      out.reachTuples.add( ReachTuple.factory( as.getSummary(),
                                               true, // multi
                                               rtOldest.getArity(),
                                               false // out-of-context
                                               )
                           );

    } else if( rtSummary != null && rtOldest != null ) {     
      out.reachTuples.add( Canonical.unionArity( rtSummary,
                                                 ReachTuple.factory( as.getSummary(),
                                                                     true, // muli
                                                                     rtOldest.getArity(),
                                                                     false // out-of-context
                                                                     )
                                                 )
                           );
    }

    out.preds = rs.preds;

    out = (ReachState) makeCanonical( out );
    op2result.put( op, out );
    return out;
  }



  public static ReachSet union( ReachSet rs1,
                                ReachSet rs2 ) {
    assert rs1 != null;
    assert rs2 != null;
    assert rs1.isCanonical();
    assert rs2.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSET_UNION_REACHSET,
                       rs1, 
                       rs2 );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = new ReachSet();
    out.reachStates.addAll( rs1.reachStates );
    out.reachStates.addAll( rs2.reachStates );

    out = (ReachSet) makeCanonical( out );
    op2result.put( op, out );
    return out;
  }

  public static ReachSet union( ReachSet   rs,
                                ReachState state ) {

    assert rs    != null;
    assert state != null;
    assert rs.isCanonical();
    assert state.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSET_UNION_REACHSTATE,
                       rs, 
                       state );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = new ReachSet();
    out.reachStates.addAll( rs.reachStates );
    out.reachStates.add( state );

    out = (ReachSet) makeCanonical( out );
    op2result.put( op, out );
    return out;
  }

  public static ReachSet intersection( ReachSet rs1,
                                       ReachSet rs2 ) {
    assert rs1 != null;
    assert rs2 != null;
    assert rs1.isCanonical();
    assert rs2.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSET_INTERSECTION_REACHSET,
                       rs1, 
                       rs2 );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = new ReachSet();
    Iterator<ReachState> itr = rs1.iterator();
    while( itr.hasNext() ) {
      ReachState state = (ReachState) itr.next();
      if( rs2.reachStates.contains( state ) ) {
        out.reachStates.add( state );
      }
    }

    out = (ReachSet) makeCanonical( out );
    op2result.put( op, out );
    return out;
  }


  public static ReachSet add( ReachSet   rs, 
                              ReachState state ) {
    return union( rs, state );
  }

  public static ReachSet remove( ReachSet   rs,
                                 ReachState state ) {
    assert rs    != null;
    assert state != null;
    assert rs.isCanonical();
    assert state.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSET_REMOVE_REACHSTATE,
                       rs, 
                       state );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...    
    ReachSet out = new ReachSet();
    out.reachStates.addAll( rs.reachStates );
    out.reachStates.remove( state );

    out = (ReachSet) makeCanonical( out );
    op2result.put( op, out );
    return out;
  }


  public static ReachSet applyChangeSet( ReachSet  rs, 
                                         ChangeSet cs,
                                         boolean   keepSourceState ) {
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
      new CanonicalOp( CanonicalOp.REACHSET_APPLY_CHANGESET,
                       rs, 
                       cs,
                       primOperand );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachSet) result;
    }
    
    // otherwise, no cached result...    
    ReachSet out = new ReachSet();

    Iterator<ReachState> stateItr = rs.iterator();
    while( stateItr.hasNext() ) {
      ReachState state = stateItr.next();

      boolean changeFound = false;

      Iterator<ChangeTuple> ctItr = cs.iterator();
      while( ctItr.hasNext() ) {
	ChangeTuple ct = ctItr.next();

	if( state.equals( ct.getSetToMatch() ) ) {
	  out.reachStates.add( ct.getSetToAdd() );
	  changeFound = true;
	}
      }

      if( keepSourceState || !changeFound ) {
	out.reachStates.add( state );
      }
    }

    out = (ReachSet) makeCanonical( out );
    op2result.put( op, out );
    return out;
  }


  public static ChangeSet unionUpArityToChangeSet( ReachSet rsO,
                                                   ReachSet rsR ) {
    assert rsO != null;
    assert rsR != null;
    assert rsO.isCanonical();
    assert rsR.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSET_UNIONTOCHANGESET_REACHSET,
                       rsO, 
                       rsR );
    
    Canonical result = op2result.get( op );
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
	  ReachTuple rtO = o.containsHrnID( rtR.getHrnID(),
                                            rtR.isOutOfContext()
                                            );
	  if( rtO != null ) {
            theUnion = Canonical.union( theUnion,
                                        Canonical.unionArity( rtR,
                                                              rtO
                                                              )
                                        );
	  } else {
            theUnion = Canonical.union( theUnion,
                                        rtR
                                        );
	  }
	}

	Iterator<ReachTuple> itrOelement = o.iterator();
	while( itrOelement.hasNext() ) {
	  ReachTuple rtO = itrOelement.next();
	  ReachTuple rtR = theUnion.containsHrnID( rtO.getHrnID(),
                                                   rtO.isOutOfContext()
                                                   );
	  if( rtR == null ) {
            theUnion = Canonical.union( theUnion,
                                        rtO
                                        );
	  }
	}
        
	if( !theUnion.isEmpty() ) {
          out = 
            Canonical.union( out,
                             ChangeSet.factory( 
                                               ChangeTuple.factory( o, theUnion ) 
                                                )
                             );
	}
      }
    }

    assert out.isCanonical();
    op2result.put( op, out );
    return out;
  }


  public static ReachSet ageTuplesFrom( ReachSet  rs,
                                        AllocSite as ) {
    assert rs != null;
    assert as != null;
    assert rs.isCanonical();
    assert as.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSET_AGETUPLESFROM_ALLOCSITE,
                       rs, 
                       as );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachSet) result;
    }
    
    // otherwise, no cached result...
    ReachSet out = new ReachSet();

    Iterator<ReachState> itrS = rs.iterator();
    while( itrS.hasNext() ) {
      ReachState state = itrS.next();
      out.reachStates.add( Canonical.ageTuplesFrom( state, as ) );
    }
    
    out = (ReachSet) makeCanonical( out );
    op2result.put( op, out );
    return out;    
  }


  public static ReachSet pruneBy( ReachSet rsO, 
                                  ReachSet rsP ) {
    assert rsO != null;
    assert rsP != null;
    assert rsO.isCanonical();
    assert rsP.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSET_PRUNEBY_REACHSET,
                       rsO, 
                       rsP );
    
    Canonical result = op2result.get( op );
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

	if( stateP.isSubset( stateO ) ) {
	  subsetExists = true;
	}
      }
      
      if( subsetExists ) {
	out.reachStates.add( stateO );
      }
    }

    out = (ReachSet) makeCanonical( out );
    op2result.put( op, out );
    return out;    
  }


  public static ChangeSet union( ChangeSet cs1, 
                                 ChangeSet cs2 ) {
    assert cs1 != null;
    assert cs2 != null;
    assert cs1.isCanonical();
    assert cs2.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.CHANGESET_UNION_CHANGESET,
                       cs1, 
                       cs2 );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ChangeSet) result;
    }
    
    // otherwise, no cached result...    
    ChangeSet out = new ChangeSet();
    out.changeTuples.addAll( cs1.changeTuples );
    out.changeTuples.addAll( cs2.changeTuples );

    out = (ChangeSet) makeCanonical( out );
    op2result.put( op, out );
    return out;    
  }

  public static ChangeSet union( ChangeSet   cs, 
                                 ChangeTuple ct ) {
    assert cs != null;
    assert ct != null;
    assert cs.isCanonical();
    assert ct.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.CHANGESET_UNION_CHANGETUPLE,
                       cs, 
                       ct );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ChangeSet) result;
    }
    
    // otherwise, no cached result...    
    ChangeSet out = new ChangeSet();
    out.changeTuples.addAll( cs.changeTuples );
    out.changeTuples.add( ct );
    
    out = (ChangeSet) makeCanonical( out );
    op2result.put( op, out );
    return out;    
  }



  public static ExistPredSet join( ExistPredSet eps1,
                                   ExistPredSet eps2 ) {

    assert eps1 != null;
    assert eps2 != null;
    assert eps1.isCanonical();
    assert eps2.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.EXISTPREDSET_JOIN_EXISTPREDSET,
                       eps1, 
                       eps2 );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ExistPredSet) result;
    }
    
    // otherwise, no cached result...    
    ExistPredSet out = new ExistPredSet();
    out.preds.addAll( eps1.preds );
    out.preds.addAll( eps2.preds );

    out = (ExistPredSet) makeCanonical( out );
    op2result.put( op, out );
    return out;    
  }

  public static ExistPredSet add( ExistPredSet eps,
                                  ExistPred    ep ) {


    assert eps != null;
    assert ep  != null;
    assert eps.isCanonical();
    assert ep.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.EXISTPREDSET_ADD_EXISTPRED,
                       eps, 
                       ep );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ExistPredSet) result;
    }
    
    // otherwise, no cached result...    
    ExistPredSet out = new ExistPredSet();
    out.preds.addAll( eps.preds );
    out.preds.add( ep );
    
    out = (ExistPredSet) makeCanonical( out );
    op2result.put( op, out );
    return out;    
  }


  /*
  public static ReachSet toCalleeContext( ReachSet  rs,
                                          AllocSite as ) {
    assert rs != null;
    assert as != null;
    assert rs.isCanonical();
    assert as.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSET_TOCALLEECONTEXT_ALLOCSITE,
                       rs, 
                       as );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = ReachSet.factory();
    Iterator<ReachState> itr = rs.iterator();
    while( itr.hasNext() ) {
      ReachState state = itr.next();
      out = Canonical.add( out,
                           Canonical.toCalleeContext( state, as )
                           );
    }

    assert out.isCanonical();
    op2result.put( op, out );
    return out;
  }
  */

  /*
  public static ReachState toCalleeContext( ReachState state,
                                            AllocSite  as ) {
    assert state != null;
    assert as    != null;
    assert state.isCanonical();
    assert as.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSTATE_TOCALLEECONTEXT_ALLOCSITE,
                       state, 
                       as );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachState) result;
    }

    // otherwise, no cached result...
    ReachState out = ReachState.factory();
    Iterator<ReachTuple> itr = state.iterator();
    while( itr.hasNext() ) {
      ReachTuple rt = itr.next();

      int age = as.getAgeCategory( rt.getHrnID() );

      // this is the current mapping, where 0, 1, 2S were allocated
      // in the current context, 0?, 1? and 2S? were allocated in a
      // previous context, and we're translating to a future context
      //
      // 0    -> 0?
      // 1    -> 1?
      // 2S   -> 2S?
      // 2S*  -> 2S?*
      //
      // 0?   -> 2S?
      // 1?   -> 2S?
      // 2S?  -> 2S?
      // 2S?* -> 2S?*
      
      if( age == AllocSite.AGE_notInThisSite ) {
        // things not from the site just go back in
	out = Canonical.union( out, rt );

      } else if( age == AllocSite.AGE_summary ||
                 rt.isOutOfContext()
                 ) {
        // the in-context summary and all existing out-of-context
        // stuff all become
        out = Canonical.union( out,
                               ReachTuple.factory( as.getSummary(),
                                                   true, // multi
                                                   rt.getArity(),
                                                   true  // out-of-context
                                                   )
                               );
      } else {
        // otherwise everything else just goes to an out-of-context
        // version, everything else the same
	Integer I = as.getAge( rt.getHrnID() );
	assert I != null;

        assert !rt.isMultiObject();

        out = Canonical.union( out,
                               ReachTuple.factory( rt.getHrnID(),
                                                   rt.isMultiObject(),
                                                   rt.getArity(),
                                                   true  // out-of-context
                                                   )
                               );        
      }
    }

    out = Canonical.attach( out,
                            state.getPreds()
                            );

    assert out.isCanonical();
    op2result.put( op, out );
    return out;
  }
  */


  public static ReachSet toCallerContext( ReachSet  rs,
                                          AllocSite as ) {
    assert rs != null;
    assert as != null;
    assert rs.isCanonical();
    assert as.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSET_TOCALLERCONTEXT_ALLOCSITE,
                       rs, 
                       as );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = ReachSet.factory();
    Iterator<ReachState> itr = rs.iterator();
    while( itr.hasNext() ) {
      ReachState state = itr.next();
      out = Canonical.union( out,
                             Canonical.toCallerContext( state, as )
                             );
    }

    assert out.isCanonical();
    op2result.put( op, out );
    return out;
  }
  

  public static ReachSet toCallerContext( ReachState state,
                                          AllocSite  as ) {
    assert state != null;
    assert as    != null;
    assert state.isCanonical();
    assert as.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSTATE_TOCALLERCONTEXT_ALLOCSITE,
                       state, 
                       as );
    
    Canonical result = op2result.get( op );
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

      int age = as.getAgeCategory( rt.getHrnID() );

      if( age == AllocSite.AGE_notInThisSite ) {
        // things not from the site just go back in
	baseState = Canonical.union( baseState, rt );

      } else if( age == AllocSite.AGE_summary ) {

        if( rt.isOutOfContext() ) {
          // if its out-of-context, we only deal here with the ZERO-OR-MORE
          // arity, if ARITY-ONE we'll branch the base state after the loop
          if( rt.getArity() == ReachTuple.ARITY_ZEROORMORE ) {
            // add two overly conservative symbols to reach state (PUNTING)
            baseState = Canonical.union( baseState,
                                         ReachTuple.factory( as.getSummary(),
                                                             true, // multi
                                                             ReachTuple.ARITY_ZEROORMORE,
                                                             false // out-of-context
                                                             )
                                         );            
            baseState = Canonical.union( baseState,
                                         ReachTuple.factory( as.getSummary(),
                                                             true, // multi
                                                             ReachTuple.ARITY_ZEROORMORE,
                                                             true  // out-of-context
                                                             )
                                         );            
          } else {
            assert rt.getArity() == ReachTuple.ARITY_ONE;
            found2Sooc = true;
          }

        } else {
          // the in-context just becomes shadow
          baseState = Canonical.union( baseState,
                                       ReachTuple.factory( as.getSummaryShadow(),
                                                           true, // multi
                                                           rt.getArity(),
                                                           false  // out-of-context
                                                           )
                                       );
        }


      } else {
        // otherwise age is in range [0, k]
        Integer I = as.getAge( rt.getHrnID() );
        assert I != null;        
        assert !rt.isMultiObject();
        assert rt.getArity() == ReachTuple.ARITY_ONE;

        if( rt.isOutOfContext() ) {
          // becomes the in-context version
          baseState = Canonical.union( baseState,
                                       ReachTuple.factory( rt.getHrnID(),
                                                           false, // multi
                                                           ReachTuple.ARITY_ONE,
                                                           false  // out-of-context
                                                           )
                                       );          

        } else {
          // otherwise the ith symbol becomes shadowed
          baseState = Canonical.union( baseState,
                                       ReachTuple.factory( -rt.getHrnID(),
                                                           false, // multi
                                                           ReachTuple.ARITY_ONE,
                                                           false  // out-of-context
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
      out = Canonical.union( out,
                             Canonical.union( baseState,
                                              ReachTuple.factory( as.getSummary(),
                                                                  true, // multi
                                                                  ReachTuple.ARITY_ONE,
                                                                  false  // out-of-context
                                                                  )
                                              )
                             );

      out = Canonical.union( out,
                             Canonical.union( baseState,
                                              ReachTuple.factory( as.getSummary(),
                                                                  true, // multi
                                                                  ReachTuple.ARITY_ONE,
                                                                  true  // out-of-context
                                                                  )
                                              )
                             );      

      for( int i = 0; i < as.getAllocationDepth(); ++i ) {
        out = Canonical.union( out,
                               Canonical.union( baseState,
                                                ReachTuple.factory( as.getIthOldest( i ),
                                                                    false, // multi
                                                                    ReachTuple.ARITY_ONE,
                                                                    true  // out-of-context
                                                                    )
                                                )
                               );
      }

    } else {
      // just use current baseState      
      out = Canonical.union( out,
                             baseState );
    }


    assert out.isCanonical();
    op2result.put( op, out );
    return out;
  }









  public static ReachSet unshadow( ReachSet  rs,
                                   AllocSite as ) {
    assert rs != null;
    assert as != null;
    assert rs.isCanonical();
    assert as.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSET_UNSHADOW_ALLOCSITE,
                       rs, 
                       as );
    
    Canonical result = op2result.get( op );
    if( result != null ) {
      return (ReachSet) result;
    }

    // otherwise, no cached result...
    ReachSet out = ReachSet.factory();
    Iterator<ReachState> itr = rs.iterator();
    while( itr.hasNext() ) {
      ReachState state = itr.next();
      out = Canonical.add( out,
                           Canonical.unshadow( state, as )
                           );
    }

    assert out.isCanonical();
    op2result.put( op, out );
    return out;
  }

  public static ReachState unshadow( ReachState state,
                                     AllocSite  as ) {
    assert state != null;
    assert as    != null;
    assert state.isCanonical();
    assert as.isCanonical();

    CanonicalOp op = 
      new CanonicalOp( CanonicalOp.REACHSTATE_UNSHADOW_ALLOCSITE,
                       state, 
                       as );
    
    Canonical result = op2result.get( op );
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

      int age = as.getShadowAgeCategory( rt.getHrnID() );
      
      if( age == AllocSite.SHADOWAGE_notInThisSite ) {
        // things not from the site just go back in
	out = Canonical.union( out, rt );

      } else {
        assert !rt.isOutOfContext();

        // otherwise unshadow it
        out = Canonical.union( out,
                               ReachTuple.factory( -rt.getHrnID(),
                                                   rt.isMultiObject(),
                                                   rt.getArity(),
                                                   false
                                                   )
                               );
      }
    }

    out = Canonical.attach( out,
                            state.getPreds()
                            );

    assert out.isCanonical();
    op2result.put( op, out );
    return out;
  }

}
