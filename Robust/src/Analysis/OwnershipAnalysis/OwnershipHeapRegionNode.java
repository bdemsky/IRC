package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class OwnershipHeapRegionNode {
    
    protected int id;
    protected Hashtable<FieldDescriptor, OwnershipHeapRegionNode> fields;
    protected Vector<TempDescriptor> analysisRegionAliases;

    public OwnershipHeapRegionNode( int id ) {
	this.id = id;
	fields = new Hashtable<FieldDescriptor, OwnershipHeapRegionNode>();
	analysisRegionAliases = new Vector<TempDescriptor>();
    }

    public void setField( FieldDescriptor fd,
			  OwnershipHeapRegionNode ohrn ) {
	fields.put( fd, ohrn );
    }

    public OwnershipHeapRegionNode getField( FieldDescriptor fd ) {
	return fields.get( fd );
    }

    public Iterator getFieldIterator() {
	Set s = fields.entrySet();
	return s.iterator();
    }

    public String getIDString() {
	return (new Integer( id )).toString();
    }

    public String toString() {
	return "OHRN"+getIDString();
    }

    public void addAnalysisRegionAlias( TempDescriptor td ) {
	analysisRegionAliases.add( td );
    }

    public Vector<TempDescriptor> getAnalysisRegionAliases() {
	return analysisRegionAliases;
    }
}
