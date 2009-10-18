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

/* =============================================================================
 * mesh_alloc
 * =============================================================================
 */
  public mesh() {
    rootElementPtr = null;
    initBadQueuePtr = new Queue_t(-1);
    size = 0;
    boundarySetPtr = new RBTree(null, element_listCompareEdge);
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
    if (!MAP_CONTAINS(edgeMapPtr, edgePtr)) {
      /* Record existance of this edge */
      boolean isSuccess =
	PMAP_INSERT(edgeMapPtr, edgePtr, elementPtr);
      yada.Assert(isSuccess);
    } else {
      /*
       * Shared edge; update each element's neighborList
       */
      boolean isSuccess;
      element sharerPtr = (element)MAP_FIND(edgeMapPtr, edgePtr);
      yada.Assert(sharerPtr!=null); /* cannot be shared by >2 elements */
      elementPtr.element_addNeighbor(sharerPtr);
      sharerPtr.element_addNeighbor(elementPtr);
      isSuccess = PMAP_REMOVE(edgeMapPtr, edgePtr);
      yada.Assert(isSuccess);
      isSuccess = PMAP_INSERT(edgeMapPtr,
			      edgePtr,
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
      element_clearEncroached(elementPtr);
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

  elementPtr.element_isGarbage(true);
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
  return boundarySetPtr.remove(boundaryPtr);
}


/* =============================================================================
 * createElement
 * =============================================================================
 */
static void createElement (coordinate coordinates,
               int numCoordinate,
			   avltree edgeMapPtr) {
    element elementPtr = new element(coordinates, numCoordinate);
    yada.Assert(elementPtr!=null);

    if (numCoordinate == 2) {
        edge boundaryPtr = elementPtr.element_getEdge(0);
        boolean status = boundarySetPtr.insert(boundaryPtr, null);
        yada.Assert(status);
    }

    mesh_insert(elementPtr, edgeMapPtr);

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
    FILE inputFile;
    coordinate coordinates;
    char fileName[]=new char[256];
    int fileNameSize = sizeof(fileName) / sizeof(fileName[0]);
    char inputBuff[]=new char[256];
    int inputBuffSize = sizeof(inputBuff) / sizeof(inputBuff[0]);
    int numEntry;
    int numDimension;
    int numCoordinate;
    int i;
    int numElement = 0;

    avltree edgeMapPtr = new avltree(0);

    /*
     * Read .node file
     */
    snprintf(fileName, fileNameSize, "%s.node", fileNamePrefix);
    inputFile = fopen(fileName, "r");
    yada.Assert(inputFile);
    fgets(inputBuff, inputBuffSize, inputFile);
    sscanf(inputBuff, "%li %li", numEntry, numDimension);
    yada.Assert(numDimension == 2); /* must be 2-D */
    numCoordinate = numEntry + 1; /* numbering can start from 1 */
    coordinates = new coordinate[numCoordinate];
    for (i = 0; i < numEntry; i++) {
        int id;
        double x;
        double y;
        if (!fgets(inputBuff, inputBuffSize, inputFile)) {
            break;
        }
        if (inputBuff[0] == '#') {
            continue; /* TODO: handle comments correctly */
        }
        sscanf(inputBuff, "%li %lf %lf", id, x, y);
        coordinates[id].x = x;
        coordinates[id].y = y;
    }
    yada.Assert(i == numEntry);
    fclose(inputFile);

    /*
     * Read .poly file, which contains boundary segments
     */
    snprintf(fileName, fileNameSize, "%s.poly", fileNamePrefix);
    inputFile = fopen(fileName, "r");
    yada.Assert(inputFile);
    fgets(inputBuff, inputBuffSize, inputFile);
    sscanf(inputBuff, "%li %li", numEntry, numDimension);
    yada.Assert(numEntry == 0); /* .node file used for vertices */
    yada.Assert(numDimension == 2); /* must be edge */
    fgets(inputBuff, inputBuffSize, inputFile);
    sscanf(inputBuff, "%li", numEntry);
    for (i = 0; i < numEntry; i++) {
        int id;
        int a;
        int b;
        coordinate insertCoordinates=new coordinate[2];
        if (!fgets(inputBuff, inputBuffSize, inputFile)) {
            break;
        }
        if (inputBuff[0] == '#') {
            continue; /* TODO: handle comments correctly */
        }
        sscanf(inputBuff, "%li %li %li", id, a, b);
        yada.Assert(a >= 0 && a < numCoordinate);
        yada.Assert(b >= 0 && b < numCoordinate);
        insertCoordinates[0] = coordinates[a];
        insertCoordinates[1] = coordinates[b];
        createElement(meshPtr, insertCoordinates, 2, edgeMapPtr);
    }
    yada.Assert(i == numEntry);
    numElement += numEntry;
    fclose(inputFile);

    /*
     * Read .ele file, which contains triangles
     */
    snprintf(fileName, fileNameSize, "%s.ele", fileNamePrefix);
    inputFile = fopen(fileName, "r");
    yada.Assert(inputFile);
    fgets(inputBuff, inputBuffSize, inputFile);
    sscanf(inputBuff, "%li %li", numEntry, numDimension);
    yada.Assert(numDimension == 3); /* must be triangle */
    for (i = 0; i < numEntry; i++) {
        int id;
        int a;
        int b;
        int c;
        coordinate insertCoordinates[]=new coordinate[3];
        if (!fgets(inputBuff, inputBuffSize, inputFile)) {
            break;
        }
        if (inputBuff[0] == '#') {
            continue; /* TODO: handle comments correctly */
        }
        sscanf(inputBuff, "%li %li %li %li", id, a, b, c);
        yada.Assert(a >= 0 && a < numCoordinate);
        yada.Assert(b >= 0 && b < numCoordinate);
        yada.Assert(c >= 0 && c < numCoordinate);
        insertCoordinates[0] = coordinates[a];
        insertCoordinates[1] = coordinates[b];
        insertCoordinates[2] = coordinates[c];
        createElement(meshPtr, insertCoordinates, 3, edgeMapPtr);
    }
    yada.Assert(i == numEntry);
    numElement += numEntry;
    fclose(inputFile);

    free(coordinates);
    MAP_FREE(edgeMapPtr);

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

        element currentElementPtr = (element)queue_pop(searchQueuePtr);
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
	    boolean isSuccess = searchQueuePtr.queue_push(neighborElementPtr);
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