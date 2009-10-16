/* =============================================================================
 *
 * region.c
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

public class region {
  coordinate centerCoordinate;
  Queue_t expandQueuePtr;
  List_t beforeListPtr; /* before retriangulation; list to avoid duplicates */
  List_t borderListPtr; /* edges adjacent to region; list to avoid duplicates */
  Vector_t badVectorPtr;

/* =============================================================================
 * Pregion_alloc
 * =============================================================================
 */
  public region() {
    expandQueuePtr = new Queue_t(-1);
    beforeListPtr = PLIST_ALLOC(element_listCompare);
    borderListPtr = PLIST_ALLOC(element_listCompareEdge);
    badVectorPtr = new Vector_t(1);
  }


  /* =============================================================================
   * TMaddToBadVector
   * =============================================================================
   */
  public void TMaddToBadVector(Vector_t badVectorPtr, element badElementPtr) {
    boolean status = badVectorPtr.vector_pushBack(badElementPtr);
    yada.Assert(status);
    badElementPtr.element_setIsReferenced(true);
  }


  /* =============================================================================
   * TMretriangulate
   * -- Returns net amount of elements added to mesh
   * =============================================================================
   */
  public int TMretriangulate (element elementPtr,
			      region regionPtr,
			      mesh meshPtr,
			      MAP_T edgeMapPtr) {
    Vector_t badVectorPtr = regionPtr.badVectorPtr; /* private */
    list_t beforeListPtr = regionPtr.beforeListPtr; /* private */
    list_t borderListPtr = regionPtr.borderListPtr; /* private */
    list_iter_t it;
    int numDelta = 0;
    
    yada.Assert(edgeMapPtr);
    
    coordinate centerCoordinate = elementPtr.element_getNewPoint();
    
    /*
     * Remove the old triangles
     */
    
    list_iter_reset(it, beforeListPtr);
    while (list_iter_hasNext(it, beforeListPtr)) {
      element beforeElementPtr = (element)list_iter_next(it, beforeListPtr);
      meshPtr.TMmesh_remove(beforeElementPtr);
    }
    
    numDelta -= beforeListPtr.getSize();
    
    /*
     * If segment is encroached, split it in half
     */
    
    if (elementPtr.element_getNumEdge() == 1) {
      coordinate coordinates[]=new coordinate[2];
      
      edge edgePtr = elementPtr.element_getEdge(0);
      coordinates[0] = centerCoordinate;
      
      coordinates[1] = (coordinate)(edgePtr.firstPtr);
      element aElementPtr = new element(coordinates, 2);
      yada.Assert(aElementPtr);
      meshPtr.TMmesh_insert(aElementPtr, edgeMapPtr);
      
      coordinates[1] = (coordinate)edgePtr.secondPtr;
      element bElementPtr = new element(coordinates, 2);
      yada.Assert(bElementPtr);
      meshPtr.TMmesh_insert(bElementPtr, edgeMapPtr);
      
      boolean status = meshPtr.TMmesh_removeBoundary(elementPtr.element_getEdge(0));
      yada.Assert(status);
      status = mesPtr.TMmesh_insertBoundary(aElementPtr.element_getEdge(0));
      yada.Assert(status);
      status = meshPtr.TMmesh_insertBoundary(bElementPtr.element_getEdge(0));
      yada.Assert(status);
      
      numDelta += 2;
    }
    
    /*
     * Insert the new triangles. These are contructed using the new
     * point and the two points from the border segment.
     */

    list_iter_reset(it, borderListPtr);
    while (list_iter_hasNext(it, borderListPtr)) {
      coordinate coordinates[]=new coordinates[3];
      edge borderEdgePtr = (edge)list_iter_next(it, borderListPtr);
      yada.Assert(borderEdgePtr);
      coordinates[0] = centerCoordinate;
      coordinates[1] = (coordinate)(borderEdgePtr.firstPtr);
      coordinates[2] = (coordinate)(borderEdgePtr.secondPtr);
      element afterElementPtr = new element(coordinates, 3);
      yada.Assert(afterElementPtr!=null);
      meshPtr.TMmesh_insert(afterElementPtr, edgeMapPtr);
      if (afterElementPTr.element_isBad()) {
	TMaddToBadVector(badVectorPtr, afterElementPtr);
      }
    }
    numDelta += borderListPtr.getSize();
    return numDelta;
  }
  

  /* =============================================================================
   * TMgrowRegion
   * -- Return NULL if success, else pointer to encroached boundary
   * =============================================================================
   */
  element TMgrowRegion(element centerElementPtr,
		       region regionPtr,
		       mesh meshPtr,
		       MAP_T edgeMapPtr) {
    boolean isBoundary = false;
    
    if (centerElementPtr.element_getNumEdge() == 1) {
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
      
      element currentElementPtr = expandQueuePtr.queue_pop();
      
      beforeListPtr.insert(currentElementPtr); /* no duplicates */
      List_t neighborListPtr = currentElementPtr.element_getNeighborListPtr();
      
      list_iter_t it;
      TMLIST_ITER_RESET(it, neighborListPtr);
      while (TMLIST_ITER_HASNEXT(it, neighborListPtr)) {
	element neighborElementPtr = (element)TMLIST_ITER_NEXT(it, neighborListPtr);
	neighborElementPtr.element_isGarbage(); /* so we can detect conflicts */
	if (!beforeListPtr.find(neighborElementPtr)) {
	  if (neighborElementPtr.element_isInCircumCircle(centerCoordinatePtr)) {
	    /* This is part of the region */
	    if (!isBoundary && (neighborElementPtr.element_getNumEdge() == 1)) {
	      /* Encroached on mesh boundary so split it and restart */
	      return neighborElementPtr;
	    } else {
	      /* Continue breadth-first search */
	      boolean isSuccess = expandQueuePtr.queue_push(neighborElementPtr);
	      yada.Assert(isSuccess);
	    }
	  } else {
	    /* This element borders region; save info for retriangulation */
	    edge borderEdgePtr = neighborElementPtr.element_getCommonEdge(currentElementPtr);
	    if (!borderEdgePtr) {
	      TM_RESTART();
	    }
	    borderListPtr.insert(borderEdgePtr); /* no duplicates */
	    if (!MAP_CONTAINS(edgeMapPtr, borderEdgePtr)) {
	      PMAP_INSERT(edgeMapPtr, borderEdgePtr, neighborElementPtr);
	    }
	  }
	} /* not visited before */
      } /* for each neighbor */
      
    } /* breadth-first search */
    
    return null;
  }


/* =============================================================================
 * TMregion_refine
 * -- Returns net number of elements added to mesh
 * =============================================================================
 */
  int TMregion_refine(element elementPtr, mesh meshPtr) {
    int numDelta = 0;
    MAP_T edgeMapPtr = null;
    element encroachElementPtr = null;
    
    elementPtr.element_isGarbage(); /* so we can detect conflicts */
    
    while (true) {
      edgeMapPtr = PMAP_ALLOC(NULL, element_mapCompareEdge);
      yada.Assert(edgeMapPtr);
      encroachElementPtr = TMgrowRegion(elementPtr,
					this,
					meshPtr,
					edgeMapPtr);
      
      if (encroachElementPtr) {
	encroachElementPtr.element_setIsReferenced(true);
	numDelta += TMregion_refine(regionPtr,
				    encroachElementPtr,
				    meshPtr);
	if (elementPtr.elementisGarbage()) {
	  break;
	}
      } else {
	break;
      }
    }
    
    /*
     * Perform retriangulation.
     */
    
    if (!elementPtr.element_isGarbage()) {
      numDelta += TMretriangulate(elementPtr,
				  this,
				  meshPtr,
				  edgeMapPtr);
    }
    
    PMAP_FREE(edgeMapPtr); /* no need to free elements */
    
    return numDelta;
  }


  /* =============================================================================
   * Pregion_clearBad
   * =============================================================================
   */
  void region_clearBad () {
    badVectorPtr.vector_clear();
  }


/* =============================================================================
 * TMregion_transferBad
 * =============================================================================
 */
  void region_transferBad(heap workHeapPtr) {
    int numBad = badVectorPtr.vector_getSize();
    
    for (int i = 0; i < numBad; i++) {
      element badElementPtr = (element)badVectorPtr.vector_at(i);
      if (badElementPtr.element_isGarbage()) {
      } else {
	boolean status = workHeapPtr.heap_insert(badElementPtr);
	yada.Assert(status);
      }
    }
  }
}
