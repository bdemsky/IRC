///////////////////////////////////////////
//
//  Just an interface for specifying
//  how to join two instances of a
//  type.  This is useful within a
//  generic structure, like MultiViewMap.
//
///////////////////////////////////////////
package Util;

public interface JoinOp<T> {

  ////////////////////////////////////////
  //
  //  join() should accept null values for
  //  the arguments!
  //
  ////////////////////////////////////////
  T join( T a, T b );
}
