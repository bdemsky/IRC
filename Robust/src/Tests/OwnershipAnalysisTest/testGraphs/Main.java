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

	/*
	OwnershipNode n0 = new OwnershipNode( new Integer( 1 ) );
	OwnershipNode n1 = new OwnershipNode( new Integer( 1 ) );
	OwnershipNode n2 = new OwnershipNode( new Integer( 2 ) );
	
	test( "n0 equals n1?", true,  n0.equals( n1 ) );
	test( "n0 equals n2?", false, n0.equals( n2 ) );
	test( "n1 equals n2?", false, n1.equals( n2 ) );
	*/



	// ownership graphs g3 and g4 are equivalent
	// graphs as specified in OwnershipGraph.java, 
	// just built different objects that contain the
	// equivalent values and merged in a different
	// order
	int newDepthK = 3;

	OwnershipGraph g0     = new OwnershipGraph( newDepthK );
	TempDescriptor g0tdp1 = new TempDescriptor( "p1" );
	TempDescriptor g0tdx  = new TempDescriptor( "x" );
	g0.parameterAllocation( g0tdp1 );
	g0.assignTempToTemp   ( g0tdx, g0tdp1 );

	OwnershipGraph g1     = new OwnershipGraph( newDepthK );
	TempDescriptor g1tdp2 = new TempDescriptor( "p2" );
	TempDescriptor g1tdy  = new TempDescriptor( "y" );
	TempDescriptor g1tdz  = new TempDescriptor( "z" );
	g1.parameterAllocation( g1tdp2 );
	g1.assignTempToTemp   ( g1tdy, g1tdp2 );
	g1.assignTempToField  ( g1tdz, g1tdp2, null );

	OwnershipGraph g2     = new OwnershipGraph( newDepthK );
	TempDescriptor g2tdp3 = new TempDescriptor( "p3" );
	TempDescriptor g2tdp4 = new TempDescriptor( "p4" );
	TempDescriptor g2tdw  = new TempDescriptor( "w" );
	g2.parameterAllocation( g2tdp3 );
	g2.parameterAllocation( g2tdp4 );
	g2.assignTempToTemp   ( g2tdw,  g2tdp4 );
	g2.assignFieldToTemp  ( g2tdp3, g2tdw, null );

	OwnershipGraph g3 = new OwnershipGraph( newDepthK );
	g3.merge( g0 );
	g3.merge( g1 );
	g3.merge( g2 );

	OwnershipGraph g4 = new OwnershipGraph( newDepthK );
	g4.merge( g1 );
	g4.merge( g2 );
	g4.merge( g0 );

	test( "4 == 5?", false, 4 == 5 );
	test( "3 == 3?", true,  3 == 3 );

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
    }
}