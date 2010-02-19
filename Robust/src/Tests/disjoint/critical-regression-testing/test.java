import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;
import Analysis.Disjoint.*;


public class test {

  static public void main( String[] args ) {
    test t = new test();
    t.execTests();
  }

  void execTests() {
    System.out.println( "Testing components of disjoint reachability analysis..." );
    t1();
    t2();
    System.out.println( "Testing completed successfully." );    
  }

  void t1() {
    ReachTuple rt11a = ReachTuple.factory( 11, true,  ReachTuple.ARITY_ONE );
    ReachTuple rt11b = ReachTuple.factory( 11, true,  ReachTuple.ARITY_ONE );
    ReachTuple rt12  = ReachTuple.factory( 12, true,  ReachTuple.ARITY_ONE );
    ReachTuple rt12z = ReachTuple.factory( 12, true,  ReachTuple.ARITY_ZEROORMORE );
    ReachTuple rt13  = ReachTuple.factory( 13, false, ReachTuple.ARITY_ONE );

    assert rt11a.equals( rt11b );
    assert rt11b.equals( rt11a );
    assert rt11a == rt11b;
    assert !rt11a.equals( rt12 );
    assert !rt12.equals( rt11b );
    
    assert Canonical.unionArity( rt11a, rt11b ).isCanonical();
    assert Canonical.unionArity( rt11a, rt11b ).getArity() == ReachTuple.ARITY_ZEROORMORE;

    assert Canonical.unionArity( rt12, rt12z ).isCanonical();
    assert Canonical.unionArity( rt12, rt12z ).getArity() == ReachTuple.ARITY_ZEROORMORE;

    assert Canonical.unionArity( rt13, rt13 ).isCanonical();
    assert Canonical.unionArity( rt13, rt13 ).getArity() == ReachTuple.ARITY_ONE;
  }

  void t2() {
    ReachTuple rt14 = ReachTuple.factory( 14, false, ReachTuple.ARITY_ONE );
    ReachTuple rt15 = ReachTuple.factory( 15, true,  ReachTuple.ARITY_ZEROORMORE );

    ReachState s1 = ReachState.factory();
    ReachState s2 = Canonical.union( s1, rt14 );

    ReachState s3 = ReachState.factory();
    ReachState s4 = Canonical.union( s3, rt15 );

    ReachState s5 = ReachState.factory( rt14 );

    ReachState s6 = ReachState.factory( rt15 );

    ReachState s7 = Canonical.union( s2, s4 );
    ReachState s8 = Canonical.union( s5, s6 );

    assert s1 == s3;
    assert s2 == s5;
    assert s4 == s6;
    assert s7 == s8;
    assert !s1.equals( s7 );
    assert !s8.equals( s5 );
  }

}
