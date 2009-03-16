package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;

public abstract class OwnershipNode {

  protected HashSet<ReferenceEdge> referencees;

  public OwnershipNode() {
    referencees = new HashSet<ReferenceEdge>();
  }


  public Iterator<ReferenceEdge> iteratorToReferencees() {
    return referencees.iterator();
  }

  public Iterator<ReferenceEdge> iteratorToReferenceesClone() {
    HashSet<ReferenceEdge> clone = (HashSet<ReferenceEdge>)referencees.clone();
    return clone.iterator();
  }

  public int getNumReferencees() {
    return referencees.size();
  }

  public void addReferencee(ReferenceEdge edge) {
    assert edge != null;

    referencees.add(edge);
  }

  public void removeReferencee(ReferenceEdge edge) {
    assert edge != null;
    assert referencees.contains(edge);

    referencees.remove(edge);
  }

  public ReferenceEdge getReferenceTo(HeapRegionNode hrn,
                                      TypeDescriptor type,
				      String field) {
    assert hrn != null;

    Iterator<ReferenceEdge> itrEdge = referencees.iterator();
    while( itrEdge.hasNext() ) {
      ReferenceEdge edge = itrEdge.next();
      if( edge.getDst().equals(hrn) &&
	  edge.typeEquals( type ) &&
          edge.fieldEquals( field ) ) {
	return edge;
      }
    }

    return null;
  }
}