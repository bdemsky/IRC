package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


public class ChangeTupleSet extends Canonical {

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

    public ChangeTupleSet makeCanonical() {
	return (ChangeTupleSet) Canonical.makeCanonical( this );
    }

    public Iterator iterator() {
	return changeTuples.iterator();
    }

    public ChangeTupleSet union( ChangeTupleSet ctsIn ) {
	assert ctsIn != null;

	ChangeTupleSet ctsOut = new ChangeTupleSet( this );
	ctsOut.changeTuples.addAll( ctsIn.changeTuples );
	return ctsOut.makeCanonical();
    }

    public ChangeTupleSet union( ChangeTuple ctIn ) {
	assert ctIn != null;

	ChangeTupleSet ctsOut = new ChangeTupleSet( this );
	ctsOut.changeTuples.add( ctIn );
	return ctsOut.makeCanonical();
    }

    public boolean isEmpty() {
	return changeTuples.isEmpty();
    }

    public boolean isSubset( ChangeTupleSet ctsIn ) {
	assert ctsIn != null;
	return ctsIn.changeTuples.containsAll( this.changeTuples );
    }

    public boolean equals( Object o ) {
	if( o == null ) {
	    return false;
	}

	if( !(o instanceof ChangeTupleSet) ) {
	    return false;
	}

	ChangeTupleSet cts = (ChangeTupleSet) o;
	return changeTuples.equals( cts.changeTuples );
    }

    public int hashCode() {
	return changeTuples.hashCode();
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
