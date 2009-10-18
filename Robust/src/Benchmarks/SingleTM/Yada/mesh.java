/* =============================================================================
 *
 * mesh.c
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

public class mesh {
  element rootElementPtr;
  Queue_t initBadQueuePtr;
  int size;
  RBTree boundarySetPtr;
  double angle;
/* =============================================================================
 * mesh_alloc
 * =============================================================================
 */
  public mesh(double angle) {
    this.angle=angle;
    rootElementPtr = null;
    initBadQueuePtr = new Queue_t(-1);
    size = 0;
    boundarySetPtr = new RBTree(0);
  }


  /* =============================================================================
   * TMmesh_insert
   * =============================================================================
   */
  void TMmesh_insert (element elementPtr, avltree edgeMapPtr) {
    /*
     * Assuming fully connected graph, we just need to record one element.
     * The root element is not needed for the actual refining, but rather
     * for checking the validity of the final mesh.
     */
    if (rootElementPtr==null) {
      rootElementPtr=elementPtr;
  }

    /*
     * Record existence of each of this element's edges
     */
  int numEdge = elementPtr.element_getNumEdge();
  for (int i = 0; i < numEdge; i++) {
    edge edgePtr = elementPtr.element_getEdge(i);
    if (!edgeMapPtr.contains(edgePtr)) {
      /* Record existance of this edge */
      boolean isSuccess =
	edgeMapPtr.insert(edgePtr, elementPtr);
      yada.Assert(isSuccess);
    } else {
      /*
       * Shared edge; update each element's neighborList
       */
      boolean isSuccess;
      element sharerPtr = (element)edgeMapPtr.find(edgePtr);
      yada.Assert(sharerPtr!=null); /* cannot be shared by >2 elements */
      elementPtr.element_addNeighbor(sharerPtr);
      sharerPtr.element_addNeighbor(elementPtr);
      isSuccess = edgeMapPtr.remove(edgePtr);
      yada.Assert(isSuccess);
      isSuccess = edgeMapPtr.insert(edgePtr,
			      null); /* marker to check >2 sharers */
      yada.Assert(isSuccess);
    }
  }

  /*
   * Check if really encroached
   */
  
  edge encroachedPtr = elementPtr.element_getEncroachedPtr();
  if (encroachedPtr!=null) {
    if (!boundarySetPtr.contains(encroachedPtr)) {
      elementPtr.element_clearEncroached();
    }
  }
}


/* =============================================================================
 * TMmesh_remove
 * =============================================================================
 */
public void TMmesh_remove(element elementPtr) {
  yada.Assert(!elementPtr.element_isGarbage());

  /*
   * If removing root, a new root is selected on the next mesh_insert, which
   * always follows a call a mesh_remove.
   */
  if (rootElementPtr == elementPtr) {
    rootElementPtr=null;
  }
    
  /*
   * Remove from neighbors
   */
  
  List_t neighborListPtr = elementPtr.element_getNeighborListPtr();
  List_Node it=neighborListPtr.head;

  while (it.nextPtr!=null) {
    it=it.nextPtr;
    element neighborPtr = (element)it.dataPtr;
    List_t neighborNeighborListPtr = neighborPtr.element_getNeighborListPtr();
    boolean status = neighborNeighborListPtr.remove(elementPtr);
    yada.Assert(status);
  }

  elementPtr.element_setIsGarbage(true);
}


/* =============================================================================
 * TMmesh_insertBoundary
 * =============================================================================
 */
boolean TMmesh_insertBoundary(edge boundaryPtr) {
  return boundarySetPtr.insert(boundaryPtr,null);
}


/* =============================================================================
 * TMmesh_removeBoundary
 * =============================================================================
 */
boolean TMmesh_removeBoundary(edge boundaryPtr) {
  return boundarySetPtr.deleteObjNode(boundaryPtr);
}


/* =============================================================================
 * createElement
 * =============================================================================
 */
  void createElement(coordinate[] coordinates, int numCoordinate,
			   avltree edgeMapPtr) {
  element elementPtr = new element(coordinates, numCoordinate, angle);
  yada.Assert(elementPtr!=null);
  
  if (numCoordinate == 2) {
    edge boundaryPtr = elementPtr.element_getEdge(0);
    boolean status = boundarySetPtr.insert(boundaryPtr, null);
    yada.Assert(status);
  }
  
  TMmesh_insert(elementPtr, edgeMapPtr);
  
  if (elementPtr.element_isBad()) {
    boolean status = initBadQueuePtr.queue_push(elementPtr);
    yada.Assert(status);
  }
}


/* =============================================================================
 * mesh_read
 *
 * Returns number of elements read from file
 *
 * Refer to http://www.cs.cmu.edu/~quake/triangle.html for file formats.
 * =============================================================================
 */
int mesh_read(String fileNamePrefix) {
    int i;
    int numElement = 0;
    avltree edgeMapPtr = new avltree(0);

    /*
     * Read .node file
     */
    
    String fileName=fileNamePrefix+".node";
    FileInputStream inputFile = new FileInputStream(fileName);
    bytereader br=new bytereader(inputFile);
    int numEntry=br.getInt();
    int numDimension=br.getInt();
    br.jumptonextline();
    yada.Assert(numDimension == 2); /* must be 2-D */
    int numCoordinate = numEntry + 1; /* numbering can start from 1 */
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
    yada.Assert(i == numEntry);
    inputFile.close();
    
    /*
     * Read .poly file, which contains boundary segments
     */
    fileName=fileNamePrefix+".poly";
    inputFile = new FileInputStream(fileName);
    br=new bytereader(inputFile);
    numEntry=br.getInt();
    numDimension=br.getInt();
    br.jumptonextline();
    yada.Assert(numEntry == 0); /* .node file used for vertices */
    yada.Assert(numDimension == 2); /* must be edge */
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
      yada.Assert(a >= 0 && a < numCoordinate);
      yada.Assert(b >= 0 && b < numCoordinate);
      insertCoordinates[0] = coordinates[a];
      insertCoordinates[1] = coordinates[b];
      createElement(insertCoordinates, 2, edgeMapPtr);
    }
    yada.Assert(i == numEntry);
    numElement += numEntry;
    inputFile.close();

    /*
     * Read .ele file, which contains triangles
     */
    fileName=fileNamePrefix+".ele";
    inputFile = new FileInputStream(fileName);
    br=new bytereader(inputFile);
    numEntry=br.getInt();
    numDimension=br.getInt();
    br.jumptonextline();
    yada.Assert(numDimension == 3); /* must be triangle */
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
      br.jumptonextline();
      yada.Assert(a >= 0 && a < numCoordinate);
      yada.Assert(b >= 0 && b < numCoordinate);
      yada.Assert(c >= 0 && c < numCoordinate);
      insertCoordinates[0] = coordinates[a];
      insertCoordinates[1] = coordinates[b];
      insertCoordinates[2] = coordinates[c];
      createElement(insertCoordinates, 3, edgeMapPtr);
    }
    yada.Assert(i == numEntry);
    numElement += numEntry;
    inputFile.close();
    return numElement;
}


/* =============================================================================
 * mesh_getBad
 * -- Returns NULL if none
 * =============================================================================
 */
element mesh_getBad() {
  return (element)initBadQueuePtr.queue_pop();
}


/* =============================================================================
 * mesh_shuffleBad
 * =============================================================================
 */
void mesh_shuffleBad (Random randomPtr) {
  initBadQueuePtr.queue_shuffle(randomPtr);
}


/* =============================================================================
 * mesh_check
 * =============================================================================
 */
boolean mesh_check(int expectedNumElement) {
    int numBadTriangle = 0;
    int numFalseNeighbor = 0;
    int numElement = 0;

    System.out.println("Checking final mesh:");
    
    Queue_t searchQueuePtr = new Queue_t(-1);
    avltree visitedMapPtr = new avltree(1);

    /*
     * Do breadth-first search starting from rootElementPtr
     */
    yada.Assert(rootElementPtr!=null);
    searchQueuePtr.queue_push(rootElementPtr);
    while (!searchQueuePtr.queue_isEmpty()) {
        List_t neighborListPtr;

        element currentElementPtr = (element)searchQueuePtr.queue_pop();
        if (visitedMapPtr.contains(currentElementPtr)) {
            continue;
        }
        boolean isSuccess = visitedMapPtr.insert(currentElementPtr, null);
        yada.Assert(isSuccess);
        if (!currentElementPtr.checkAngles()) {
            numBadTriangle++;
        }
        neighborListPtr = currentElementPtr.element_getNeighborListPtr();

        List_Node it=neighborListPtr.head;
        while (it.nextPtr!=null) {
	  it=it.nextPtr;
	  element neighborElementPtr =
	    (element)it.dataPtr;
	  /*
	   * Continue breadth-first search
	   */
	  if (!visitedMapPtr.contains(neighborElementPtr)) {
	    isSuccess = searchQueuePtr.queue_push(neighborElementPtr);
	    yada.Assert(isSuccess);
	  }
        } /* for each neighbor */
	
        numElement++;

    } /* breadth-first search */

    System.out.println("Number of elements      = "+ numElement);
    System.out.println("Number of bad triangles = "+ numBadTriangle);

    return (!(numBadTriangle > 0 ||
	      numFalseNeighbor > 0 ||
	      numElement != expectedNumElement));
}
}