/*=============================================================================
 *
 * Maze.java
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


public class Maze {
    Grid gridPtr;
    Queue workQueuePtr;
    Vector_t wallvectorPtr; /* contains source/destination pairs to route */
    Vector_t srcVectorPtr;  /* obstacles */
    Vector_t dstVectorPtr;  /* destinations */


/* =============================================================================
 * maze_alloc
 * =============================================================================
 maze_t* maze_alloc ();
 */
   public static Maze alloc() 
   {
       Maze mazePtr = new Maze();

       if(mazePtr != null) {
           mazePtr.gridPtr = null;
           mazePtr.workQueuePtr = Queue.alloc(1024);
           mazePtr.wallVectorPtr = Vector.alloc(1);
           mazePtr.srcVectorPtr = Vector.alloc(1);
           mazePtr.dstVectorPtr = Vector.alloc(1);

       }

       return mazePtr;
   }

/* =============================================================================
 * maze_free
 * =============================================================================
 void maze_free (maze_t* mazePtr);
 */
    public static void free(Maze m)
    {
        m = null;
    }    

/* =============================================================================
 * addToGrid
 * =============================================================================
 */
    private void addToGrid(Grid gridPtr,Vector_t vectorPtr,char[] type)
    {
        int i;
        int n = vectorPtr.getSize();

        for(i = 0; i < n; i++) {
            Coordinate coordinatePtr = (Coordinate)vectorPtr.vector_at(i);
            if(!gridPtr.isPointValid(coodinatePtr.x,coordinatePtr.y,coordinatePtr.z))
            {
                System.err.println("Error: " + type + " (" + coordinate.x + 
                                                      ", " + coordinate.y + 
                                                      ", " + coordinate.z);
                System.exit(1);
            }
        }
        gridPtr.addPath(vectorPtr);
    }
/* =============================================================================
 * maze_read
 * -- Return number of path to route
 * =============================================================================
 long maze_read (maze_t* mazePtr, char* inputFileName);
 */
    public int read(String inputFileName)
    {
            BufferedReader in = new BufferedReader(new FileReader(inputFileName));

            /*
             * Parse input file
             */
            int lineNumber = 0;
            int height = -1;
            int width = -1;
            int depth = -1;
            char[] line = new char[256];
            boolean isParseError = false;
            List_t workListPtr = List.alloc(1); // List.alloc(Coordinate.comparePair);
            String line;
            
            while(line = in.readLine()) {
                
                char code;
                int[] xy = new int[6];  // equivalent to x1,y1,z1,x2,y2,z2
                int numToken = 0;
                
                StringTokenizer tok = new StringTokenizer(line);

                if(numToken = tok.countTokens() < 1 ) {
                    continue;
                }

                code = (char)tok.nextElement();
               
                for(i=0;i<numToken-1;i++) {
                    xy[i] = Integer.ParserInt(tok.nextToken());
                }                
                
                if(code == '#') 
                 { /* comment */
                   /* ignore line */
                
                 }else if(code == 'd') {
                      /* dimensions (format: d x y z) */
                     if(numToken != 4) {
                        isParseError = true;
                     }
                     else {
                        width = xy[0];
                        height = xy[1];
                        depth = xy[2];
                        if(width < 1 || height < 1 || depth <1)
                            isParseError = true;
                     }
                 }else if(code == 'p') { /* paths (format: p x1 y1 z1 x2 y2 z2) */
                    if(numToken != 7) {
                        isParseError = true;
                    }
                    else {
                        Coordinate srcPtr = Coordinate.alloc(xy[0],xy[1],xy[2]);
                        Coordinate dstPtr = Coordinate.alloc(xy[3],xy[4],xy[5]);
                        
                        if(Coordinate.isEqual(srcPtr,dstPtr)) {
                            isParseError = true;
                        }
                        else { 
                            Pair coordinatePtr = Pair.alloc(srcPtr,dstPtr);
                            boolean status = workListPtr.insert((Object)coordinatePairPtr);
                        }
                    }
                }else if(code == 'w') {
                         /* walls (format: w x y z) */
                        if(numToken != 4) {
                            isParseError = true;
                        } else {
                            Coordinate wallPtr = Coordinate.alloc(xy[0],xy[1],xy[2]);
                            wallVectorPtr.vector_pushBack(wallPtr);
                        }
                }else { /* error */
                       isParseError = true;
                }
                
                if(isParseError)  {/* Error */
                    System.err.println("Error: line " + lineNumber + " of " + inputfileName + "invalid");
                    System.exit(1);
                }
            }
            /* iterate over lines in put file */
           
            /* 
             * Initialize grid contents
             */
            if(width < 1 || height < 1 || depth < 1) {
                System.err.println("Error : Invalid dimensions ( " + width + ", " + height + ", "+ depth + ")");
                System.exit(1);
            }

            Grid gridPtr = Grid.alloc(width,height,depth);
            mazePtr.gridPtr = gridPtr;
            gridPtr.addToGrid(wallVectorPtr,"wall");
            gridPtr.addToGrid(srcVectorPtr, "source");
            grdPtr.addToGrid(dstVectorPtr, " destination");
            System.out.println("Maze dimensions = " + width + " x " + height + " x " + depth);
            System.out.println("Paths to route  = " + workListPtr.getSize());

            /*
             * Initialize work queue
             */
            Queue workQueuePtr = mazePtr.workQueuePtr;
            List_Iter it = new List_Iter();
            it.reset(workListPtr);

            while(it.hasNext(wrokListPtr)) {
                Pair coordinatePtr = (Pair)it.next(workListPtr);
                workQueuePtr.queue_push((Object)coordinatePairPtr);
            }

            workListPtr = free;

            return srcVectorPtr.getSize();
    }
    

/* =============================================================================
 * maze_checkPaths
 * =============================================================================
 bool_t maze_checkPaths (maze_t* mazePtr, list_t* pathListPtr, bool_t doPrintPaths);
 */
    public boolean checkPaths(List_t pathListPtr,boolean doPrintPaths)
    {
        int i;

        /* Mark walls */
        Grid testGridPtr = Grid.alloc(width,height,depth);
        testGridPtr.addPath(wallVectorPtr);

        /* Mark sources */
        int numSrc = srcVectorPtr.getSize();
        for(i = 0; i < numSrc; i++) {
            Coordinate srcPtr = (Coordinate)srcVectorPtr.vector_at(i);
            testGridPtr.setPoint(srcPtr.x,srcPtr.y,srcPtr.z,0);
        }

        /* Mark destinations */
        int numDst = destVectorPtr.getSize();
        for(i = 0; i < numdst; i++) {
            Coordinate dstPtr = (Coordinate)dstVector.vector_at(i);
            testGridPtr.setPoint(dstPtr.x,dstPtr.y,dstPtr.z,0);
        }

        /* Make sure path is contiguous and does not overlap */
        int id = 0;
        List_Iter it = new List_Iter();
        it.reset(pathVectorListPtr);
        while(it.hasNext(pathVectorListPtr)) {
            Vector_t pathVectorPtr = it.next(pathVectorListPtr);
            int numPath = pathVectorPtr.getSize();
            int i;
            for(i = 0; i < numPath; i++) {
                id++;
                Vector pointVectorPtr = pathVectorPtr.vector_at(i);
                /* Check start */
                int prevGridPointIndex = pointVectorPtr.vector_at(0);
                int[] x = new int[1];
                int[] y = new int[1];
                int[] z = new int[1];
                gridPtr.getPointIndices(prevGridPointIndex,x,y,z);
                if(testGridPtr.getPoint(x[0],y[0],z[0]) != 0) {
                    Grid.free(testGridPtr);
                    return false;
                }
                Coordinate prevCoordinate = new Coordinate();
                int[] x = new int[1];
                int[] y = new int[1];
                int[] z = new int[1];
                gridPtr.getpointIndices(prevGridPointIndex,x,y,z);
                prevCoordinate.x = x[0];
                prevCoordinate.y = y[0];
                prevCoordinate.z = z[0];

                int numPoiont = pointVectorPtr.getSize();
                int j;
                for(j = 1; j< (numPoint - 1) ;j++) { /* no need to check endpoints */
                    int currGridPointIndex = pointVectorPtr.vector_at(j);
                    Coordinate currCoordinate = new Coordinate();
                    int[] x = new int[1];
                    int[] y = new int[1];
                    int[] z = new int[1];
                    gridPtr.getPointIndices(currGridPointIndex,x,y,z);
                    currGridPoint.x = x[0];
                    currGridPoint.y = y[0];
                    currGridPoint.z = z[0];

                    if(Coordinate.areAdjacent(currCoordinate,preCoordinate)) {
                        Grid.free(testGridPtr);
                        return false;
                    }

                    prevCoordinate = currCoordinate;
                    int x = currCoordinate.x;
                    int y = currCoordinate.y;
                    int z = currCoordinate.z;
                    if(testGridPtr.getPoint(x,y,z) != GRID_POINT_EMPTY) {
                        Grid.free(testGridPtr);
                        return false;
                    } else {
                        testGridPtr.setPoint(x,y,z,id);
                    }
                }
                /* Check end */
                int lastGridPointIndex = pointVectorPtr.vector_at(j);
                gridPtr.getPointindices(lastGridPointIndex,x,y,z);
                if(testGridPtr.getPoint(x[0],y[0],z[0]) != 0) {
                    Grid.free(testGridPtr);
                    return false;
                }
            } /* iterate over pathVector */
        } /* iterate over pathVectorList */

        if(doPrintPaths) {
            system.out.println("\nRouted Maze:");
            testGridPtr.print();
        }

        Grid.free(testGridPtr);

        return true;
    }
                    
 }
/* =============================================================================
 *
 * End of maze.h
 *
 * =============================================================================
 */
