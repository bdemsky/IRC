package Analysis.OoOJava;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

// This class formerly had lazy consistency properties, but
// it is being changed so that the full set and the extra
// hash tables to access the full set efficiently by different
// elements will be consistent after EVERY operation.  Also,
// a consistent assert method allows a debugger to ask whether
// an operation has produced an inconsistent VarSrcTokTable.

// in an effort to make sure operations keep the table consistent,
// all public methods that are also used by other methods for
// intermediate results (add and remove are used in other methods)
// there should be a public version that calls the private version
// so consistency is checked after public ops, but not private ops
public class VarSrcTokTable {

  // a set of every token in the table
  private HashSet<VariableSourceToken> trueSet;

  // these hashtables provide an efficient retreival from the true set
  private Hashtable< TempDescriptor,    Set<VariableSourceToken> >  var2vst;
  private Hashtable< FlatSESEEnterNode, Set<VariableSourceToken> > sese2vst;
  private Hashtable< SVKey,             Set<VariableSourceToken> >   sv2vst;

  // maximum age from aging operation
  private static final Integer MAX_AGE = new Integer( 2 );
  
  public static final Integer SrcType_READY   = new Integer( 34 );
  public static final Integer SrcType_STATIC  = new Integer( 35 );
  public static final Integer SrcType_DYNAMIC = new Integer( 36 );

  public static RBlockRelationAnalysis rblockRel;



  public VarSrcTokTable() {
    trueSet  = new HashSet<VariableSourceToken>();

    sese2vst = new Hashtable< FlatSESEEnterNode, Set<VariableSourceToken> >();
    var2vst  = new Hashtable< TempDescriptor,    Set<VariableSourceToken> >();
    sv2vst   = new Hashtable< SVKey,             Set<VariableSourceToken> >();

    assertConsistency();
  }


  // make a deep copy of the in table
  public VarSrcTokTable( VarSrcTokTable in ) {
    this();
    merge( in );
    assertConsistency();
  }


  public void add( VariableSourceToken vst ) {
    addPrivate( vst );
    assertConsistency();
  }

  private void addPrivate( VariableSourceToken vst ) {

    // make sure we aren't clobbering anything!
    if( trueSet.contains( vst ) ) {
      // if something with the same hashcode is in the true set, they might
      // have different reference variable sets because that set is not considered
      // in a token's equality, so make sure we smooth that out right here
      Iterator<VariableSourceToken> vstItr = trueSet.iterator();
      while( vstItr.hasNext() ) {
        VariableSourceToken vstAlready = vstItr.next();

        if( vstAlready.equals( vst ) ) {    

          // take out the one that is in (we dont' want collisions in
          // any of the other hash map sets either)
          removePrivate( vstAlready );

          // combine reference variable sets
          vst.getRefVars().addAll( vstAlready.getRefVars() );

          // now jump back as we are adding in a brand new token
          break;
        }
      }
    }

    trueSet.add( vst );

    Set<VariableSourceToken> s;

    s = sese2vst.get( vst.getSESE() );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();
    }
    s.add( vst );
    sese2vst.put( vst.getSESE(), s );

    Iterator<TempDescriptor> refVarItr = vst.getRefVars().iterator();
    while( refVarItr.hasNext() ) {
      TempDescriptor refVar = refVarItr.next();
      s = var2vst.get( refVar );
      if( s == null ) {
        s = new HashSet<VariableSourceToken>();
      }
      s.add( vst );
      var2vst.put( refVar, s );

      SVKey key = new SVKey( vst.getSESE(), refVar );
      s = sv2vst.get( key );
      if( s == null ) {
        s = new HashSet<VariableSourceToken>();
      }
      s.add( vst );
      sv2vst.put( key, s );
    }
  }

  public void addAll( Set<VariableSourceToken> s ) {
    Iterator<VariableSourceToken> itr = s.iterator();
    while( itr.hasNext() ) {
      addPrivate( itr.next() );
    }
    assertConsistency();
  }


  public Set<VariableSourceToken> get() {
    return trueSet;
  }

  public Set<VariableSourceToken> get( FlatSESEEnterNode sese ) {
    Set<VariableSourceToken> s = sese2vst.get( sese );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();      
      sese2vst.put( sese, s );
    }
    return s;
  }

  public Set<VariableSourceToken> get( TempDescriptor refVar ) {
    Set<VariableSourceToken> s = var2vst.get( refVar );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();
      var2vst.put( refVar, s );
    }
    return s;
  }

  public Set<VariableSourceToken> get( FlatSESEEnterNode sese,
                                       TempDescriptor    refVar ) {
    SVKey key = new SVKey( sese, refVar );
    Set<VariableSourceToken> s = sv2vst.get( key );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();
      sv2vst.put( key, s );
    }
    return s;
  }

  public Set<VariableSourceToken> get( FlatSESEEnterNode sese,
                                       Integer           age ) {

    HashSet<VariableSourceToken> s0 = (HashSet<VariableSourceToken>) sese2vst.get( sese );
    if( s0 == null ) {
      s0 = new HashSet<VariableSourceToken>();      
      sese2vst.put( sese, s0 );
    }

    Set<VariableSourceToken> s = (Set<VariableSourceToken>) s0.clone();
    Iterator<VariableSourceToken> sItr = s.iterator();
    while( sItr.hasNext() ) {
      VariableSourceToken vst = sItr.next();
      if( !vst.getAge().equals( age ) ) {
        s.remove( vst );
      }
    }

    return s;
  }


  // merge now makes a deep copy of incoming stuff because tokens may
  // be modified (reference var sets) by later ops that change more
  // than one table, causing inconsistency
  public void merge( VarSrcTokTable in ) {

    if( in == null ) {
      return;
    }

    Iterator<VariableSourceToken> vstItr = in.trueSet.iterator();
    while( vstItr.hasNext() ) {
      VariableSourceToken vst = vstItr.next();
      this.addPrivate( vst.copy() );
    }

    assertConsistency();
  }


  // remove operations must leave the trueSet 
  // and the hash maps consistent
  public void remove( VariableSourceToken vst ) {
    removePrivate( vst );
    assertConsistency();
  }

  private void removePrivate( VariableSourceToken vst ) {
    trueSet.remove( vst );
    
    Set<VariableSourceToken> s;

    s = get( vst.getSESE() );
    if( s != null ) { s.remove( vst ); }

    Iterator<TempDescriptor> refVarItr = vst.getRefVars().iterator();
    while( refVarItr.hasNext() ) {
      TempDescriptor refVar = refVarItr.next();

      s = get( refVar );
      if( s != null ) { 
	s.remove( vst );
	if( s.isEmpty() ) {
	  var2vst.remove( refVar );
	}
      }
      
      s = get( vst.getSESE(), refVar );
      if( s != null ) { 
	s.remove( vst );
	if( s.isEmpty() ) {
	  sv2vst.remove( new SVKey( vst.getSESE(), refVar ) );
	}
      }
    }
  }


  public void remove( FlatSESEEnterNode sese ) {
    removePrivate( sese );
    assertConsistency();
  }

  public void removePrivate( FlatSESEEnterNode sese ) {
    Set<VariableSourceToken> s = sese2vst.get( sese );
    if( s == null ) {
      return;
    }

    Iterator<VariableSourceToken> itr = s.iterator();
    while( itr.hasNext() ) {
      VariableSourceToken vst = itr.next();
      removePrivate( vst );
    }

    sese2vst.remove( sese );
  }


  public void remove( TempDescriptor refVar ) {
    removePrivate( refVar );
    assertConsistency();
  }

  private void removePrivate( TempDescriptor refVar ) {
    Set<VariableSourceToken> s = var2vst.get( refVar );
    if( s == null ) {
      return;
    }
    
    Set<VariableSourceToken> forRemoval = new HashSet<VariableSourceToken>();

    // iterate over tokens that this temp can reference, make a set
    // of tokens that need this temp stripped out of them
    Iterator<VariableSourceToken> itr = s.iterator();
    while( itr.hasNext() ) {
      VariableSourceToken vst = itr.next();
      Set<TempDescriptor> refVars = vst.getRefVars();
      assert refVars.contains( refVar );
      forRemoval.add( vst );
    }

    itr = forRemoval.iterator();
    while( itr.hasNext() ) {

      // here's a token marked for removal
      VariableSourceToken vst = itr.next();
      Set<TempDescriptor> refVars = vst.getRefVars();

      // if there was only one one variable
      // referencing this token, just take it
      // out of the table all together
      if( refVars.size() == 1 ) {
        removePrivate( vst );
      }

      sv2vst.remove( new SVKey( vst.getSESE(), refVar ) );

      refVars.remove( refVar );      
    }

    var2vst.remove( refVar );    
  }


  public void remove( FlatSESEEnterNode sese,
		      TempDescriptor    var  ) {

    // don't seem to need this, don't bother maintaining
    // until its clear we need it
    assert false;
  }


  // age tokens with respect to SESE curr, where
  // any curr tokens increase age by 1
  public void age( FlatSESEEnterNode curr ) {

    Set<VariableSourceToken> forRemoval =
      new HashSet<VariableSourceToken>();

    Set<VariableSourceToken> forAddition =
      new HashSet<VariableSourceToken>();

    Iterator<VariableSourceToken> itr = trueSet.iterator();
    while( itr.hasNext() ) {
      VariableSourceToken vst = itr.next();

      if( vst.getSESE().equals( curr ) ) {

	// only age if the token isn't already the maximum age
	if( vst.getAge() < MAX_AGE ) {
	
	  forRemoval.add( vst );

	  forAddition.add( new VariableSourceToken( vst.getRefVars(), 
						    curr,                                           
						    vst.getAge() + 1,
						    vst.getAddrVar()
						    )
			   );
	}
      }	
    }
    
    itr = forRemoval.iterator();
    while( itr.hasNext() ) {
      VariableSourceToken vst = itr.next();
      remove( vst );
    }
    
    itr = forRemoval.iterator();
    while( itr.hasNext() ) {
      VariableSourceToken vst = itr.next();
      add( vst );
    }

    assertConsistency();
  }


  // at an SESE enter node, all ref vars in the SESE's in-set will
  // be copied into the SESE's local scope, change source to itself
  public void ownInSet( FlatSESEEnterNode curr ) {
    Iterator<TempDescriptor> inVarItr = curr.getInVarSet().iterator();
    while( inVarItr.hasNext() ) {
      TempDescriptor inVar = inVarItr.next();

      remove( inVar );
      assertConsistency();

      Set<TempDescriptor> refVars = new HashSet<TempDescriptor>();
      refVars.add( inVar );
      add( new VariableSourceToken( refVars,
				    curr,
				    new Integer( 0 ),
				    inVar
				    )
	   );
      assertConsistency();
    }
  }

  
  // for the given SESE, change child tokens into this parent
  public void remapChildTokens( FlatSESEEnterNode curr ) {

    Iterator<FlatSESEEnterNode> childItr = curr.getLocalChildren().iterator();
    if( childItr.hasNext() ) {
      FlatSESEEnterNode child = childItr.next();
      
      // set of VSTs for removal
      HashSet<VariableSourceToken> removalSet=new HashSet<VariableSourceToken>();
      // set of VSTs for additon
      HashSet<VariableSourceToken> additionSet=new HashSet<VariableSourceToken>();
      
      Iterator<VariableSourceToken> vstItr = get( child ).iterator();
      while( vstItr.hasNext() ) {
        VariableSourceToken vst = vstItr.next();
        removalSet.add(vst);
        additionSet.add(new VariableSourceToken( vst.getRefVars(),
			      curr,
			      new Integer( 0 ),
			      vst.getAddrVar()
                                  ));
      }
      
      // remove( eah item in forremoval )
      vstItr = removalSet.iterator();
      while( vstItr.hasNext() ) {
        VariableSourceToken vst = vstItr.next();
        remove( vst );
      }
      // add( each  ite inm for additon _
      vstItr = additionSet.iterator();
      while( vstItr.hasNext() ) {
        VariableSourceToken vst = vstItr.next();
        add( vst );
      }
    }

    assertConsistency();
  }   
  

  // this method is called at the SESE exit of SESE 'curr'
  // if the sources for a variable written by curr can also
  // come from curr's parent or curr's siblings then we're not
  // sure that curr will actually modify the variable.  There are
  // many ways to handle this, but for now, mark the variable as
  // virtually read so curr insists on having ownership of it
  // whether it ends up writing to it or not.  It will always, then,
  // appear in curr's out-set.
  public Set<TempDescriptor>
    calcVirtReadsAndPruneParentAndSiblingTokens( FlatSESEEnterNode   exiter,
						 Set<TempDescriptor> liveVars ) {

    Set<TempDescriptor> virtReadSet = new HashSet<TempDescriptor>();

    // this calculation is unneeded for the main task, just return an
    // empty set of virtual reads
    if( rblockRel.getMainSESE() == exiter ) {
      return virtReadSet;
    }

    // who are the parent and siblings?
    Set<FlatSESEEnterNode> alternateSESEs = new HashSet<FlatSESEEnterNode>();
    Iterator<FlatSESEEnterNode> childItr;

    FlatSESEEnterNode parent = exiter.getLocalParent();

    if( parent == null ) {
      // when some caller task is the exiter's parent, the siblings
      // of the exiter are other local root tasks
      parent = rblockRel.getCallerProxySESE();      
      childItr = rblockRel.getLocalRootSESEs( exiter.getfmEnclosing() ).iterator();
      
    } else {
      // otherwise, the siblings are locally-defined
      childItr = parent.getLocalChildren().iterator();
    }

    alternateSESEs.add( parent );
    while( childItr.hasNext() ) {
      FlatSESEEnterNode sibling = childItr.next();      
      if( !sibling.equals( exiter ) ) {
        alternateSESEs.add( sibling );
      }
    }


    
    // VSTs to remove if they are alternate sources for exiter VSTs
    // whose variables will become virtual reads
    Set<VariableSourceToken> forRemoval = new HashSet<VariableSourceToken>();

    // look at all of this SESE's VSTs at exit...
    Iterator<VariableSourceToken> vstItr = get( exiter ).iterator();
    while( vstItr.hasNext() ) {
      VariableSourceToken vstExiterSrc = vstItr.next();

      // only interested in tokens that come from our current instance
      if( vstExiterSrc.getAge() != 0 ) {
	continue;
      }

      // for each variable that might come from those sources...
      Iterator<TempDescriptor> refVarItr = vstExiterSrc.getRefVars().iterator();
      while( refVarItr.hasNext() ) {
        TempDescriptor refVar = refVarItr.next();

	// only matters for live variables at SESE exit program point
	if( !liveVars.contains( refVar ) ) {
	  continue;
	}

	// examine other sources for a variable...
	Iterator<VariableSourceToken> srcItr = get( refVar ).iterator();
	while( srcItr.hasNext() ) {
	  VariableSourceToken vstPossibleOtherSrc = srcItr.next();

	  if( vstPossibleOtherSrc.getSESE().equals( exiter ) &&
	      vstPossibleOtherSrc.getAge() > 0 
	    ) {
	    // this is an alternate source if its 
	    // an older instance of this SESE	  	    
	    virtReadSet.add( refVar );
	    forRemoval.add( vstPossibleOtherSrc );
	    
	  } else if( alternateSESEs.contains( vstPossibleOtherSrc.getSESE() ) ) {
	    // this is an alternate source from parent or sibling
	    virtReadSet.add( refVar );
	    forRemoval.add( vstPossibleOtherSrc );  

	  } else {
            if( !vstPossibleOtherSrc.getSESE().equals( exiter ) ||
                !vstPossibleOtherSrc.getAge().equals( 0 )
                ) {
              System.out.println( "For refVar="+refVar+" at exit of "+exiter+
                                  ", unexpected possible variable source "+vstPossibleOtherSrc );
              assert false;
            }
	  }
	}
      }
    }

    vstItr = forRemoval.iterator();
    while( vstItr.hasNext() ) {
      VariableSourceToken vst = vstItr.next();
      remove( vst );
    }
    assertConsistency();
    
    return virtReadSet;
  }
  

  // given a table from a subsequent program point, decide
  // which variables are going from a non-dynamic to a
  // dynamic source and return them
  public Hashtable<TempDescriptor, VSTWrapper> 
    getReadyOrStatic2DynamicSet( VarSrcTokTable nextTable,
                                 Set<TempDescriptor> nextLiveIn,
                                 FlatSESEEnterNode current
                                 ) {
    
    Hashtable<TempDescriptor, VSTWrapper> out = 
      new Hashtable<TempDescriptor, VSTWrapper>();
    
    Iterator itr = var2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                    me  = (Map.Entry)                    itr.next();
      TempDescriptor               var = (TempDescriptor)               me.getKey();
      HashSet<VariableSourceToken> s1  = (HashSet<VariableSourceToken>) me.getValue();      

      // only worth tracking if live
      if( nextLiveIn.contains( var ) ) {
        
        VSTWrapper vstIfStaticBefore = new VSTWrapper();
        VSTWrapper vstIfStaticAfter  = new VSTWrapper();

        Integer srcTypeBefore =      this.getRefVarSrcType( var, current, vstIfStaticBefore );
        Integer srcTypeAfter  = nextTable.getRefVarSrcType( var, current, vstIfStaticAfter  );

	if( !srcTypeBefore.equals( SrcType_DYNAMIC ) &&
              srcTypeAfter.equals( SrcType_DYNAMIC )	   
          ) {
	  // remember the variable and a source
	  // it had before crossing the transition
          // 1) if it was ready, vstIfStatic.vst is null
          // 2) if is was static, use vstIfStatic.vst
	  out.put( var, vstIfStaticBefore );
	}
      }
    }

    return out;
  }


  // for some reference variable, return the type of source
  // it might have in this table, which might be:
  // 1. Ready -- this variable is
  //      definitely available when you are issued.
  // 2. Static -- there is definitely one child SESE with
  //      a known age that will produce the value
  // 3. Dynamic -- we don't know where the value will come
  //      from statically, so we'll track it dynamically
  public Integer getRefVarSrcType( TempDescriptor    refVar,
				   FlatSESEEnterNode currentSESE,
                                   VSTWrapper        vstIfStatic ) {
    assert refVar      != null;
    assert vstIfStatic != null;

    vstIfStatic.vst = null;
   
    // when the current SESE is null, that simply means it is
    // an unknown placeholder, in which case the system will
    // ensure that any variables are READY
    if( currentSESE == null ) {
      return SrcType_READY;
    }

    // if there appear to be no sources, it means this variable
    // comes from outside of any statically-known SESE scope,
    // which means the system guarantees its READY, so jump over
    // while loop
    Set<VariableSourceToken>      srcs    = get( refVar );
    Iterator<VariableSourceToken> itrSrcs = srcs.iterator();
    while( itrSrcs.hasNext() ) {
      VariableSourceToken vst = itrSrcs.next();

      // to make the refVar non-READY we have to find at least
      // one child token, there are two cases
      //  1. if the current task invoked the local method context,
      //     its children are the locally-defined root tasks
      boolean case1 = 
        currentSESE.getIsCallerProxySESE() &&
        rblockRel.getLocalRootSESEs().contains( vst.getSESE() );

      //  2. if the child task is a locally-defined child of the current task
      boolean case2 = currentSESE.getLocalChildren().contains( vst.getSESE() );
            
      if( case1 || case2 ) {
      
        // if we ever have at least one child source with an
        // unknown age, have to treat var as dynamic
        if( vst.getAge().equals( OoOJavaAnalysis.maxSESEage ) ) {
          return SrcType_DYNAMIC;
        }

        // if we have a known-age child source, this var is
        // either static or dynamic now: it's static if this
        // source is the only source, otherwise dynamic
        if( srcs.size() > 1 ) {
          return SrcType_DYNAMIC;
        }
        
        vstIfStatic.vst = vst;
        return SrcType_STATIC;
      }
    }

    // if we never found a child source, all other
    // sources must be READY before we could even
    // begin executing!
    return SrcType_READY;
  }


  // any reference variables that are not live can be pruned
  // from the table, and if any VSTs are then no longer 
  // referenced, they can be dropped as well
  // THIS CAUSES INCONSISTENCY, FIX LATER, NOT REQUIRED
  public void pruneByLiveness( Set<TempDescriptor> rootLiveSet ) {
    
    // the set of reference variables in the table minus the
    // live set gives the set of reference variables to remove
    Set<TempDescriptor> deadRefVars = new HashSet<TempDescriptor>();
    deadRefVars.addAll( var2vst.keySet() );

    if( rootLiveSet != null ) {
      deadRefVars.removeAll( rootLiveSet );
    }

    // just use the remove operation to prune the table now
    Iterator<TempDescriptor> deadItr = deadRefVars.iterator();
    while( deadItr.hasNext() ) {
      TempDescriptor dead = deadItr.next();
      removePrivate( dead );
    }

    assertConsistency();
  }
 


  // use as an aid for debugging, where true-set is checked
  // against the alternate mappings: assert that nothing is
  // missing or extra in the alternates
  public void assertConsistency() {

    Iterator itr; 
    Set s;

    Set<VariableSourceToken> trueSetByAlts = new HashSet<VariableSourceToken>();
    itr = sese2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                    me   = (Map.Entry)                    itr.next();
      FlatSESEEnterNode            sese = (FlatSESEEnterNode)            me.getKey();
      HashSet<VariableSourceToken> s1   = (HashSet<VariableSourceToken>) me.getValue();      
      assert s1 != null;
      
      // the trueSet should have all entries in s1
      assert trueSet.containsAll( s1 );

      // s1 should not have anything that doesn't appear in trueset
      Set<VariableSourceToken> sInt = (Set<VariableSourceToken>) s1.clone();
      sInt.removeAll( trueSet );

      assert sInt.isEmpty();

      // add s1 to a running union--at the end check if trueSet has extra
      trueSetByAlts.addAll( s1 );
    }
    // make sure trueSet isn't too big
    assert trueSetByAlts.containsAll( trueSet );


    trueSetByAlts = new HashSet<VariableSourceToken>();
    itr = var2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                    me   = (Map.Entry)                    itr.next();
      TempDescriptor               var  = (TempDescriptor)               me.getKey();
      HashSet<VariableSourceToken> s1   = (HashSet<VariableSourceToken>) me.getValue();      
      assert s1 != null;
      
      // the trueSet should have all entries in s1
      assert trueSet.containsAll( s1 );

      // s1 should not have anything that doesn't appear in trueset
      Set<VariableSourceToken> sInt = (Set<VariableSourceToken>) s1.clone();
      sInt.removeAll( trueSet );

      assert sInt.isEmpty();

      // add s1 to a running union--at the end check if trueSet has extra
      trueSetByAlts.addAll( s1 );
    }
    // make sure trueSet isn't too big
    assert trueSetByAlts.containsAll( trueSet );


    trueSetByAlts = new HashSet<VariableSourceToken>();
    itr = sv2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                    me   = (Map.Entry)                    itr.next();
      SVKey                        key  = (SVKey)                        me.getKey();
      HashSet<VariableSourceToken> s1   = (HashSet<VariableSourceToken>) me.getValue();      
      assert s1 != null;
      
      // the trueSet should have all entries in s1
      assert trueSet.containsAll( s1 );

      // s1 should not have anything that doesn't appear in trueset
      Set<VariableSourceToken> sInt = (Set<VariableSourceToken>) s1.clone();
      sInt.removeAll( trueSet );

      assert sInt.isEmpty();

      // add s1 to a running union--at the end check if trueSet has extra
      trueSetByAlts.addAll( s1 );
    }
    // make sure trueSet isn't too big
    assert trueSetByAlts.containsAll( trueSet );


    // also check that the reference var sets are consistent
    Hashtable<VariableSourceToken, Set<TempDescriptor> > vst2refVars =
      new Hashtable<VariableSourceToken, Set<TempDescriptor> >();
    itr = var2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                     me     = (Map.Entry)                    itr.next();
      TempDescriptor                refVar = (TempDescriptor)               me.getKey();
      HashSet<VariableSourceToken>  s1     = (HashSet<VariableSourceToken>) me.getValue();      
      Iterator<VariableSourceToken> vstItr = s1.iterator();
      while( vstItr.hasNext() ) {
	VariableSourceToken vst = vstItr.next();
	assert vst.getRefVars().contains( refVar );

	Set<TempDescriptor> refVarsPart = vst2refVars.get( vst );
	if( refVarsPart == null ) {
	  refVarsPart = new HashSet<TempDescriptor>();
	}
	refVarsPart.add( refVar );
	vst2refVars.put( vst, refVarsPart );
      }
    }
    itr = vst2refVars.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry           me  = (Map.Entry)           itr.next();
      VariableSourceToken vst = (VariableSourceToken) me.getKey();
      Set<TempDescriptor> s1  = (Set<TempDescriptor>) me.getValue();

      assert vst.getRefVars().equals( s1 );
    }    
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof VarSrcTokTable) ) {
      return false;
    }

    VarSrcTokTable table = (VarSrcTokTable) o;
    return trueSet.equals( table.trueSet );
  }

  public int hashCode() {
    return trueSet.hashCode();
  }

  public Iterator<VariableSourceToken> iterator() {
    return trueSet.iterator();
  }

  public String toString() {
    return toStringPretty();
  }

  public String toStringVerbose() {
    return "trueSet ="+trueSet.toString()+"\n"+
           "sese2vst="+sese2vst.toString()+"\n"+
           "var2vst ="+var2vst.toString()+"\n"+
           "sv2vst  ="+sv2vst.toString();
  }

  public String toStringPretty() {
    String tokHighlighter = "o";

    String str = "VarSrcTokTable\n";
    Iterator<VariableSourceToken> vstItr = trueSet.iterator();    
    while( vstItr.hasNext() ) {
      str += "   "+tokHighlighter+" "+vstItr.next()+"\n";
    }
    return str;
  }

  public String toStringPrettyVerbose() {
    String tokHighlighter = "o";

    String str = "VarSrcTokTable\n";

    Set s;
    Iterator itr; 
    Iterator<VariableSourceToken> vstItr;

    str += "  trueSet\n";
    vstItr = trueSet.iterator();    
    while( vstItr.hasNext() ) {
      str += "     "+tokHighlighter+" "+vstItr.next()+"\n";
    }

    str += "  sese2vst\n";
    itr = sese2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                    me   = (Map.Entry)                    itr.next();
      FlatSESEEnterNode            sese = (FlatSESEEnterNode)            me.getKey();
      HashSet<VariableSourceToken> s1   = (HashSet<VariableSourceToken>) me.getValue();      
      assert s1 != null;

      str += "    "+sese.getPrettyIdentifier()+" -> \n";

      vstItr = s1.iterator();
      while( vstItr.hasNext() ) {
	str += "       "+tokHighlighter+" "+vstItr.next()+"\n";
      }
    }

    str += "  var2vst\n";
    itr = var2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                me  = (Map.Entry)                itr.next();
      TempDescriptor           var = (TempDescriptor)           me.getKey();
      Set<VariableSourceToken> s1  = (Set<VariableSourceToken>) me.getValue();
      assert s1 != null;

      str += "    "+var+" -> \n";

      vstItr = s1.iterator();
      while( vstItr.hasNext() ) {
	str += "       "+tokHighlighter+" "+vstItr.next()+"\n";
      }
    }

    str += "  sv2vst\n";
    itr = sv2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                me  = (Map.Entry)                itr.next();
      SVKey                    key = (SVKey)                    me.getKey();
      Set<VariableSourceToken> s1  = (Set<VariableSourceToken>) me.getValue();
      assert s1 != null;

      str += "    "+key+" -> \n";

      vstItr = s1.iterator();
      while( vstItr.hasNext() ) {
	str += "       "+tokHighlighter+" "+vstItr.next()+"\n";
      }
    }

    return str;
  }
}
