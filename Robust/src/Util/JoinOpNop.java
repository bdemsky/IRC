////////////////////////////////////////////
//
//  This join op is useful for multiviewmaps
//  where the multikey holds all useful values
//  and the templated value of the map is ignored.
//  When used this way, the multiviewmap functions
//  like an indexed set of tuples.
//
////////////////////////////////////////////

package Util;

public class JoinOpNop implements JoinOp<Object> {
  public Object join( Object a, Object b ) {
    return null;
  }
}
