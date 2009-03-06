package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;


public class ReferenceEdge {


  // a null field descriptor means "any field"
  protected FieldDescriptor fieldDesc;

  protected boolean isInitialParamReflexive;

  protected ReachabilitySet beta;
  protected ReachabilitySet betaNew;

  protected OwnershipNode src;
  protected HeapRegionNode dst;


  public ReferenceEdge(OwnershipNode src,
                       HeapRegionNode dst,
                       FieldDescriptor fieldDesc,
                       boolean isInitialParamReflexive,
                       ReachabilitySet beta) {

    this.src                     = src;
    this.dst                     = dst;
    this.fieldDesc               = fieldDesc;
    this.isInitialParamReflexive = isInitialParamReflexive;

    if( beta != null ) {
      this.beta = beta;
    } else {
      this.beta = new ReachabilitySet().makeCanonical();
    }

    // when edges are not undergoing a transitional operation
    // that is changing beta info, betaNew is always empty
    betaNew = new ReachabilitySet().makeCanonical();
  }


  public ReferenceEdge copy() {
    return new ReferenceEdge(src,
                             dst,
                             fieldDesc,
                             isInitialParamReflexive,
                             beta);
  }


  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ReferenceEdge) ) {
      return false;
    }

    ReferenceEdge edge = (ReferenceEdge) o;

    if( fieldDesc != edge.fieldDesc ) {
      return false;
    }

    // Equality of edges is only valid within a graph, so
    // compare src and dst by reference
    if( !(src == edge.src) ||
        !(dst == edge.dst)   ) {
      return false;
    }

    return true;
  }


  public boolean equalsIncludingBeta(ReferenceEdge edge) {
    return equals(edge) && beta.equals(edge.beta);
  }


  public int hashCode() {
    int hash = 0;

    if( fieldDesc != null ) {
      hash += fieldDesc.hashCode();
    }

    hash += src.hashCode()*11;
    hash += dst.hashCode();

    return hash;
  }


  public OwnershipNode getSrc() {
    return src;
  }

  public void setSrc(OwnershipNode on) {
    assert on != null;
    src = on;
  }

  public HeapRegionNode getDst() {
    return dst;
  }

  public void setDst(HeapRegionNode hrn) {
    assert hrn != null;
    dst = hrn;
  }


  public FieldDescriptor getFieldDesc() {
    return fieldDesc;
  }

  public void setFieldDesc(FieldDescriptor fieldDesc) {
    this.fieldDesc = fieldDesc;
  }


  public boolean isInitialParamReflexive() {
    return isInitialParamReflexive;
  }
  public void setIsInitialParamReflexive(boolean isInitialParamReflexive) {
    this.isInitialParamReflexive = isInitialParamReflexive;
  }


  public ReachabilitySet getBeta() {
    return beta;
  }

  public void setBeta(ReachabilitySet beta) {
    assert beta != null;
    this.beta = beta;
  }

  public ReachabilitySet getBetaNew() {
    return betaNew;
  }

  public void setBetaNew(ReachabilitySet beta) {
    assert beta != null;
    this.betaNew = beta;
  }

  public void applyBetaNew() {
    assert betaNew != null;

    beta    = betaNew;
    betaNew = new ReachabilitySet().makeCanonical();
  }


  public String toGraphEdgeString() {
    String edgeLabel = "";

    if( fieldDesc != null ) {
      edgeLabel += fieldDesc.toPrettyStringBrief() + "\\n";
    }

    if( isInitialParamReflexive ) {
      edgeLabel += "Rflx\\n";
    }

    edgeLabel += beta.toStringEscapeNewline();

    return edgeLabel;
  }

  public String toString() {
    return new String("("+src+" "+fieldDesc+" "+dst+")");
  }
}
