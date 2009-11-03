package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class HeapRegionNode extends OwnershipNode {

  protected Integer id;

  protected boolean isSingleObject;
  protected boolean isFlagged;
  protected boolean isParameter;
  protected boolean isNewSummary;

  protected HashSet<ReferenceEdge> referencers;

  protected TypeDescriptor type;

  protected AllocationSite allocSite;

  protected ReachabilitySet alpha;
  protected ReachabilitySet alphaNew;

  protected String description;
  
  protected String globalIdentifier;



  public HeapRegionNode(Integer id,
                        boolean isSingleObject,
                        boolean isFlagged,
			boolean isParameter,
                        boolean isNewSummary,
			TypeDescriptor type,
                        AllocationSite allocSite,
                        ReachabilitySet alpha,
                        String description,
                        String globalIdentifier) {
    this.id = id;
    this.isSingleObject = isSingleObject;
    this.isFlagged      = isFlagged;
    this.isParameter    = isParameter;
    this.isNewSummary   = isNewSummary;
    this.type           = type;
    this.allocSite      = allocSite;
    this.alpha          = alpha;
    this.description    = description;
    this.globalIdentifier = globalIdentifier;

    referencers = new HashSet<ReferenceEdge>();
    alphaNew    = new ReachabilitySet().makeCanonical();
  }

  public HeapRegionNode copy() {
    return new HeapRegionNode(id,
                              isSingleObject,
                              isFlagged,
			      isParameter,
                              isNewSummary,
			      type,
                              allocSite,
                              alpha,
                              description,
                              globalIdentifier);
  }


  public Integer getID() {
    return id;
  }


  public boolean equalsIncludingAlpha(HeapRegionNode hrn) {
    return equals(hrn) && alpha.equals(hrn.alpha);
  }


  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !( o instanceof HeapRegionNode) ) {
      return false;
    }

    HeapRegionNode hrn = (HeapRegionNode) o;

    if( !id.equals(hrn.getID() ) ) {
      return false;
    }

    assert isSingleObject == hrn.isSingleObject();
    assert isFlagged      == hrn.isFlagged();
    assert isParameter    == hrn.isParameter();
    assert isNewSummary   == hrn.isNewSummary();
    assert description.equals(hrn.getDescription() );

    return true;
  }

  public int hashCode() {
    return id.intValue()*17;
  }


  public boolean isSingleObject() {
    return isSingleObject;
  }

  public boolean isFlagged() {
    return isFlagged;
  }

  public boolean isParameter() {
    return isParameter;
  }

  public boolean isNewSummary() {
    return isNewSummary;
  }



  public Iterator<ReferenceEdge> iteratorToReferencers() {
    return referencers.iterator();
  }

  public Iterator<ReferenceEdge> iteratorToReferencersClone() {
    HashSet<ReferenceEdge> clone = (HashSet<ReferenceEdge>)referencers.clone();
    return clone.iterator();
  }

  public int getNumReferencers() {
    return referencers.size();
  }


  public void addReferencer(ReferenceEdge edge) {
    assert edge != null;

    referencers.add(edge);
  }

  public void removeReferencer(ReferenceEdge edge) {
    assert edge != null;
    assert referencers.contains(edge);

    referencers.remove(edge);
  }

  public ReferenceEdge getReferenceFrom(OwnershipNode on,
                                        TypeDescriptor type,
					String field) {
    assert on != null;

    Iterator<ReferenceEdge> itrEdge = referencers.iterator();
    while( itrEdge.hasNext() ) {
      ReferenceEdge edge = itrEdge.next();
      if( edge.getSrc().equals(on) &&
	  edge.typeEquals(type) &&
          edge.fieldEquals(field) ) {
	return edge;
      }
    }

    return null;
  }


  public TypeDescriptor getType() {
    return type;
  }  

  public AllocationSite getAllocationSite() {
    return allocSite;
  }


  public void setAlpha(ReachabilitySet alpha) {
    this.alpha = alpha;
  }

  public ReachabilitySet getAlpha() {
    return alpha;
  }

  public ReachabilitySet getAlphaNew() {
    return alphaNew;
  }

  public void setAlphaNew(ReachabilitySet alpha) {
    this.alphaNew = alpha;
  }

  public void applyAlphaNew() {
    assert alphaNew != null;
    alpha = alphaNew;
    alphaNew = new ReachabilitySet().makeCanonical();
  }


  public String getIDString() {
    String s;

    if( id < 0 ) {
      s = "minus" + new Integer(-id).toString();
    } else {
      s = id.toString();
    }

    return s;
  }

  public String getAlphaString( boolean hideSubsetReachability ) {
    return alpha.toStringEscapeNewline(hideSubsetReachability);
  }

  public String toString() {
    return "HRN"+getIDString();
  }

  // WHY WHY WHY WHY WHY WHY?!
  public String getDescription() {
    return new String(description);
    //return new String( description+" ID "+getIDString() );
  }
  
  public String getGloballyUniqueIdentifier(){
	  return globalIdentifier;
  }
}
