package Analysis.MLP;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class VarSrcTokTable {
  
  // the true set represents the set of (sese, variable, age)
  // triples that are truly in the table
  private HashSet<VariableSourceToken> trueSet;

  // these hashtables provide an efficient retreival from the
  // true set.  Note that a particular triple from the quick
  // look up must be checked against the true set--remove ops
  // can cause the hashtables to be inconsistent to each other
  private Hashtable< TempDescriptor,    Set<VariableSourceToken> >  var2vst;
  private Hashtable< FlatSESEEnterNode, Set<VariableSourceToken> > sese2vst;
  private Hashtable< SVKey,             Set<VariableSourceToken> >   sv2vst;

  // maximum age from aging operation
  private Integer MAX_AGE = new Integer( 2 );


  public VarSrcTokTable() {
    trueSet = new HashSet<VariableSourceToken>();

    sese2vst = new Hashtable< FlatSESEEnterNode, Set<VariableSourceToken> >();
    var2vst  = new Hashtable< TempDescriptor,    Set<VariableSourceToken> >();
    sv2vst   = new Hashtable< SVKey,             Set<VariableSourceToken> >();
  }

  
  public VarSrcTokTable( VarSrcTokTable in ) {
    trueSet = (HashSet<VariableSourceToken>) in.trueSet.clone();

    Iterator itr; Set s;

    sese2vst = new Hashtable< FlatSESEEnterNode, Set<VariableSourceToken> >();
    itr = sese2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                    me   = (Map.Entry)                    itr.next();
      FlatSESEEnterNode            sese = (FlatSESEEnterNode)            me.getKey();
      HashSet<VariableSourceToken> s1   = (HashSet<VariableSourceToken>) me.getValue();      
      assert s1 != null;
      sese2vst.put( sese, 
		    (HashSet<VariableSourceToken>) (s1.clone()) );
    }

    var2vst = new Hashtable< TempDescriptor, Set<VariableSourceToken> >();
    itr = var2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                    me  = (Map.Entry)                    itr.next();
      TempDescriptor               var = (TempDescriptor)               me.getKey();
      HashSet<VariableSourceToken> s1  = (HashSet<VariableSourceToken>) me.getValue();      
      assert s1 != null;
      var2vst.put( var, 
		   (HashSet<VariableSourceToken>) (s1.clone()) );
    }

    sv2vst = new Hashtable< SVKey, Set<VariableSourceToken> >();
    itr = sv2vst.entrySet().iterator();
    while( itr.hasNext() ) {
      Map.Entry                    me  = (Map.Entry)                    itr.next();
      SVKey                        key = (SVKey)                        me.getKey();
      HashSet<VariableSourceToken> s1  = (HashSet<VariableSourceToken>) me.getValue();      
      assert s1 != null;
      sv2vst.put( key, 
		  (HashSet<VariableSourceToken>) (s1.clone()) );
    }
  }


  public void add( VariableSourceToken vst ) {
    trueSet.add( vst );

    Set<VariableSourceToken> s;

    s = sese2vst.get( vst.getSESE() );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();
    }
    s.add( vst );
    sese2vst.put( vst.getSESE(), s );

    s = var2vst.get( vst.getVarLive() );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();
    }
    s.add( vst );
    var2vst.put( vst.getVarLive(), s );

    SVKey key = new SVKey( vst.getSESE(), vst.getVarLive() );
    s = sv2vst.get( key );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();
    }
    s.add( vst );
    sv2vst.put( key, s );
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
    s.retainAll( trueSet );
    return s;
  }

  public Set<VariableSourceToken> get( TempDescriptor var ) {
    Set<VariableSourceToken> s = var2vst.get( var );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();
      var2vst.put( var, s );
    }
    s.retainAll( trueSet );
    return s;
  }

  public Set<VariableSourceToken> get( SVKey key ) {
    Set<VariableSourceToken> s = sv2vst.get( key );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();
      sv2vst.put( key, s );
    }
    s.retainAll( trueSet );
    return s;
  }


  public void merge( VarSrcTokTable table ) {

    if( table == null ) {
      return;
    }

    trueSet.addAll( table.trueSet );

    Iterator itr; 
    Set s;

    itr = sese2vst.entrySet().iterator();
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
    s = table.sese2vst.entrySet();
    s.removeAll( sese2vst.entrySet() );
    sese2vst.putAll( table.sese2vst );

    itr = var2vst.entrySet().iterator();
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
    s = table.var2vst.entrySet();
    s.removeAll( var2vst.entrySet() );
    var2vst.putAll( table.var2vst );

    itr = sv2vst.entrySet().iterator();
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
    s = table.sv2vst.entrySet();
    s.removeAll( sv2vst.entrySet() );
    sv2vst.putAll( table.sv2vst );
  }


  public void remove( FlatSESEEnterNode sese ) {
    Set<VariableSourceToken> s = sese2vst.get( sese );
    if( s == null ) {
      return;
    }
    
    trueSet.removeAll( s );        
    sese2vst.remove( sese );
  }

  public void remove( TempDescriptor var ) {
    Set<VariableSourceToken> s = var2vst.get( var );
    if( s == null ) {
      return;
    }
    
    trueSet.removeAll( s );        
    var2vst.remove( var );
  }

  public void remove( FlatSESEEnterNode sese,
		      TempDescriptor    var  ) {

    SVKey key = new SVKey( sese, var );
    Set<VariableSourceToken> s = sv2vst.get( key );
    if( s == null ) {
      return;
    }
    
    trueSet.removeAll( s );
    sv2vst.remove( key );
  }

  public void remove( VariableSourceToken vst ) {
    trueSet.remove( vst );
  }


  // return a new table based on this one and
  // age tokens with respect to SESE curr, where
  // any child becomes curr with age 0, and any
  // curr tokens increase age by 1
  public VarSrcTokTable age( FlatSESEEnterNode curr ) {

    VarSrcTokTable out = new VarSrcTokTable();

    Iterator<VariableSourceToken> itr = trueSet.iterator();
    while( itr.hasNext() ) {
      VariableSourceToken vst = itr.next();
      if( vst.getSESE().equals( curr ) ) {
	Integer newAge = vst.getAge()+1;
	if( newAge > MAX_AGE ) {
	  newAge = MAX_AGE;
	}
        out.add( new VariableSourceToken( vst.getVarLive(), 
					  curr,                                           
					  newAge,
					  vst.getVarSrc() 
					  )
		 );
      }	
    }

    return out;
  }

  
  // for the given SESE, remove tokens for the same live
  // variable if it comes from a child SESE
  public VarSrcTokTable removeChildToks( FlatSESEEnterNode curr ) {

    // create a table to modify as a copy of this
    VarSrcTokTable out = new VarSrcTokTable( this );

    // iterate over this table, modify out table
    Iterator<VariableSourceToken> itr = get( curr ).iterator();
    while( itr.hasNext() ) {
      VariableSourceToken vst = itr.next();

      Iterator<VariableSourceToken> itr2 = get( vst.getVarLive() ).iterator();
      while( itr2.hasNext() ) {
	VariableSourceToken vst2 = itr2.next();

	if( curr.getChildren().contains( vst2.getSESE() ) ) {
	  out.remove( vst2 );
	}	
      }
    }

    return out;    
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
    return "trueSet ="+trueSet.toString()+"\n"+
           "sese2vst="+sese2vst.toString()+"\n"+
           "var2vst ="+var2vst.toString()+"\n"+
           "sv2vst  ="+sv2vst.toString();
  }
}
