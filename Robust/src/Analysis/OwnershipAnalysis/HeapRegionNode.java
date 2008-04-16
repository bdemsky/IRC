package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class HeapRegionNode extends OwnershipNode {

    protected Integer id;

    protected boolean isSingleObject;
    protected boolean isFlagged;
    protected boolean isNewSummary;

    protected HashSet<TempDescriptor> memberFields;
    protected HashSet<OwnershipNode>  referencers;

    protected AllocationSite allocSite;

    protected String description;



    public HeapRegionNode( Integer        id,
			   boolean        isSingleObject,
			   boolean        isFlagged,
			   boolean        isNewSummary,
			   AllocationSite allocSite,
			   String         description ) {
	this.id = id;
	this.isSingleObject = isSingleObject;
	this.isFlagged      = isFlagged;
	this.isNewSummary   = isNewSummary;
	this.allocSite      = allocSite;
	this.description    = description;

	referencers  = new HashSet<OwnershipNode>();
	memberFields = new HashSet<TempDescriptor>();
    }

    public HeapRegionNode copy() {
	return new HeapRegionNode( id,
				   isSingleObject,
				   isFlagged,
				   isNewSummary,
				   allocSite,
				   description );
    }


    public Integer getID() {
	return id;
    }

    public boolean equals( HeapRegionNode hrn ) {
	assert hrn != null;

	return id.equals( hrn.getID() )            &&
	    isSingleObject == hrn.isSingleObject() &&
	    isFlagged      == hrn.isFlagged()      &&
	    isNewSummary   == hrn.isNewSummary()   &&
	    description.equals( hrn.getDescription() );
    }



    public boolean isSingleObject() {
	return isSingleObject;
    }

    public boolean isFlagged() {
	return isFlagged;
    }

    public boolean isNewSummary() {
	return isNewSummary;
    }



    public Iterator iteratorToReferencers() {
	return referencers.iterator();
    }

    public Iterator iteratorToReferencersClone() {
	HashSet hs = (HashSet) referencers.clone();
	return hs.iterator();
    }

    public void addReferencer( OwnershipNode on ) {
	assert on != null;

	referencers.add( on );
    }

    public void removeReferencer( OwnershipNode on ) {
	assert on != null;
	assert referencers.contains( on );

	referencers.remove( on );
    }

    public boolean isReferencedBy( OwnershipNode on ) {
	assert on != null;
	return referencers.contains( on );
    }


    public AllocationSite getAllocationSite() {
	return allocSite;
    }

    public String getIDString() {
	return id.toString();
    }

    public String toString() {
	return "HRN"+getIDString();
    }

    // WHY WHY WHY WHY WHY WHY?!
    public String getDescription() {
	return new String( description );
	//return new String( description+" ID "+getIDString() );
    }
}
