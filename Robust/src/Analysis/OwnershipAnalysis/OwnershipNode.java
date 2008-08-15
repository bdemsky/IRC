package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public abstract class OwnershipNode {   

    protected HashSet<ReferenceEdge> referencees;

    public OwnershipNode() {
	referencees = new HashSet<ReferenceEdge>();
    }


    public Iterator<ReferenceEdge> iteratorToReferencees() {
	return referencees.iterator();
    }

    public Iterator<ReferenceEdge> iteratorToReferenceesClone() {
	HashSet<ReferenceEdge> clone = (HashSet<ReferenceEdge>) referencees.clone();
	return clone.iterator();
    }


    public void addReferencee( ReferenceEdge edge ) {
	assert edge != null;

	referencees.add( edge );
    }

    public void removeReferencee( ReferenceEdge edge ) {
	assert edge != null;
	assert referencees.contains( edge );

	referencees.remove( edge );
    }

    public ReferenceEdge getReferenceTo( HeapRegionNode  hrn,
					 FieldDescriptor fd ) {
	assert hrn != null;

	Iterator<ReferenceEdge> itrEdge = referencees.iterator();
	while( itrEdge.hasNext() ) {
	    ReferenceEdge edge = itrEdge.next();
	    if( edge.getDst().equals( hrn ) &&
		edge.getFieldDesc() == fd     ) {
		return edge;
	    }
	}

	return null;
    }

    /*
    public HashSet<ReferenceEdge> getAllReferencesTo( HeapRegionNode  hrn ) {
	assert hrn != null;

	HashSet<ReferenceEdge> s = new HashSet<ReferenceEdge>();

	Iterator<ReferenceEdge> itrEdge = referencees.iterator();
	while( itrEdge.hasNext() ) {
	    ReferenceEdge edge = itrEdge.next();
	    if( edge.getDst().equals( hrn ) ) {
		s.add( edge );
	    }
	}

	return s;
    }
    */

    /*
    abstract public boolean equals( Object o );
    abstract public int hashCode();
    */
}