package Analysis.Disjoint;

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
  private static BitSet viewR0;
  private static BitSet viewR1;
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




  // for MultiViewMaps that don't need to use the value,
  // always map to this dummy
  private static Object dummy = new Integer( -12345 );


  // call before instantiating this class
  static public void initBuilders() {
    RBuilder =
      new MultiViewMapBuilder<Object>( new Class[] {
                                         TempDescriptor.class,
                                         TempDescriptor.class,
                                         EdgeKey.class },
                                       new JoinOpNop() );
    viewR0  = RBuilder.addPartialView( 0 );
    viewR1  = RBuilder.addPartialView( 1 );
    viewR01 = RBuilder.addPartialView( 0, 1 );
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





  public DefiniteReachState() {
    //Rs = new HashMap<TempDescriptor, DefReachKnown>();
    //Fu = FuBuilder.build();
  }



  public void methodEntry(Set<TempDescriptor> parameters) {
    // R' := {}
    // R.clear();

    //Rs.clear();
    //for( TempDescriptor p : parameters ) {
    //  Rs.put( p, DefReachKnown.UNKNOWN );
    //}
    //
    //Fu = FuBuilder.build();
  }

  public void copy(TempDescriptor x,
                   TempDescriptor y) {
    // R' := (R - <x,*> - <*,x>)        U
    //       {<x,z>->e | <y,z>->e in R} U
    //       {<z,x>->e | <z,y>->e in R}
    // R' = new Map(R)
    // R'.remove(view0, x);
    // R'.remove(view1, x);
    // setYs = R.get(view0, y);
    // for each <y,z>->e: R'.put(<x,z>, e);
    // setYs = R.get(view1, y);
    // for each <z,y>->e: R'.put(<z,x>, e);

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

  public void load(TempDescriptor x,
                   TempDescriptor y,
                   FieldDescriptor f) {
    // R' := (R - <x,*> - <*,x>) U
    //       ({<x,y>} x Eo(y,f)) U
    //       U        {<x,z>} x (Eo(y,f)U{e})
    //   <y,z>->e in R
    // R' = new Map(R)
    // R'.remove(view0, x);
    // R'.remove(view1, x);
    // R'.put(<x,y>, eee!);
    // setYs = R.get(view0, y);
    // for each <y,z>->e: R'.put(<x,z>, eee!Ue);

    // Rs' := (Rs - <x,*>) U {<x, unknown>}
    //Rs.put( x, DefReachKnown.UNKNOWN );
  }

  public void store(TempDescriptor x,
                   FieldDescriptor f,
                   TempDescriptor y,
                   Set<EdgeKey> edgeKeysRemoved) {
    // I think this should be if there is ANY <w,z>->e' IN Eremove, then kill all <w,z>
    // R' := (R - {<w,z>->e | <w,z>->e in R, A<w,z>->e' in R, e' notin Eremove}) U
    //       {<y,x>->e | e in E(x) x {f} x E(y)}
    // R' = new Map(R)
    // R'.remove(?); some e's...
    // R'.put(<y,x>, E(x) x {f} x E(y));

    // Rs' := Rs
  }

  public void newObject(TempDescriptor x) {
    // R' := (R - <x,*> - <*,x>)
    // R' = new Map(R)
    // R'.remove(view0, x);
    // R'.remove(view1, x);

    // Rs' := (Rs - <x,*>) U {<x, new>}
    //Rs.put( x, DefReachKnown.KNOWN );
    
  }

  // x is the return value, x = foo(...);
  public void methodCall(TempDescriptor x) {
    // R' := (R - <x,*> - <*,x>)
    // R' = new Map(R)
    // R'.remove(view0, x);
    // R'.remove(view1, x);

    // Rs' := (Rs - <x,*>) U {<x, unknown>}
    //Rs.put( x, DefReachKnown.UNKNOWN );
  }

  public void merge( DefiniteReachState that ) {
    // R' := <x,y>->e iff its in all incoming edges

    // Rs' := <x, new> iff in all incoming edges, otherwie <x, unknown>
    //mergeRs( that );
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



  public String toString() {
    StringBuilder s = new StringBuilder( "R_s = {" );
    //for( TempDescriptor x : Rs.keySet() ) {
    //  s.append( "  "+x+"->"+Rs.get( x ) );
    //}
    //s.append( "}" );
    return s.toString();
  }
}
