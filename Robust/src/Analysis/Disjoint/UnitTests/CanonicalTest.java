package Analysis.Disjoint.UnitTests;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

import Analysis.Disjoint.*;


public class CanonicalTest {

  private static void check( String testName, ReachSet actual, ReachSet expected ) {
    System.out.print( testName + "... " );
    if( expected.equals( actual ) ) {
      System.out.println( "passed" );
    } else {
      System.out.println( "FAILED:" );
      System.out.println( "  expected = "+expected );
      System.out.println( "  actual   = "+actual );
    }
    System.out.println();
  }


  private static ReachSet toCallerContext( ReachSet in, AllocSite... sites ) {
    ReachSet out = in;
    for( AllocSite as : sites ) {
      out = Canonical.toCallerContext( out, as );
    }
    return out;
  }


  public static void main( String[] args ) {
    AllocSite as1 = AllocSite.factory( 1, null, "as1", true );
    as1.setIthOldest( 0, 1 );
    as1.setSummary(      2 );

    AllocSite as2 = AllocSite.factory( 1, null, "as2", true );
    as2.setIthOldest( 0, 3 );
    as2.setSummary(      4 );

    ReachTuple rt1  = ReachTuple.factory(  1, false, ReachTuple.ARITY_ONE, false );
    ReachTuple rt1o = ReachTuple.factory(  1, false, ReachTuple.ARITY_ONE, true );
    ReachTuple rtm1 = ReachTuple.factory( -1, false, ReachTuple.ARITY_ONE, false );

    ReachTuple rt2   = ReachTuple.factory(  2, true, ReachTuple.ARITY_ONE,        false );
    ReachTuple rt2s  = ReachTuple.factory(  2, true, ReachTuple.ARITY_ZEROORMORE, false );
    ReachTuple rt2o  = ReachTuple.factory(  2, true, ReachTuple.ARITY_ONE,        true  );
    ReachTuple rt2so = ReachTuple.factory(  2, true, ReachTuple.ARITY_ZEROORMORE, true  );
    ReachTuple rtm2  = ReachTuple.factory( -2, true, ReachTuple.ARITY_ONE,        false );
    ReachTuple rtm2s = ReachTuple.factory( -2, true, ReachTuple.ARITY_ZEROORMORE, false );

    ReachTuple rt3  = ReachTuple.factory(  3, false, ReachTuple.ARITY_ONE, false );
    ReachTuple rt3o = ReachTuple.factory(  3, false, ReachTuple.ARITY_ONE, true );
    ReachTuple rtm3 = ReachTuple.factory( -3, false, ReachTuple.ARITY_ONE, false );

    ReachTuple rt4   = ReachTuple.factory(  4, true, ReachTuple.ARITY_ONE,        false );
    ReachTuple rt4s  = ReachTuple.factory(  4, true, ReachTuple.ARITY_ZEROORMORE, false );
    ReachTuple rt4o  = ReachTuple.factory(  4, true, ReachTuple.ARITY_ONE,        true  );
    ReachTuple rt4so = ReachTuple.factory(  4, true, ReachTuple.ARITY_ZEROORMORE, true  );
    ReachTuple rtm4  = ReachTuple.factory( -4, true, ReachTuple.ARITY_ONE,        false );
    ReachTuple rtm4s = ReachTuple.factory( -4, true, ReachTuple.ARITY_ZEROORMORE, false );

    // [1, 4?*] to caller context, with respect to AS1(1,2S), should be [-1, 4?*]
    check( "change just 0th",
           toCallerContext( ReachSet.factory( ReachState.factory( rt1, rt4so ) ), as1 ),
           ReachSet.factory( ReachState.factory( rtm1, rt4so ) )
           );

    // [1, 3?] to caller context should be [-1, 3]
    check( "against both allocation sites",
           toCallerContext( ReachSet.factory( ReachState.factory( rt1, rt3o ) ), as1, as2 ),
           ReachSet.factory( ReachState.factory( rtm1, rt3 ) )
           );

    // [1?, 2S, 3, 4S*] -> [1, -2S, -3, -4S*]
    check( "all 1-to-1 translations",
           toCallerContext( ReachSet.factory( ReachState.factory( rt1o, 
                                                                  rt2,
                                                                  rt3,
                                                                  rt4s ) ), as1, as2 ),
           ReachSet.factory( ReachState.factory( rt1, rtm2, rtm3, rtm4s ) )
           );
    
    // [2S?, 3] -> [2S, -3], [1?, -3], [2S?, -3]
    check( "Out-of-context summary becomes three things",
           toCallerContext( ReachSet.factory( ReachState.factory( rt2o, rt3 ) ), as1, as2 ),
           ReachSet.factory( ReachState.factory( rt2,  rtm3 ),
                             ReachState.factory( rt1o, rtm3 ),
                             ReachState.factory( rt2o, rtm3 ) )
           );

    // [2S?, 1?] -> [2S, 1], [1?, 1], [2S?, 1]
    check( "Out-of-context summary becomes three things",
           toCallerContext( ReachSet.factory( ReachState.factory( rt2o, rt1o ) ), as1, as2 ),
           ReachSet.factory( ReachState.factory( rt2,  rt1 ),
                             ReachState.factory( rt1o, rt1 ),
                             ReachState.factory( rt2o, rt1 ) )
           );

    // [2S?*, 1?] -> [2S*, 2S?*, 1]
    check( "* Out-of-context summary becomes two tokens in one state",
           toCallerContext( ReachSet.factory( ReachState.factory( rt2so, rt1o ) ), as1, as2 ),
           ReachSet.factory( ReachState.factory( rt2s, rt2so, rt1 ) )
           );
  }
}
