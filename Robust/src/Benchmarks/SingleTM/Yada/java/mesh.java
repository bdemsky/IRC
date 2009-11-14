import java.io.*;
public class mesh {
  element rootElementPtr;
  Queue_t initBadQueuePtr;
  int size;
  RBTree boundarySetPtr;
  double angle;
  public mesh(double angle) {
    this.angle=angle;
    rootElementPtr = null;
    initBadQueuePtr = new Queue_t(-1);
    size = 0;
    boundarySetPtr = new RBTree(0);
  }
  void TMmesh_insert (element elementPtr, avltree edgeMapPtr) {
    if (rootElementPtr==null) {
      rootElementPtr=elementPtr;
    }
    int numEdge = elementPtr.element_getNumEdge();
    for (int i = 0; i < numEdge; i++) {
      edge edgePtr = elementPtr.element_getEdge(i);
      if (!edgeMapPtr.contains(edgePtr)) {
 boolean isSuccess = edgeMapPtr.insert(edgePtr, elementPtr);
 ;
      } else {
 element sharerPtr = (element)edgeMapPtr.find(edgePtr);
 ;
 elementPtr.element_addNeighbor(sharerPtr);
 sharerPtr.element_addNeighbor(elementPtr);
 boolean isSuccess = edgeMapPtr.remove(edgePtr);
 ;
 isSuccess = edgeMapPtr.insert(edgePtr, null);
 ;
      }
    }
    edge encroachedPtr = elementPtr.element_getEncroachedPtr();
    if (encroachedPtr!=null) {
      if (!boundarySetPtr.contains(encroachedPtr)) {
 elementPtr.element_clearEncroached();
      }
    }
  }
public void TMmesh_remove(element elementPtr) {
  ;
  if (rootElementPtr == elementPtr) {
    rootElementPtr=null;
  }
  List_t neighborListPtr = elementPtr.element_getNeighborListPtr();
  List_Node it=neighborListPtr.head;
  while (it.nextPtr!=null) {
    it=it.nextPtr;
    element neighborPtr = (element)it.dataPtr;
    List_t neighborNeighborListPtr = neighborPtr.element_getNeighborListPtr();
    boolean status = neighborNeighborListPtr.remove(elementPtr);
    ;
  }
  elementPtr.element_setIsGarbage(true);
}
boolean TMmesh_insertBoundary(edge boundaryPtr) {
  return boundarySetPtr.insert(boundaryPtr,null);
}
boolean TMmesh_removeBoundary(edge boundaryPtr) {
  return boundarySetPtr.deleteObjNode(boundaryPtr);
}
  void createElement(coordinate[] coordinates, int numCoordinate,
      avltree edgeMapPtr) {
  element elementPtr = new element(coordinates, numCoordinate, angle);
  if (numCoordinate == 2) {
    edge boundaryPtr = elementPtr.element_getEdge(0);
    boolean status = boundarySetPtr.insert(boundaryPtr, null);
    ;
  }
  TMmesh_insert(elementPtr, edgeMapPtr);
  if (elementPtr.element_isBad()) {
    boolean status = initBadQueuePtr.queue_push(elementPtr);
    ;
  }
}
int mesh_read(String fileNamePrefix) throws Exception {
    int i;
    int numElement = 0;
    avltree edgeMapPtr = new avltree(0);
    String fileName=fileNamePrefix+".node";
    FileInputStream inputFile = new FileInputStream(fileName);
    bytereader br=new bytereader(inputFile);
    int numEntry=br.getInt();
    int numDimension=br.getInt();
    br.jumptonextline();
    ;
    int numCoordinate = numEntry + 1;
    coordinate coordinates[] = new coordinate[numCoordinate];
    for(i=0;i<numCoordinate;i++)
      coordinates[i]=new coordinate();
    for (i = 0; i < numEntry; i++) {
      int id;
      double x;
      double y;
      id=br.getInt();
      x=br.getDouble();
      y=br.getDouble();
      br.jumptonextline();
      coordinates[id].x = x;
      coordinates[id].y = y;
    }
    ;
    inputFile.close();
    fileName=fileNamePrefix+".poly";
    inputFile = new FileInputStream(fileName);
    br=new bytereader(inputFile);
    numEntry=br.getInt();
    numDimension=br.getInt();
    br.jumptonextline();
    ;
    ;
    numEntry=br.getInt();
    br.jumptonextline();
    for (i = 0; i < numEntry; i++) {
      int id;
      int a;
      int b;
      coordinate insertCoordinates[]=new coordinate[2];
      id=br.getInt();
      a=br.getInt();
      b=br.getInt();
      br.jumptonextline();
      ;
      ;
      insertCoordinates[0] = coordinates[a];
      insertCoordinates[1] = coordinates[b];
      createElement(insertCoordinates, 2, edgeMapPtr);
    }
    ;
    numElement += numEntry;
    inputFile.close();
    fileName=fileNamePrefix+".ele";
    inputFile = new FileInputStream(fileName);
    br=new bytereader(inputFile);
    numEntry=br.getInt();
    numDimension=br.getInt();
    br.jumptonextline();
    ;
    for (i = 0; i < numEntry; i++) {
      int id;
      int a;
      int b;
      int c;
      coordinate insertCoordinates[]=new coordinate[3];
      id=br.getInt();
      a=br.getInt();
      b=br.getInt();
      c=br.getInt();
      ;
      ;
      ;
      insertCoordinates[0] = coordinates[a];
      insertCoordinates[1] = coordinates[b];
      insertCoordinates[2] = coordinates[c];
      createElement(insertCoordinates, 3, edgeMapPtr);
    }
    ;
    numElement += numEntry;
    inputFile.close();
    return numElement;
}
element mesh_getBad() {
  return (element)initBadQueuePtr.queue_pop();
}
void mesh_shuffleBad (Random randomPtr) {
  initBadQueuePtr.queue_shuffle(randomPtr);
}
boolean mesh_check(int expectedNumElement) {
    int numBadTriangle = 0;
    int numFalseNeighbor = 0;
    int numElement = 0;
    System.out.println("Checking final mesh:");
    Queue_t searchQueuePtr = new Queue_t(-1);
    avltree visitedMapPtr = new avltree(1);
    ;
    searchQueuePtr.queue_push(rootElementPtr);
    while (!searchQueuePtr.queue_isEmpty()) {
        List_t neighborListPtr;
        element currentElementPtr = (element)searchQueuePtr.queue_pop();
        if (visitedMapPtr.contains(currentElementPtr)) {
            continue;
        }
        boolean isSuccess = visitedMapPtr.insert(currentElementPtr, null);
 ;
        if (!currentElementPtr.element_checkAngles()) {
            numBadTriangle++;
        }
        neighborListPtr = currentElementPtr.element_getNeighborListPtr();
        List_Node it=neighborListPtr.head;
        while (it.nextPtr!=null) {
   it=it.nextPtr;
   element neighborElementPtr =
     (element)it.dataPtr;
   if (!visitedMapPtr.contains(neighborElementPtr)) {
     isSuccess = searchQueuePtr.queue_push(neighborElementPtr);
     ;
   }
        }
        numElement++;
    }
    System.out.println("Number of elements      = "+ numElement);
    System.out.println("Number of bad triangles = "+ numBadTriangle);
    return (!(numBadTriangle > 0 ||
       numFalseNeighbor > 0 ||
       numElement != expectedNumElement));
}
}
