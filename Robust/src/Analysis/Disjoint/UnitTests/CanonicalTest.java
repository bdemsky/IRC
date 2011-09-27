package Analysis.Disjoint.UnitTests;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

import Analysis.Disjoint.*;


public class CanonicalTest {

  private static void check( String testName, ReachState expected, ReachState actual ) {
    System.out.print( testName + "... " );
    if( expected.equals( actual ) ) {
      System.out.println( "passed" );
    } else {
      System.out.println( "FAILED" );
    }
  }

  public static void main( String[] args ) {
    ReachTuple a    = ReachTuple.factory( 1, false, ReachTuple.ARITY_ONE, false );
    ReachTuple aooc = ReachTuple.factory( 1, false, ReachTuple.ARITY_ONE, true );
    ReachState state1 = ReachState.factory( a, aooc );
    ReachState state2 = ReachState.factory( a, aooc );
    check( "a test", state1, state2 );
  }
}
