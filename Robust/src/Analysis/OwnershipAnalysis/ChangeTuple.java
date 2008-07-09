package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;


// a change touple is a pair that indicates if the
// first TokenTupleSet is found in a ReachabilitySet,
// then the second TokenTupleSet should be added

// THIS CLASS IS IMMUTABLE!

public class ChangeTuple extends Canonical
{
    private TokenTupleSet toMatch;
    private TokenTupleSet toAdd;

    public ChangeTuple( TokenTupleSet toMatch,
			TokenTupleSet toAdd ) {
	this.toMatch = toMatch;
	this.toAdd   = toAdd;
    }

    public ChangeTuple makeCanonical() {
	return (ChangeTuple) Canonical.makeCanonical( this );
    }

    public TokenTupleSet getSetToMatch() { return toMatch; }
    public TokenTupleSet getSetToAdd()   { return toAdd;   }

    public boolean equals( Object o ) {
	if( !(o instanceof ChangeTuple) ) {
	    return false;
	}

	ChangeTuple ct = (ChangeTuple) o;

	return toMatch.equals( ct.getSetToMatch() ) &&
	         toAdd.equals( ct.getSetToAdd()   );
    }

    public int hashCode() {
	return toMatch.hashCode() + toAdd.hashCode();
    }

    public String toString() {
	return new String( "<"+toMatch+" -> "+toAdd+">" );
    }
}
