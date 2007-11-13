package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class TestOwnership {
    
    public static void main( String args[] ) throws java.io.IOException {
	System.out.println( "Testing ownership analysis components." );

	/*
	TempDescriptor ta = new TempDescriptor( "ta" );
	TempDescriptor tb = new TempDescriptor( "tb" );
	TempDescriptor tc = new TempDescriptor( "tc" );
	TempDescriptor td = new TempDescriptor( "td" );

	OwnershipGraph og1 = new OwnershipGraph();

	og1.assignTempToTemp( ta, tb );
	og1.assignTempToTemp( tb, tc );
	og1.assignTempToTemp( td, tc );
	og1.newHeapRegion( tc );

	og1.writeGraph( "testGraph1" );
	*/

	/*
	og1.addEdge( ta, tb );
	og1.addEdge( tb, tc );
	og1.addEdge( tc, td );
	og1.addEdge( td, ta );
	og1.addEdge( tb, td );
	


	OwnershipGraph og2 = og1.copy();
	og2.writeGraph( "testGraph2" );
	*/
    }
}
