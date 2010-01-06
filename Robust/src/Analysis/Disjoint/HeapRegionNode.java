package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;

public class HeapRegionNode extends RefSrcNode {

  protected Integer id;

  protected boolean isSingleObject;
  protected boolean isFlagged;
  protected boolean isNewSummary;

  // clean means that the node existed
  // before the current analysis context
  protected boolean isClean;  

  // special nodes that represent heap parts
  // outside of the current method context
  protected boolean isOutOfContext;

  protected HashSet<RefEdge> referencers;

  protected TypeDescriptor type;

  protected AllocSite allocSite;

  // some reachability states are inherent
  // to a node by its definition
  protected ReachSet inherent;  

  // use alpha for the current reach states
  // and alphaNew during iterative calculations
  // to update alpha
  protected ReachSet alpha;
  protected ReachSet alphaNew;

  protected String description;


  public HeapRegionNode( Integer        id,
                         boolean        isSingleObject,
                         boolean        isFlagged,
                         boolean        isNewSummary,
                         boolean        isClean,
                         boolean        isOutOfContext,
                         TypeDescriptor type,
                         AllocSite      allocSite,
                         ReachSet       inherent,
                         ReachSet       alpha,
                         String         description
                         ) {
    this.id             = id;
    this.isSingleObject = isSingleObject;
    this.isFlagged      = isFlagged;
    this.isNewSummary   = isNewSummary;
    this.isClean        = isClean;
    this.isOutOfContext = isOutOfContext;
    this.type           = type;
    this.allocSite      = allocSite;
    this.inherent       = inherent;
    this.alpha          = alpha;
    this.description    = description;

    referencers = new HashSet<RefEdge>();
    alphaNew    = new ReachSet().makeCanonical();
  }

  public HeapRegionNode copy() {
    return new HeapRegionNode( id,
                               isSingleObject,
                               isFlagged,
                               isNewSummary,
                               isClean,
                               isOutOfContext,
                               type,
                               allocSite,
                               inherent,
                               alpha,
                               description );
  }


  public Integer getID() {
    return id;
  }


  public boolean equalsIncludingAlpha( HeapRegionNode hrn ) {
    return equals( hrn ) && alpha.equals( hrn.alpha );
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof HeapRegionNode) ) {
      return false;
    }

    HeapRegionNode hrn = (HeapRegionNode) o;

    if( !id.equals( hrn.getID() ) ) {
      return false;
    }

    assert isSingleObject == hrn.isSingleObject();
    assert isFlagged      == hrn.isFlagged();
    assert isNewSummary   == hrn.isNewSummary();
    assert isClean        == hrn.isClean();
    assert isOutOfContext == hrn.isOutOfContext();
    assert description.equals( hrn.getDescription() );

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

  public boolean isNewSummary() {
    return isNewSummary;
  }

  public boolean isOutOfContext() {
    return isOutOfContext();
  }

  public boolean isClean() {
    return isClean();
  }

  public void setIsClean( boolean isClean ) {
    this.isClean = isClean;
  }


  public Iterator<RefEdge> iteratorToReferencers() {
    return referencers.iterator();
  }

  public Iterator<RefEdge> iteratorToReferencersClone() {
    HashSet<RefEdge> clone = (HashSet<RefEdge>)referencers.clone();
    return clone.iterator();
  }

  public int getNumReferencers() {
    return referencers.size();
  }

  public void addReferencer( RefEdge edge ) {
    assert edge != null;

    referencers.add( edge );
  }

  public void removeReferencer( RefEdge edge ) {
    assert edge != null;
    assert referencers.contains( edge );

    referencers.remove( edge );
  }

  public RefEdge getReferenceFrom( RefSrcNode     rsn,
                                   TypeDescriptor type,
                                   String         field
                                   ) {
    assert rsn != null;

    Iterator<RefEdge> itrEdge = referencers.iterator();
    while( itrEdge.hasNext() ) {
      RefEdge edge = itrEdge.next();

      if( edge.getSrc().equals( rsn ) &&
	  edge.typeEquals( type )     &&
          edge.fieldEquals( field ) 
          ) {
	return edge;
      }
    }

    return null;
  }


  public TypeDescriptor getType() {
    return type;
  }  

  public AllocSite getAllocSite() {
    return allocSite;
  }

  
  public ReachSet getInherent() {
    return inherent;
  }
  
  public ReachSet getAlpha() {
    return alpha;
  }

  public void setAlpha( ReachSet alpha ) {
    this.alpha = alpha;
  }

  public ReachSet getAlphaNew() {
    return alphaNew;
  }

  public void setAlphaNew( ReachSet alpha ) {
    this.alphaNew = alpha;
  }

  public void applyAlphaNew() {
    assert alphaNew != null;
    alpha = alphaNew;
    alphaNew = new ReachSet().makeCanonical();
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
    return alpha.toStringEscapeNewline( hideSubsetReachability );
  }

  public String toString() {
    return "HRN"+getIDString();
  }

  public String getDescription() {
    return description;
  }  
}
