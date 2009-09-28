package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;


public class ReferenceEdge {

  // null descriptors mean "any field"
  protected TypeDescriptor type;
  protected String field;

  protected boolean isInitialParam;

  protected ReachabilitySet beta;
  protected ReachabilitySet betaNew;

  protected OwnershipNode src;
  protected HeapRegionNode dst;
  private int taintIdentifier;


  public ReferenceEdge(OwnershipNode src,
                       HeapRegionNode dst,
		       TypeDescriptor type,
		       String field,
                       boolean isInitialParam,
                       ReachabilitySet beta) {

    this.src                     = src;
    this.dst                     = dst;
    this.type                    = type;
    this.field                   = field;
    this.isInitialParam = isInitialParam;
    this.taintIdentifier = 0;

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
	  ReferenceEdge copy= new ReferenceEdge(src,
                             dst,
			     type,
			     field,
                             isInitialParam,
                             beta);
	  copy.setTaintIdentifier(this.taintIdentifier);
	  return copy;
  }


  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof ReferenceEdge) ) {
      return false;
    }

    ReferenceEdge edge = (ReferenceEdge) o;

    if( !typeEquals( edge.type ) ) {
      return false;
    }

    if( !fieldEquals( edge.field ) ) {
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

    if( type != null ) {
      hash += type.hashCode()*17;
    }

    if( field != null ) {
      hash += field.hashCode()*7;
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


  public TypeDescriptor getType() {
    return type;
  }

  public void setType( TypeDescriptor td ) {
    type = td;
  }

  public String getField() {
    return field;
  }

  public void setField( String s ) {
    field = s;
  }


  public boolean typeEquals( TypeDescriptor td ) {
    if( type == null && td == null ) {
      return true;
    }
    if( type == null ) {
      return false;
    }
    return type.equals( td );
  }

  public boolean fieldEquals( String s ) {
    if( field == null && s == null ) {
      return true;
    }
    if( field == null ) {
      return false;
    }
    return field.equals( s );
  }

  public boolean typeAndFieldEquals( ReferenceEdge e ) {
    return typeEquals ( e.getType()  ) &&
           fieldEquals( e.getField() );
  }


  public boolean isInitialParam() {
    return isInitialParam;
  }

  public void setIsInitialParam(boolean isInitialParam) {
    this.isInitialParam = isInitialParam;
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

    if( type != null ) {
      edgeLabel += type.toPrettyString()+"\\n";
    }

    if( field != null ) {
      edgeLabel += field+"\\n";
    }

    if( isInitialParam ) {
      edgeLabel += "*init*\\n";
    }
    
    edgeLabel+="*taint*="+taintIdentifier+"\\n";

    edgeLabel += beta.toStringEscapeNewline();

    return edgeLabel;
  }

  public String toString() {
    if( type != null ) {
      return new String("("+src+"->"+type.toPrettyString()+" "+field+"->"+dst+")");
    }

    return new String("("+src+"->"+type+" "+field+"->"+dst+")");
  }
  
  public void tainedBy(Integer paramIdx){
	  int newTaint=(int) Math.pow(2, paramIdx.intValue());
	  taintIdentifier=taintIdentifier | newTaint;
  }
  
  public void setTaintIdentifier(int newTaint){
	  taintIdentifier=newTaint;
  }
  
  public void unionTaintIdentifier(int newTaint){
	  taintIdentifier=taintIdentifier | newTaint;
  }
  
  public int getTaintIdentifier(){
	  return taintIdentifier;
  }
  
}
