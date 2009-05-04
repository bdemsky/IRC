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
public class VarSrcTokTable {

  // a set of every token in the table
  private HashSet<VariableSourceToken> trueSet;

  // these hashtables provide an efficient retreival from the true set
  private Hashtable< TempDescriptor,    Set<VariableSourceToken> >  var2vst;
  private Hashtable< FlatSESEEnterNode, Set<VariableSourceToken> > sese2vst;
  private Hashtable< SVKey,             Set<VariableSourceToken> >   sv2vst;

  // maximum age from aging operation
  private Integer MAX_AGE = new Integer( 2 );


  public VarSrcTokTable() {
    trueSet  = new HashSet<VariableSourceToken>();

    sese2vst = new Hashtable< FlatSESEEnterNode, Set<VariableSourceToken> >();
    var2vst  = new Hashtable< TempDescriptor,    Set<VariableSourceToken> >();
    sv2vst   = new Hashtable< SVKey,             Set<VariableSourceToken> >();

    assertConsistency();
  }

  
  public VarSrcTokTable( VarSrcTokTable in ) {
    trueSet = (HashSet<VariableSourceToken>) in.trueSet.clone();

    Iterator itr; Set s;

    sese2vst = new Hashtable< FlatSESEEnterNode, Set<VariableSourceToken> >();
    itr = in.sese2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                    me   = (Map.Entry)                    itr.next();
      FlatSESEEnterNode            sese = (FlatSESEEnterNode)            me.getKey();
      HashSet<VariableSourceToken> s1   = (HashSet<VariableSourceToken>) me.getValue();      
      assert s1 != null;
      sese2vst.put( sese, 
		    (HashSet<VariableSourceToken>) (s1.clone()) );
    }

    var2vst = new Hashtable< TempDescriptor, Set<VariableSourceToken> >();
    itr = in.var2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                    me  = (Map.Entry)                    itr.next();
      TempDescriptor               var = (TempDescriptor)               me.getKey();
      HashSet<VariableSourceToken> s1  = (HashSet<VariableSourceToken>) me.getValue();      
      assert s1 != null;
      var2vst.put( var, 
		   (HashSet<VariableSourceToken>) (s1.clone()) );
    }

    sv2vst = new Hashtable< SVKey, Set<VariableSourceToken> >();
    itr = in.sv2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                    me  = (Map.Entry)                    itr.next();
      SVKey                        key = (SVKey)                        me.getKey();
      HashSet<VariableSourceToken> s1  = (HashSet<VariableSourceToken>) me.getValue();      
      assert s1 != null;
      sv2vst.put( key, 
		  (HashSet<VariableSourceToken>) (s1.clone()) );
    }

    assertConsistency();
  }


  public void add( VariableSourceToken vst ) {

    // make sure we aren't clobbering anything!
    assert !trueSet.contains( vst );

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

    assertConsistency();
  }

  public void addAll( Set<VariableSourceToken> s ) {
    Iterator<VariableSourceToken> itr = s.iterator();
    while( itr.hasNext() ) {
      add( itr.next() );
    }
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

  public Set<VariableSourceToken> get( TempDescriptor var ) {
    Set<VariableSourceToken> s = var2vst.get( var );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();
      var2vst.put( var, s );
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


  public void merge( VarSrcTokTable table ) {

    if( table == null ) {
      return;
    }


    System.out.println( "MERGING\n" );
    System.out.println( "THIS="+this.toStringPrettyVerbose() );
    System.out.println( "TABLEIN="+table.toStringPrettyVerbose() );


    trueSet.addAll( table.trueSet );

    Iterator itr; 


    // merge sese2vst mappings
    itr = this.sese2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                me   = (Map.Entry)                itr.next();
      FlatSESEEnterNode        sese = (FlatSESEEnterNode)        me.getKey();
      Set<VariableSourceToken> s1   = (Set<VariableSourceToken>) me.getValue();
      Set<VariableSourceToken> s2   = table.sese2vst.get( sese );     
      assert s1 != null;

      if( s2 != null ) {
	s1.addAll( s2 );
      }
    }
    itr = table.sese2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                me   = (Map.Entry)                itr.next();
      FlatSESEEnterNode        sese = (FlatSESEEnterNode)        me.getKey();
      Set<VariableSourceToken> s2   = (Set<VariableSourceToken>) me.getValue();
      Set<VariableSourceToken> s1   = this.sese2vst.get( sese );
      assert s2 != null;

      if( s1 == null ) {
	this.sese2vst.put( sese, s2 );
      }      
    }


    // merge var2vst mappings
    itr = this.var2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                me  = (Map.Entry)                itr.next();
      TempDescriptor           var = (TempDescriptor)           me.getKey();
      Set<VariableSourceToken> s1  = (Set<VariableSourceToken>) me.getValue();
      Set<VariableSourceToken> s2  = table.var2vst.get( var );      
      assert s1 != null;

      if( s2 != null ) {
	s1.addAll( s2 );
      }           
    }
    itr = table.var2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                me   = (Map.Entry)                itr.next();
      TempDescriptor           var  = (TempDescriptor)           me.getKey();
      Set<VariableSourceToken> s2   = (Set<VariableSourceToken>) me.getValue();
      Set<VariableSourceToken> s1   = this.var2vst.get( var );
      assert s2 != null;

      if( s1 == null ) {
	this.var2vst.put( var, s2 );
      }      
    }


    // merge sv2vst mappings
    itr = this.sv2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                me  = (Map.Entry)                itr.next();
      SVKey                    key = (SVKey)                    me.getKey();
      Set<VariableSourceToken> s1  = (Set<VariableSourceToken>) me.getValue();
      Set<VariableSourceToken> s2  = table.sv2vst.get( key );      
      assert s1 != null;

      if( s2 != null ) {
	s1.addAll( s2 );
      }           
    }
    itr = table.sv2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                me   = (Map.Entry)                itr.next();
      SVKey                    key  = (SVKey)                    me.getKey();
      Set<VariableSourceToken> s2   = (Set<VariableSourceToken>) me.getValue();
      Set<VariableSourceToken> s1   = this.sv2vst.get( key );
      assert s2 != null;

      if( s1 == null ) {
	this.sv2vst.put( key, s2 );
      }      
    }

    System.out.println( "OUT="+this.toStringPrettyVerbose() );


    assertConsistency();
  }


  // remove operations must leave the trueSet 
  // and the hash maps consistent!
  public void remove( VariableSourceToken vst ) {
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

    assertConsistency();
  }

  public void remove( FlatSESEEnterNode sese ) {
    Set<VariableSourceToken> s = sese2vst.get( sese );
    if( s == null ) {
      return;
    }

    Iterator<VariableSourceToken> itr = s.iterator();
    while( itr.hasNext() ) {
      VariableSourceToken vst = itr.next();
      remove( vst );
    }

    sese2vst.remove( sese );

    assertConsistency();
  }

  public void remove( TempDescriptor refVar ) {
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
      // here's a token marked for removal, take it out as it is
      // in this state
      VariableSourceToken vst = itr.next();
      remove( vst );

      // if there are other references to the token, alter the
      // token and then re-add it to this table, because it has
      // a new hash value now
      Set<TempDescriptor> refVars = vst.getRefVars();
      refVars.remove( refVar );
      if( refVars.size() > 0 ) {
        add( vst );
      }
    }

    var2vst.remove( refVar );

    assertConsistency();
  }

  public void remove( FlatSESEEnterNode sese,
		      TempDescriptor    var  ) {

    // dont' use this yet
    assert false;

    SVKey key = new SVKey( sese, var );
    Set<VariableSourceToken> s = sv2vst.get( key );
    if( s == null ) {
      return;
    }
    
    Iterator<VariableSourceToken> itr = s.iterator();
    while( itr.hasNext() ) {
      VariableSourceToken vst = itr.next();
      remove( vst );
    }

    sv2vst.remove( key );

    assertConsistency();
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

  
  public Set<VariableSourceToken> getStallSet( FlatSESEEnterNode curr ) {
    
    Set<VariableSourceToken> out = new HashSet<VariableSourceToken>();
    
    Iterator<FlatSESEEnterNode> cItr = curr.getChildren().iterator();
    while( cItr.hasNext() ) {
      FlatSESEEnterNode child = cItr.next();
      out.addAll( get( child ) );
    }

    assertConsistency();    
    return out;
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
    return "trueSet ="+trueSet.toString();
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
