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

  protected ReachSet beta;
  protected ReachSet betaNew;

  protected RefSrcNode     src;
  protected HeapRegionNode dst;

  // existence predicates must be true in a caller
  // context for this edge to transfer from this
  // callee to that context--NOTE, existence predicates
  // do not factor into edge comparisons
  protected ExistPredSet preds;

  
  public RefEdge( RefSrcNode     src,
                  HeapRegionNode dst,
                  TypeDescriptor type,
                  String         field,
                  ReachSet       beta,
                  ExistPredSet   preds ) {
    assert type != null;

    this.src     = src;
    this.dst     = dst;
    this.type    = type;
    this.field   = field;

    if( preds != null ) {
      this.preds = preds;
    } else {
      // TODO: do this right?
      this.preds = ExistPredSet.factory();
    }

    if( beta != null ) {
      this.beta = beta;
    } else {
      this.beta = ReachSet.factory();
    }

    // when edges are not undergoing an operation that
    // is changing beta info, betaNew is always empty
    betaNew = ReachSet.factory();
  }


  public RefEdge copy() {
    RefEdge copy = new RefEdge( src,
                                dst,
                                type,
                                field,
                                beta,
                                preds );
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

    return true;
  }


  // beta and preds contribute towards reaching the
  // fixed point, so use this method to determine if
  // an edge is "equal" to some previous visit, basically
  public boolean equalsIncludingBetaAndPreds( RefEdge edge ) {
    return equals( edge ) && 
      beta.equals( edge.beta ) &&
      preds.equals( edge.preds );
  }

  public boolean equalsPreds( RefEdge edge ) {
    return preds.equals( edge.preds );
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
    betaNew = ReachSet.factory();
  }


  public ExistPredSet getPreds() {
    return preds;
  }

  public void setPreds( ExistPredSet preds ) {
    this.preds = preds;
  }


  public String toStringDOT( boolean hideSubsetReach, 
                             String  otherAttributes ) {
    return new String( "[label=\""+
                       type.toPrettyString()+"\\n"+
                       field+"\\n"+
                       beta.toStringEscNewline( hideSubsetReach )+"\\n"+
                       preds.toString()+"\",decorate"+
                       otherAttributes+"]"
                       );
  }

  public String toString() {
    return new String( "("+src+
                       "->"+type.toPrettyString()+
                       " "+field+
                       "->"+dst+")"
                       );
  }  

  public String toStringAndBeta() {
    return toString()+beta.toString();
  }
}
