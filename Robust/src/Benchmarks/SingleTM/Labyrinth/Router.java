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

#define MOMENTUM_ZERO 0
#define MOMENTUM_POSX 1
#define MOMENTUM_POSY 2
#define MOMENTUM_POSZ 3
#define MOMENTUM_NEGX 4
#define MOMENTUM_NEGY 5
#define MOMENTUM_NEGZ 6
#define GRID_POINT_FULL -2
#define GRID_POINT_EMPTY -1

public class Router {
    public int xCost;
    public int yCost;
    public int zCost;
    public int bendCost;
    public static Point MOVE_POSX;
    public static Point MOVE_POSY;
    public static Point MOVE_POSZ;
    public static Point MOVE_NEGX;
    public static Point MOVE_NEGY;
    public static Point MOVE_NEGZ;

    public Router() {}

/* =============================================================================
 * router_alloc
 * =============================================================================
 * router_t* router_alloc (long xCost, long yCost, long zCost, long bendCost);
 */
    public static Router alloc(int xCost,int yCost,int zCost,int bendCost)
    {
        Router routerPtr = new Router();


        routerPtr.MOVE_POSX = new Point(1,0,0,0,MOMENTUM_POSX);
        routerPtr.MOVE_POSY = new Point(0,1,0,0,MOMENTUM_POSY);
        routerPtr.MOVE_POSZ = new Point(0,0,1,0,MOMENTUM_POSZ);
        routerPtr.MOVE_NEGX = new Point(-1,0,0,0,MOMENTUM_NEGX);
        routerPtr.MOVE_NEGY = new Point(0,-1,0,0,MOMENTUM_NEGY);
        routerPtr.MOVE_NEGZ = new Point(0,0,-1,0,MOMENTUM_NEGZ);

        if(routerPtr != null) {
            routerPtr.xCost = xCost;
            routerPtr.yCost = yCost;
            routerPtr.zCost = zCost;
            routerPtr.bendCost = bendCost;
        }

        return routerPtr;    
    }




/* =============================================================================
 * router_free
 * =============================================================================
 * void router_free (router_t* routerPtr);
 */
    public static void free(Router routerPtr) 
    {
        routerPtr = null;
    }

/* ============================================================================
 * PexpandToneighbor
 * ============================================================================
 */
    private void PexpandToNeighbor(Grid myGridPtr, 
                                    int x,int y,int z, int value,Queue_Int queuePtr)
    {
        if (myGridPtr.isPointValid(x,y,z)) {
            int neighborGridPointIndex = myGridPtr.getPointIndex(x,y,z);
            int neighborValue = myGridPtr.points_unaligned[neighborGridPointIndex][0];
            if (neighborValue == GRID_POINT_EMPTY) {
                myGridPtr.points_unaligned[neighborGridPointIndex][0] = value;
                queuePtr.queue_push(neighborGridPointIndex);
            } else if (neighborValue != GRID_POINT_FULL) {
                
                if (value < neighborValue) {
                    myGridPtr.points_unaligned[neighborGridPointIndex][0] = value;
                    queuePtr.queue_push(neighborGridPointIndex);
                }
            }
        }
    }


/* ============================================================================
 * PdoExpansion
 * ============================================================================
 */
    public boolean PdoExpansion (Router routerPtr,Grid myGridPtr,Queue_Int queuePtr,
                                  Coordinate srcPtr,Coordinate dstPtr)
    {
        int xCost = routerPtr.xCost;
        int yCost = routerPtr.yCost;
        int zCost = routerPtr.zCost;

        /* 
         * Potential Optimization: Make 'src' the one closet to edge.
         * This will likely decrease the area of the emitted wave.
         */

        queuePtr.queue_clear();

        int srcGridPointIndex = myGridPtr.getPointIndex(srcPtr.x,srcPtr.y,srcPtr.z);

        queuePtr.queue_push(srcGridPointIndex);
 //       System.out.println("dstPtr :\tx = " + dstPtr.x + "\ty = " + dstPtr.y + "\tz = " + dstPtr.z); 
        myGridPtr.setPoint(srcPtr.x,srcPtr.y,srcPtr.z,0);
        myGridPtr.setPoint(dstPtr.x,dstPtr.y,dstPtr.z,GRID_POINT_EMPTY);
        int dstGridPointIndex = myGridPtr.getPointIndex(dstPtr.x,dstPtr.y,dstPtr.z);
        boolean isPathFound = false;
        int[] x = new int[1];
        int[] y = new int[1];
        int[] z = new int[1];

        while (!queuePtr.queue_isEmpty()) {
            int gridPointIndex = queuePtr.queue_pop();

//            System.out.println("gridPointIndex = " +gridPointIndex);
            if(gridPointIndex == dstGridPointIndex) {
                isPathFound = true;
                break;
            }
                        
            myGridPtr.getPointIndices(gridPointIndex,x,y,z);
            int value = myGridPtr.points_unaligned[gridPointIndex][0];

            /*
             * Check 6 neighbors
             *
             * Potential Optimization: Only need to check 5 of these
             */
          PexpandToNeighbor(myGridPtr, x[0]+1, y[0],   z[0],   (value + xCost), queuePtr);
          PexpandToNeighbor(myGridPtr, x[0]-1, y[0],   z[0],   (value + xCost), queuePtr);
          PexpandToNeighbor(myGridPtr, x[0], y[0]+1,   z[0],   (value + yCost), queuePtr);
          PexpandToNeighbor(myGridPtr, x[0], y[0]-1,   z[0],   (value + yCost), queuePtr);   
          PexpandToNeighbor(myGridPtr, x[0], y[0],   z[0]+1,   (value + zCost), queuePtr);
          PexpandToNeighbor(myGridPtr, x[0], y[0],   z[0]-1,   (value + zCost), queuePtr);

        } /* iterate over work queue */

        return isPathFound;
    }
            
            
/* ============================================================================
 * traceToNeighbor
 * ============================================================================
 */
    private void traceToNeighbor(Grid myGridPtr,
                                 Point currPtr,
                                 Point movePtr,
                                 boolean useMomentum,
                                 int bendCost,
                                 Point nextPtr)
    {
        int x = currPtr.x + movePtr.x;
        int y = currPtr.y + movePtr.y;
        int z = currPtr.z + movePtr.z;

        if (myGridPtr.isPointValid(x,y,z) &&
                !myGridPtr.isPointEmpty(x,y,z) &&
                !myGridPtr.isPointFull(x,y,z))
        {
            int value = myGridPtr.getPoint(x,y,z);
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
/* =============================================================================
 * PdoTraceback
 * =============================================================================
 */

    private Vector_t PdoTraceback(Grid gridPtr,Grid myGridPtr,
                                  Coordinate dstPtr, int bendCost)
    {
        Vector_t pointVectorPtr = Vector_t.vector_alloc(1);

        Point next = new Point();
        next.x = dstPtr.x;
        next.y = dstPtr.y;
        next.z = dstPtr.z;
        next.value = myGridPtr.getPoint(next.x,next.y,next.z);
        next.momentum = MOMENTUM_ZERO;

        while(true) {
            int gridPointIndex = gridPtr.getPointIndex(next.x,next.y,next.z);
            pointVectorPtr.vector_pushBack(new Integer(gridPointIndex));
            myGridPtr.setPoint(next.x,next.y,next.z,GRID_POINT_FULL);

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

            traceToNeighbor(myGridPtr,curr,MOVE_POSX,true, bendCost, next);
            traceToNeighbor(myGridPtr,curr,MOVE_POSY,true, bendCost, next);   
            traceToNeighbor(myGridPtr,curr,MOVE_POSZ,true, bendCost, next);
            traceToNeighbor(myGridPtr,curr,MOVE_NEGX,true, bendCost, next); 
            traceToNeighbor(myGridPtr,curr,MOVE_NEGY,true, bendCost, next);           
            traceToNeighbor(myGridPtr,curr,MOVE_NEGZ,true, bendCost, next);          

            /* 
             * Because of bend costs, none of the neighbors may appear to be closer.
             * In this case, pick a neighbor while ignoring momentum.
             */

//            System.out.println("next x = " + next.x + " y = " + next.y + " z = " + next.z);
            
            if ((curr.x == next.y) &&
                (curr.y == next.y) &&
                (curr.z == next.z))
            {
                next.value = curr.value;
                traceToNeighbor(myGridPtr,curr,MOVE_POSX,false, bendCost, next);   
                traceToNeighbor(myGridPtr,curr,MOVE_POSY,false, bendCost, next);               
                traceToNeighbor(myGridPtr,curr,MOVE_POSZ,false, bendCost, next);              
                traceToNeighbor(myGridPtr,curr,MOVE_NEGX,false, bendCost, next);   
                traceToNeighbor(myGridPtr,curr,MOVE_NEGY,false, bendCost, next);   
                traceToNeighbor(myGridPtr,curr,MOVE_NEGZ,false, bendCost, next);   
                
                if ((curr.x == next.x) &&
                    (curr.y == next.y) &&
                    (curr.z == next.z))
                {
                    pointVectorPtr.vector_free();
                    System.out.println("Dead");
                    return null;
                }
            }
        }

        return pointVectorPtr;
    }

/* =============================================================================
 * router_solve
 * =============================================================================
 * void router_solve (void* argPtr);
 */
    public static void solve(Object argPtr) 
    {
        // TM_THREAD_ENTER();
        //
        Solve_Arg routerArgPtr = (Solve_Arg) argPtr;
        Router routerPtr = routerArgPtr.routerPtr;
        Maze mazePtr = routerArgPtr.mazePtr;
        Vector_t myPathVectorPtr = Vector_t.vector_alloc(1);

        Queue_t workQueuePtr = mazePtr.workQueuePtr;
        Grid gridPtr = mazePtr.gridPtr;
        Grid myGridPtr = Grid.alloc(gridPtr.width,gridPtr.height,gridPtr.depth);
        int bendCost = routerPtr.bendCost;
        Queue_Int myExpansionQueuePtr = Queue_Int.queue_alloc(-1);

        /*
         * Iterate over work list to route each path. This involves an
         * 'expansion' and 'tracback' phase for each source/destination pair.
         */
        while(true) {
            Pair coordinatePairPtr;
            
            // TM_BEGIN();
            atomic {
                if(workQueuePtr.queue_isEmpty()) {
                    coordinatePairPtr = null;
                } else {
                    coordinatePairPtr = (Pair)workQueuePtr.queue_pop();
                }
            }
            // TM_END();
            //
            
            if (coordinatePairPtr == null) {
                break;
            }

            Coordinate srcPtr = (Coordinate)coordinatePairPtr.first;
            Coordinate dstPtr = (Coordinate)coordinatePairPtr.second;

//            System.out.println("SRC x = " + srcPtr.x + "  y = " + srcPtr.y + " z = " +srcPtr.z);
            
            boolean success = false;
            Vector_t pointVectorPtr = null;


            // TM_BEGIN();
            atomic {
                Grid.copy(myGridPtr, gridPtr); /* ok if not most up-to-date */
                               
                if(routerPtr.PdoExpansion(routerPtr,myGridPtr,myExpansionQueuePtr,srcPtr,dstPtr)) {
                    pointVectorPtr = routerPtr.PdoTraceback(gridPtr,myGridPtr,dstPtr,bendCost);

                    if (pointVectorPtr != null) {
                        gridPtr.TM_addPath(pointVectorPtr);
                        success = true;
                    }

                }
            }

            if(success) {
                boolean status = myPathVectorPtr.vector_pushBack(pointVectorPtr);
                
                if(!status) {
                    System.out.println("Assert in Router_Solve");
                    System.exit(1);
                }
            }
        }

        /*
         * Add my paths to global list
         */
        List_t pathVectorListPtr = routerArgPtr.pathVectorListPtr;

        atomic {
            pathVectorListPtr.insert(myPathVectorPtr);
        }

        myGridPtr = null;
        myExpansionQueuePtr = null;
//        System.out.println("Final Grid: ");
//        gridPtr.print();
        
        // TM_THREAD_EXIT();
    }
}
/* =============================================================================
 *
 * End of router.java
 * 
 * =============================================================================
 */
