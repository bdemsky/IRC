import IR.*;
import IR.Flat.*;
import Analysis.OwnershipAnalysis.*;
import java.util.*;
import java.io.*;


public class Main {

    protected static void test( String test,
				boolean expected,
				boolean result ) {

	String outcome = "...\tFAILED";
	if( expected == result ) {
	    outcome = "...\tpassed";
	}
	
	System.out.println( test+" expected "+expected+outcome );
    }

    public static void main(String args[]) throws Exception {

	// example test to know the testing routine is correct!
	test( "4 == 5?", false, 4 == 5 );
	test( "3 == 3?", true,  3 == 3 );


	TokenTuple tt0 = new TokenTuple( new Integer( 1 ),
					 true,
					 TokenTuple.ARITY_ONE );

	TokenTuple tt1 = new TokenTuple( new Integer( 1 ),
					 true,
					 TokenTuple.ARITY_ONE );

	TokenTuple tt2 = new TokenTuple( new Integer( 2 ),
					 true,
					 TokenTuple.ARITY_ONE );

	TokenTuple tt3 = new TokenTuple( new Integer( 1 ),
					 true,
					 TokenTuple.ARITY_MANY );

	test( "tt0 equals tt1?", true,  tt0.equals( tt1 ) );
	test( "tt1 equals tt0?", true,  tt1.equals( tt0 ) );

	test( "tt0 equals tt2?", false, tt0.equals( tt2 ) );
	test( "tt2 equals tt0?", false, tt2.equals( tt0 ) );

	test( "tt0 equals tt3?", false, tt0.equals( tt3 ) );
	test( "tt3 equals tt0?", false, tt3.equals( tt0 ) );

	test( "tt2 equals tt3?", false, tt2.equals( tt3 ) );
	test( "tt3 equals tt2?", false, tt3.equals( tt2 ) );

	tt1 = tt1.increaseArity();

	test( "tt1 equals tt2?", false, tt1.equals( tt2 ) );
	test( "tt2 equals tt1?", false, tt2.equals( tt1 ) );

	test( "tt1 equals tt3?", true,  tt1.equals( tt3 ) );
	test( "tt3 equals tt1?", true,  tt3.equals( tt1 ) );
	
	
	TokenTupleSet tts0 = new TokenTupleSet( tt0 );
	TokenTupleSet tts1 = new TokenTupleSet( tt1 );
	TokenTupleSet tts2 = new TokenTupleSet( tt2 );
	TokenTupleSet tts3 = new TokenTupleSet( tt3 );
	TokenTupleSet tts4 = tts1.union( tts3 );
	TokenTupleSet tts5 = tts0.union( tts2 );
	TokenTupleSet tts6 = tts1.union( tts1 );

	System.out.println( "tts4 is "+tts4 );
	System.out.println( "tts5 is "+tts5 );
	System.out.println( "tts6 is "+tts6 );

	ReachabilitySet rs0 = new ReachabilitySet( tts0 );
	rs0 = rs0.union( new ReachabilitySet( tts2 ) );
	rs0 = rs0.union( new ReachabilitySet( tts5 ) );

	System.out.println( "rs0 is "+rs0 );

	TokenTuple tt4 = new TokenTuple( new Integer( 4 ),
					 true,
					 TokenTuple.ARITY_ONE );

	/*	TokenTuple tt5 = new TokenTuple( new Integer( 4 ),
					 true,
					 TokenTuple.ARITY_ONE );
	*/
	TokenTuple tt6 = new TokenTuple( new Integer( 6 ),
					 false,
					 TokenTuple.ARITY_ONE );

	TokenTupleSet tts7 = new TokenTupleSet( tt4 );
	//TokenTupleSet tts8 = new TokenTupleSet( tt5 );
	TokenTupleSet tts9 = new TokenTupleSet( tt1 );
	tts9 = tts9.union( tts2 );

	ReachabilitySet rs1 = new ReachabilitySet( tts7 );
	//rs1 = rs1.union( new ReachabilitySet( tts8 ) );
	rs1 = rs1.union( new ReachabilitySet( tts9 ) );

	System.out.println( "rs1 is "+rs1 );


	ChangeTupleSet cts0 = rs0.unionUpArity( rs1 );
	System.out.println( "cts0 is "+cts0 );
    }
}