package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class HeapRegionNode extends OwnershipNode {

    public HeapRegionNode( Integer id,
			   boolean isSingleObject,
			   boolean isFlagged,
			   boolean isNewSummary ) {
	this.id = id;
	this.isSingleObject = isSingleObject;
	this.isFlagged      = isFlagged;
	this.isNewSummary   = isNewSummary;

	referencers           = new HashSet<OwnershipNode>();
	//analysisRegionAliases = new HashSet<TempDescriptor>();
	memberFields          = new HashSet<TempDescriptor>();
    }

    public HeapRegionNode copy() {
	return new HeapRegionNode( id,
				   isSingleObject,
				   isFlagged,
				   isNewSummary );
    }


    /////////////////
    // equality  
    /////////////////
    protected Integer id;

    public Integer getID() {
	return id;
    }

    public boolean equals( HeapRegionNode hrn ) {
	assert hrn != null;

	return id.equals( hrn.getID() )            &&
	    isSingleObject == hrn.isSingleObject() &&
	    isFlagged      == hrn.isFlagged()      &&
	    isNewSummary   == hrn.isNewSummary();
    }
    /////////////////
    // end equality  
    /////////////////


    
    /////////////////
    // predicates
    /////////////////
    boolean isSingleObject;
    public boolean isSingleObject() {
	return isSingleObject;
    }

    boolean isFlagged;
    public boolean isFlagged() {
	return isFlagged;
    }

    boolean isNewSummary;
    public boolean isNewSummary() {
	return isNewSummary;
    }
    ///////////////////
    // end predicates 
    ///////////////////



    ///////////////////////////////////////////
    // interface with larger graph
    ///////////////////////////////////////////
    protected HashSet<TempDescriptor> memberFields;
    protected HashSet<OwnershipNode>  referencers;

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
    ///////////////////////////////////////////////
    // end interface with larger graph
    ///////////////////////////////////////////////




    ///////////////////////////////////////////////
    // analysis interface
    ///////////////////////////////////////////////
    /*
    protected HashSet<TempDescriptor> analysisRegionAliases;

    public void addAnalysisRegionAlias( TempDescriptor td ) {
	assert td != null;
	assert !analysisRegionAliases.contains( td );
	
	analysisRegionAliases.add( td );
    }

    public Iterator iteratorToAnalysisRegionAliases() {
	return analysisRegionAliases.iterator();
    }
    */
    ///////////////////////////////////////////////
    // end analysis interface
    ///////////////////////////////////////////////


    // for writing out
    public String getIDString() {
	return id.toString();
    }

    public String toString() {
	return "HRN"+getIDString();
    }
}
