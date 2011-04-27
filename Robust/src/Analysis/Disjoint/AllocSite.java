package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;

// allocation sites are independent of any particular
// reachability graph, unlike most of the other elements
// of the reachability analysis.  An allocation site is
// simply a collection of heap region identifiers that
// are associated with a single allocation site in the
// program under analysis.

// So two different reachability graphs may incorporate
// nodes that represent the memory from one allocation
// site.  In this case there are two different sets of
// HeapRegionNode objects, but they have the same
// node identifiers, and there is one AllocSite
// object associated with the FlatNew node that gives
// the graphs the identifiers in question.

// note that an allocsite extends Canonical because they
// are Canonical, but specifically so an AllocSite can
// be an operand to a CanonicalOp

public class AllocSite extends Canonical implements Alloc {

  static protected int uniqueIDcount = 0;

  public static final int AGE_notInThisSite = 100;
  public static final int AGE_in_I          = 101;
  public static final int AGE_oldest        = 102;
  public static final int AGE_summary       = 103;

  public static final int SHADOWAGE_notInThisSite = -100;
  public static final int SHADOWAGE_in_I          = -101;
  public static final int SHADOWAGE_oldest        = -102;
  public static final int SHADOWAGE_summary       = -103;

  protected Integer id;
  protected int allocationDepth;
  protected Vector<Integer> ithOldest;
  protected Integer summary;
  protected FlatNew flatNew;
  protected String disjointId;
  protected boolean isFlagged;


  public static AllocSite factory(int allocationDepth,
                                  FlatNew flatNew,
                                  String disjointId,
                                  boolean markAsFlagged
                                  ) {
    AllocSite out = new AllocSite(allocationDepth,
                                  flatNew,
                                  disjointId,
                                  markAsFlagged);
    out = (AllocSite) Canonical.makeCanonical(out);
    return out;
  }


  protected AllocSite(int allocationDepth,
                      FlatNew flatNew,
                      String disjointId,
                      boolean markAsFlagged
                      ) {

    assert allocationDepth >= 1;

    this.allocationDepth = allocationDepth;
    this.flatNew         = flatNew;
    this.disjointId      = disjointId;

    // mark this allocation site as being flagged
    // for the analysis if
    // 1) we have a non-null disjointID (a named flagged site)
    // OR
    // 2) the type is a class with Bamboo-parameter flags
    // OR
    // 3) a client wants to programmatically flag this site,
    // such as the OoOJava method effects analysis
    this.isFlagged = false;

    if( disjointId != null ) {
      this.isFlagged = true;

    } else if( flatNew.getType().isClass() &&
               flatNew.getType().getClassDesc().hasFlags()
               ) {
      this.isFlagged = true;

    } else if( markAsFlagged ) {
      this.isFlagged = true;
    }


    ithOldest = new Vector<Integer>(allocationDepth);
    id        = generateUniqueAllocSiteID();
  }

  static public Integer generateUniqueAllocSiteID() {
    ++uniqueIDcount;
    return new Integer(uniqueIDcount);
  }

  public int getUniqueAllocSiteID() {
    return id;
  }

  public String getDisjointAnalysisId() {
    return disjointId;
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

  public FlatNew getFlatNew() {
    return flatNew;
  }

  public TypeDescriptor getType() {
    return flatNew.getType();
  }

  public boolean isFlagged() {
    return isFlagged;
  }

  public int getAgeCategory(Integer id) {

    if( id.equals(summary) ) {
      return AGE_summary;
    }

    if( id.equals(getOldest() ) ) {
      return AGE_oldest;
    }

    for( int i = 0; i < allocationDepth - 1; ++i ) {
      if( id.equals(ithOldest.get(i) ) ) {
        return AGE_in_I;
      }
    }

    return AGE_notInThisSite;
  }

  public Integer getAge(Integer id) {
    for( int i = 0; i < allocationDepth; ++i ) {
      if( id.equals(ithOldest.get(i) ) ) {
        return new Integer(i);
      }
    }

    return null;
  }

  public int getShadowAgeCategory(Integer id) {
    if( id.equals(-summary) ) {
      return SHADOWAGE_summary;
    }

    if( id.equals(getOldestShadow() ) ) {
      return SHADOWAGE_oldest;
    }

    for( int i = 0; i < allocationDepth - 1; ++i ) {
      if( id.equals(getIthOldestShadow(i) ) ) {
        return SHADOWAGE_in_I;
      }
    }

    return SHADOWAGE_notInThisSite;
  }

  public Integer getShadowAge(Integer id) {
    for( int i = 0; i < allocationDepth - 1; ++i ) {
      if( id.equals(getIthOldestShadow(i) ) ) {
        return new Integer(-i);
      }
    }

    return null;
  }

  public Integer getShadowIDfromID(Integer id) {
    int ageCat = getAgeCategory(id);
    switch( ageCat ) {

    case AGE_summary:
    case AGE_oldest:
    case AGE_in_I:
      return -id;

    case AGE_notInThisSite:
    default:
      System.out.println(toStringWithIDs() );
      throw new Error("ID "+id+" not from this site.");
    }
  }

  public String toString() {
    if( disjointId == null ) {
      return "allocSite"+id;
    }
    return "allocSite "+disjointId+" ("+id+")";
  }

  public String toStringBrief() {
    return id.toString();
  }

  public String toStringVerbose() {
    if( disjointId == null ) {
      return "allocSite"+id+" "+
             flatNew.getType().toPrettyString();
    }
    return "allocSite "+disjointId+" ("+id+") "+
           flatNew.getType().toPrettyString();
  }

  public String toStringForDOT() {
    if( disjointId != null ) {
      return "disjoint "+disjointId+"\\n"+toString()+
             "\\n"+getType().toPrettyString();
    } else {
      return toString()+
             "\\n"+getType().toPrettyString();
    }
  }

  public String toStringWithIDs() {
    String s = "allocSite"+id+" ";
    for( int i = 0; i < ithOldest.size(); ++i ) {
      s += i+"("+ithOldest.get(i)+") ";
    }
    s += "summary("+summary+")";
    return s;
  }

  public boolean equalsSpecific(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof AllocSite) ) {
      return false;
    }

    AllocSite as = (AllocSite) o;

    return this.id == as.id;
  }

  public int hashCodeSpecific() {
    return id.hashCode();
  }
}
