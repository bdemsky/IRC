import IR.*;
import IR.Flat.*;
import Analysis.OwnershipAnalysis.*;
import java.util.*;
import java.io.*;


public class Main {

    static boolean aTestFailed;


    protected static void test( String test,
				boolean expected,
				boolean result ) {
	String outcome;
	if( expected == result ) {
	    outcome = "...\tpassed";
	} else {
	    outcome = "...\tFAILED";
	    aTestFailed = true;
	}
	
	System.out.println( test+" expected "+expected+outcome );
    }


    public static void main(String args[]) throws Exception {

	aTestFailed = false;

	testExample();
	System.out.println( "---------------------------------------" );
	testTokenTuple();
	System.out.println( "---------------------------------------" );
	testTokenTupleSet();
	System.out.println( "---------------------------------------" );

	if( aTestFailed ) {
	    System.out.println( "<><><><><><><><><><><><><><><><><><><><><><><><>" );
	    System.out.println( "<><><> WARNING: At least one test failed. <><><>" );
	    System.out.println( "<><><><><><><><><><><><><><><><><><><><><><><><>" );
	} else {
	    System.out.println( "All tests passed." );
	}
    }

    
    public static void testExample() {
	
	// example test to know the testing routine is correct!
	test( "4 == 5?", false, 4 == 5 );
	test( "3 == 3?", true,  3 == 3 );
    }


    public static void testTokenTuple() {

	TokenTuple tt0 = new TokenTuple( new Integer( 1 ), true,  TokenTuple.ARITY_ONE  );
	TokenTuple tt1 = new TokenTuple( new Integer( 1 ), true,  TokenTuple.ARITY_ONE  );
	TokenTuple tt2 = new TokenTuple( new Integer( 2 ), true,  TokenTuple.ARITY_ONE  );
	TokenTuple tt3 = new TokenTuple( new Integer( 1 ), true,  TokenTuple.ARITY_MANY );
	TokenTuple tt4 = new TokenTuple( new Integer( 3 ), false, TokenTuple.ARITY_ONE  );
	TokenTuple tt5 = new TokenTuple( new Integer( 3 ), false, TokenTuple.ARITY_ONE  );

	test( "tt0 equals tt1?", true,  tt0.equals( tt1 ) );
	test( "tt1 equals tt0?", true,  tt1.equals( tt0 ) );
	test( "tt1.hashCode == tt0.hashCode?", true, tt1.hashCode() == tt0.hashCode() );

	test( "tt0 equals tt2?", false, tt0.equals( tt2 ) );
	test( "tt2 equals tt0?", false, tt2.equals( tt0 ) );
	test( "tt2.hashCode == tt0.hashCode?", false, tt2.hashCode() == tt0.hashCode() );

	test( "tt0 equals tt3?", false, tt0.equals( tt3 ) );
	test( "tt3 equals tt0?", false, tt3.equals( tt0 ) );
	test( "tt3.hashCode == tt0.hashCode?", false, tt3.hashCode() == tt0.hashCode() );

	test( "tt2 equals tt3?", false, tt2.equals( tt3 ) );
	test( "tt3 equals tt2?", false, tt3.equals( tt2 ) );
	test( "tt3.hashCode == tt2.hashCode?", false, tt3.hashCode() == tt2.hashCode() );

	tt1 = tt1.increaseArity();

	test( "tt1 equals tt2?", false, tt1.equals( tt2 ) );
	test( "tt2 equals tt1?", false, tt2.equals( tt1 ) );
	test( "tt2.hashCode == tt1.hashCode?", false, tt2.hashCode() == tt1.hashCode() );

	test( "tt1 equals tt3?", true,  tt1.equals( tt3 ) );
	test( "tt3 equals tt1?", true,  tt3.equals( tt1 ) );	
	test( "tt3.hashCode == tt1.hashCode?", true, tt3.hashCode() == tt1.hashCode() );

	test( "tt4 equals tt5?", true,  tt4.equals( tt5 ) );
	test( "tt5 equals tt4?", true,  tt5.equals( tt4 ) );
	test( "tt5.hashCode == tt4.hashCode?", true, tt5.hashCode() == tt4.hashCode() );

	tt4 = tt4.increaseArity();

	test( "tt4 equals tt5?", true,  tt4.equals( tt5 ) );
	test( "tt5 equals tt4?", true,  tt5.equals( tt4 ) );
	test( "tt5.hashCode == tt4.hashCode?", true, tt5.hashCode() == tt4.hashCode() );


	TokenTuple tt6 = new TokenTuple( new Integer( 6 ), false, TokenTuple.ARITY_ONE  );
	TokenTuple tt7 = new TokenTuple( new Integer( 6 ), false, TokenTuple.ARITY_ONE  );
	TokenTuple tt8 = new TokenTuple( new Integer( 8 ), false, TokenTuple.ARITY_ONE  );
	TokenTuple tt9 = new TokenTuple( new Integer( 9 ), false, TokenTuple.ARITY_ONE  );

	test( "tt6 equals tt7?",               true,  tt6.equals( tt7 )                );
	test( "tt6.hashCode == tt7.hashCode?", true,  tt6.hashCode() == tt7.hashCode() );

	test( "tt8 equals tt7?",               false, tt8.equals( tt7 )                );
	test( "tt8.hashCode == tt7.hashCode?", false, tt8.hashCode() == tt7.hashCode() );

	// notice that this makes tt7 canonical
	tt7 = tt7.changeTokenTo( new Integer( 8 ) );

	test( "tt6 equals tt7?",               false, tt6.equals( tt7 )                );
	test( "tt6.hashCode == tt7.hashCode?", false, tt6.hashCode() == tt7.hashCode() );

	test( "tt8 equals tt7?",               true,  tt8.equals( tt7 )                );
	test( "tt8.hashCode == tt7.hashCode?", true,  tt8.hashCode() == tt7.hashCode() );

	test( "tt6 == tt7?", false, tt6 == tt7 );
	test( "tt8 == tt7?", false, tt8 == tt7 );
	test( "tt9 == tt7?", false, tt9 == tt7 );

	tt6 = tt6.makeCanonical();
	tt8 = tt8.makeCanonical();
	tt9 = tt9.makeCanonical();

	test( "tt6 == tt7?", false, tt6 == tt7 );
	test( "tt8 == tt7?", true,  tt8 == tt7 );
	test( "tt9 == tt7?", false, tt9 == tt7 );
    }


    public static void testTokenTupleSet() {


    }




    public static void garbage() {
	/*


	
	
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


	ChangeTupleSet cts0 = rs0.unionUpArityToChangeSet( rs1 );
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


	
	ReachabilitySet rs2 = new ReachabilitySet( tts00 );
	ReachabilitySet rs3 = new ReachabilitySet( tts01 ).union( rs2 );

	System.out.println( "rs3 is "+rs3 );

	rs3 = rs3.increaseArity( new Integer( 11 ) );
	System.out.println( "rs3 is "+rs3 );
	*/

	/*
	TokenTuple tt11 = new TokenTuple( new Integer( 1 ),
					 false,
					 TokenTuple.ARITY_ONE );

	TokenTuple tt12 = new TokenTuple( new Integer( 2 ),
					 true,
					 TokenTuple.ARITY_ONE );

	TokenTuple tt13 = new TokenTuple( new Integer( 3 ),
					 true,
					 TokenTuple.ARITY_MANY );

	TokenTuple tt14 = new TokenTuple( new Integer( 4 ),
					 true,
					 TokenTuple.ARITY_ONE );

	TokenTuple tt15 = new TokenTuple( new Integer( 5 ),
					 true,
					 TokenTuple.ARITY_ONE );

	TokenTuple tt16 = new TokenTuple( new Integer( 6 ),
					 true,
					 TokenTuple.ARITY_MANY );
	*/
	/*
	TokenTupleSet tts10 = new TokenTupleSet();
	tts10 = tts10.add( tt11 );
	tts10 = tts10.add( tt12 );
	tts10 = tts10.add( tt13 );
	tts10 = tts10.add( tt14 );
	tts10 = tts10.add( tt15 );
	tts10 = tts10.add( tt16 );
	*/

	/*
	TokenTuple tt21 = new TokenTuple( new Integer( 1 ),
					 false,
					 TokenTuple.ARITY_ONE );

	TokenTuple tt22 = new TokenTuple( new Integer( 5 ),
					 true,
					 TokenTuple.ARITY_ONE );

	TokenTuple tt23 = new TokenTuple( new Integer( 3 ),
					 true,
					 TokenTuple.ARITY_ONE );

	TokenTuple tt24 = new TokenTuple( new Integer( 6 ),
					 true,
					 TokenTuple.ARITY_MANY );

	TokenTuple tt25 = new TokenTuple( new Integer( 7 ),
					 true,
					 TokenTuple.ARITY_ONE );

	TokenTuple tt26 = new TokenTuple( new Integer( 8 ),
					 true,
					 TokenTuple.ARITY_MANY );
	*/
	/*
	TokenTupleSet tts20 = new TokenTupleSet();
	tts20 = tts20.add( tt21 );
	tts20 = tts20.add( tt22 );
	tts20 = tts20.add( tt23 );
	tts20 = tts20.add( tt24 );
	tts20 = tts20.add( tt25 );
	tts20 = tts20.add( tt26 );		

	TokenTupleSet tts30 = tts10.unionUpArity( tts20 );

	System.out.println( "tts10 is "+tts10 );
	System.out.println( "tts20 is "+tts20 );
	System.out.println( "" );
	System.out.println( "tts30 is "+tts30 );
	*/
	/*
	TokenTupleSet tts40 = new TokenTupleSet();
	tts40 = tts40.add( tt21 );
	tts40 = tts40.add( tt23 );

	TokenTupleSet tts50 = new TokenTupleSet();
	tts50 = tts50.add( tt21 );
	tts50 = tts50.add( tt23 );
	tts50 = tts50.add( tt22 );

	TokenTupleSet tts60 = new TokenTupleSet();
	tts60 = tts60.add( tt21 );
	tts60 = tts60.add( tt24 );

	TokenTupleSet tts70 = new TokenTupleSet();
	tts70 = tts70.add( tt11 );
	tts70 = tts70.add( tt13 );
	tts70 = tts70.add( tt12 );

	TokenTupleSet tts71 = new TokenTupleSet();
	tts71 = tts71.add( tt13 );
	tts71 = tts71.add( tt11 );
	tts71 = tts71.add( tt15 );

	TokenTupleSet tts72 = new TokenTupleSet();
	tts72 = tts72.add( tt11 );
	tts72 = tts72.add( tt16 );

	TokenTupleSet tts73 = new TokenTupleSet();
	tts73 = tts73.add( tt12 );

	ReachabilitySet rs40 = new ReachabilitySet();
	rs40 = rs40.add( tts40 );
	rs40 = rs40.add( tts50 );
	rs40 = rs40.add( tts60 );

	ReachabilitySet rs50 = new ReachabilitySet();
	rs50 = rs50.add( tts70 );
	rs50 = rs50.add( tts71 );
	rs50 = rs50.add( tts72 );
	rs50 = rs50.add( tts73 );

	ReachabilitySet rs60 = rs50.unionUpArity( rs40 );

	System.out.println( "rs40 is "+rs40 );
	System.out.println( "rs50 is "+rs50 );
	System.out.println( "" );
	System.out.println( "rs60 is "+rs60 );
	*/
    }
}
