// This builder does several things:
//
//   1) The MultiViewMap constructor is private because
//      there is a lot of consistency checking to do in
//      the inputs (like whether a view is specified 
//      twice) so do all that in a builder and the map
//      itself can assume valid inputs at the constructor.
//
//   2) The inputs to construct a MultiViewMap are tedious
//      to code, so this builder lets you write the 
//      specifications succinctly.
//
//   3) If you are creating many MultiViewMap's of the same
//      type and views, it is best to have one builder that
//      generates each fresh map rather than build up all
//      the small specification objects again.
//
package Util;

import java.util.BitSet;
import java.util.Vector;


public class MultiViewMapBuilder<T> {

  private Class[]        keyTypes;
  private Vector<BitSet> partialViews;
  private BitSet         fullView;

  private JoinOp<T> joinOp;
  
  private boolean checkTypes;
  private boolean checkConsistency;
  

  // define the types and ordering of the multikey
  public MultiViewMapBuilder( Class[] keyTypes, JoinOp<T> joinOp ) {
    assert( keyTypes != null );
    assert( joinOp != null );

    if( keyTypes.length == 0 ) {
      throw new IllegalArgumentException( "The multikey must have at least one key type." );
    }

    this.keyTypes         = keyTypes;
    this.partialViews     = new Vector<BitSet>();
    this.joinOp           = joinOp;
    this.checkTypes       = false;
    this.checkConsistency = false;

    fullView = new BitSet( keyTypes.length );
    for( int i = 0; i < keyTypes.length; ++i ) {
      fullView.set( i );
    }
  }


  public final BitSet addPartialView( Integer... keyIndices ) { 
    if( keyIndices.length == 0 ) {
      throw new IllegalArgumentException( "A view must have at least one key index." );
    }

    // build a view from the arg list
    BitSet view = new BitSet( keyTypes.length );
    for( Integer i : keyIndices ) {
      if( i < 0 || i >= keyTypes.length ) {
        throw new IllegalArgumentException( "Key index in view is out of bounds." );
      }
      if( view.get( i ) ) {
        throw new IllegalArgumentException( "Key index in view is specified more than once." );
      }
      view.set( i );
    }

    if( keyIndices.length  == keyTypes.length &&
        view.cardinality() == keyTypes.length ) {
      throw new IllegalArgumentException( "The full view is always included implicitly." );
    }

    for( BitSet existingView : partialViews ) {
      if( view.equals( existingView ) ) {
        throw new IllegalArgumentException( "View matches an existing view." );
      }
    }

    return addPartialView( view );
  }


  public final BitSet addPartialView( BitSet view ) {
    partialViews.add( view );
    return (BitSet) view.clone();
  }


  public final BitSet getFullView() {
    return fullView;
  }


  public void setCheckTypes( boolean checkTypes ) {
    this.checkTypes = checkTypes;
  }


  public void setCheckConsistency( boolean checkConsistency ) {
    this.checkConsistency = checkConsistency;
  }


  public MultiViewMap<T> build() {
    if( partialViews.isEmpty() ) {
      throw new IllegalArgumentException( "No partial views specified for this builder." );
    }
    return new MultiViewMap<T>( keyTypes,
                                joinOp,
                                fullView,
                                partialViews, 
                                checkTypes, 
                                checkConsistency );
  }
}
