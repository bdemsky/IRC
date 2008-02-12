package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class OwnershipNode {   

    /*
    public OwnershipNode() {
	referencedRegions = 
	    new Hashtable<HeapRegionNode, ReferenceEdgeProperties>();
    }


    ///////////////////////////////////////////
    // interface with larger graph
    ///////////////////////////////////////////
    protected Hashtable
	<HeapRegionNode, ReferenceEdgeProperties>
	referencedRegions;

    public Iterator setIteratorToReferencedRegions() {
	Set s = referencedRegions.entrySet();
	return s.iterator();
    }

    public Iterator setIteratorToReferencedRegionsClone() {
	Hashtable ht = (Hashtable) referencedRegions.clone();
	Set s = ht.entrySet();
	return s.iterator();
    }

    public void addReferencedRegion( HeapRegionNode hrn,
				     ReferenceEdgeProperties rep ) {
	assert hrn != null;
	assert rep != null;

	referencedRegions.put( hrn, rep );
    }

    public void removeReferencedRegion( HeapRegionNode hrn ) {
	assert hrn != null;
	assert referencedRegions.containsKey( hrn );

	referencedRegions.remove( hrn );
    }

    public ReferenceEdgeProperties getReferenceTo( HeapRegionNode hrn ) {
	assert hrn != null;

	return referencedRegions.get( hrn );
    }
    ///////////////////////////////////////////////
    // end interface with larger graph
    ///////////////////////////////////////////////
    */
}