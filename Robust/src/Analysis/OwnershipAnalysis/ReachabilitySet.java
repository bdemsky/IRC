package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class ReachabilitySet {

    public HashSet<TokenTupleSet> possibleReachabilities;

    public ReachabilitySet() {
	possibleReachabilities = new HashSet<TokenTupleSet>();
    }

    public ReachabilitySet( ReachabilitySet rs ) {
	possibleReachabilities = (HashSet<TokenTupleSet>) rs.possibleReachabilities.clone(); // again, DEEP COPY?!
    }

    public ReachabilitySet union( ReachabilitySet rsIn ) {
	ReachabilitySet rsOut = new ReachabilitySet( this );
	rsOut.possibleReachabilities.addAll( rsIn.possibleReachabilities );
	return rsOut;
    }

    public ReachabilitySet intersection( ReachabilitySet rsIn ) {
	ReachabilitySet rsOut = new ReachabilitySet();

	Iterator i = this.possibleReachabilities.iterator();
	while( i.hasNext() ) {
	    TokenTupleSet tts = (TokenTupleSet) i.next();
	    if( rsIn.possibleReachabilities.contains( tts ) ) {
		rsOut.possibleReachabilities.add( tts );
	    }
	}

	return rsOut;
    }

    /*
    public ChangeTupleSet unionUpArity( ReachabilitySet rsIn ) {
       
    }
    */
}
