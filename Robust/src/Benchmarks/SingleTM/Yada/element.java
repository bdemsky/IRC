/* =============================================================================
 *
 * element.c
 *
 * =============================================================================
 *
 * Copyright (C) Stanford University, 2006.  All Rights Reserved.
 * Author: Chi Cao Minh
 *
 * =============================================================================
 *
 * For the license of bayes/sort.h and bayes/sort.c, please see the header
 * of the files.
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of kmeans, please see kmeans/LICENSE.kmeans
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of ssca2, please see ssca2/COPYRIGHT
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of lib/mt19937ar.c and lib/mt19937ar.h, please see the
 * header of the files.
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of lib/rbtree.h and lib/rbtree.c, please see
 * lib/LEGALNOTICE.rbtree and lib/LICENSE.rbtree
 * 
 * ------------------------------------------------------------------------
 * 
 * Unless otherwise noted, the following license applies to STAMP files:
 * 
 * Copyright (c) 2007, Stanford University
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 * 
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 * 
 *     * Neither the name of Stanford University nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY STANFORD UNIVERSITY ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL STANFORD UNIVERSITY BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 *
 * =============================================================================
 */

public class element {
  coordinate coordinates[];
  int numCoordinate;
  coordinate circumCenter;
  double circumRadius;
  double minAngle;
  edge edges[];
  int numEdge;
  coordinate midpoints[]; /* midpoint of each edge */
  double radii[];           /* half of edge length */
  edge encroachedEdgePtr; /* opposite obtuse angle */
  boolean isSkinny;
  List_t neighborListPtr;
  boolean isGarbage;
  boolean isReferenced;



/* =============================================================================
 * minimizeCoordinates
 * -- put smallest coordinate in position 0
 * =============================================================================
 */
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


/* =============================================================================
 * checkAngles
 * -- Sets isSkinny to TRUE if the angle constraint is not met
 * =============================================================================
 */
  void checkAngles() {
    minAngle = 180.0;
    yada.Assert(numCoordinate == 2 || numCoordinate == 3);
    isReferenced = false;
    isSkinny = false;
    encroachedEdgePtr = null;

    if (numCoordinate == 3) {
      for (int i = 0; i < 3; i++) {
	double angle = coordinate.coordinate_angle(coordinates[i],
						   coordinates[(i + 1) % 3],
						   coordinates[(i + 2) % 3]);
	yada.Assert(angle > 0.0);
	yada.Assert(angle < 180.0);
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
      yada.Assert(minAngle < 180.0);
    }
  }


/* =============================================================================
 * calculateCircumCenter
 *
 * Given three points A(ax,ay), B(bx,by), C(cx,cy), circumcenter R(rx,ry):
 *
 *              |                         |
 *              | by - ay   (||b - a||)^2 |
 *              |                         |
 *              | cy - ay   (||c - a||)^2 |
 *              |                         |
 *   rx = ax - -----------------------------
 *                   |                   |
 *                   | bx - ax   by - ay |
 *               2 * |                   |
 *                   | cx - ax   cy - ay |
 *                   |                   |
 *
 *              |                         |
 *              | bx - ax   (||b - a||)^2 |
 *              |                         |
 *              | cx - ax   (||c - a||)^2 |
 *              |                         |
 *   ry = ay + -----------------------------
 *                   |                   |
 *                   | bx - ax   by - ay |
 *               2 * |                   |
 *                   | cx - ax   cy - ay |
 *                   |                   |
 *
 * =============================================================================
 */
  void calculateCircumCircle() {
    coordinate circumCenterPtr = this.circumCenter;
    yada.Assert(numCoordinate == 2 || numCoordinate == 3);

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
      yada.Assert(Math.fabs(denominator) > 2.2250738585072014e-308); /* make sure not colinear */
      circumCenterPtr.x = rx;
      circumCenterPtr.y = ry;
    }

    circumRadius = coordinate.coordinate_distance(circumCenterPtr,
						  coordinates[0]);
  }


/* =============================================================================
 * setEdge
 *
  * Note: Makes pairPtr sorted; i.e., coordinate_compare(first, second) < 0
 * =============================================================================
 */
  public void setEdge(int i) {
    coordinate firstPtr = coordinates[i];
    coordinate secondPtr = coordinates[(i + 1) % numCoordinate];
    
    edge edgePtr = edges[i];
    int cmp = coordinate.coordinate_compare(firstPtr, secondPtr);
    yada.Assert(cmp != 0);
    if (cmp < 0) {
      edgePtr.firstPtr  = firstPtr;
      edgePtr.secondPtr = secondPtr;
    } else {
      edgePtr.firstPtr  = secondPtr;
      edgePtr.secondPtr = firstPtr;
    }

    coordinate midpointPtr = midpoints[i];
    midpointPtr.x = (firstPtr.x + secondPtr.x) / 2.0;
    midpointPtr.y = (firstPtr.y + secondPtr.y) / 2.0;

    radii[i] = coordinate.coordinate_distance(firstPtr, midpointPtr);
  }


/* =============================================================================
 * initEdges
 * =============================================================================
 */
  void initEdges(coordinate[] coordinates, int numCoordinate) {
    numEdge = ((numCoordinate * (numCoordinate - 1)) / 2);
    
    for (int e = 0; e < numEdge; e++) {
      setEdge(e);
    }
  }


/* =============================================================================
 * element_compare
 * =============================================================================
 */
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


/* =============================================================================
 * element_listCompare
 *
 * For use in list_t
 * =============================================================================
 */
  int element_listCompare (Object aPtr, Object  bPtr) {
    element aElementPtr = (element)aPtr;
    element bElementPtr = (element)bPtr;
    
    return element_compare(aElementPtr, bElementPtr);
  }


/* =============================================================================
 * element_mapCompare
 *
 * For use in MAP_T
 * =============================================================================
 */
  static int element_mapCompare(Object aPtr, Object bPtr) {
    element aElementPtr = (element)(((edge)aPtr).firstPtr);
    element bElementPtr = (element)(((edge)bPtr).firstPtr);
    
    return element_compare(aElementPtr, bElementPtr);
  }


  /* =============================================================================
   * TMelement_alloc
   *
   * Contains a copy of input arg 'coordinates'
   * =============================================================================
   */

  double angleConstraint;
  public element(coordinate[] coordinates, int numCoordinate, double angle) {
    this.circumCenter=new coordinate();
    this.coordinates=new coordinate[3];
    this.midpoints=new coordinate[3]; /* midpoint of each edge */
    this.radii=new double[3];           /* half of edge length */
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
    neighborListPtr = new List_t(0);//TMLIST_ALLOC(element_listCompare);
    isGarbage = false;
    isReferenced = false;
  }


/* =============================================================================
 * element_getNumEdge
 * =============================================================================
 */
  int element_getNumEdge() {
    return numEdge;
  }


/* =============================================================================
 * element_getEdge
 *
 * Returned edgePtr is sorted; i.e., coordinate_compare(first, second) < 0
 * =============================================================================
 */
  edge element_getEdge(int i) {
    if (i < 0 || i > numEdge)
      return null;
    return edges[i];
  }


/* =============================================================================
 * element_compareEdge
 * =============================================================================
 */
  static int compareEdge(edge aEdgePtr, edge bEdgePtr) {
    int diffFirst = coordinate.coordinate_compare((coordinate)aEdgePtr.firstPtr,
                                        (coordinate)bEdgePtr.firstPtr);

    return ((diffFirst != 0) ?
            (diffFirst) :
            (coordinate.coordinate_compare((coordinate)aEdgePtr.secondPtr,
                                (coordinate)bEdgePtr.secondPtr)));
  }


/* ============================================================================
 * element_listCompareEdge
 *
 * For use in list_t
 * ============================================================================
 */
  int element_listCompareEdge (Object aPtr, Object bPtr) {
    edge aEdgePtr = (edge)(aPtr);
    edge bEdgePtr = (edge)(bPtr);
    
    return compareEdge(aEdgePtr, bEdgePtr);
  }


/* =============================================================================
 * element_mapCompareEdge
 *
  * For use in MAP_T
 * =============================================================================
 */
  static int element_mapCompareEdge (edge aPtr, edge bPtr) {
    edge aEdgePtr = (edge)(aPtr.firstPtr);
    edge bEdgePtr = (edge)(bPtr.firstPtr);
    
    return compareEdge(aEdgePtr, bEdgePtr);
  }
  

/* =============================================================================
 * element_heapCompare
 *
 * For use in heap_t. Consider using minAngle instead of "do not care".
 * =============================================================================
 */
  int element_heapCompare (Object aPtr, Object bPtr) {
    element aElementPtr = (element)aPtr;
    element bElementPtr = (element)bPtr;
   
    if (aElementPtr.encroachedEdgePtr!=null) {
      if (bElementPtr.encroachedEdgePtr!=null) {
	return 0; /* do not care */
      } else {
	return 1;
      }
    }
    
    if (bElementPtr.encroachedEdgePtr!=null) {
      return -1;
    }
    
    return 0; /* do not care */
  }
  

/* =============================================================================
 * element_isInCircumCircle
 * =============================================================================
 */
  boolean element_isInCircumCircle(coordinate coordinatePtr) {
    double distance = coordinate.coordinate_distance(coordinatePtr, circumCenter);
    return distance <= circumRadius;
  }


/* =============================================================================
 * isEncroached
 * =============================================================================
 */
  boolean isEncroached() {
    return encroachedEdgePtr!=null;
  }


/* =============================================================================
 * element_setEncroached
 * =============================================================================
 */
  void element_clearEncroached() {
    encroachedEdgePtr = null;
  }


/* =============================================================================
 * element_getEncroachedPtr
 * =============================================================================
 */
  edge element_getEncroachedPtr() {
    return encroachedEdgePtr;
  }


/* =============================================================================
 * element_isSkinny
 * =============================================================================
 */
  boolean element_isSkinny() {
    return isSkinny;
  }


/* =============================================================================
 * element_isBad
 * -- Does it need to be refined?
 * =============================================================================
 */
  boolean element_isBad() {
    return (isEncroached() || element_isSkinny());
  }


/* =============================================================================
 * TMelement_isReferenced
 * -- Held by another data structure?
 * =============================================================================
 */
  boolean element_isReferenced () {
    return isReferenced;
  }


/* =============================================================================
 * TMelement_setIsReferenced
 * =============================================================================
 */
  void element_setIsReferenced(boolean status) {
    isReferenced= status;
  }


/* =============================================================================
 * element_isGarbage
 * -- Can we deallocate?
 * =============================================================================
 */

/* =============================================================================
 * TMelement_isGarbage
 * -- Can we deallocate?
 * =============================================================================
 */
  public boolean element_isGarbage() {
    return isGarbage;
  }



/* =============================================================================
 * TMelement_setIsGarbage
 * =============================================================================
 */
  void element_setIsGarbage(boolean status) {
    isGarbage=status;
  }


/* =============================================================================
 * TMelement_addNeighbor
 * =============================================================================
 */
  void element_addNeighbor(element neighborPtr) {
    neighborListPtr.insert(neighborPtr);
  }


/* =============================================================================
 * element_getNeighborListPtr
 * =============================================================================
 */
  List_t element_getNeighborListPtr () {
    return neighborListPtr;
  }


/* =============================================================================
 * element_getCommonEdge
 *
 * Returns pointer to aElementPtr's shared edge
 * =============================================================================
 */
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


/* =============================================================================
 * element_getNewPoint
 * -- Either the element is encroached or is skinny, so get the new point to add
 * =============================================================================
 */
  coordinate element_getNewPoint() {
    if (encroachedEdgePtr!=null) {
      for (int e = 0; e < numEdge; e++) {
	if (compareEdge(encroachedEdgePtr, edges[e]) == 0) {
	  return midpoints[e];
	}
      }
      yada.Assert(false);
    }
    return circumCenter;
  }


/* =============================================================================
 * element_checkAngles
 *
 * Return FALSE if minimum angle constraint not met
 * =============================================================================
 */
  boolean element_checkAngles() {
    //    double angleConstraint = global_angleConstraint;
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


/* =============================================================================
 * element_print
 * =============================================================================
 */
  void element_print() {
    for (int c = 0; c < numCoordinate; c++) {
      coordinates[c].coordinate_print();
      System.out.print(" ");
    }
  }
  

/* =============================================================================
 * element_printEdge
 * =============================================================================
 */
  void element_printEdge (edge edgePtr) {
    ((coordinate)edgePtr.firstPtr).coordinate_print();
    System.out.println(" -> ");
    ((coordinate)edgePtr.secondPtr).coordinate_print();
  }


/* =============================================================================
 * element_printAngles
 * =============================================================================
 */
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