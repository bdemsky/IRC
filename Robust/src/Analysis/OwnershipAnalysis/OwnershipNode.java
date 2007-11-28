package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class OwnershipNode {

    protected Integer id;
    protected HashSet<OwnershipHeapRegionNode> reachableRegions;
    protected HashSet<OwnershipNode>           referencers;

    public OwnershipNode( Integer id ) {
	this.id = id;
	reachableRegions = new HashSet<OwnershipHeapRegionNode>();
	referencers      = new HashSet<OwnershipNode>();
    }

    public Integer getID() {
	return id;
    }

    public String getIDString() {
	return id.toString();
    }

    public Iterator iteratorToReachableRegions() {
	return reachableRegions.iterator();
    }

    public void addReachableRegion( OwnershipHeapRegionNode ohrn ) {
	assert ohrn!=null;
	reachableRegions.add( ohrn );
    }

    public void clearReachableRegions() {
	reachableRegions.clear();
    }
    
    public Iterator iteratorToReferencers() {
	return referencers.iterator();
    }

    public void addReferencer( OwnershipNode on ) {
	referencers.add( on );
    }

    public void clearReferencers() {
	referencers.clear();
    }
}