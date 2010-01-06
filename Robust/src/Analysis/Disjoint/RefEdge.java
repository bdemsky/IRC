package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;

public class RefEdge {

  // all edges should have a non-null
  // TypeDescriptor now
  protected TypeDescriptor type;

  // the field name may be null if this
  // edge models a variable reference
  protected String field;

  // clean means that the reference existed
  // before the current analysis context
  protected boolean isClean;

  protected ReachSet beta;
  protected ReachSet betaNew;

  protected RefSrcNode     src;
  protected HeapRegionNode dst;

  
  public RefEdge( RefSrcNode     src,
                  HeapRegionNode dst,
                  TypeDescriptor type,
                  String         field,
                  boolean        isClean,
                  ReachSet       beta ) {
    assert type != null;

    this.src     = src;
    this.dst     = dst;
    this.type    = type;
    this.field   = field;
    this.isClean = isClean;

    if( beta != null ) {
      this.beta = beta;
    } else {
      this.beta = new ReachSet().makeCanonical();
    }

    // when edges are not undergoing an operation that
    // is changing beta info, betaNew is always empty
    betaNew = new ReachSet().makeCanonical();
  }


  public RefEdge copy() {
    RefEdge copy = new RefEdge( src,
                                dst,
                                type,
                                field,
                                isClean,
                                beta );
    return copy;
  }


  public boolean equals( Object o ) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof RefEdge) ) {
      return false;
    }
    
    RefEdge edge = (RefEdge) o;
    
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

    assert isClean == edge.isClean;

    return true;
  }


  public boolean equalsIncludingBeta( RefEdge edge ) {
    return equals( edge ) && beta.equals( edge.beta );
  }
  

  public int hashCode() {
    int hash = 0;

    hash += type.hashCode()*17;

    if( field != null ) {
      hash += field.hashCode()*7;
    }
    
    hash += src.hashCode()*11;
    hash += dst.hashCode();

    return hash;
  }


  public RefSrcNode getSrc() {
    return src;
  }

  public void setSrc( RefSrcNode rsn ) {
    assert rsn != null;
    src = rsn;
  }

  public HeapRegionNode getDst() {
    return dst;
  }

  public void setDst( HeapRegionNode hrn ) {
    assert hrn != null;
    dst = hrn;
  }


  public TypeDescriptor getType() {
    return type;
  }

  public void setType( TypeDescriptor td ) {
    assert td != null;
    type = td;
  }

  public String getField() {
    return field;
  }

  public void setField( String s ) {
    field = s;
  }


  public boolean typeEquals( TypeDescriptor td ) {
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

  public boolean typeAndFieldEquals( RefEdge e ) {
    return typeEquals ( e.getType()  ) &&
           fieldEquals( e.getField() );
  }


  public boolean isClean() {
    return isClean;
  }

  public void setIsClean( boolean isClean ) {
    this.isClean = isClean;
  }


  public ReachSet getBeta() {
    return beta;
  }

  public void setBeta( ReachSet beta ) {
    assert beta != null;
    this.beta = beta;
  }

  public ReachSet getBetaNew() {
    return betaNew;
  }

  public void setBetaNew( ReachSet beta ) {
    assert beta != null;
    this.betaNew = beta;
  }
  
  public void applyBetaNew() {
    assert betaNew != null;

    beta    = betaNew;
    betaNew = new ReachSet().makeCanonical();
  }


  public String toGraphEdgeString( boolean hideSubsetReachability ) {
    String edgeLabel = "";

    if( type != null ) {
      edgeLabel += type.toPrettyString() + "\\n";
    }

    if( field != null ) {
      edgeLabel += field + "\\n";
    }

    if( isClean ) {
      edgeLabel += "*clean*\\n";
    }

    edgeLabel += beta.toStringEscapeNewline( hideSubsetReachability );
      
    return edgeLabel;
  }

  public String toString() {
    assert type != null;
    return new String( "("+src+
                       "->"+type.toPrettyString()+
                       " "+field+
                       "->"+dst+")"
                       );
  }  
}
