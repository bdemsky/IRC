package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

// allocation sites are independent of any particular
// ownership graph, unlike most of the other elements
// of the ownership analysis.  An allocation site is
// simply a collection of heap region identifiers that
// are associated with a single allocation site in the
// program under analysis.

// So two different ownership graphs may incorporate
// nodes that represent the memory from one allocation
// site.  In this case there are two different sets of
// HeapRegionNode objects, but they have the same
// node identifiers, and there is one AllocationSite
// object associated with the FlatNew node that gives
// the graphs the identifiers in question.

public class AllocationSite {

    static private int uniqueIDcount = 0;

    protected Integer         id;
    protected int             allocationDepth;
    protected Vector<Integer> ithOldest;
    protected Integer         summary;
    protected TypeDescriptor  type;


    public AllocationSite( int allocationDepth, TypeDescriptor type ) {
	assert allocationDepth >= 3;

	this.allocationDepth = allocationDepth;	
	this.type            = type;

	ithOldest = new Vector<Integer>( allocationDepth );
	id        = generateUniqueAllocationSiteID();
    }

    static public Integer generateUniqueAllocationSiteID() {
	++uniqueIDcount;
	return new Integer( uniqueIDcount );
    }    

    
    public void setIthOldest( int i, Integer id ) {
	assert i  >= 0;
	assert i  <  allocationDepth;
	assert id != null;

	ithOldest.add( i, id );
    }

    public Integer getIthOldest( int i ) {
	assert i >= 0;
	assert i <  allocationDepth;

	return ithOldest.get( i );
    }

    public Integer getOldest() {
	return ithOldest.get( allocationDepth - 1 );
    }

    public void setSummary( Integer id ) {
	assert id != null;
	summary = id;
    }

    public Integer getSummary() {
	return summary;
    }

    public TypeDescriptor getType() {
	return type;
    }

    public String toString() {
	return "allocSite" + id + "\\n" + type;
    }
}
