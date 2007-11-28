package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class OwnershipLabelNode extends OwnershipNode {

    protected TempDescriptor td; 

    public OwnershipLabelNode( Integer id, TempDescriptor td ) {
	super( id );
	this.td = td;
    }

    public TempDescriptor getTempDescriptor() {
	return td;
    }

    public String getTempDescriptorString() {
	return td.toString();
    }

    public String toString() {
	return "OLN"+getIDString()+"_"+getTempDescriptorString();
    }
}