package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class HeapRegionNode extends RefSrcNode {

  protected Integer id;

  protected boolean isSingleObject;
  protected boolean isFlagged;
  protected boolean isNewSummary;

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

  // existence predicates must be true in a caller
  // context for this node to transfer from this
  // callee to that context
  protected ExistPredSet preds;


  public HeapRegionNode(Integer id,
                        boolean isSingleObject,
                        boolean isFlagged,
                        boolean isNewSummary,
                        boolean isOutOfContext,
                        TypeDescriptor type,
                        AllocSite allocSite,
                        ReachSet inherent,
                        ReachSet alpha,
                        ExistPredSet preds,
                        String description
                        ) {

    this.id             = id;
    this.isSingleObject = isSingleObject;
    this.isFlagged      = isFlagged;
    this.isNewSummary   = isNewSummary;
    this.isOutOfContext = isOutOfContext;
    this.type           = type;
    this.allocSite      = allocSite;
    this.inherent       = inherent;
    this.alpha          = alpha;
    this.preds          = preds;
    this.description    = description;

    referencers = new HashSet<RefEdge>();
    alphaNew    = ReachSet.factory();
  }

  public HeapRegionNode copy() {
    return new HeapRegionNode(id,
                              isSingleObject,
                              isFlagged,
                              isNewSummary,
                              isOutOfContext,
                              type,
                              allocSite,
                              inherent,
                              alpha,
                              preds,
                              description);
  }


  public Integer getID() {
    return id;
  }


  // alpha and preds contribute towards reaching the
  // fixed point, so use this method to determine if
  // a node is "equal" to some previous visit, basically
  public boolean equalsIncludingAlphaAndPreds(HeapRegionNode hrn) {
    return equals(hrn) &&
           alpha.equals(hrn.alpha) &&
           preds.equals(hrn.preds);
  }


  public boolean equals(Object o) {
    if( o == null ) {
      return false;
    }

    if( !(o instanceof HeapRegionNode) ) {
      return false;
    }

    HeapRegionNode hrn = (HeapRegionNode) o;

    if( !id.equals(hrn.getID() ) ) {
      return false;
    }

    assert isSingleObject == hrn.isSingleObject();
    assert isFlagged      == hrn.isFlagged();
    assert isNewSummary   == hrn.isNewSummary();
    assert isOutOfContext == hrn.isOutOfContext();
    //assert description.equals( hrn.getDescription() );

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
    return isOutOfContext;
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


  // in other words, this node is not functionally
  // part of the graph (anymore)
  public boolean isWiped() {
    return
      getNumReferencers() == 0 &&
      getNumReferencees() == 0;
  }


  public void addReferencer(RefEdge edge) {
    assert edge != null;

    referencers.add(edge);
  }

  public void removeReferencer(RefEdge edge) {
    assert edge != null;
    assert referencers.contains(edge);

    referencers.remove(edge);
  }

  public RefEdge getReferenceFrom(RefSrcNode rsn,
                                  TypeDescriptor type,
                                  String field
                                  ) {
    assert rsn != null;

    Iterator<RefEdge> itrEdge = referencers.iterator();
    while( itrEdge.hasNext() ) {
      RefEdge edge = itrEdge.next();

      if( edge.getSrc().equals(rsn) &&
          edge.typeEquals(type)     &&
          edge.fieldEquals(field)
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

  public void setAlpha(ReachSet alpha) {
    this.alpha = alpha;
  }

  public ReachSet getAlphaNew() {
    return alphaNew;
  }

  public void setAlphaNew(ReachSet alpha) {
    this.alphaNew = alpha;
  }

  public void applyAlphaNew() {
    assert alphaNew != null;
    alpha = alphaNew;
    alphaNew = ReachSet.factory();
  }


  public ExistPredSet getPreds() {
    return preds;
  }

  public void setPreds(ExistPredSet preds) {
    this.preds = preds;
  }


  // use this method to assert that an out-of-context
  // heap region node has only out-of-context symbols
  // in its reachability
  public boolean reachHasOnlyOOC() {
    assert isOutOfContext;

    Iterator<ReachState> stateItr = alpha.iterator();
    while( stateItr.hasNext() ) {
      ReachState state = stateItr.next();

      Iterator<ReachTuple> rtItr = state.iterator();
      while( rtItr.hasNext() ) {
        ReachTuple rt = rtItr.next();

        if( !rt.isOutOfContext() ) {
          return false;
        }
      }
    }

    return true;
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

  public String getDescription() {
    return description;
  }

  public String toStringDOT(boolean hideReach,
                            boolean hideSubsetReach,
                            boolean hidePreds) {
    String attributes = "";

    if( isSingleObject ) {
      attributes += "shape=box";
    } else {
      attributes += "shape=Msquare";
    }

    if( isFlagged ) {
      attributes += ",style=filled,fillcolor=lightgrey";
    }

    String typeStr;
    if( type == null ) {
      typeStr = "null";
    } else {
      typeStr = type.toPrettyString();
    }

    String s =
      "["+attributes+
      ",label=\"ID"+getIDString()+"\\n"+
      typeStr+"\\n"+
      description;

    if( !hideReach ) {
      s += "\\n"+alpha.toStringEscNewline(hideSubsetReach, hidePreds);
    }

    if( !hidePreds ) {
      s += "\\n"+preds.toStringEscNewline();
    }

    return s+"\"]";
  }

  public String toString() {
    return "HRN"+getIDString();
  }
}
