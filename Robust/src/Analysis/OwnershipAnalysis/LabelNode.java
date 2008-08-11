package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class LabelNode extends OwnershipNode {
    protected TempDescriptor td;

    public LabelNode( TempDescriptor td ) {
	this.td = td;
    }

    public TempDescriptor getTempDescriptor() {
	return td;
    }

    /*
    public boolean equals( Object o ) {
	if( o == null ) {
	    return false;
	}

	if( !( o instanceof LabelNode) ) {
	    return false;
	}

	LabelNode ln = (LabelNode) o;

	return td == ln.getTempDescriptor();
    }

    public int hashCode() {
	return td.getNum();
    }
    */

    public String getTempDescriptorString() {
	return td.toString();
    }

    public String toString() {
	return "LN_"+getTempDescriptorString();
    }
}
