public class element {
  coordinate coordinates[];
  int numCoordinate;
  coordinate circumCenter;
  double circumRadius;
  double minAngle;
  edge edges[];
  int numEdge;
  coordinate midpoints[];
  double radii[];
  edge encroachedEdgePtr;
  boolean isSkinny;
  List_t neighborListPtr;
  boolean isGarbage;
  boolean isReferenced;
  void minimizeCoordinates() {
    int minPosition = 0;
    for (int i = 1; i < numCoordinate; i++) {
      if (coordinate.coordinate_compare(coordinates[i], coordinates[minPosition]) < 0) {
 minPosition = i;
      }
    }
    while(minPosition != 0) {
      coordinate tmp = coordinates[0];
      for (int j = 0; j < (numCoordinate - 1); j++) {
 coordinates[j] = coordinates[j+1];
      }
      coordinates[numCoordinate-1] = tmp;
      minPosition--;
    }
  }
  void checkAngles() {
    minAngle = 180.0;
    ;
    isReferenced = false;
    isSkinny = false;
    encroachedEdgePtr = null;
    if (numCoordinate == 3) {
      for (int i = 0; i < 3; i++) {
 double angle = coordinate.coordinate_angle(coordinates[i],
         coordinates[(i + 1) % 3],
         coordinates[(i + 2) % 3]);
 ;
 ;
 if (angle > 90.0) {
   encroachedEdgePtr = edges[(i + 1) % 3];
 }
 if (angle < angleConstraint) {
   isSkinny = true;
 }
 if (angle < minAngle) {
   minAngle = angle;
 }
      }
      ;
    }
  }
  void calculateCircumCircle() {
    coordinate circumCenterPtr = this.circumCenter;
    ;
    if (numCoordinate == 2) {
      circumCenterPtr.x = (coordinates[0].x + coordinates[1].x) / 2.0;
      circumCenterPtr.y = (coordinates[0].y + coordinates[1].y) / 2.0;
    } else {
      double ax = coordinates[0].x;
      double ay = coordinates[0].y;
      double bx = coordinates[1].x;
      double by = coordinates[1].y;
      double cx = coordinates[2].x;
      double cy = coordinates[2].y;
      double bxDelta = bx - ax;
      double byDelta = by - ay;
      double cxDelta = cx - ax;
      double cyDelta = cy - ay;
      double bDistance2 = (bxDelta * bxDelta) + (byDelta * byDelta);
      double cDistance2 = (cxDelta * cxDelta) + (cyDelta * cyDelta);
      double xNumerator = (byDelta * cDistance2) - (cyDelta * bDistance2);
      double yNumerator = (bxDelta * cDistance2) - (cxDelta * bDistance2);
      double denominator = 2 * ((bxDelta * cyDelta) - (cxDelta * byDelta));
      double rx = ax - (xNumerator / denominator);
      double ry = ay + (yNumerator / denominator);
      ;
      circumCenterPtr.x = rx;
      circumCenterPtr.y = ry;
    }
    circumRadius = coordinate.coordinate_distance(circumCenterPtr,
        coordinates[0]);
  }
  public void setEdge(int i) {
    coordinate firstPtr = coordinates[i];
    coordinate secondPtr = coordinates[(i + 1) % numCoordinate];
    edge edgePtr = edges[i];
    int cmp = coordinate.coordinate_compare(firstPtr, secondPtr);
    ;
    if (cmp < 0) {
      edgePtr.firstPtr = firstPtr;
      edgePtr.secondPtr = secondPtr;
    } else {
      edgePtr.firstPtr = secondPtr;
      edgePtr.secondPtr = firstPtr;
    }
    coordinate midpointPtr = midpoints[i];
    midpointPtr.x = (firstPtr.x + secondPtr.x) / 2.0;
    midpointPtr.y = (firstPtr.y + secondPtr.y) / 2.0;
    radii[i] = coordinate.coordinate_distance(firstPtr, midpointPtr);
  }
  void initEdges(coordinate[] coordinates, int numCoordinate) {
    numEdge = ((numCoordinate * (numCoordinate - 1)) / 2);
    for (int e = 0; e < numEdge; e++) {
      setEdge(e);
    }
  }
static int element_compare (element aElementPtr, element bElementPtr) {
  int aNumCoordinate = aElementPtr.numCoordinate;
  int bNumCoordinate = bElementPtr.numCoordinate;
  coordinate aCoordinates[] = aElementPtr.coordinates;
  coordinate bCoordinates[] = bElementPtr.coordinates;
  if (aNumCoordinate < bNumCoordinate) {
    return -1;
  } else if (aNumCoordinate > bNumCoordinate) {
    return 1;
  }
  for (int i = 0; i < aNumCoordinate; i++) {
    int compareCoordinate =
      coordinate.coordinate_compare(aCoordinates[i], bCoordinates[i]);
    if (compareCoordinate != 0) {
      return compareCoordinate;
    }
  }
  return 0;
}
  int element_listCompare (Object aPtr, Object bPtr) {
    element aElementPtr = (element)aPtr;
    element bElementPtr = (element)bPtr;
    return element_compare(aElementPtr, bElementPtr);
  }
  static int element_mapCompare(Object aPtr, Object bPtr) {
    element aElementPtr = (element)(((edge)aPtr).firstPtr);
    element bElementPtr = (element)(((edge)bPtr).firstPtr);
    return element_compare(aElementPtr, bElementPtr);
  }
  double angleConstraint;
  public element(coordinate[] coordinates, int numCoordinate, double angle) {
    this.circumCenter=new coordinate();
    this.coordinates=new coordinate[3];
    this.midpoints=new coordinate[3];
    this.radii=new double[3];
    this.edges=new edge[3];
    for (int i = 0; i < 3; i++) {
      this.midpoints[i] = new coordinate();
      this.edges[i]=new edge();
    }
    for (int i = 0; i < numCoordinate; i++) {
      this.coordinates[i] = coordinates[i];
    }
    this.numCoordinate = numCoordinate;
    this.angleConstraint=angle;
    minimizeCoordinates();
    checkAngles();
    calculateCircumCircle();
    initEdges(coordinates, numCoordinate);
    neighborListPtr = new List_t(0);
    isGarbage = false;
    isReferenced = false;
  }
  int element_getNumEdge() {
    return numEdge;
  }
  edge element_getEdge(int i) {
    if (i < 0 || i > numEdge)
      return null;
    return edges[i];
  }
  static int compareEdge(edge aEdgePtr, edge bEdgePtr) {
    int diffFirst = coordinate.coordinate_compare((coordinate)aEdgePtr.firstPtr,
                                        (coordinate)bEdgePtr.firstPtr);
    return ((diffFirst != 0) ?
            (diffFirst) :
            (coordinate.coordinate_compare((coordinate)aEdgePtr.secondPtr,
                                (coordinate)bEdgePtr.secondPtr)));
  }
  int element_listCompareEdge (Object aPtr, Object bPtr) {
    edge aEdgePtr = (edge)(aPtr);
    edge bEdgePtr = (edge)(bPtr);
    return compareEdge(aEdgePtr, bEdgePtr);
  }
  static int element_mapCompareEdge (edge aPtr, edge bPtr) {
    edge aEdgePtr = (edge)(aPtr.firstPtr);
    edge bEdgePtr = (edge)(bPtr.firstPtr);
    return compareEdge(aEdgePtr, bEdgePtr);
  }
  int element_heapCompare (Object aPtr, Object bPtr) {
    element aElementPtr = (element)aPtr;
    element bElementPtr = (element)bPtr;
    if (aElementPtr.encroachedEdgePtr!=null) {
      if (bElementPtr.encroachedEdgePtr!=null) {
 return 0;
      } else {
 return 1;
      }
    }
    if (bElementPtr.encroachedEdgePtr!=null) {
      return -1;
    }
    return 0;
  }
  boolean element_isInCircumCircle(coordinate coordinatePtr) {
    double distance = coordinate.coordinate_distance(coordinatePtr, circumCenter);
    return distance <= circumRadius;
  }
  boolean isEncroached() {
    return encroachedEdgePtr!=null;
  }
  void element_clearEncroached() {
    encroachedEdgePtr = null;
  }
  edge element_getEncroachedPtr() {
    return encroachedEdgePtr;
  }
  boolean element_isSkinny() {
    return isSkinny;
  }
  boolean element_isBad() {
    return (isEncroached() || element_isSkinny());
  }
  boolean element_isReferenced () {
    return isReferenced;
  }
  void element_setIsReferenced(boolean status) {
    isReferenced= status;
  }
  public boolean element_isGarbage() {
    return isGarbage;
  }
  void element_setIsGarbage(boolean status) {
    isGarbage=status;
  }
  void element_addNeighbor(element neighborPtr) {
    neighborListPtr.insert(neighborPtr);
  }
  List_t element_getNeighborListPtr () {
    return neighborListPtr;
  }
  static edge element_getCommonEdge (element aElementPtr, element bElementPtr) {
    edge aEdges[] = aElementPtr.edges;
    edge bEdges[] = bElementPtr.edges;
    int aNumEdge = aElementPtr.numEdge;
    int bNumEdge = bElementPtr.numEdge;
    for (int a = 0; a < aNumEdge; a++) {
      edge aEdgePtr = aEdges[a];
      for (int b = 0; b < bNumEdge; b++) {
 edge bEdgePtr = bEdges[b];
 if (compareEdge(aEdgePtr, bEdgePtr) == 0) {
   return aEdgePtr;
 }
      }
    }
    return null;
  }
  coordinate element_getNewPoint() {
    if (encroachedEdgePtr!=null) {
      for (int e = 0; e < numEdge; e++) {
 if (compareEdge(encroachedEdgePtr, edges[e]) == 0) {
   return midpoints[e];
 }
      }
      ;
    }
    return circumCenter;
  }
  boolean element_checkAngles() {
    if (numCoordinate == 3) {
      for (int i = 0; i < 3; i++) {
 double angle = coordinate.coordinate_angle(coordinates[i],
     coordinates[(i + 1) % 3],
     coordinates[(i + 2) % 3]);
 if (angle < angleConstraint) {
   return false;
 }
      }
    }
    return true;
  }
  void element_print() {
    for (int c = 0; c < numCoordinate; c++) {
      coordinates[c].coordinate_print();
      System.out.print(" ");
    }
  }
  void element_printEdge (edge edgePtr) {
    ((coordinate)edgePtr.firstPtr).coordinate_print();
    System.out.println(" -> ");
    ((coordinate)edgePtr.secondPtr).coordinate_print();
  }
  void element_printAngles() {
    if (numCoordinate == 3) {
      for (int i = 0; i < 3; i++) {
 double angle = coordinate.coordinate_angle(coordinates[i],
     coordinates[(i + 1) % 3],
     coordinates[(i + 2) % 3]);
 System.out.println(angle);
      }
    }
  }
}
