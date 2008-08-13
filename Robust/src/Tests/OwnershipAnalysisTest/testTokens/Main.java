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
	
	System.out.println( test+" expect "+expected+outcome );
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
	    System.out.println( "<><> All tests passed. <><>" );
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
	TokenTuple tt0 = new TokenTuple( new Integer( 0 ), false, TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt1 = new TokenTuple( new Integer( 1 ), false, TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt2 = new TokenTuple( new Integer( 2 ), false, TokenTuple.ARITY_ONE  ).makeCanonical();

	TokenTupleSet tts0  = new TokenTupleSet( tt0 );
	TokenTupleSet tts1  = new TokenTupleSet( tt1 );
	TokenTupleSet tts2a = new TokenTupleSet( tt2 );
	TokenTupleSet tts2b = new TokenTupleSet( tt2 );

	test( "tts0.equals( null )?", false, tts0.equals( null ) );
	test( "tts0.equals( tts1 )?", false, tts0.equals( tts1 ) );
	test( "tts0.hashCode == tts1.hashCode?", false, tts0.hashCode() == tts1.hashCode() );

	test( "tts2a.equals( tts2b )?", true, tts2a.equals( tts2b ) );
	test( "tts2b.equals( tts2a )?", true, tts2b.equals( tts2a ) );
	test( "tts2a.hashCode == tts2b.hashCode?", true, tts2a.hashCode() == tts2b.hashCode() );


	TokenTupleSet tts012a = new TokenTupleSet();
	tts012a = tts012a.add( tt0 );
	tts012a = tts012a.add( tt1 );
	tts012a = tts012a.add( tt2 );

	TokenTupleSet tts012b = tts0.union( tts1.union( tts2b.union( tts2a ) ) );

	test( "tts012a.equals( tts012b )?", true, tts012a.equals( tts012b ) );
	test( "tts012a.hashCode == tts012b.hashCode?", true, tts012a.hashCode() == tts012b.hashCode() );


	TokenTupleSet ttsEmpty = new TokenTupleSet();
	
	test( "tts012a.isEmpty()?",  false, tts012a.isEmpty()  );
	test( "ttsEmpty.isEmpty()?", true,  ttsEmpty.isEmpty() );
	test( "ttsEmpty.isSubset( tts012a )?", true,  ttsEmpty.isSubset( tts012a  ) );
	test( "tts012a.isSubset( ttsEmpty )?", false, tts012a.isSubset ( ttsEmpty ) );
	test( "tts2a.isSubset( tts012a )?",    true,  tts2a.isSubset   ( tts012a  ) );
	test( "tts012a.isSubset( tts2a )?",    false, tts012a.isSubset ( tts2a    ) );
	test( "tts2a.isSubset( tts2b )?",      true,  tts2a.isSubset   ( tts2b    ) );

	
	TokenTuple tt1star = new TokenTuple( new Integer( 1 ), true,  TokenTuple.ARITY_MANY ).makeCanonical();
	TokenTuple tt3     = new TokenTuple( new Integer( 3 ), false, TokenTuple.ARITY_ONE  ).makeCanonical();

	test( "ttsEmpty.containsTuple( tt2     )?", false, ttsEmpty.containsTuple( tt2     ) );
	test( "ttsEmpty.containsTuple( tt1     )?", false, ttsEmpty.containsTuple( tt1     ) );
	test( "ttsEmpty.containsTuple( tt1star )?", false, ttsEmpty.containsTuple( tt1star ) );
	test( "ttsEmpty.containsTuple( tt3     )?", false, ttsEmpty.containsTuple( tt3     ) );

	test( "tts2a.containsTuple( tt2     )?", true,  tts2a.containsTuple( tt2     ) );
	test( "tts2a.containsTuple( tt1     )?", false, tts2a.containsTuple( tt1     ) );
	test( "tts2a.containsTuple( tt1star )?", false, tts2a.containsTuple( tt1star ) );
	test( "tts2a.containsTuple( tt3     )?", false, tts2a.containsTuple( tt3     ) );

	test( "tts012a.containsTuple( tt2     )?", true,  tts012a.containsTuple( tt2     ) );
	test( "tts012a.containsTuple( tt1     )?", true,  tts012a.containsTuple( tt1     ) );
	test( "tts012a.containsTuple( tt1star )?", false, tts012a.containsTuple( tt1star ) );
	test( "tts012a.containsTuple( tt3     )?", false, tts012a.containsTuple( tt3     ) );


	TokenTuple tt4 = new TokenTuple( new Integer( 4 ), false, TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt5 = new TokenTuple( new Integer( 5 ), true,  TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt6 = new TokenTuple( new Integer( 6 ), true,  TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt7 = new TokenTuple( new Integer( 7 ), true,  TokenTuple.ARITY_MANY ).makeCanonical();

	TokenTuple tt5star = new TokenTuple( new Integer( 5 ), true,  TokenTuple.ARITY_MANY ).makeCanonical();
	TokenTuple tt6star = new TokenTuple( new Integer( 6 ), true,  TokenTuple.ARITY_MANY ).makeCanonical();

	TokenTupleSet tts4567a = new TokenTupleSet();
	tts4567a = tts4567a.add( tt4 ).add( tt5 ).add( tt6 ).add( tt7 );

	TokenTupleSet tts4567b = new TokenTupleSet( tts4567a );

	TokenTupleSet tts4567c = new TokenTupleSet();
	tts4567c = tts4567c.add( tt4 ).add( tt5star ).add( tt6 ).add( tt7 );

	TokenTupleSet tts4567d = new TokenTupleSet();
	tts4567d = tts4567d.add( tt4 ).add( tt5star ).add( tt6star ).add( tt7 );

	test( "tts4567a.equals( tts4567b )?", true, tts4567a.equals( tts4567b ) );
	test( "tts4567a.hashCode == tts4567b.hashCode?", true, tts4567a.hashCode() == tts4567b.hashCode() );

	test( "tts4567a.equals( tts4567c )?", false, tts4567a.equals( tts4567c ) );
	test( "tts4567a.hashCode == tts4567c.hashCode?", false, tts4567a.hashCode() == tts4567c.hashCode() );

	test( "tts4567a.equals( tts4567d )?", false, tts4567a.equals( tts4567d ) );
	test( "tts4567a.hashCode == tts4567d.hashCode?", false, tts4567a.hashCode() == tts4567d.hashCode() );

	tts4567a = tts4567a.increaseArity( new Integer( 6 ) );

	test( "tts4567a.equals( tts4567b )?", false, tts4567a.equals( tts4567b ) );
	test( "tts4567a.hashCode == tts4567b.hashCode?", false, tts4567a.hashCode() == tts4567b.hashCode() );

	test( "tts4567a.equals( tts4567c )?", false, tts4567a.equals( tts4567c ) );
	// it's okay if the objects are not equal but hashcodes are, its a collision
	//test( "tts4567a.hashCode == tts4567c.hashCode?", false, tts4567a.hashCode() == tts4567c.hashCode() );

	test( "tts4567a.equals( tts4567d )?", false, tts4567a.equals( tts4567d ) );
	test( "tts4567a.hashCode == tts4567d.hashCode?", false, tts4567a.hashCode() == tts4567d.hashCode() );

	tts4567a = tts4567a.increaseArity( new Integer( 5 ) );


	test( "tts4567a.equals( tts4567b )?", false, tts4567a.equals( tts4567b ) );
	test( "tts4567a.hashCode == tts4567b.hashCode?", false, tts4567a.hashCode() == tts4567b.hashCode() );

	test( "tts4567a.equals( tts4567c )?", false, tts4567a.equals( tts4567c ) );
	test( "tts4567a.hashCode == tts4567c.hashCode?", false, tts4567a.hashCode() == tts4567c.hashCode() );

	test( "tts4567a.equals( tts4567d )?", true, tts4567a.equals( tts4567d ) );
	test( "tts4567a.hashCode == tts4567d.hashCode?", true, tts4567a.hashCode() == tts4567d.hashCode() );

	
	test( "tts4567a.containsToken( new Integer( 1 ) )?", false, tts4567a.containsToken( new Integer( 1 ) ) );
	test( "tts4567a.containsToken( new Integer( 4 ) )?", true,  tts4567a.containsToken( new Integer( 4 ) ) );
	test( "tts4567a.containsToken( new Integer( 5 ) )?", true,  tts4567a.containsToken( new Integer( 5 ) ) );
	test( "tts4567a.containsToken( new Integer( 7 ) )?", true,  tts4567a.containsToken( new Integer( 7 ) ) );
	test( "tts4567a.containsToken( new Integer( 8 ) )?", false, tts4567a.containsToken( new Integer( 8 ) ) );


	TokenTuple tt10     = new TokenTuple( new Integer( 10 ), false, TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt11     = new TokenTuple( new Integer( 11 ), false, TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt12     = new TokenTuple( new Integer( 12 ), false, TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt13     = new TokenTuple( new Integer( 13 ), true,  TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt13star = new TokenTuple( new Integer( 13 ), true,  TokenTuple.ARITY_MANY ).makeCanonical();
	TokenTuple tt42     = new TokenTuple( new Integer( 42 ), false, TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt52     = new TokenTuple( new Integer( 52 ), true,  TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt62star = new TokenTuple( new Integer( 62 ), true,  TokenTuple.ARITY_MANY ).makeCanonical();

	AllocationSite as = new AllocationSite( 3, null );
	as.setIthOldest( 0, new Integer( 10 ) );
	as.setIthOldest( 1, new Integer( 11 ) );
	as.setIthOldest( 2, new Integer( 12 ) );
	as.setSummary  (    new Integer( 13 ) );


	TokenTupleSet ttsAgeTest0a = new TokenTupleSet();
	ttsAgeTest0a = ttsAgeTest0a.add( tt11 ).add( tt52 ).add( tt42 ).add( tt62star );

	TokenTupleSet ttsAgeTest0b = new TokenTupleSet();
	ttsAgeTest0b = ttsAgeTest0b.add( tt12 ).add( tt52 ).add( tt42 ).add( tt62star );

	test( "ttsAgeTest0a.equals( ttsAgeTest0b )?", false, ttsAgeTest0a.equals( ttsAgeTest0b ) );
	ttsAgeTest0a = ttsAgeTest0a.ageTokens( as );
	test( "ttsAgeTest0a.equals( ttsAgeTest0b )?", true,  ttsAgeTest0a.equals( ttsAgeTest0b ) );


	TokenTupleSet ttsAgeTest1a = new TokenTupleSet();
	ttsAgeTest1a = ttsAgeTest1a.add( tt10 ).add( tt52 ).add( tt42 ).add( tt62star ).add( tt13 );

	TokenTupleSet ttsAgeTest1b = new TokenTupleSet();
	ttsAgeTest1b = ttsAgeTest1b.add( tt11 ).add( tt52 ).add( tt42 ).add( tt62star ).add( tt13 );

	test( "ttsAgeTest1a.equals( ttsAgeTest1b )?", false, ttsAgeTest1a.equals( ttsAgeTest1b ) );
	ttsAgeTest1a = ttsAgeTest1a.ageTokens( as );
	test( "ttsAgeTest1a.equals( ttsAgeTest1b )?", true,  ttsAgeTest1a.equals( ttsAgeTest1b ) );


	TokenTupleSet ttsAgeTest2a = new TokenTupleSet();
	ttsAgeTest2a = ttsAgeTest2a.add( tt10 ).add( tt52 ).add( tt42 ).add( tt62star ).add( tt12 ).add( tt11 );

	TokenTupleSet ttsAgeTest2b = new TokenTupleSet();
	ttsAgeTest2b = ttsAgeTest2b.add( tt11 ).add( tt52 ).add( tt42 ).add( tt62star ).add( tt13 ).add( tt12 );

	test( "ttsAgeTest2a.equals( ttsAgeTest2b )?", false, ttsAgeTest2a.equals( ttsAgeTest2b ) );
	ttsAgeTest2a = ttsAgeTest2a.ageTokens( as );
	test( "ttsAgeTest2a.equals( ttsAgeTest2b )?", true,  ttsAgeTest2a.equals( ttsAgeTest2b ) );


	TokenTupleSet ttsAgeTest3a = new TokenTupleSet();
	ttsAgeTest3a = ttsAgeTest3a.add( tt13 ).add( tt52 ).add( tt42 ).add( tt62star ).add( tt12 ).add( tt10 );

	TokenTupleSet ttsAgeTest3b = new TokenTupleSet();
	ttsAgeTest3b = ttsAgeTest3b.add( tt11 ).add( tt52 ).add( tt42 ).add( tt62star ).add( tt13star );

	test( "ttsAgeTest3a.equals( ttsAgeTest3b )?", false, ttsAgeTest3a.equals( ttsAgeTest3b ) );
	ttsAgeTest3a = ttsAgeTest3a.ageTokens( as );
	test( "ttsAgeTest3a.equals( ttsAgeTest3b )?", true,  ttsAgeTest3a.equals( ttsAgeTest3b ) );
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
