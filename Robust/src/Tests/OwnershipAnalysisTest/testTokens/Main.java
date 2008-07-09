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

		TokenTuple tt5 = new TokenTuple( new Integer( 4 ),
 					 true,
					 TokenTuple.ARITY_ONE );
	
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
	


	TokenTuple tt00 = new TokenTuple( new Integer( 9 ),
					  true,
					  TokenTuple.ARITY_ONE );

	TokenTuple tt01 = new TokenTuple( new Integer( 9 ),
					  true,
					  TokenTuple.ARITY_ONE );

	test( "tt00 equals tt01?", true,  tt00.equals( tt01 ) );	
	test( "tt00 ==     tt01?", false, tt00 ==      tt01   );	

	tt00 = (TokenTuple) Canonical.makeCanonical( tt00 );
	tt01 = (TokenTuple) Canonical.makeCanonical( tt01 );

	test( "tt00 equals tt01?", true,  tt00.equals( tt01 ) );	
	test( "tt00 ==     tt01?", true,  tt00 ==      tt01   );	


	TokenTuple tt02 = 
	    (TokenTuple) Canonical.makeCanonical( 
						 new TokenTuple( new Integer( 10 ),
								 true,
								 TokenTuple.ARITY_ONE )
						  );

	TokenTuple tt03 = 
	    (TokenTuple) Canonical.makeCanonical( 
						 new TokenTuple( new Integer( 11 ),
								 true,
								 TokenTuple.ARITY_ONE )
						  );

	TokenTuple tt04 = 
	    (TokenTuple) Canonical.makeCanonical( 
						 new TokenTuple( new Integer( 12 ),
								 true,
								 TokenTuple.ARITY_ONE )
						  );

	TokenTupleSet ttsT00 =
	    (TokenTupleSet) Canonical.makeCanonical( new TokenTupleSet( tt00 ) );

	TokenTupleSet ttsT01 =
	    (TokenTupleSet) Canonical.makeCanonical( new TokenTupleSet( tt01 ) );

	TokenTupleSet ttsT02 =
	    (TokenTupleSet) Canonical.makeCanonical( new TokenTupleSet( tt02 ) );

	TokenTupleSet ttsT03 =
	    (TokenTupleSet) Canonical.makeCanonical( new TokenTupleSet( tt03 ) );

	TokenTupleSet ttsT04 =
	    (TokenTupleSet) Canonical.makeCanonical( new TokenTupleSet( tt04 ) );

	TokenTupleSet tts00 = ttsT00.union( ttsT02.union( ttsT03.union( ttsT04 ) ) );
	TokenTupleSet tts01 = ttsT01.union( ttsT02.union( ttsT03.union( ttsT04 ) ) );

	test( "tts00 equals tts01?", true,  tts00.equals( tts01 ) );

	// It's OK that this one turns out true--I changed the union operator
	// to automatically canonicalize stuff!
	test( "tts00 ==     tts01?", false, tts00 ==      tts01   );	

	tts00 = (TokenTupleSet) Canonical.makeCanonical( tts00 );
	tts01 = (TokenTupleSet) Canonical.makeCanonical( tts01 );

	test( "tts00 equals tts01?", true,  tts00.equals( tts01 ) );	
	test( "tts00 ==     tts01?", true,  tts00 ==      tts01   );	
    }
}
