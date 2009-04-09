package Analysis.MLP;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class VarSrcTokTable {
  
  // be able to grab the VariableSourceToken triples from the
  // table by the sese, by the variable, or by a key that is
  // an sese/variable pair
  private Hashtable< FlatSESEEnterNode, Set<VariableSourceToken> > sese2vst;
  private Hashtable< TempDescriptor,    Set<VariableSourceToken> >  var2vst;
  private Hashtable< SVKey,             Set<VariableSourceToken> >   sv2vst;

  public VarSrcTokTable() {
    sese2vst = new Hashtable< FlatSESEEnterNode, Set<VariableSourceToken> >();
    var2vst  = new Hashtable< TempDescriptor,    Set<VariableSourceToken> >();
    sv2vst   = new Hashtable< SVKey,             Set<VariableSourceToken> >();
  }


  public void add( VariableSourceToken vst ) {
    Set<VariableSourceToken> s;

    s = sese2vst.get( vst.getSESE() );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();
    }
    s.add( vst );
    sese2vst.put( vst.getSESE(), s );

    s = var2vst.get( vst.getVar() );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();
    }
    s.add( vst );
    var2vst.put( vst.getVar(), s );

    SVKey key = new SVKey( vst.getSESE(), vst.getVar() );
    s = sv2vst.get( key );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();
    }
    s.add( vst );
    sv2vst.put( key, s );
  }


  public Set<VariableSourceToken> get( FlatSESEEnterNode sese ) {
    Set<VariableSourceToken> s = sese2vst.get( sese );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();      
    }
    return s;
  }

  public Set<VariableSourceToken> get( TempDescriptor var ) {
    Set<VariableSourceToken> s = var2vst.get( var );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();      
    }
    return s;
  }

  public Set<VariableSourceToken> get( SVKey key ) {
    Set<VariableSourceToken> s = sv2vst.get( key );
    if( s == null ) {
      s = new HashSet<VariableSourceToken>();      
    }
    return s;
  }


  public void merge( VarSrcTokTable table ) {
    sese2vst.putall( table.sese2vst );
     var2vst.putall( table.var2vst  );
      sv2vst.putall( table.sv2vst   );
  }


  public void remove( FlatSESEEnterNode sese ) {
    Set<VariableSourceToken> s = sese2vst.get( sese );
    if( s == null ) {
      return;
    }

    Iterator<VariableSourceToken> vstItr = s.iterator();
    while( vstItr.hasNext() ) {
      VariableSourceToken vst = vstItr.next();      

	/*
      TempDescriptor      var = vst.getVar();

      sv2vst.remove( new SVKey( sese, var ) );

      Set<VariableSourceToken> sByVar = var2vst.get( var );
      if( sByVar == null ) {
        continue;
      }
      Iterator<VariableSourceToken> byVarItr = sByVar.clone().iterator();
      while( byVarItr.hasNext() ) {
	VariableSourceToken vstByVar = byVarItr.next();

	
      }
	*/
    }
        
    sese2vst.remove( sese );
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof VariableSourceToken) ) {
      return false;
    }

    VariableSourceToken vst = (VariableSourceToken) o;

    return var.equals( vst.var ) &&
           age.equals( vst.age );
  }

  public int hashCode() {
    return (var.hashCode() << 2) ^ age.intValue();
  }


  public String toString() {
    return "["+var+", "+age+"]";
  }
}
