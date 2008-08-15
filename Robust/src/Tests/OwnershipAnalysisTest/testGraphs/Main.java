import IR.*;
import IR.Flat.*;
import Analysis.OwnershipAnalysis.*;
import java.util.*;
import java.io.*;


///////////////////////////////////////////
//
//  The testing of the ownership graph
//  components relies on the testTokens
//  test in another directory.  Those classes
//  are assumed to be fully tested here.
//
///////////////////////////////////////////


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
	testNodesAndEdges();
	System.out.println( "---------------------------------------" );
	testGraphs();
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
    

    public static void testNodesAndEdges() {
	TempDescriptor lnTestA = new TempDescriptor( "lnTestA" );
	TempDescriptor lnTestB = new TempDescriptor( "lnTestB" );

	LabelNode ln0 = new LabelNode( lnTestA );
	LabelNode ln1 = new LabelNode( lnTestA );
	LabelNode ln2 = new LabelNode( lnTestB );

	test( "ln0 equals ln1?", true,  ln0.equals( ln1 ) );
	test( "ln1 equals ln0?", true,  ln1.equals( ln0 ) );
	test( "ln0 equals ln2?", false, ln0.equals( ln2 ) );
	test( "ln2 equals ln1?", false, ln2.equals( ln1 ) );

	/*
	public HeapRegionNode( Integer         id,
			       boolean         isSingleObject,
			       boolean         isFlagged,
			       boolean         isNewSummary,
			       AllocationSite  allocSite,
			       ReachabilitySet alpha,
			       String          description );
	*/

	TokenTuple tt00 = new TokenTuple( new Integer( 00 ), false, TokenTuple.ARITY_ONE );
	TokenTuple tt01 = new TokenTuple( new Integer( 01 ), false, TokenTuple.ARITY_ONE );
	TokenTupleSet tts0 = new TokenTupleSet( tt00 ).add( tt01 );
	ReachabilitySet a0 = new ReachabilitySet( tts0 );

	HeapRegionNode hrn00 = new HeapRegionNode( new Integer( 00 ),
						   true,
						   false,
						   false,
						   null,
						   a0,
						   "hrn00" );

	HeapRegionNode hrn01 = new HeapRegionNode( new Integer( 01 ),
						   true,
						   false,
						   false,
						   null,
						   a0,
						   "hrn01" );

	HeapRegionNode hrn02 = new HeapRegionNode( new Integer( 01 ),
						   true,
						   false,
						   false,
						   null,
						   a0,
						   "hrn01" );

	test( "hrn01.equals( hrn00 )?", false, hrn01.equals( hrn00 ) );
	test( "hrn01.equals( hrn02 )?", true,  hrn01.equals( hrn02 ) );

	test( "hrn01.hashCode() == hrn00.hashCode()?", false, hrn01.hashCode() == hrn00.hashCode() );
	test( "hrn01.hashCode() == hrn02.hashCode()?", true,  hrn01.hashCode() == hrn02.hashCode() );

	HeapRegionNode hrn03 = new HeapRegionNode( new Integer( 00 ),
						   true,
						   false,
						   false,
						   null,
						   a0,
						   "hrn00" );


	/*
	public ReferenceEdge( OwnershipNode   src,
			      HeapRegionNode  dst,			  
			      FieldDescriptor fieldDesc, 
			      boolean         isInitialParamReflexive,
			      ReachabilitySet beta ) {
	*/

	ReferenceEdge edge0 = new ReferenceEdge(  ln0,  hrn00, null, false, a0 );
	ReferenceEdge edge1 = new ReferenceEdge(  ln0,  hrn00, null, false, a0 );
	ReferenceEdge edge2 = new ReferenceEdge( hrn01, hrn00, null, false, a0 );
	ReferenceEdge edge3 = new ReferenceEdge( hrn02, hrn03, null, false, a0 );
	ReferenceEdge edge4 = new ReferenceEdge( hrn01, hrn00, null, false, a0 );

	test( "edge0.equals(       edge1 )?",          true,  edge0.equals(       edge1 ) );
	test( "edge0.hashCode() == edge1.hashCode()?", true,  edge0.hashCode() == edge1.hashCode() );

	test( "edge2.equals(       edge1 )?",          false, edge2.equals(       edge1 ) );
	test( "edge2.hashCode() == edge1.hashCode()?", false, edge2.hashCode() == edge1.hashCode() );

	test( "edge2.equals(       edge3 )?",          false, edge2.equals(       edge3 ) );
	// a collision, because even though src and dst are not same objects, they are equivalent and
	// produce the same hashcode
	//test( "edge2.hashCode() == edge3.hashCode()?", false, edge2.hashCode() == edge3.hashCode() );

	test( "edge2.equals(       edge4 )?",          true,  edge2.equals(       edge4 ) );
	test( "edge2.hashCode() == edge4.hashCode()?", true,  edge2.hashCode() == edge4.hashCode() );

    }


    public static void testGraphs() {
	// test equality of label objects that are logically
	// same/different but all separate objects
	// these tests show how a label node object or other
	// ownership graph element can be equivalent logically
	// to a different object in another graph
	TempDescriptor lnTestA = new TempDescriptor( "lnTestA" );
	TempDescriptor lnTestB = new TempDescriptor( "lnTestB" );

	LabelNode ln0 = new LabelNode( lnTestA );
	LabelNode ln1 = new LabelNode( lnTestA );
	LabelNode ln2 = new LabelNode( lnTestB );

	test( "ln0 equals ln1?", true,  ln0.equals( ln1 ) );
	test( "ln1 equals ln0?", true,  ln1.equals( ln0 ) );
	test( "ln0 equals ln2?", false, ln0.equals( ln2 ) );
	test( "ln2 equals ln1?", false, ln2.equals( ln1 ) );


	// ownership graphs g3 and g4 are equivalent
	// graphs as specified in OwnershipGraph.java, 
	// just built from different objects that contain the
	// equivalent values and merged in a different
	// order
	int allocationDepth = 3;

	OwnershipGraph g0     = new OwnershipGraph( allocationDepth );
	TempDescriptor g0tdp1 = new TempDescriptor( "p1" );
	TempDescriptor g0tdx  = new TempDescriptor( "x" );
	g0.assignTempToParameterAllocation( true, g0tdp1, new Integer( 0 ) );
	g0.assignTempXToTempY             ( g0tdx, g0tdp1 );

	OwnershipGraph g1     = new OwnershipGraph( allocationDepth );
	TempDescriptor g1tdp2 = new TempDescriptor( "p2" );
	TempDescriptor g1tdy  = new TempDescriptor( "y" );
	TempDescriptor g1tdz  = new TempDescriptor( "z" );
	g1.assignTempToParameterAllocation( true, g1tdp2, new Integer( 0 ) );
	g1.assignTempXToTempY             ( g1tdy, g1tdp2 );
	g1.assignTempXToTempYFieldF       ( g1tdz, g1tdp2, null );

	OwnershipGraph g2     = new OwnershipGraph( allocationDepth );
	TempDescriptor g2tdp3 = new TempDescriptor( "p3" );
	TempDescriptor g2tdp4 = new TempDescriptor( "p4" );
	TempDescriptor g2tdw  = new TempDescriptor( "w" );
	g2.assignTempToParameterAllocation( true, g2tdp3, new Integer( 0 ) );
	g2.assignTempToParameterAllocation( true, g2tdp4, new Integer( 1 ) );
	g2.assignTempXToTempY             ( g2tdw,  g2tdp4 );
	g2.assignTempXFieldFToTempY       ( g2tdp3, null, g2tdw );

	OwnershipGraph g3 = new OwnershipGraph( allocationDepth );
	g3.merge( g0 );
	g3.merge( g1 );
	g3.merge( g2 );

	OwnershipGraph g4 = new OwnershipGraph( allocationDepth );
	g4.merge( g1 );
	g4.merge( g2 );
	g4.merge( g0 );

	OwnershipGraph g5     = new OwnershipGraph( allocationDepth );
	TempDescriptor g5tdp1 = new TempDescriptor( "p1" );
	TempDescriptor g5tdy  = new TempDescriptor( "y" );
	g5.assignTempToParameterAllocation( true, g5tdp1, new Integer( 0 ) );
	g5.assignTempXToTempY             ( g5tdy, g5tdp1 );

	try {
	    g3.writeGraph( "g3", true, false, false, false );
	    g4.writeGraph( "g4", true, false, false, false );
	} catch( IOException e ) {}

	test( "g0 equals to g1?", false, g0.equals( g1 ) );
	test( "g1 equals to g0?", false, g1.equals( g0 ) );

	test( "g0 equals to g2?", false, g0.equals( g2 ) );
	test( "g2 equals to g0?", false, g2.equals( g0 ) );

	test( "g1 equals to g2?", false, g1.equals( g2 ) );
	test( "g2 equals to g1?", false, g2.equals( g1 ) );

	test( "g3 equals to g0?", false, g3.equals( g0 ) );
	test( "g3 equals to g1?", false, g3.equals( g1 ) );
	test( "g3 equals to g2?", false, g3.equals( g2 ) );

	test( "g4 equals to g0?", false, g4.equals( g0 ) );
	test( "g4 equals to g1?", false, g4.equals( g1 ) );
	test( "g4 equals to g2?", false, g4.equals( g2 ) );
	
	test( "g3 equals to g4?", true,  g3.equals( g4 ) );
	test( "g4 equals to g3?", true,  g4.equals( g3 ) );

	test( "g0 equals to g5?", false, g0.equals( g5 ) );
	test( "g5 equals to g0?", false, g5.equals( g0 ) );
    }
}