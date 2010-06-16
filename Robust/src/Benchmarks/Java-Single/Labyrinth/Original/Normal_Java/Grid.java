

//#define GRID_POINT_FULL -2
//#define GRID_POINT_EMPTY -1



public class Grid 
{
	//TODO change these back into #defines up top
	private static int GRID_POINT_FULL;
	private static int GRID_POINT_EMPTY;
	
	
    public int width;
    public int height;
    public int depth;
    public int[][] points_unaligned;

    public Grid() 
    {
    	//Kept from #defines
    	GRID_POINT_FULL = -2;
    	GRID_POINT_EMPTY =-1;
    }

    
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
            int[][] points_unaligned = new int[n][1];

            grid.points_unaligned = points_unaligned;
            
            for(int i=0;i<n;i++) 
                grid.points_unaligned[i][0] = grid.GRID_POINT_EMPTY;            
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
    
    //changed to void
  public static void free(Grid gridPtr) {
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
            dstGridPtr.points_unaligned[i][0] = 
	      srcGridPtr.points_unaligned[i][0];
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
        return ((z * height) + y) * width + x;
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
        int index3d = (gridPointIndex);
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
