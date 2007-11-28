import IR.*;
import IR.Flat.*;
import Analysis.OwnershipAnalysis.*;
import java.util.*;
import java.io.*;


public class Main {

    public static void main(String args[]) throws Exception {

	// ownership graphs g3 and g4 are equivalent
	// graphs as specified in OwnershipGraph.java, 
	// just built different objects that contain the
	// equivalent values and merged in a different
	// order

	OwnershipGraph g0     = new OwnershipGraph();
	TempDescriptor g0tdp1 = new TempDescriptor( "p1" );
	TempDescriptor g0tdx  = new TempDescriptor( "x" );
	g0.newHeapRegion   ( g0tdp1 );
	g0.assignTempToTemp( g0tdx, g0tdp1 );

	OwnershipGraph g1     = new OwnershipGraph();
	TempDescriptor g1tdp2 = new TempDescriptor( "p2" );
	TempDescriptor g1tdy  = new TempDescriptor( "y" );
	TempDescriptor g1tdz  = new TempDescriptor( "z" );
	g1.newHeapRegion    ( g1tdp2 );
	g1.assignTempToTemp ( g1tdy, g1tdp2 );
	g1.assignTempToField( g1tdz, g1tdp2, null );

	OwnershipGraph g2     = new OwnershipGraph();
	TempDescriptor g2tdp3 = new TempDescriptor( "p3" );
	TempDescriptor g2tdp4 = new TempDescriptor( "p4" );
	TempDescriptor g2tdw  = new TempDescriptor( "w" );
	g2.newHeapRegion    ( g2tdp3 );
	g2.newHeapRegion    ( g2tdp4 );
	g2.assignTempToTemp ( g2tdw,  g2tdp4 );
	g2.assignFieldToTemp( g2tdp3, g2tdw, null );

	OwnershipGraph g3 = new OwnershipGraph();
	g3.merge( g0 );
	g3.merge( g1 );
	g3.merge( g2 );

	OwnershipGraph g4 = new OwnershipGraph();
	g4.merge( g1 );
	g4.merge( g2 );
	g4.merge( g0 );

	test( "4 == 5?", false, 4 == 5 );
	test( "3 == 3?", true,  3 == 3 );

	test( "g0 equivalent to g1?", false, g0.equivalent( g1 ) );
	test( "g1 equivalent to g0?", false, g1.equivalent( g0 ) );

	test( "g0 equivalent to g2?", false, g0.equivalent( g2 ) );
	test( "g2 equivalent to g0?", false, g2.equivalent( g0 ) );

	test( "g1 equivalent to g2?", false, g1.equivalent( g2 ) );
	test( "g2 equivalent to g1?", false, g2.equivalent( g1 ) );

	test( "g3 equivalent to g0?", false, g3.equivalent( g0 ) );
	test( "g3 equivalent to g1?", false, g3.equivalent( g1 ) );
	test( "g3 equivalent to g2?", false, g3.equivalent( g2 ) );

	test( "g4 equivalent to g0?", false, g4.equivalent( g0 ) );
	test( "g4 equivalent to g1?", false, g4.equivalent( g1 ) );
	test( "g4 equivalent to g2?", false, g4.equivalent( g2 ) );
	
	test( "g3 equivalent to g4?", true,  g3.equivalent( g4 ) );
	test( "g4 equivalent to g3?", true,  g4.equivalent( g3 ) );
    }

    protected static void test( String test,
				boolean expected,
				boolean result ) {

	String outcome = "... FAILED";
	if( expected == result ) {
	    outcome = "... passed";
	}
	
	System.out.println( test + 
			    " expected " + 
			    expected + 
			    outcome );
    }
}