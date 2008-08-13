package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class HeapRegionNode extends OwnershipNode {

    protected Integer id;

    protected boolean isSingleObject;
    protected boolean isFlagged;
    protected boolean isNewSummary;

    protected HashSet<ReferenceEdge>  referencers;

    protected AllocationSite allocSite;

    protected ReachabilitySet alpha;
    protected ReachabilitySet alphaNew;

    protected String description;



    public HeapRegionNode( Integer         id,
			   boolean         isSingleObject,
			   boolean         isFlagged,
			   boolean         isNewSummary,
			   AllocationSite  allocSite,
			   ReachabilitySet alpha,
			   String          description ) {
	this.id = id;
	this.isSingleObject = isSingleObject;
	this.isFlagged      = isFlagged;
	this.isNewSummary   = isNewSummary;
	this.allocSite      = allocSite;
	this.alpha          = alpha;
	this.description    = description;	

	referencers = new HashSet<ReferenceEdge>();
	alphaNew    = new ReachabilitySet().makeCanonical();
    }

    public HeapRegionNode copy() {
	return new HeapRegionNode( id,
				   isSingleObject,
				   isFlagged,
				   isNewSummary,
				   allocSite,
				   alpha,
				   description );
    }


    public Integer getID() {
	return id;
    }


    public boolean equals( Object o ) {
	if( o == null ) {
	    return false;
	}

	if( !( o instanceof HeapRegionNode) ) {
	    return false;
	}

	HeapRegionNode hrn = (HeapRegionNode) o;

	if( !id.equals( hrn.getID() ) ) {
	    return false;
	}

	assert isSingleObject == hrn.isSingleObject();
	assert isFlagged      == hrn.isFlagged();
	assert isNewSummary   == hrn.isNewSummary();
	assert description.equals( hrn.getDescription() ); 

	return alpha.equals( hrn.getAlpha() );

	/*
	return id.equals( hrn.getID() )            &&
	    isSingleObject == hrn.isSingleObject() &&
	    isFlagged      == hrn.isFlagged()      &&
	    isNewSummary   == hrn.isNewSummary()   &&
	    alpha.equals( hrn.getAlpha() )         &&
	    description.equals( hrn.getDescription() );
	*/
    }

    public int hashCode() {
	return id.intValue()*17 + alpha.hashCode();
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



    public Iterator<ReferenceEdge> iteratorToReferencers() {
	return referencers.iterator();
    }

    public Iterator<ReferenceEdge> iteratorToReferencersClone() {
	HashSet<ReferenceEdge> clone = (HashSet<ReferenceEdge>) referencers.clone();
	return clone.iterator();
    }

    public void addReferencer( ReferenceEdge edge ) {
	assert edge != null;

	referencers.add( edge );
    }

    public void removeReferencer( ReferenceEdge edge ) {
	assert edge != null;
	assert referencers.contains( edge );

	referencers.remove( edge );
    }

    public ReferenceEdge getReferenceFrom( OwnershipNode   on,
					   FieldDescriptor fd ) {
	assert on != null;

	Iterator<ReferenceEdge> itrEdge = referencers.iterator();
	while( itrEdge.hasNext() ) {
	    ReferenceEdge edge = itrEdge.next();
	    if( edge.getSrc().equals( on ) &&
		edge.getFieldDesc() == fd     ) {
		return edge;
	    }
	}

	return null;
    }


    public AllocationSite getAllocationSite() {
	return allocSite;
    }


    public void setAlpha( ReachabilitySet alpha ) {
	this.alpha = alpha;
    }

    public ReachabilitySet getAlpha() {
	return alpha;
    }

    public ReachabilitySet getAlphaNew() {
	return alphaNew;
    }

    public void setAlphaNew( ReachabilitySet alpha ) {
	this.alphaNew = alpha;
    }

    public void applyAlphaNew() {
	assert alphaNew != null;

	alpha = alphaNew;

	alphaNew = new ReachabilitySet();
	alphaNew = alphaNew.makeCanonical();
    }


    public String getIDString() {
	return id.toString();
    }

    public String getAlphaString() {
	return alpha.toStringEscapeNewline();
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
