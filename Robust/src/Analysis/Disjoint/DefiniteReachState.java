package Analysis.Disjoint;

import java.io.*;
import java.util.*;

import IR.*;
import IR.Flat.*;
import Util.*;


public class DefiniteReachState {

  // R
  //
  // Maps two variables to an edge (x, y, e) to an unused value when the
  // object of x is already reachable from the object of y, and the
  // set of edges conservatively gives the path.
  // NOTE: Use EdgeKey instead of edges because this analysis's
  // scope is beyond the scope of a single reach graph.
  private static MultiViewMapBuilder<Object> RBuilder;
  private static BitSet viewRfull;
  private static BitSet viewR0;
  private static BitSet viewR1;
  private static BitSet viewR2;
  private static BitSet viewR01;
  private MultiViewMap<Object> R;

  // Rs
  //
  // Tracks whether the analysis must know the definite reachability
  // information of a given variable.
  //private enum DefReachKnown {
  //  UNKNOWN,
  //  KNOWN,
  //}
  //private Map<TempDescriptor, DefReachKnown> Rs;
  
  
  // Fu (upstream)
  //
  // Maps a variable that points to object o0 to the
  // set of variables that point to objects o1...oN
  // that have a reference to o0.
  //private static MultiViewMapBuilder<Object> FuBuilder;
  //private static BitSet viewFu0;
  //private static BitSet viewFu1;
  //private MultiViewMap<Object> Fu;


  // Fd (downstream)






  // call before instantiating this class
  static public void initBuilders() {
    RBuilder =
      new MultiViewMapBuilder<Object>( new Class[] {
                                         TempDescriptor.class,
                                         TempDescriptor.class,
                                         EdgeKey.class },
                                       new JoinOpNop() );
    viewRfull = RBuilder.getFullView();
    viewR0    = RBuilder.addPartialView( 0 );
    viewR1    = RBuilder.addPartialView( 1 );
    viewR2    = RBuilder.addPartialView( 2 );
    viewR01   = RBuilder.addPartialView( 0, 1 );
    RBuilder.setCheckTypes( true );
    RBuilder.setCheckConsistency( true );

    //FuBuilder =
    //  new MultiViewMapBuilder<Object>( new Class[] {
    //                                     TempDescriptor.class,
    //                                     DefReachFuVal.class},
    //                                   new JoinOpNop() );
    //viewFu0 = FuBuilder.addPartialView( 0 );
    //viewFu1 = FuBuilder.addPartialView( 1 );
    //FuBuilder.setCheckTypes( true );
    //FuBuilder.setCheckConsistency( true );
  }



  public DefiniteReachState( DefiniteReachState toCopy ) {
    this.R = toCopy.R.clone( RBuilder );
  }


  public DefiniteReachState() {
    R = RBuilder.build();
    //Rs = new HashMap<TempDescriptor, DefReachKnown>();

    //Fu = FuBuilder.build();
  }


  public void methodEntry( Set<TempDescriptor> parameters ) {
    methodEntryR( parameters );

    //Rs.clear();
    //for( TempDescriptor p : parameters ) {
    //  Rs.put( p, DefReachKnown.UNKNOWN );
    //}
    //
    //Fu = FuBuilder.build();
  }

  public void copy( TempDescriptor x,
                    TempDescriptor y ) {
    copyR( x, y );

    // Rs' := (Rs - <x,*>) U {<x,v> | <y,v> in Rs}
    //DefReachKnown valRs = Rs.get( y );
    //assert( valRs != null );
    //Rs.put( x, valRs );

    // Fu' := (Fu - <x, *> - <*, x>) U
    //        {<x,v> | <y,v> in Fu} U
    //        {<v,x> | <v,y> in Fu} U
    //        {<z, unknown> | <z,<x>> in Fu}
    //Fu.remove( viewFu0, MultiKey.factory( x ) );
    //Fu.remove( viewFu1, MultiKey.factory( x ) );
    //for( MultiKey key : Fu.get( viewFu0, MultiKey.factory( y ) ).keySet() ) {
    //  DefReachFuVal val = (DefReachFuVal) key.get( 1 );
    //  Fu.put( MultiKey.factory( x, val ), dummy );
    //}
    //for( MultiKey key : Fu.get( viewFu1, MultiKey.factory( y ) ).keySet() ) {
    //  TempDescriptor v = (TempDescriptor) key.get( 0 );
    //  Fu.put( MultiKey.factory( v, DefReachFuVal.factory( x ) ), dummy );
    //}
    //for( MultiKey key : 
    //       Fu.get( viewFu1, 
    //               MultiKey.factory( DefReachFuVal.factory( DefReachFuVal.Val.UNKNOWN ) )
    //               ).keySet() 
    //     ) {
    //  TempDescriptor z = (TempDescriptor) key.get( 0 );
    //  Fu.put( MultiKey.factory( z, DefReachFuVal.factory( x ) ), dummy );      
    //}
  }

  public void load( TempDescriptor x,
                    TempDescriptor y,
                    FieldDescriptor f,
                    Set<EdgeKey> edgeKeysForLoad ) {

    loadR( x, y, f, edgeKeysForLoad );
    // Rs' := (Rs - <x,*>) U {<x, unknown>}
    //Rs.put( x, DefReachKnown.UNKNOWN );
  }

  public void store( TempDescriptor x,
                     FieldDescriptor f,
                     TempDescriptor y,
                     Set<EdgeKey> edgeKeysRemoved,
                     Set<EdgeKey> edgeKeysAdded ) {

    storeR( x, f, y, edgeKeysRemoved, edgeKeysAdded );
    // Rs' := Rs
  }

  public void newObject( TempDescriptor x ) {
    newObjectR( x );

    // Rs' := (Rs - <x,*>) U {<x, new>}
    //Rs.put( x, DefReachKnown.KNOWN );
    
  }

  public void methodCall( TempDescriptor retVal ) {
    methodCallR( retVal );

    // Rs' := (Rs - <x,*>) U {<x, unknown>}
    //Rs.put( x, DefReachKnown.UNKNOWN );
  }

  public void merge( DefiniteReachState that ) {
    mergeR( that );

    // Rs' := <x, new> iff in all incoming edges, otherwie <x, unknown>
    //mergeRs( that );
  }






  public void methodEntryR( Set<TempDescriptor> parameters ) {
    R.clear();
  }

  public void copyR( TempDescriptor x,
                     TempDescriptor y ) {
    // consider that x and y can be the same, so do the
    // parts of the update in the right order:

    // first get all info for update
    MultiKey keyY = MultiKey.factory( y );
    Map<MultiKey, Object> mapY0 = R.get( viewR0, keyY );
    Map<MultiKey, Object> mapY1 = R.get( viewR1, keyY );

    // then remove anything
    MultiKey keyX = MultiKey.factory( x );
    R.remove( viewR0, keyX );
    R.remove( viewR1, keyX );

    // then insert new stuff
    for( MultiKey fullKeyY : mapY0.keySet() ) {
      MultiKey fullKeyX = MultiKey.factory( x, 
                                            fullKeyY.get( 1 ), 
                                            fullKeyY.get( 2 ) );
      R.put( fullKeyX, MultiViewMap.dummyValue );
    }
    for( MultiKey fullKeyY : mapY1.keySet() ) {
      MultiKey fullKeyX = MultiKey.factory( fullKeyY.get( 0 ), 
                                            x,
                                            fullKeyY.get( 2 ) );
      R.put( fullKeyX, MultiViewMap.dummyValue );
    }
  }
  
  public void loadR( TempDescriptor x,
                     TempDescriptor y,
                     FieldDescriptor f,
                     Set<EdgeKey> edgeKeysForLoad ) {
    // consider that x and y can be the same, so do the
    // parts of the update in the right order:

    // first get all info for update
    MultiKey keyY = MultiKey.factory( y );
    Map<MultiKey, Object> mapY0 = R.get( viewR0, keyY );

    // then remove anything
    MultiKey keyX = MultiKey.factory( x );
    R.remove( viewR0, keyX );
    R.remove( viewR1, keyX );

    // then insert new stuff
    for( EdgeKey e : edgeKeysForLoad ) {
      R.put( MultiKey.factory( x, y, e ), MultiViewMap.dummyValue );

      for( MultiKey fullKeyY : mapY0.keySet() ) {
        R.put( MultiKey.factory( x,
                                 fullKeyY.get( 1 ), 
                                 e ), 
               MultiViewMap.dummyValue );

        R.put( MultiKey.factory( x, 
                                 fullKeyY.get( 1 ), 
                                 fullKeyY.get( 2 ) ), 
               MultiViewMap.dummyValue );
      }
    }
  }

  public void storeR( TempDescriptor x,
                      FieldDescriptor f,
                      TempDescriptor y,
                      Set<EdgeKey> edgeKeysRemoved,
                      Set<EdgeKey> edgeKeysAdded ) {

    for( EdgeKey edgeKeyWZ : edgeKeysRemoved ) {
      R.remove( viewR2, MultiKey.factory( edgeKeyWZ ) );
    }

    for( EdgeKey edgeKeyXY : edgeKeysAdded ) {
      R.put( MultiKey.factory( y, x, edgeKeyXY ), MultiViewMap.dummyValue );
    }
  }
  
  public void newObjectR( TempDescriptor x ) {
    MultiKey keyX = MultiKey.factory( x );
    R.remove( viewR0, keyX );
    R.remove( viewR1, keyX );
  }

  public void methodCallR( TempDescriptor retVal ) {
    MultiKey keyRetVal = MultiKey.factory( retVal );
    R.remove( viewR0, keyRetVal );
    R.remove( viewR1, keyRetVal );
  }

  public void mergeR( DefiniteReachState that ) {
    for( MultiKey key : this.R.get().keySet() ) {
      if( that.R.get( viewRfull, key ).isEmpty() ) {
        this.R.remove( viewRfull, key );
      }
    }
  }




  ///////////////////////////////////////////////////////////
  //
  //  This is WRONG
  //
  //  It definitely tests the current R as well as Rs
  //  
  //  but also be careful what null means, is it actually
  //  equivalent to UNKOWN?  I'd rather put nothing, meaning
  //  we have to do an analysis pass over all the incoming edges
  //  before there is a sensical answer.  I think...
  private void mergeRs( DefiniteReachState that ) {
    // merge "that" into "this" and leave "that" unchanged
    //Set<TempDescriptor> allVars = new HashSet<TempDescriptor>();
    //allVars.addAll( this.Rs.keySet() );
    //allVars.addAll( that.Rs.keySet() );
    //for( TempDescriptor x : allVars ) {
    //  DefReachKnown vThis = this.Rs.get( x );
    //  DefReachKnown vThat = that.Rs.get( x );
    //  if( vThis != null && vThis.equals( DefReachKnown.KNOWN ) &&
    //      vThat != null && vThat.equals( DefReachKnown.KNOWN ) ) {
    //    this.Rs.put( x, DefReachKnown.KNOWN );
    //  } else {
    //    this.Rs.put( x, DefReachKnown.UNKNOWN );
    //  }
    //}
  }



  public boolean equals( Object o ) {
    if( this == o ) {
      return true;
    }
    if( o == null ) {
      return false;
    }
    if( !(o instanceof DefiniteReachState) ) {
      return false;
    }
    DefiniteReachState that = (DefiniteReachState) o;
    
    assert( false );
    return false;
  }


  public int hashCode() {
    assert( false );
    return 0;
  }


  public void writeState( String outputName ) {
    try {
      BufferedWriter bw = new BufferedWriter( new FileWriter( "defReach-"+outputName+".txt" ) );
      bw.write( this.toString() );
      bw.close();
    } catch( IOException e ) {
      System.out.println( "ERROR writing definite reachability state:\n  "+e );
    }
  }



  public String toString() {
    StringBuilder s = new StringBuilder();

    s.append( "R = {\n" );
    s.append( R.toString( 2 ) );
    s.append( "}\n" );

    //s.append( "R_s = {" );
    //for( TempDescriptor x : Rs.keySet() ) {
    //  s.append( "  "+x+"->"+Rs.get( x ) );
    //}
    //s.append( "}" );
    return s.toString();
  }
}
