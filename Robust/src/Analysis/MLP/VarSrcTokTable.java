package Analysis.MLP;

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
      if( s != null ) { s.remove( vst ); }
      
      s = get( vst.getSESE(), refVar );
      if( s != null ) { s.remove( vst ); }
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

    Iterator<VariableSourceToken> itr = trueSet.iterator();
    while( itr.hasNext() ) {
      VariableSourceToken vst = itr.next();

      if( vst.getSESE().equals( curr ) ) {

	Integer newAge = vst.getAge()+1;
	if( newAge > MAX_AGE ) {
	  newAge = MAX_AGE;
	}
	
	remove( vst );

        add( new VariableSourceToken( vst.getRefVars(), 
				      curr,                                           
				      newAge,
				      vst.getAddrVar()
				      )
	     );
      }	
    }
    
    assertConsistency();
  }

  
  // for the given SESE, change child tokens into this parent
  public void remapChildTokens( FlatSESEEnterNode curr ) {

    Iterator<FlatSESEEnterNode> childItr = curr.getChildren().iterator();
    if( childItr.hasNext() ) {
      FlatSESEEnterNode child = childItr.next();
      
      Iterator<VariableSourceToken> vstItr = get( child ).iterator();
      while( vstItr.hasNext() ) {
        VariableSourceToken vst = vstItr.next();

        remove( vst );
	
        add( new VariableSourceToken( vst.getRefVars(),
				      curr,
				      new Integer( 0 ),
				      vst.getAddrVar()
                                      )
             );
      }
    }

    assertConsistency();
  }   
  

  // if we can get a value from the current SESE and the parent
  // or a sibling, just getting from the current SESE suffices now
  // return a set of temps that are virtually read
  public Set<TempDescriptor> removeParentAndSiblingTokens( FlatSESEEnterNode curr,
							   Set<TempDescriptor> liveIn ) {
    
    HashSet<TempDescriptor> virtualLiveIn = new HashSet<TempDescriptor>();
    
    FlatSESEEnterNode parent = curr.getParent();
    if( parent == null ) {
      // have no parent or siblings
      return virtualLiveIn;
    }      
    
    remove_A_if_B( parent, curr, liveIn, virtualLiveIn );

    Iterator<FlatSESEEnterNode> childItr = parent.getChildren().iterator();
    if( childItr.hasNext() ) {
      FlatSESEEnterNode child = childItr.next();
      
      if( !child.equals( curr ) ) {
        remove_A_if_B( child, curr, liveIn, virtualLiveIn );
      }
    }
    
    assertConsistency();
    return virtualLiveIn;
  }
  
  // if B is also a source for some variable, remove all entries
  // of A as a source for that variable: s is virtual reads
  protected void remove_A_if_B( FlatSESEEnterNode a, 
				FlatSESEEnterNode b,
				Set<TempDescriptor> liveInCurrentSESE,
				Set<TempDescriptor> virtualLiveIn ) {

    Set<VariableSourceToken> forRemoval = new HashSet<VariableSourceToken>();

    Iterator<VariableSourceToken> vstItr = get( a ).iterator();
    while( vstItr.hasNext() ) {
      VariableSourceToken      vst       = vstItr.next();
      Iterator<TempDescriptor> refVarItr = vst.getRefVars().iterator();
      while( refVarItr.hasNext() ) {
        TempDescriptor           refVar = refVarItr.next();
        Set<VariableSourceToken> bSet   = get( b, refVar );
      
	if( !bSet.isEmpty() ) {
          forRemoval.add( vst );

	  // mark this variable as a virtual read as well
	  virtualLiveIn.add( refVar );
	}
      }
    }

    vstItr = forRemoval.iterator();
    while( vstItr.hasNext() ) {
      VariableSourceToken vst = vstItr.next();
      remove( vst );
    }

    assertConsistency();
  }

  
  // get the set of VST's that come from a child
  public Set<VariableSourceToken> getChildrenVSTs( FlatSESEEnterNode curr ) {
    
    Set<VariableSourceToken> out = new HashSet<VariableSourceToken>();
    
    Iterator<FlatSESEEnterNode> cItr = curr.getChildren().iterator();
    while( cItr.hasNext() ) {
      FlatSESEEnterNode child = cItr.next();
      out.addAll( get( child ) );
    }

    return out;
  }


  // get the set of variables that have exactly one source
  // from the static perspective
  public Set<VariableSourceToken> getStaticSet() {
    
    Set<VariableSourceToken> out = new HashSet<VariableSourceToken>();
    
    Iterator itr = var2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                    me  = (Map.Entry)                    itr.next();
      TempDescriptor               var = (TempDescriptor)               me.getKey();
      HashSet<VariableSourceToken> s1  = (HashSet<VariableSourceToken>) me.getValue();      
    
      if( s1.size() == 1 ) {
	out.addAll( s1 );
      }
    }

    return out;
  }


  // given a table from a subsequent program point, decide
  // which variables are going from a static source to a
  // dynamic source and return them
  public Hashtable<TempDescriptor, VariableSourceToken> getStatic2DynamicSet( VarSrcTokTable next ) {
    
    Hashtable<TempDescriptor, VariableSourceToken> out = 
      new Hashtable<TempDescriptor, VariableSourceToken>();
    
    Iterator itr = var2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                    me  = (Map.Entry)                    itr.next();
      TempDescriptor               var = (TempDescriptor)               me.getKey();
      HashSet<VariableSourceToken> s1  = (HashSet<VariableSourceToken>) me.getValue();      

      // this is a variable with a static source if it
      // currently has one vst
      if( s1.size() == 1 ) {
        Set<VariableSourceToken> s2 = next.get( var );

	// and if in the next table, it is dynamic, then
	// this is a transition point, so
        if( s2.size() > 1 ) {

	  // remember the variable and the only source
	  // it had before crossing the transition
	  out.put( var, s1.iterator().next() );
        }
      }
    }

    return out;
  }


  // for some reference variable, return the type of source
  // it might have in this table, which might be:
  // 1. Ready -- this variable comes from your parent and is
  //      definitely available when you are issued.
  // 2. Static -- there is definitely one SESE that will
  //      produce the value for this variable
  // 3. Dynamic -- we don't know where the value will come
  //      from, so we'll track it dynamically
  public Integer getRefVarSrcType( TempDescriptor    refVar,
				   FlatSESEEnterNode current,
				   FlatSESEEnterNode parent ) {
    assert refVar != null;
    
    // if you have no parent (root) and the variable in
    // question is in your in-set, it's a command line
    // argument and it is definitely available
    if( parent == null && 
	current.getInVarSet().contains( refVar ) ) {
      return SrcType_READY;
    }

    Set<VariableSourceToken> srcs = get( refVar );
    assert !srcs.isEmpty();

    // if the variable may have more than one source, or that
    // source is at the summary age, it must be tracked dynamically
    if( srcs.size() > 1 || 
	srcs.iterator().next().getAge() == MLPAnalysis.maxSESEage ) {
      return SrcType_DYNAMIC;
    } 

    // if it has one source that comes from the parent, it's ready
    if( srcs.iterator().next().getSESE() == parent ) {
      return SrcType_READY;
    }
    
    // otherwise it comes from one source not the parent (sibling)
    // and we know exactly which static SESE/age it will come from
    return SrcType_STATIC;
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
