/* =============================================================================
 *
 * grid.java
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

import java.lang.Long;

#define CACHE_LINE_SIZE 8
#define GRID_POINT_FULL -2
#define GRID_POINT_EMPTY -1

public class Grid {
    public int width;
    public int height;
    public int depth;
    public int points_index;
    public int[][] points_unaligned;

    public Grid() {}

    
/* =============================================================================
 * grid_alloc
 * =============================================================================
    grid_t* grid_alloc (long width, long height, long depth);

    well... need to implement
    got stuck
 */
    public static Grid alloc(int width,int height,int depth) {
        Grid grid = new Grid();

        if(grid != null) {

            grid.width = width;
            grid.height = height;
            grid.depth = depth;

            int n = width * height * depth;

            // long* points_unaligned = (long*) malloc(n*sizeof(long) + CACHE_LINE_SIZE);
            int size = n + CACHE_LINE_SIZE;
            int[][] points_unaligned = new int[size][1];

            grid.points_unaligned = points_unaligned;
            grid.points_index = CACHE_LINE_SIZE-2;        // not sure it is right..

            for(int i=grid.points_index;i<n;i++) 
                grid.points_unaligned[i][0] = GRID_POINT_EMPTY;            
        }
                    
        return grid;         
    }



/* =============================================================================
 * TMgrid_alloc
 * =============================================================================
 */
//grid_t* Pgrid_alloc (long width, long height, long depth);


/* =============================================================================
 * grid_free
 * =============================================================================
    void grid_free (grid_t* gridPtr);
*/
    public static free(Grid gridPtr)
    {
        gridPtr = null;
    }


/* =============================================================================
 * Pgrid_free
 * =============================================================================
  void Pgrid_free (grid_t* gridPtr);
  */
    


/* =============================================================================
 * grid_copy
 * =============================================================================
    void grid_copy (grid_t* dstGridPtr, grid_t* srcGridPtr);
 */
    public static void copy(Grid dstGridPtr,Grid srcGridPtr)
    {
        if((srcGridPtr.width == dstGridPtr.width) ||
           (srcGridPtr.height == dstGridPtr.height) ||
           (srcGridPtr.depth == dstGridPtr.depth))
        {
        int n = srcGridPtr.width * srcGridPtr.height * srcGridPtr.depth;

        for(int i=0;i<n;i++)
            dstGridPtr.points_unaligned[dstGridPtr.points_index + i][0] = 
                                                                srcGridPtr.points_unaligned[srcGridPtr.points_index + i][0];   
        }
    }



/* =============================================================================
 * grid_isPointValid
 * =============================================================================
 bool_t grid_isPointValid (grid_t* gridPtr, long x, long y, long z);
 */
    public boolean isPointValid(int x,int y,int z)
    {
        if(x < 0 || x >= width || 
           y < 0 || y >= height ||
           z < 0 || z >= depth)
        {
            return false;
        }

        return true;
    }


/* =============================================================================
 * grid_getPointRef
 * =============================================================================
long* grid_getPointRef (grid_t* gridPtr, long x, long y, long z);

    it is returning the index of the point
*/
    public int getPointIndex(int x,int y,int z)
    {
        return points_index + (((z * height) + y) * width + x);
    }


/* =============================================================================
 * grid_getPointIndices
 * =============================================================================
 void grid_getPointIndices (grid_t* gridPtr,
                      long* gridPointPtr, long* xPtr, long* yPtr, long* zPtr);
 */
    public void getPointIndices(int gridPointIndex,int[] xPtr, int[] yPtr,int[] zPtr)
    {
        int height = this.height;
        int width = this.width;
        int area = height * width;
        int index3d = (gridPointIndex - this.points_index);
        zPtr[0] = index3d / area;
        int index2d = index3d % area;
        yPtr[0] = index2d / width;
        xPtr[0] = index2d % width;        
    }


/* =============================================================================
 * grid_getPoint
 * =============================================================================
 long grid_getPoint (grid_t* gridPtr, long x, long y, long z);
 */
    public int getPoint(int x,int y,int z)
    {
        return this.points_unaligned[getPointIndex(x,y,z)][0];
    }

    public int getPoint(int index)
    {
        return this.points_unaligned[index][0];
    }


/* =============================================================================
 * grid_isPointEmpty
 * =============================================================================
 bool_t grid_isPointEmpty (grid_t* gridPtr, long x, long y, long z);
 */
    public boolean isPointEmpty(int x,int y,int z)
    {
        int value = getPoint(x,y,z);
        return ((value == GRID_POINT_EMPTY) ? true:false);
    }



/* =============================================================================
 * grid_isPointFull
 * =============================================================================
 bool_t grid_isPointFull (grid_t* gridPtr, long x, long y, long z);
 */
    public boolean isPointFull(int x,int y,int z)
    {
        int value = getPoint(x,y,z);
        return ((value == GRID_POINT_FULL) ? true : false);
    }


/* =============================================================================
 * grid_setPoint
 * =============================================================================
 void grid_setPoint (grid_t* gridPtr, long x, long y, long z, long value);
 */
    public void setPoint(int x,int y,int z,int value)
    {
        points_unaligned[getPointIndex(x,y,z)][0] = value;
    }


/* =============================================================================
 * grid_addPath
 * =============================================================================
 
void grid_addPath (grid_t* gridPtr, vector_t* pointVectorPtr);
*/
    public void addPath(Vector_t pointVectorPtr)
    {
        int i;
        int n = pointVectorPtr.vector_getSize();

        for(i = 0; i < n; i++) {
            Coordinate coordinatePtr = (Coordinate)pointVectorPtr.vector_at(i);
            int x = coordinatePtr.x;
            int y = coordinatePtr.y;
            int z = coordinatePtr.z;

//            System.out.println("x = " + x + " y = " + y + " z = " + z);
            setPoint(x,y,z,GRID_POINT_FULL);
        }
    }

    public void TM_addPath(Vector_t pointVectorPtr)
    {
        int i;
        int n = pointVectorPtr.vector_getSize();

        for(i = 0; i < n; i++) {
            int gridPointIndex = ((Integer)(pointVectorPtr.vector_at(i))).intValue();
            points_unaligned[gridPointIndex][0] = GRID_POINT_FULL;            
        }
    }

/* =============================================================================
 * TMgrid_addPath
 * =============================================================================
 TM_CALLABLE
void
TMgrid_addPath (TM_ARGDECL  grid_t* gridPtr, vector_t* pointVectorPtr);
*/


/* =============================================================================
 * grid_print
 * =============================================================================
void grid_print (grid_t* gridPtr);
*/
    public void print()
    {
        int z;

        for(z = 0; z < depth; z++) {
            System.out.println("[z = " + z + "]");
            int x;
            for(x = 0; x < width; x++) {
                int y;
                for(y = 0; y < height; y++) {
                    System.out.print(points_unaligned[getPointIndex(x,y,z)][0] + " ");
                }
                System.out.println("");
            }
            System.out.println("");
        }
 
    }
}

/* =============================================================================
 *
 * End of grid.c
 *
 * =============================================================================
 */
