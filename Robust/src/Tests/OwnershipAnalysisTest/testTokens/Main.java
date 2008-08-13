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
	testChangeTupleAndChangeTupleSet();
	System.out.println( "---------------------------------------" );
	testReachabilitySet();
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

	// they should be canonical
	test( "tts4567a.equals( tts4567d )?", true, tts4567a.equals( tts4567d ) );
	test( "tts4567a == tts4567d?",        true, tts4567a == tts4567d );
	


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

	System.out.println( ttsAgeTest0a );	
	test( "ttsAgeTest0a.equals( ttsAgeTest0b )?", false, ttsAgeTest0a.equals( ttsAgeTest0b ) );
	ttsAgeTest0a = ttsAgeTest0a.ageTokens( as );
	test( "ttsAgeTest0a.equals( ttsAgeTest0b )?", true,  ttsAgeTest0a.equals( ttsAgeTest0b ) );
	System.out.println( ttsAgeTest0a );	


	TokenTupleSet ttsAgeTest1a = new TokenTupleSet();
	ttsAgeTest1a = ttsAgeTest1a.add( tt10 ).add( tt52 ).add( tt42 ).add( tt62star ).add( tt13 );

	TokenTupleSet ttsAgeTest1b = new TokenTupleSet();
	ttsAgeTest1b = ttsAgeTest1b.add( tt11 ).add( tt52 ).add( tt42 ).add( tt62star ).add( tt13 );

	System.out.println( ttsAgeTest1a );	
	test( "ttsAgeTest1a.equals( ttsAgeTest1b )?", false, ttsAgeTest1a.equals( ttsAgeTest1b ) );
	ttsAgeTest1a = ttsAgeTest1a.ageTokens( as );
	test( "ttsAgeTest1a.equals( ttsAgeTest1b )?", true,  ttsAgeTest1a.equals( ttsAgeTest1b ) );
	System.out.println( ttsAgeTest1a );	


	TokenTupleSet ttsAgeTest2a = new TokenTupleSet();
	ttsAgeTest2a = ttsAgeTest2a.add( tt10 ).add( tt52 ).add( tt42 ).add( tt62star ).add( tt12 ).add( tt11 );

	TokenTupleSet ttsAgeTest2b = new TokenTupleSet();
	ttsAgeTest2b = ttsAgeTest2b.add( tt11 ).add( tt52 ).add( tt42 ).add( tt62star ).add( tt13 ).add( tt12 );

	System.out.println( ttsAgeTest2a );	
	test( "ttsAgeTest2a.equals( ttsAgeTest2b )?", false, ttsAgeTest2a.equals( ttsAgeTest2b ) );
	ttsAgeTest2a = ttsAgeTest2a.ageTokens( as );
	test( "ttsAgeTest2a.equals( ttsAgeTest2b )?", true,  ttsAgeTest2a.equals( ttsAgeTest2b ) );
	System.out.println( ttsAgeTest2a );	


	TokenTupleSet ttsAgeTest3a = new TokenTupleSet();
	ttsAgeTest3a = ttsAgeTest3a.add( tt13 ).add( tt52 ).add( tt42 ).add( tt62star ).add( tt12 ).add( tt10 );

	TokenTupleSet ttsAgeTest3b = new TokenTupleSet();
	ttsAgeTest3b = ttsAgeTest3b.add( tt11 ).add( tt52 ).add( tt42 ).add( tt62star ).add( tt13star );

	System.out.println( ttsAgeTest3a );	
	test( "ttsAgeTest3a.equals( ttsAgeTest3b )?", false, ttsAgeTest3a.equals( ttsAgeTest3b ) );
	ttsAgeTest3a = ttsAgeTest3a.ageTokens( as );
	test( "ttsAgeTest3a.equals( ttsAgeTest3b )?", true,  ttsAgeTest3a.equals( ttsAgeTest3b ) );
	System.out.println( ttsAgeTest3a );	


	// they should be canonical
	test( "ttsAgeTest3a.equals( ttsAgeTest3b )?", true, ttsAgeTest3a.equals( ttsAgeTest3b ) );
	test( "ttsAgeTest3a == ttsAgeTest3b?",        true, ttsAgeTest3a == ttsAgeTest3b );	
    }


    public static void testChangeTupleAndChangeTupleSet() {
	TokenTuple tt0 = new TokenTuple( new Integer( 0 ), true,  TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt1 = new TokenTuple( new Integer( 1 ), true,  TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt2 = new TokenTuple( new Integer( 2 ), true,  TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt8 = new TokenTuple( new Integer( 8 ), true,  TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt8b = new TokenTuple( new Integer( 8 ), true, TokenTuple.ARITY_MANY ).makeCanonical();

	TokenTupleSet tts01   = new TokenTupleSet().add( tt0 ).add( tt1 );
	TokenTupleSet tts12   = new TokenTupleSet().add( tt1 ).add( tt2 );
	TokenTupleSet tts128  = new TokenTupleSet().add( tt1 ).add( tt2 ).add( tt8 );
	TokenTupleSet tts128b = new TokenTupleSet().add( tt1 ).add( tt2 ).add( tt8b );

	ChangeTuple ct0 = new ChangeTuple( tts01, tts12 );
	ChangeTuple ct1 = new ChangeTuple( tts12, tts01 );
	ChangeTuple ct2 = new ChangeTuple( tts01, tts128 );
	ChangeTuple ct3 = new ChangeTuple( tts01, tts128b );
	ChangeTuple ct4 = new ChangeTuple( tts01, tts128 );

	test( "ct0.equals(       ct1 )?",          false, ct0.equals(       ct1 ) );
	test( "ct0            == ct1?",            false, ct0 ==            ct1 );
	test( "ct0.hashCode() == ct1.hashCode()?", false, ct0.hashCode() == ct1.hashCode() );

	test( "ct0.equals(       ct2 )?",          false, ct0.equals(       ct2 ) );
	test( "ct0            == ct2?",            false, ct0 ==            ct2 );
	test( "ct0.hashCode() == ct2.hashCode()?", false, ct0.hashCode() == ct2.hashCode() );

	test( "ct3.equals(       ct2 )?",          false, ct3.equals(       ct2 ) );
	test( "ct3            == ct2?",            false, ct3 ==            ct2 );
	test( "ct3.hashCode() == ct2.hashCode()?", false, ct3.hashCode() == ct2.hashCode() );

	test( "ct4.equals(       ct2 )?",          true,  ct4.equals(       ct2 ) );
	test( "ct4            == ct2?",            false, ct4 ==            ct2 );
	test( "ct4.hashCode() == ct2.hashCode()?", true,  ct4.hashCode() == ct2.hashCode() );

	ct2 = ct2.makeCanonical();
	ct4 = ct4.makeCanonical();

	test( "ct4.equals(       ct2 )?",          true,  ct4.equals(       ct2 ) );
	test( "ct4            == ct2?",            true,  ct4 ==            ct2 );
	test( "ct4.hashCode() == ct2.hashCode()?", true,  ct4.hashCode() == ct2.hashCode() );

	
	ChangeTupleSet cts0 = new ChangeTupleSet();
	ChangeTupleSet cts1 = new ChangeTupleSet( ct0 );
	ChangeTupleSet cts2 = new ChangeTupleSet( cts1 );

	test( "cts1.equals(       cts0 )?",          false, cts1.equals(       cts0 ) );
	test( "cts1            == cts0?",            false, cts1 ==            cts0 );
	test( "cts1.hashCode() == cts0.hashCode()?", false, cts1.hashCode() == cts0.hashCode() );

	test( "cts1.equals(       cts2 )?",          true,  cts1.equals(       cts2 ) );
	test( "cts1            == cts2?",            false, cts1 ==            cts2 );
	test( "cts1.hashCode() == cts2.hashCode()?", true,  cts1.hashCode() == cts2.hashCode() );

	cts1 = cts1.makeCanonical();
	cts2 = cts2.makeCanonical();

	test( "cts1.equals(       cts2 )?",          true,  cts1.equals(       cts2 ) );
	test( "cts1            == cts2?",            true,  cts1 ==            cts2 );
	test( "cts1.hashCode() == cts2.hashCode()?", true,  cts1.hashCode() == cts2.hashCode() );

	ChangeTupleSet cts3 = new ChangeTupleSet( ct1 ).union( ct0 );

	test( "cts0.isEmpty()?", true,  cts0.isEmpty() );
	test( "cts1.isEmpty()?", false, cts1.isEmpty() );

	test( "cts0.isSubset( cts1 )?", true,  cts0.isSubset( cts1 ) );
	test( "cts1.isSubset( cts0 )?", false, cts1.isSubset( cts0 ) );

	test( "cts1.isSubset( cts2 )?", true,  cts1.isSubset( cts2 ) );
	test( "cts2.isSubset( cts1 )?", true,  cts2.isSubset( cts1 ) );

	test( "cts1.isSubset( cts3 )?", true,  cts1.isSubset( cts3 ) );
	test( "cts3.isSubset( cts1 )?", false, cts3.isSubset( cts1 ) );
    }


    public static void testReachabilitySet() {
	TokenTuple tt0  = new TokenTuple( new Integer( 100 ), true,  TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt1  = new TokenTuple( new Integer( 101 ), true,  TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt2  = new TokenTuple( new Integer( 102 ), true,  TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt3  = new TokenTuple( new Integer( 103 ), true,  TokenTuple.ARITY_MANY ).makeCanonical();
	TokenTuple tt4  = new TokenTuple( new Integer( 104 ), false, TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt5  = new TokenTuple( new Integer( 105 ), false, TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt6  = new TokenTuple( new Integer( 106 ), false, TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt7  = new TokenTuple( new Integer( 107 ), true,  TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt8  = new TokenTuple( new Integer( 108 ), true,  TokenTuple.ARITY_ONE  ).makeCanonical();
	TokenTuple tt9  = new TokenTuple( new Integer( 109 ), true,  TokenTuple.ARITY_MANY ).makeCanonical();
	TokenTuple tt8b = new TokenTuple( new Integer( 108 ), true,  TokenTuple.ARITY_MANY ).makeCanonical();


	AllocationSite as = new AllocationSite( 3, null );
	as.setIthOldest( 0, new Integer( 104 ) );
	as.setIthOldest( 1, new Integer( 105 ) );
	as.setIthOldest( 2, new Integer( 106 ) );
	as.setSummary  (    new Integer( 108 ) );


	TokenTupleSet tts01   = new TokenTupleSet().add( tt0 ).add( tt1 );
	TokenTupleSet tts12   = new TokenTupleSet().add( tt1 ).add( tt2 );
	TokenTupleSet tts128  = new TokenTupleSet().add( tt1 ).add( tt2 ).add( tt8 );
	TokenTupleSet tts128b = new TokenTupleSet().add( tt1 ).add( tt2 ).add( tt8b );

	ReachabilitySet rs0 = new ReachabilitySet( tts128 );

	test( "rs0.contains( tts01  )?", false, rs0.contains( tts01  ) );
	test( "rs0.contains( tts128 )?", true,  rs0.contains( tts128 ) );

	test( "rs0.containsTuple( tt8b )?", false, rs0.containsTuple( tt8b ) );
	test( "rs0.containsTuple( tt8  )?", true,  rs0.containsTuple( tt8  ) );


	TokenTupleSet tts048  = new TokenTupleSet().add( tt0 ).add( tt4 ).add( tt8 );
	TokenTupleSet tts048b = new TokenTupleSet().add( tt0 ).add( tt4 ).add( tt8b );

	ReachabilitySet rs1 = new ReachabilitySet( tts128 ).add( tts048 ).add( tts01 );
	ReachabilitySet rs2 = rs1.increaseArity( new Integer( 108 ) );

	test( "rs1.equals( rs2 )?",                false, rs1.equals( rs2 ) );
	test( "rs1 == rs2?",                       false, rs1 == rs2 );
	test( "rs1.hashCode() == rs2.hashCode()?", false, rs1.hashCode() == rs2.hashCode() );

	ReachabilitySet rs3 = new ReachabilitySet( tts128b ).add( tts048b ).add( tts01 );

	test( "rs2.equals( rs3 )?",                true, rs2.equals( rs3 ) );
	test( "rs2 == rs3?",                       true, rs2 == rs3 );
	test( "rs2.hashCode() == rs3.hashCode()?", true, rs2.hashCode() == rs3.hashCode() );


	ReachabilitySet rs4 = rs0.union( rs3 );
	ReachabilitySet rs5 = new ReachabilitySet().union( tts128 ).union( tts128b ).union( tts048b ).union( tts01 );

	test( "rs4.equals( rs5 )?",                true, rs4.equals( rs5 ) );
	test( "rs4 == rs5?",                       true, rs4 == rs5 );
	test( "rs4.hashCode() == rs5.hashCode()?", true, rs4.hashCode() == rs5.hashCode() );

	
	ReachabilitySet rs6 = new ReachabilitySet().add( tts128b ).add( tts048b ).add( tts01 ).add( tts12 );
	ReachabilitySet rs7 = rs6.intersection( rs5 );

	test( "rs6.equals(       rs7 )?",          false, rs6.equals(       rs7 ) );
	test( "rs6 ==            rs7?",            false, rs6 ==            rs7 );
	test( "rs6.hashCode() == rs7.hashCode()?", false, rs6.hashCode() == rs7.hashCode() );

	test( "rs3.equals(       rs7 )?",          true, rs3.equals(       rs7 ) );
	test( "rs3 ==            rs7?",            true, rs3 ==            rs7 );
	test( "rs3.hashCode() == rs7.hashCode()?", true, rs3.hashCode() == rs7.hashCode() );

      
	TokenTupleSet tts67  = new TokenTupleSet().add( tt6 ).add( tt7 );
	TokenTupleSet tts806 = new TokenTupleSet().add( tt8 ).add( tt0 ).add( tt6 );

	TokenTupleSet tts058b = new TokenTupleSet().add( tt8b ).add( tt0 ).add( tt5 );
	TokenTupleSet tts87   = new TokenTupleSet().add( tt8 ).add( tt7 );
	TokenTupleSet tts08b  = new TokenTupleSet().add( tt0 ).add( tt8b );

	ReachabilitySet rs8 = new ReachabilitySet().add( tts128 ).add( tts048b ).add( tts01 ).add( tts67 ).add( tts806 );
	ReachabilitySet rs9 = new ReachabilitySet().add( tts128 ).add( tts058b ).add( tts01 ).add( tts87 ).add( tts08b );

	test( "rs8.equals(       rs9 )?",          false, rs8.equals(       rs9 ) );
	test( "rs8 ==            rs9?",            false, rs8 ==            rs9 );
	test( "rs8.hashCode() == rs9.hashCode()?", false, rs8.hashCode() == rs9.hashCode() );

	rs8 = rs8.ageTokens( as );

	test( "rs8.equals(       rs9 )?",          true, rs8.equals(       rs9 ) );
	test( "rs8 ==            rs9?",            true, rs8 ==            rs9 );
	test( "rs8.hashCode() == rs9.hashCode()?", true, rs8.hashCode() == rs9.hashCode() );
	
	ReachabilitySet rs10 = new ReachabilitySet().add( tts08b ).add( tts01 );
	ReachabilitySet rs11 = new ReachabilitySet().add( tts058b ).add( tts01 ).add( tts08b );
	ReachabilitySet rs12 = new ReachabilitySet().add( tts128 ).add( tts058b ).add( tts01 ).add( tts87 ).add( tts08b );

	test( "rs11.equals(       rs12 )?",          false, rs11.equals(       rs12 ) );
	test( "rs11 ==            rs12?",            false, rs11 ==            rs12 );
	test( "rs11.hashCode() == rs12.hashCode()?", false, rs11.hashCode() == rs12.hashCode() );

	rs12 = rs12.pruneBy( rs10 );

	test( "rs11.equals(       rs12 )?",          true, rs11.equals(       rs12 ) );
	test( "rs11 ==            rs12?",            true, rs11 ==            rs12 );
	test( "rs11.hashCode() == rs12.hashCode()?", true, rs11.hashCode() == rs12.hashCode() );


	ReachabilitySet rs13 = new ReachabilitySet( tts128 ).add( tts048 ).add( tts01 );
	ReachabilitySet rs14 = new ReachabilitySet( tts87 ).add( tts01 );

	ChangeTupleSet cts0 = rs14.unionUpArityToChangeSet( rs13 );
	System.out.println( cts0 );
	

	TokenTuple tt0b = new TokenTuple( new Integer( 100 ), true,  TokenTuple.ARITY_MANY  ).makeCanonical();
	TokenTuple tt1b = new TokenTuple( new Integer( 101 ), true,  TokenTuple.ARITY_MANY  ).makeCanonical();
	
	TokenTupleSet tts01b28 = new TokenTupleSet().add( tt1b ).add( tt0 ).add( tt2 ).add( tt8 );
	TokenTupleSet tts0b148 = new TokenTupleSet().add( tt0b ).add( tt1 ).add( tt4 ).add( tt8 );
	TokenTupleSet tts0b1b  = new TokenTupleSet().add( tt1b ).add( tt0b );
	TokenTupleSet tts1278b = new TokenTupleSet().add( tt1  ).add( tt7 ).add( tt2 ).add( tt8b );
	TokenTupleSet tts0478b = new TokenTupleSet().add( tt0  ).add( tt7 ).add( tt4 ).add( tt8b );
	TokenTupleSet tts1078  = new TokenTupleSet().add( tt1  ).add( tt7 ).add( tt0 ).add( tt8 );

	ChangeTuple ct0 = new ChangeTuple( tts01, tts01b28 ); 
	ChangeTuple ct1 = new ChangeTuple( tts01, tts0b148 );
	ChangeTuple ct2 = new ChangeTuple( tts01, tts0b1b  ); 
	ChangeTuple ct3 = new ChangeTuple( tts87, tts1278b ); 
	ChangeTuple ct4 = new ChangeTuple( tts87, tts0478b ); 
	ChangeTuple ct5 = new ChangeTuple( tts87, tts1078  ); 

	ChangeTupleSet cts1 
	  = new ChangeTupleSet( ct0 ).union( ct1 ).union( ct2 ).union( ct3 ).union( ct4 ).union( ct5 );

	test( "cts1.equals(       cts0 )?",          true,  cts1.equals(       cts0 ) );
	test( "cts1            == cts0?",            true,  cts1 ==            cts0 );
	test( "cts1.hashCode() == cts0.hashCode()?", true,  cts1.hashCode() == cts0.hashCode() );

    }
}
