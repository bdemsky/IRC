package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;


public class RefEdge {

  // all edges should have a non-null
  // TypeDescriptor now
  protected TypeDescriptor type;

  // the field name may be null if this
  // edge models a variable reference
  protected String field;

  protected ReachSet beta;
  protected ReachSet betaNew;

  protected RefSrcNode src;
  protected HeapRegionNode dst;

  // existence predicates must be true in a caller
  // context for this edge to transfer from this
  // callee to that context--NOTE, existence predicates
  // do not factor into edge comparisons
  protected ExistPredSet preds;

  // taint sets indicate which heap roots have
  // tainted this edge-->meaning which heap roots
  // code must have had access to in order to
  // read or write through this edge
  protected TaintSet taints;


  public RefEdge(RefSrcNode src,
                 HeapRegionNode dst,
                 TypeDescriptor type,
                 String field,
                 ReachSet beta,
                 ExistPredSet preds,
                 TaintSet taints) {

    assert src  != null;
    assert dst  != null;
    assert type != null;

    this.src     = src;
    this.dst     = dst;
    this.type    = type;
    this.field   = field;

    if( preds != null ) {
      this.preds = preds;
    } else {
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

    if( taints != null ) {
      this.taints = taints;
    } else {
      this.taints = TaintSet.factory();
    }
  }


  public RefEdge copy() {
    RefEdge copy = new RefEdge(src,
                               dst,
                               type,
                               field,
                               beta,
                               preds,
                               taints);
    return copy;
  }


  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof RefEdge) ) {
      return false;
    }

    RefEdge edge = (RefEdge) o;

    if( !typeEquals(edge.type) ) {
      return false;
    }

    if( !fieldEquals(edge.field) ) {
      return false;
    }

    if( src instanceof VariableNode ) {
      VariableNode vsrc = (VariableNode) src;
      if( !vsrc.equals( (VariableNode) edge.src ) ) {
        return false;
      }
    } else {
      HeapRegionNode hsrc = (HeapRegionNode) src;
      if( !hsrc.equalsIncludingAlphaAndPreds( (HeapRegionNode) edge.src ) ) {
        return false;
      }
    }
    
    if( !dst.equalsIncludingAlphaAndPreds( edge.dst ) ) {
      return false;
    }

    return true;
  }


  // beta and preds contribute towards reaching the
  // fixed point, so use this method to determine if
  // an edge is "equal" to some previous visit, basically
  // and taints!
  public boolean equalsIncludingBetaPredsTaints(RefEdge edge) {
    return equals(edge) &&
           beta.equals(edge.beta) &&
           preds.equals(edge.preds) &&
           taints.equals(edge.taints);
  }

  public boolean equalsPreds(RefEdge edge) {
    return preds.equals(edge.preds);
  }


  // this method SPECIFICALLY does not use the
  // beta/preds/taints in the hash code--it uses
  // the same fields as normal equals.  Again,
  // there are two meanings of equality for edges,
  // one is "this edge is the same edge object" like when
  // deciding if an edge is already in a set, which
  // is represented by this hashcode.  The other
  // meaning is "this edge equals an edge from another
  // graph that is abstractly the same edge"
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

  public void setSrc(RefSrcNode rsn) {
    assert rsn != null;
    src = rsn;
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

  public void setType(TypeDescriptor td) {
    assert td != null;
    type = td;
  }

  public String getField() {
    return field;
  }

  public void setField(String s) {
    field = s;
  }


  public boolean typeEquals(TypeDescriptor td) {
    return type.equals(td);
  }

  public boolean fieldEquals(String s) {
    if( field == null && s == null ) {
      return true;
    }
    if( field == null ) {
      return false;
    }
    return field.equals(s);
  }

  public boolean typeAndFieldEquals(RefEdge e) {
    return typeEquals(e.getType()  ) &&
           fieldEquals(e.getField() );
  }


  public ReachSet getBeta() {
    return beta;
  }

  public void setBeta(ReachSet beta) {
    assert beta != null;
    this.beta = beta;
  }

  public ReachSet getBetaNew() {
    return betaNew;
  }

  public void setBetaNew(ReachSet beta) {
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

  public void setPreds(ExistPredSet preds) {
    this.preds = preds;
  }


  public TaintSet getTaints() {
    return taints;
  }

  public void setTaints(TaintSet taints) {
    this.taints = taints;
  }


  public String toStringDOT(boolean hideReach,
                            boolean hideSubsetReach,
                            boolean hidePreds,
                            boolean hideEdgeTaints,
                            String otherAttributes) {
    String s =
      "[label=\""+
      type.toPrettyString()+"\\n"+
      field;
    if( !hideReach ) {
      s += "\\n"+beta.toStringEscNewline(hideSubsetReach, hidePreds);
    }

    if( !hidePreds ) {
      s += "\\n"+preds.toStringEscNewline();
    }

    if( !hideEdgeTaints ) {
      if( !taints.isEmpty() ) {
        s += "\\n"+taints.toStringEscNewline( hidePreds );
      }
    }

    return s+"\",decorate"+otherAttributes+"]";
  }

  public String toString() {
    return new String("("+src+
                      "->"+type.toPrettyString()+
                      " "+field+
                      "->"+dst+")"
                      );
  }

  public String toStringAndBeta() {
    return toString()+beta.toString();
  }
}
