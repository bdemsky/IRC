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
    SET_T* boundarySetPtr;

/* =============================================================================
 * mesh_alloc
 * =============================================================================
 */
  public mesh() {
    rootElementPtr = null;
    initBadQueuePtr = new Queue_t(-1);
    size = 0;
    boundarySetPtr = SET_ALLOC(null, &element_listCompareEdge);
  }


  /* =============================================================================
   * TMmesh_insert
   * =============================================================================
   */
  void TMmesh_insert (element elementPtr, MAP_T edgeMapPtr) {
    /*
     * Assuming fully connected graph, we just need to record one element.
     * The root element is not needed for the actual refining, but rather
     * for checking the validity of the final mesh.
     */
    if (rootElementPtr==null) {
      rootElementPtr=elementPtr);
  }

    /*
     * Record existence of each of this element's edges
     */
  int numEdge = elementPtr.element_getNumEdge();
  for (int i = 0; i < numEdge; i++) {
    edge edgePtr = elementPtr.element_getEdge(i);
    if (!MAP_CONTAINS(edgeMapPtr, (void*)edgePtr)) {
      /* Record existance of this edge */
      boolean isSuccess =
	PMAP_INSERT(edgeMapPtr, (void*)edgePtr, (void*)elementPtr);
      assert(isSuccess);
    } else {
      /*
       * Shared edge; update each element's neighborList
       */
      boolean isSuccess;
      element sharerPtr = (element_t*)MAP_FIND(edgeMapPtr, edgePtr);
      assert(sharerPtr); /* cannot be shared by >2 elements */
      elementPtr.element_addNeighbor(sharerPtr);
      sharerPtr.element_addNeighbor(elementPtr);
      isSuccess = PMAP_REMOVE(edgeMapPtr, edgePtr);
      assert(isSuccess);
      isSuccess = PMAP_INSERT(edgeMapPtr,
			      edgePtr,
			      NULL); /* marker to check >2 sharers */
      assert(isSuccess);
    }
  }

  /*
   * Check if really encroached
   */
  
  edge encroachedPtr = elementPtr.element_getEncroachedPtr();
  if (encroachedPtr!=null) {
    if (!TMSET_CONTAINS(meshPtr.boundarySetPtr, encroachedPtr)) {
      element_clearEncroached(elementPtr);
    }
  }
}


/* =============================================================================
 * TMmesh_remove
 * =============================================================================
 */
public void TMmesh_remove(element elementPtr) {
  assert(!elementPtr.element_isGarbage());

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
  list_iter_t it;
  List neighborListPtr = elementPtr.element_getNeighborListPtr();
  TMLIST_ITER_RESET(&it, neighborListPtr);
  while (TMLIST_ITER_HASNEXT(&it, neighborListPtr)) {
      element neighborPtr = (element)TMLIST_ITER_NEXT(&it, neighborListPtr);
      List_t neighborNeighborListPtr = neighborPtr.element_getNeighborListPtr();
      boolean status = neighborNeighborListPtr.remove(elementPtr);
      assert(status);
  }

  elementPtr.element_isGarbage(true);
}


/* =============================================================================
 * TMmesh_insertBoundary
 * =============================================================================
 */
boolean TMmesh_insertBoundary (meshPtr, edge boundaryPtr) {
  return TMSET_INSERT(boundarySetPtr, boundaryPtr);
}


/* =============================================================================
 * TMmesh_removeBoundary
 * =============================================================================
 */
boolean TMmesh_removeBoundary (meshPtr, edge boundaryPtr) {
  return TMSET_REMOVE(boundarySetPtr, boundaryPtr);
}


/* =============================================================================
 * createElement
 * =============================================================================
 */
static void createElement (coordinate coordinates,
               int numCoordinate,
			   MAP_T edgeMapPtr) {
    element elementPtr = new element(coordinates, numCoordinate);
    assert(elementPtr);

    if (numCoordinate == 2) {
        edge boundaryPtr = elementPtr.element_getEdge(0);
        boolean status = SET_INSERT(boundarySetPtr, boundaryPtr);
        assert(status);
    }

    mesh_insert(elementPtr, edgeMapPtr);

    if (elementPtr.element_isBad()) {
        boolean status = initBadQueuePtr.queue_push(elementPtr);
        assert(status);
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
    FILE* inputFile;
    coordinate_t* coordinates;
    char fileName[256];
    int fileNameSize = sizeof(fileName) / sizeof(fileName[0]);
    char inputBuff[256];
    int inputBuffSize = sizeof(inputBuff) / sizeof(inputBuff[0]);
    int numEntry;
    int numDimension;
    int numCoordinate;
    int i;
    int numElement = 0;

    MAP_T* edgeMapPtr = MAP_ALLOC(NULL, &element_mapCompareEdge);
    assert(edgeMapPtr);

    /*
     * Read .node file
     */
    snprintf(fileName, fileNameSize, "%s.node", fileNamePrefix);
    inputFile = fopen(fileName, "r");
    assert(inputFile);
    fgets(inputBuff, inputBuffSize, inputFile);
    sscanf(inputBuff, "%li %li", &numEntry, &numDimension);
    assert(numDimension == 2); /* must be 2-D */
    numCoordinate = numEntry + 1; /* numbering can start from 1 */
    coordinates = (coordinate_t*)malloc(numCoordinate * sizeof(coordinate_t));
    assert(coordinates);
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
        sscanf(inputBuff, "%li %lf %lf", &id, &x, &y);
        coordinates[id].x = x;
        coordinates[id].y = y;
    }
    assert(i == numEntry);
    fclose(inputFile);

    /*
     * Read .poly file, which contains boundary segments
     */
    snprintf(fileName, fileNameSize, "%s.poly", fileNamePrefix);
    inputFile = fopen(fileName, "r");
    assert(inputFile);
    fgets(inputBuff, inputBuffSize, inputFile);
    sscanf(inputBuff, "%li %li", &numEntry, &numDimension);
    assert(numEntry == 0); /* .node file used for vertices */
    assert(numDimension == 2); /* must be edge */
    fgets(inputBuff, inputBuffSize, inputFile);
    sscanf(inputBuff, "%li", &numEntry);
    for (i = 0; i < numEntry; i++) {
        int id;
        int a;
        int b;
        coordinate_t insertCoordinates[2];
        if (!fgets(inputBuff, inputBuffSize, inputFile)) {
            break;
        }
        if (inputBuff[0] == '#') {
            continue; /* TODO: handle comments correctly */
        }
        sscanf(inputBuff, "%li %li %li", &id, &a, &b);
        assert(a >= 0 && a < numCoordinate);
        assert(b >= 0 && b < numCoordinate);
        insertCoordinates[0] = coordinates[a];
        insertCoordinates[1] = coordinates[b];
        createElement(meshPtr, insertCoordinates, 2, edgeMapPtr);
    }
    assert(i == numEntry);
    numElement += numEntry;
    fclose(inputFile);

    /*
     * Read .ele file, which contains triangles
     */
    snprintf(fileName, fileNameSize, "%s.ele", fileNamePrefix);
    inputFile = fopen(fileName, "r");
    assert(inputFile);
    fgets(inputBuff, inputBuffSize, inputFile);
    sscanf(inputBuff, "%li %li", &numEntry, &numDimension);
    assert(numDimension == 3); /* must be triangle */
    for (i = 0; i < numEntry; i++) {
        int id;
        int a;
        int b;
        int c;
        coordinate_t insertCoordinates[3];
        if (!fgets(inputBuff, inputBuffSize, inputFile)) {
            break;
        }
        if (inputBuff[0] == '#') {
            continue; /* TODO: handle comments correctly */
        }
        sscanf(inputBuff, "%li %li %li %li", &id, &a, &b, &c);
        assert(a >= 0 && a < numCoordinate);
        assert(b >= 0 && b < numCoordinate);
        assert(c >= 0 && c < numCoordinate);
        insertCoordinates[0] = coordinates[a];
        insertCoordinates[1] = coordinates[b];
        insertCoordinates[2] = coordinates[c];
        createElement(meshPtr, insertCoordinates, 3, edgeMapPtr);
    }
    assert(i == numEntry);
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
  return (element)queue_pop(initBadQueuePtr);
}


/* =============================================================================
 * mesh_shuffleBad
 * =============================================================================
 */
void mesh_shuffleBad (Random randomPtr) {
  queue_shuffle(initBadQueuePtr, randomPtr);
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
    assert(searchQueuePtr);
    MAP_T visitedMapPtr = MAP_ALLOC(NULL, &element_mapCompare);
    assert(visitedMapPtr);

    /*
     * Do breadth-first search starting from rootElementPtr
     */
    assert(rootElementPtr!=null);
    searchQueuePtr.queue_push(rootElementPtr);
    while (!searchQueuePtr.queue_isEmpty()) {
        list_iter_t it;
        List_t neighborListPtr;

        element currentElementPtr = (element)queue_pop(searchQueuePtr);
        if (MAP_CONTAINS(visitedMapPtr, (void*)currentElementPtr)) {
            continue;
        }
        boolean isSuccess = MAP_INSERT(visitedMapPtr, (void*)currentElementPtr, NULL);
        assert(isSuccess);
        if (!currentElementPtr.checkAngles()) {
            numBadTriangle++;
        }
        neighborListPtr = currentElementPtr.element_getNeighborListPtr();

        list_iter_reset(&it, neighborListPtr);
        while (list_iter_hasNext(&it, neighborListPtr)) {
            element neighborElementPtr =
                (element)list_iter_next(&it, neighborListPtr);
            /*
             * Continue breadth-first search
             */
            if (!MAP_CONTAINS(visitedMapPtr, (void*)neighborElementPtr)) {
                boolean isSuccess = searchQueuePtr.queue_push(neighborElementPtr);
                assert(isSuccess);
            }
        } /* for each neighbor */

        numElement++;

    } /* breadth-first search */

    System.out.println("Number of elements      = "+ numElement);
    System.out.println("Number of bad triangles = "+ numBadTriangle);

    MAP_FREE(visitedMapPtr);

    return (!(numBadTriangle > 0 ||
	      numFalseNeighbor > 0 ||
	      numElement != expectedNumElement));
}
