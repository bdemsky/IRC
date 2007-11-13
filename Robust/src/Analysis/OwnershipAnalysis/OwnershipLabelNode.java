package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class OwnershipLabelNode {

    protected int id;
    protected TempDescriptor td; 
    protected OwnershipHeapRegionNode ohrn;

    public OwnershipLabelNode( int id, TempDescriptor td ) {
	this.id = id;
	this.td = td;
    }

    public OwnershipHeapRegionNode getOwnershipHeapRegionNode() {
	return ohrn;
    }

    public void setOwnershipHeapRegionNode( OwnershipHeapRegionNode ohrn ) {
	this.ohrn = ohrn;
    }

    public TempDescriptor getTempDescriptor() {
	return td;
    }

    public String getIDString() {
	return (new Integer( id )).toString();
    }

    public String getTempDescriptorString() {
	return td.toString();
    }

    public String toString() {
	return "OLN"+getIDString()+"_"+getTempDescriptorString();
    }
}