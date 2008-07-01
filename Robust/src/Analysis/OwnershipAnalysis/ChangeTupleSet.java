package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class ChangeTupleSet {

    private HashSet<ChangeTuple> changeTuples;

    public ChangeTupleSet() {
	changeTuples = new HashSet<ChangeTuple>();
    }

    public ChangeTupleSet( ChangeTuple ct ) {
	this();
	changeTuples.add( ct );
    }

    public ChangeTupleSet( ChangeTupleSet cts ) {
	changeTuples = (HashSet<ChangeTuple>) cts.changeTuples.clone(); //COPY?!
    }

    public Iterator iterator() {
	return changeTuples.iterator();
    }

    public ChangeTupleSet union( ChangeTupleSet ctsIn ) {
	ChangeTupleSet ctsOut = new ChangeTupleSet( this );
	ctsOut.changeTuples.addAll( ctsIn.changeTuples );
	return ctsOut;
    }

    public boolean isSubset( ChangeTupleSet ctsIn ) {
	return ctsIn.changeTuples.containsAll( this.changeTuples );
    }

    public String toString() {
	String s = "[";

	Iterator i = this.iterator();
	while( i.hasNext() ) {
	    s += "\n  "+i.next();
	}

	s += "\n]";

	return s;
    }
}
