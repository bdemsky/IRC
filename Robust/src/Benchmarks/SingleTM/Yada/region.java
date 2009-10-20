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
    beforeListPtr = new List_t(0);//PLIST_ALLOC(element_listCompare);
    borderListPtr = new List_t(1);//PLIST_ALLOC(element_listCompareEdge);
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
			      avltree edgeMapPtr, double angle) {
    Vector_t badVectorPtr = regionPtr.badVectorPtr; /* private */
    List_t beforeListPtr = regionPtr.beforeListPtr; /* private */
    List_t borderListPtr = regionPtr.borderListPtr; /* private */
    int numDelta = 0;
    
    yada.Assert(edgeMapPtr!=null);
    
    coordinate centerCoordinate = elementPtr.element_getNewPoint();
    
    /*
     * Remove the old triangles
     */
    
    List_Node it=beforeListPtr.head;

    while (it.nextPtr!=null) {
      it=it.nextPtr;
      element beforeElementPtr = (element)it.dataPtr;
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
      element aElementPtr = new element(coordinates, 2, angle);
      yada.Assert(aElementPtr!=null);
      meshPtr.TMmesh_insert(aElementPtr, edgeMapPtr);
      
      coordinates[1] = (coordinate)edgePtr.secondPtr;
      element bElementPtr = new element(coordinates, 2, angle);
      yada.Assert(bElementPtr!=null);
      meshPtr.TMmesh_insert(bElementPtr, edgeMapPtr);
      
      boolean status = meshPtr.TMmesh_removeBoundary(elementPtr.element_getEdge(0));
      yada.Assert(status);
      status = meshPtr.TMmesh_insertBoundary(aElementPtr.element_getEdge(0));
      yada.Assert(status);
      status = meshPtr.TMmesh_insertBoundary(bElementPtr.element_getEdge(0));
      yada.Assert(status);
      
      numDelta += 2;
    }
    
    /*
     * Insert the new triangles. These are contructed using the new
     * point and the two points from the border segment.
     */

    it=borderListPtr.head;
    while (it.nextPtr!=null) {
      coordinate coordinates[]=new coordinate[3];
      it=it.nextPtr;
      edge borderEdgePtr = (edge)it.dataPtr;
      yada.Assert(borderEdgePtr!=null);
      coordinates[0] = centerCoordinate;
      coordinates[1] = (coordinate)(borderEdgePtr.firstPtr);
      coordinates[2] = (coordinate)(borderEdgePtr.secondPtr);
      element afterElementPtr = new element(coordinates, 3, angle);
      yada.Assert(afterElementPtr!=null);
      meshPtr.TMmesh_insert(afterElementPtr, edgeMapPtr);
      if (afterElementPtr.element_isBad()) {
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
      
      beforeListPtr.insert(currentElementPtr); /* no duplicates */
      List_t neighborListPtr = currentElementPtr.element_getNeighborListPtr();
      
      List_Node it=neighborListPtr.head;
      while (it.nextPtr!=null) {
	it=it.nextPtr;
	element neighborElementPtr = (element)it.dataPtr;
	neighborElementPtr.element_isGarbage(); /* so we can detect conflicts */
	if (beforeListPtr.find(neighborElementPtr)==null) {
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
	    edge borderEdgePtr = element.element_getCommonEdge(neighborElementPtr, currentElementPtr);

	    if (borderEdgePtr==null) {
	      //	      Thread.abort();
	    }
	    borderListPtr.insert(borderEdgePtr); /* no duplicates */
	    if (!edgeMapPtr.contains(borderEdgePtr)) {
	      edgeMapPtr.insert(borderEdgePtr, neighborElementPtr);
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
  int TMregion_refine(element elementPtr, mesh meshPtr, double angle) {
    int numDelta = 0;
    avltree edgeMapPtr = null;
    element encroachElementPtr = null;
    
    elementPtr.element_isGarbage(); /* so we can detect conflicts */
    
    while (true) {
      edgeMapPtr = new avltree(0);
      yada.Assert(edgeMapPtr!=null);
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
    
    /*
     * Perform retriangulation.
     */
    
    if (!elementPtr.element_isGarbage()) {
      numDelta += TMretriangulate(elementPtr,
				  this,
				  meshPtr,
				  edgeMapPtr, angle);
    }
    
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
