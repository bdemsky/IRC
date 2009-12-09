package Analysis.Disjoint;

import IR.*;
import IR.Flat.*;
import java.util.*;

public abstract class RefSrcNode {

  protected HashSet<RefEdge> referencees;

  public RefSrcNode() {
    referencees = new HashSet<RefEdge>();
  }


  public Iterator<RefEdge> iteratorToReferencees() {
    return referencees.iterator();
  }

  public Iterator<RefEdge> iteratorToReferenceesClone() {
    HashSet<RefEdge> clone = (HashSet<RefEdge>)referencees.clone();
    return clone.iterator();
  }

  public int getNumReferencees() {
    return referencees.size();
  }

  public void addReferencee( RefEdge edge ) {
    assert edge != null;
    referencees.add( edge );
  }

  public void removeReferencee( RefEdge edge ) {
    assert edge != null;
    assert referencees.contains( edge );
    referencees.remove( edge );
  }

  public RefEdge getReferenceTo(HeapRegionNode hrn,
                                      TypeDescriptor type,
				      String field) {
    assert hrn != null;

    Iterator<RefEdge> itrEdge = referencees.iterator();
    while( itrEdge.hasNext() ) {
      RefEdge edge = itrEdge.next();
      if( edge.getDst().equals(hrn) &&
	  edge.typeEquals( type ) &&
          edge.fieldEquals( field ) ) {
	return edge;
      }
    }

    return null;
  }
}