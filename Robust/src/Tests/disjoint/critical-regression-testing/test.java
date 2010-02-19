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
    ReachTuple rt11a = ReachTuple.factory( 11, true, ReachTuple.ARITY_ONE );
    ReachTuple rt11b = ReachTuple.factory( 11, true, ReachTuple.ARITY_ONE );
    ReachTuple rt12  = ReachTuple.factory( 12, true, ReachTuple.ARITY_ONE );
    ReachTuple rt12z = ReachTuple.factory( 12, true, ReachTuple.ARITY_ZEROORMORE );
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
    
  }

}
