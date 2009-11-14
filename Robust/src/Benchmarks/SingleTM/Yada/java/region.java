public class region {
  coordinate centerCoordinate;
  Queue_t expandQueuePtr;
  List_t beforeListPtr;
  List_t borderListPtr;
  Vector_t badVectorPtr;
  public region() {
    expandQueuePtr = new Queue_t(-1);
    beforeListPtr = new List_t(0);
    borderListPtr = new List_t(1);
    badVectorPtr = new Vector_t(1);
  }
  public static void TMaddToBadVector(Vector_t badVectorPtr, element badElementPtr) {
    boolean status = badVectorPtr.vector_pushBack(badElementPtr);
    ;
    badElementPtr.element_setIsReferenced(true);
  }
  public static int TMretriangulate (element elementPtr,
         region regionPtr,
         mesh meshPtr,
         avltree edgeMapPtr, double angle) {
    Vector_t badVectorPtr = regionPtr.badVectorPtr;
    List_t beforeListPtr = regionPtr.beforeListPtr;
    List_t borderListPtr = regionPtr.borderListPtr;
    int numDelta = 0;
    ;
    coordinate centerCoordinate = elementPtr.element_getNewPoint();
    List_Node it=beforeListPtr.head;
    while (it.nextPtr!=null) {
      it=it.nextPtr;
      element beforeElementPtr = (element)it.dataPtr;
      meshPtr.TMmesh_remove(beforeElementPtr);
    }
    numDelta -= beforeListPtr.getSize();
    if (elementPtr.element_getNumEdge() == 1) {
      coordinate coordinates[]=new coordinate[2];
      edge edgePtr = elementPtr.element_getEdge(0);
      coordinates[0] = centerCoordinate;
      coordinates[1] = (coordinate)(edgePtr.firstPtr);
      element aElementPtr = new element(coordinates, 2, angle);
      meshPtr.TMmesh_insert(aElementPtr, edgeMapPtr);
      coordinates[1] = (coordinate)edgePtr.secondPtr;
      element bElementPtr = new element(coordinates, 2, angle);
      meshPtr.TMmesh_insert(bElementPtr, edgeMapPtr);
      boolean status = meshPtr.TMmesh_removeBoundary(elementPtr.element_getEdge(0));
      ;
      status = meshPtr.TMmesh_insertBoundary(aElementPtr.element_getEdge(0));
      ;
      status = meshPtr.TMmesh_insertBoundary(bElementPtr.element_getEdge(0));
      ;
      numDelta += 2;
    }
    it=borderListPtr.head;
    while (it.nextPtr!=null) {
      coordinate coordinates[]=new coordinate[3];
      it=it.nextPtr;
      edge borderEdgePtr = (edge)it.dataPtr;
      ;
      coordinates[0] = centerCoordinate;
      coordinates[1] = (coordinate)(borderEdgePtr.firstPtr);
      coordinates[2] = (coordinate)(borderEdgePtr.secondPtr);
      element afterElementPtr = new element(coordinates, 3, angle);
      meshPtr.TMmesh_insert(afterElementPtr, edgeMapPtr);
      if (afterElementPtr.element_isBad()) {
 TMaddToBadVector(badVectorPtr, afterElementPtr);
      }
    }
    numDelta += borderListPtr.getSize();
    return numDelta;
  }
  element TMgrowRegion(element centerElementPtr,
         region regionPtr,
         mesh meshPtr,
         avltree edgeMapPtr) {
    boolean isBoundary = false;
    if(centerElementPtr.element_getNumEdge() == 1) {
      isBoundary = true;
    }
    List_t beforeListPtr = regionPtr.beforeListPtr;
    List_t borderListPtr = regionPtr.borderListPtr;
    Queue_t expandQueuePtr = regionPtr.expandQueuePtr;
    beforeListPtr.clear();
    borderListPtr.clear();
    expandQueuePtr.queue_clear();
    coordinate centerCoordinatePtr = centerElementPtr.element_getNewPoint();
    expandQueuePtr.queue_push(centerElementPtr);
    while (!expandQueuePtr.queue_isEmpty()) {
      element currentElementPtr = (element) expandQueuePtr.queue_pop();
      beforeListPtr.insert(currentElementPtr);
      List_t neighborListPtr = currentElementPtr.element_getNeighborListPtr();
      List_Node it=neighborListPtr.head;
      while (it.nextPtr!=null) {
 it=it.nextPtr;
 element neighborElementPtr = (element)it.dataPtr;
 neighborElementPtr.element_isGarbage();
 if (beforeListPtr.find(neighborElementPtr)==null) {
   if (neighborElementPtr.element_isInCircumCircle(centerCoordinatePtr)) {
     if (!isBoundary && (neighborElementPtr.element_getNumEdge() == 1)) {
       return neighborElementPtr;
     } else {
       boolean isSuccess = expandQueuePtr.queue_push(neighborElementPtr);
       ;
     }
   } else {
     edge borderEdgePtr = element.element_getCommonEdge(neighborElementPtr, currentElementPtr);
     if (borderEdgePtr==null) {
       System.out.println("Abort case");
     }
     borderListPtr.insert(borderEdgePtr);
     if (!edgeMapPtr.contains(borderEdgePtr)) {
       edgeMapPtr.insert(borderEdgePtr, neighborElementPtr);
     }
   }
 }
      }
    }
    return null;
  }
  int TMregion_refine(element elementPtr, mesh meshPtr, double angle) {
    int numDelta = 0;
    avltree edgeMapPtr = null;
    element encroachElementPtr = null;
    elementPtr.element_isGarbage();
    while (true) {
      edgeMapPtr = new avltree(0);
      encroachElementPtr = TMgrowRegion(elementPtr,
     this,
     meshPtr,
     edgeMapPtr);
      if (encroachElementPtr!=null) {
 encroachElementPtr.element_setIsReferenced(true);
 numDelta += TMregion_refine(encroachElementPtr,
        meshPtr, angle);
 if (elementPtr.element_isGarbage()) {
   break;
 }
      } else {
 break;
      }
    }
    if (!elementPtr.element_isGarbage()) {
      numDelta += TMretriangulate(elementPtr,
      this,
      meshPtr,
      edgeMapPtr, angle);
    }
    return numDelta;
  }
  void region_clearBad () {
    badVectorPtr.vector_clear();
  }
  void region_transferBad(heap workHeapPtr) {
    int numBad = badVectorPtr.vector_getSize();
    for (int i = 0; i < numBad; i++) {
      element badElementPtr = (element)badVectorPtr.vector_at(i);
      if (badElementPtr.element_isGarbage()) {
      } else {
 boolean status = workHeapPtr.heap_insert(badElementPtr);
 ;
      }
    }
  }
}
