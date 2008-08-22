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

  protected Integer id;
  protected int allocationDepth;
  protected Vector<Integer> ithOldest;
  protected Integer summary;
  protected TypeDescriptor type;

  public static final int AGE_notInThisSite = -1;
  public static final int AGE_oldest        = -2;
  public static final int AGE_summary       = -3;


  public AllocationSite(int allocationDepth, TypeDescriptor type) {
    assert allocationDepth >= 1;

    this.allocationDepth = allocationDepth;
    this.type            = type;

    ithOldest = new Vector<Integer>(allocationDepth);
    id        = generateUniqueAllocationSiteID();
  }

  static public Integer generateUniqueAllocationSiteID() {
    ++uniqueIDcount;
    return new Integer(uniqueIDcount);
  }


  public int getAllocationDepth() {
    return allocationDepth;
  }

  public void setIthOldest(int i, Integer id) {
    assert i  >= 0;
    assert i  <  allocationDepth;
    assert id != null;

    ithOldest.add(i, id);
  }

  public Integer getIthOldest(int i) {
    assert i >= 0;
    assert i <  allocationDepth;

    return ithOldest.get(i);
  }

  public Integer getIthOldestShadow(int i) {
    assert i >= 0;
    assert i <  allocationDepth;

    return -ithOldest.get(i);
  }

  public Integer getOldest() {
    return ithOldest.get(allocationDepth - 1);
  }

  public Integer getOldestShadow() {
    return -ithOldest.get(allocationDepth - 1);
  }

  public void setSummary(Integer id) {
    assert id != null;
    summary = id;
  }

  public Integer getSummary() {
    return summary;
  }

  public Integer getSummaryShadow() {
    return -summary;
  }

  public TypeDescriptor getType() {
    return type;
  }

  public int getAge(Integer id) {
    if( id.equals(summary) ) {
      return AGE_summary;
    }

    if( id.equals(getOldest() ) ) {
      return AGE_oldest;
    }

    for( int i = 0; i < allocationDepth - 1; ++i ) {
      if( id.equals(ithOldest.get(i) ) ) {
	return i;
      }
    }

    return AGE_notInThisSite;
  }

  public String toString() {
    return "allocSite" + id;
  }
}
