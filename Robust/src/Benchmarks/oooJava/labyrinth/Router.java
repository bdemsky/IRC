/* =============================================================================
 *
 * Router.java
 *
 * =============================================================================
 *
 * Copyright (C) Stanford University, 2006.  All Rights Reserved.
 * Author: Chi Cao Minh
 * 
 * Ported to Java
 * Author: Jihoon Lee
 * University of California, Irvine
 * 
 * rBlock Compilation
 * Author: Stephen Yang
 * University of California, Irvine
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

public class Router {
  public int xCost;
  public int yCost;
  public int zCost;
  public int bendCost;
  public Point MOVE_POSX;
  public Point MOVE_POSY;
  public Point MOVE_POSZ;
  public Point MOVE_NEGX;
  public Point MOVE_NEGY;
  public Point MOVE_NEGZ;

  private int MOMENTUM_ZERO;
  private int MOMENTUM_POSX;
  private int MOMENTUM_POSY;
  private int MOMENTUM_POSZ;
  private int MOMENTUM_NEGX;
  private int MOMENTUM_NEGY;
  private int MOMENTUM_NEGZ;
  private int GRID_POINT_FULL;
  private int GRID_POINT_EMPTY;

  public Router() {
    // Replaced #defines
    MOMENTUM_ZERO = 0;
    MOMENTUM_POSX = 1;
    MOMENTUM_POSY = 2;
    MOMENTUM_POSZ = 3;
    MOMENTUM_NEGX = 4;
    MOMENTUM_NEGY = 5;
    MOMENTUM_NEGZ = 6;
    GRID_POINT_FULL = -2;
    GRID_POINT_EMPTY = -1;
  }

  /*
   * ============================================================================
   * = router_alloc
   * ==============================================================
   * =============== router_t* router_alloc (long xCost, long yCost, long zCost,
   * long bendCost);
   */
  public Router alloc(int xCost, int yCost, int zCost, int bendCost) {
    Router routerPtr = new Router();

    routerPtr.MOVE_POSX = new Point(1, 0, 0, 0, MOMENTUM_POSX);
    routerPtr.MOVE_POSY = new Point(0, 1, 0, 0, MOMENTUM_POSY);
    routerPtr.MOVE_POSZ = new Point(0, 0, 1, 0, MOMENTUM_POSZ);
    routerPtr.MOVE_NEGX = new Point(-1, 0, 0, 0, MOMENTUM_NEGX);
    routerPtr.MOVE_NEGY = new Point(0, -1, 0, 0, MOMENTUM_NEGY);
    routerPtr.MOVE_NEGZ = new Point(0, 0, -1, 0, MOMENTUM_NEGZ);

    routerPtr.xCost = xCost;
    routerPtr.yCost = yCost;
    routerPtr.zCost = zCost;
    routerPtr.bendCost = bendCost;

    return routerPtr;
  }

  /*
   * ============================================================================
   * PexpandToneighbor
   * ==========================================================
   * ==================
   */
  private void PexpandToNeighbor(Grid myGridPtr, int x, int y, int z, int value, Queue_Int queuePtr) {
    if (myGridPtr.isPointValid(x, y, z)) {
      int neighborValue = myGridPtr.points_unaligned[x][y][z];
      if (neighborValue == GRID_POINT_EMPTY) {
        int neighborGridPointIndex = myGridPtr.getPointIndex(x, y, z);
        myGridPtr.points_unaligned[x][y][z] = value;
        queuePtr.queue_push(neighborGridPointIndex);
      } else if (neighborValue != GRID_POINT_FULL) {
        if (value < neighborValue) {
          int neighborGridPointIndex = myGridPtr.getPointIndex(x, y, z);
          myGridPtr.points_unaligned[x][y][z] = value;
          queuePtr.queue_push(neighborGridPointIndex);
        }
      }
    }
  }

  /*
   * ============================================================================
   * PdoExpansion
   * ================================================================
   * ============
   */
  public boolean PdoExpansion(Router routerPtr, Grid myGridPtr, Queue_Int queuePtr, Coordinate srcPtr, Coordinate dstPtr) {
    int xCost = routerPtr.xCost;
    int yCost = routerPtr.yCost;
    int zCost = routerPtr.zCost;

    /*
     * Potential Optimization: Make 'src' the one closet to edge. This will
     * likely decrease the area of the emitted wave.
     */

    queuePtr.queue_clear();

    int srcGridPointIndex = myGridPtr.getPointIndex(srcPtr.x, srcPtr.y, srcPtr.z);

    queuePtr.queue_push(srcGridPointIndex);

    myGridPtr.setPoint(srcPtr.x, srcPtr.y, srcPtr.z, 0);
    myGridPtr.setPoint(dstPtr.x, dstPtr.y, dstPtr.z, GRID_POINT_EMPTY);
    int dstGridPointIndex = myGridPtr.getPointIndex(dstPtr.x, dstPtr.y, dstPtr.z);
    boolean isPathFound = false;
    int height = myGridPtr.height;
    int width = myGridPtr.width;
    int area = height * width;
    while (!queuePtr.queue_isEmpty()) {
      int gridPointIndex = queuePtr.queue_pop();

      if (gridPointIndex == dstGridPointIndex) {
        isPathFound = true;
        break;
      }

      int z = gridPointIndex / area;
      int index2d = gridPointIndex % area;
      int y = index2d / width;
      int x = index2d % width;
      int value = myGridPtr.points_unaligned[x][y][z];

      /*
       * Check 6 neighbors
       * 
       * Potential Optimization: Only need to check 5 of these
       */
      PexpandToNeighbor(myGridPtr, x + 1, y, z, (value + xCost), queuePtr);
      PexpandToNeighbor(myGridPtr, x - 1, y, z, (value + xCost), queuePtr);
      PexpandToNeighbor(myGridPtr, x, y + 1, z, (value + yCost), queuePtr);
      PexpandToNeighbor(myGridPtr, x, y - 1, z, (value + yCost), queuePtr);
      PexpandToNeighbor(myGridPtr, x, y, z + 1, (value + zCost), queuePtr);
      PexpandToNeighbor(myGridPtr, x, y, z - 1, (value + zCost), queuePtr);

    } /* iterate over work queue */

    return isPathFound;
  }

  /*
   * ============================================================================
   * traceToNeighbor
   * ============================================================
   * ================
   */
  private void traceToNeighbor(Grid myGridPtr, Point currPtr, Point movePtr, boolean useMomentum, int bendCost,
      Point nextPtr) {
    int x = currPtr.x + movePtr.x;
    int y = currPtr.y + movePtr.y;
    int z = currPtr.z + movePtr.z;

    if (myGridPtr.isPointValid(x, y, z) && !myGridPtr.isPointEmpty(x, y, z) && !myGridPtr.isPointFull(x, y, z)) {
      int value = myGridPtr.getPoint(x, y, z);
      int b = 0;

      if (useMomentum && (currPtr.momentum != movePtr.momentum)) {
        b = bendCost;
      }
      if ((value + b) <= nextPtr.value) { /* '=' favors neighbors over current */
        nextPtr.x = x;
        nextPtr.y = y;
        nextPtr.z = z;
        nextPtr.value = value;
        nextPtr.momentum = movePtr.momentum;
      }
    }
  }

  /*
   * ============================================================================
   * = PdoTraceback
   * ==============================================================
   * ===============
   */

  private Vector_t PdoTraceback(Grid myGridPtr, Coordinate dstPtr, int bendCost) {
    Vector_t vector_t = new Vector_t();
    Vector_t pointVectorPtr = vector_t.vector_alloc(1);

    Point next = new Point();
    next.x = dstPtr.x;
    next.y = dstPtr.y;
    next.z = dstPtr.z;
    next.value = myGridPtr.getPoint(next.x, next.y, next.z);
    next.momentum = MOMENTUM_ZERO;

    while (true) {
      int gridPointIndex = myGridPtr.getPointIndex(next.x, next.y, next.z);
      pointVectorPtr.vector_pushBack(new Integer(gridPointIndex));
      myGridPtr.setPoint(next.x, next.y, next.z, GRID_POINT_FULL);

      /* Check if we are done */
      if (next.value == 0) {
        break;
      }
      Point curr = new Point();
      curr.x = next.x;
      curr.y = next.y;
      curr.z = next.z;
      curr.value = next.value;
      curr.momentum = next.momentum;

      /*
       * Check 6 neibors
       */

      traceToNeighbor(myGridPtr, curr, MOVE_POSX, true, bendCost, next);
      traceToNeighbor(myGridPtr, curr, MOVE_POSY, true, bendCost, next);
      traceToNeighbor(myGridPtr, curr, MOVE_POSZ, true, bendCost, next);
      traceToNeighbor(myGridPtr, curr, MOVE_NEGX, true, bendCost, next);
      traceToNeighbor(myGridPtr, curr, MOVE_NEGY, true, bendCost, next);
      traceToNeighbor(myGridPtr, curr, MOVE_NEGZ, true, bendCost, next);
      /*
       * Because of bend costs, none of the neighbors may appear to be closer.
       * In this case, pick a neighbor while ignoring momentum.
       */

      if ((curr.x == next.x) && (curr.y == next.y) && (curr.z == next.z)) {
        next.value = curr.value;
        traceToNeighbor(myGridPtr, curr, MOVE_POSX, false, bendCost, next);
        traceToNeighbor(myGridPtr, curr, MOVE_POSY, false, bendCost, next);
        traceToNeighbor(myGridPtr, curr, MOVE_POSZ, false, bendCost, next);
        traceToNeighbor(myGridPtr, curr, MOVE_NEGX, false, bendCost, next);
        traceToNeighbor(myGridPtr, curr, MOVE_NEGY, false, bendCost, next);
        traceToNeighbor(myGridPtr, curr, MOVE_NEGZ, false, bendCost, next);

        if ((curr.x == next.x) && (curr.y == next.y) && (curr.z == next.z)) {
          System.out.println("Dead");
          return null;
        }
      }
    }

    return pointVectorPtr;
  }
  
  public boolean isEmpty(Queue_t q){
    return (((q.pop + 1) % q.capacity == q.push) ? true : false);
  }

  /*
   * ============================================================================
   * = router_solve
   * ==============================================================
   * =============== void router_solve (void* argPtr);
   */
  public void solve(Object argPtr) {
    Solve_Arg routerArgPtr = (Solve_Arg) argPtr;
    Router routerPtr = routerArgPtr.routerPtr;
    Maze mazePtr = routerArgPtr.mazePtr;
    int workload = routerArgPtr.rblock_workload;
    List_t pathVectorListPtr = routerArgPtr.pathVectorListPtr; 
    
    Queue_t masterWorkQueuePtr = mazePtr.workQueuePtr;
    Grid masterGridPtr = mazePtr.gridPtr; 
    int bendCost = routerPtr.bendCost;
    
    int id = 0;
    
    while(!masterWorkQueuePtr.queue_isEmpty() ) {
      Queue_t redoQueue = masterWorkQueuePtr.Pqueue_alloc(masterWorkQueuePtr.capacity);
      while(!masterWorkQueuePtr.queue_isEmpty()) {
	Queue_t localWorkQueue = masterWorkQueuePtr.queue_getUpTo(workload);
        
	sese P {
	  //Clone needed since new paths are added to local Grid. Cannot add to master Grid because of rBlock p conflicts
	  Grid MGClone  = masterGridPtr.alloc(masterGridPtr.width, masterGridPtr.height, masterGridPtr.depth);
	  masterGridPtr.copy(MGClone, masterGridPtr);
	  
	  Vector_t computedPaths = solveLogic(localWorkQueue, MGClone, routerPtr, bendCost, workload);
	}
            	
	sese S {
	  Vector_t sucessfulPaths = computedPaths.vector_alloc(workload);
	  CoordPathWrapper singlePathSolution = (CoordPathWrapper) computedPaths.vector_popBack();
	  while(singlePathSolution != null)	{
	    if(masterGridPtr.TM_addPath(singlePathSolution.pathVector)) {
	      //fail
	      redoQueue.queue_push(singlePathSolution.coordinatePair);
	    } else {
	      //success           		  
	      sucessfulPaths.vector_pushBack(singlePathSolution.pathVector);
	      System.out.println("Path # " + ++id + " added sucessfully!");
	    }
	    singlePathSolution = (CoordPathWrapper)computedPaths.vector_popBack(); 
	  }
	  pathVectorListPtr.insert(sucessfulPaths);
	}//end of sese S
      }//end of inner while
      masterWorkQueuePtr = redoQueue;
    }//end of outer while
  }

  private Vector_t solveLogic(Queue_t localWorkQueue, Grid MGCopyPtr, Router routerPtr, int bendCost, int workload) {
    /*
     * Iterate over work list to route each path. This involves an 'expansion'
     * and 'traceback' phase for each source/destination pair.
     */

    Vector_t vector_t = new Vector_t();
    Queue_Int queue_int = new Queue_Int();
    Vector_t computedPathsPtr = vector_t.vector_alloc(workload + 2);
    while (true) {
      Pair coordinatePairPtr;
      Queue_Int myExpansionQueuePtr = queue_int.queue_alloc(-1);

      if (localWorkQueue.queue_isEmpty()) {
        coordinatePairPtr = null;
      } else {
        coordinatePairPtr = (Pair) localWorkQueue.queue_pop();
      }

      if (coordinatePairPtr == null)
        break;

      Coordinate srcPtr = (Coordinate) coordinatePairPtr.first;
      Coordinate dstPtr = (Coordinate) coordinatePairPtr.second;

      // System.out.println("SRC x = " + srcPtr.x + "  y = " + srcPtr.y +
      // " z = " +srcPtr.z);
      Vector_t pointVectorPtr = null;

      // Copy needed here since PdoExpansion fills grid with misc data
      Grid tempGrid = MGCopyPtr.alloc(MGCopyPtr.width, MGCopyPtr.height, MGCopyPtr.depth);
      MGCopyPtr.copy(tempGrid, MGCopyPtr); /* ok if not most up-to-date */

      if (routerPtr.PdoExpansion(routerPtr, tempGrid, myExpansionQueuePtr, srcPtr, dstPtr)) {
        pointVectorPtr = routerPtr.PdoTraceback(tempGrid, dstPtr, bendCost);
        if (pointVectorPtr != null) {
          // Cannot add to master grid as original due to rBlocks conflicting
          if (MGCopyPtr.TM_addPath(pointVectorPtr)) {
            pointVectorPtr = null;
          } else {
            // Success!
            CoordPathWrapper currPath = new CoordPathWrapper(coordinatePairPtr, pointVectorPtr);
            computedPathsPtr.vector_pushBack(currPath);
          }
        }
      }
    }

    return computedPathsPtr;
  }
}
/*
 * =============================================================================
 * 
 * End of router.java
 * 
 * =============================================================================
 */
