/**
 ** An object representing the entity in the game that
 ** is going to move along the path. This allows us to pass around entity/state
 ** information to determine whether a particular tile is blocked, or how much
 ** cost to apply on a particular tile.
 ** 
 ** a) Saves the current X and Y coordinates for a Player
 ** b) Saves the destination goalX and goalY
 ** c) Determines the boundary using high/low X and Y coordinates for
 **    the current position
 ** d) Keeps track of the STATE of the player
 **/
public class Player {
  private int type;
  private int x;
  private int y;
  private int lowx, highx;
  private int lowy, highy;
  private int state;
  private int goalx, goaly;
  private int rows, cols;
  private Random rand;

  public Player(int type, int x, int y) {
    this.type = type;
    this.x = x;
    this.y = y;
  }

  public Player(int type, int x, int y, int rows, int cols, int bounds) {
    this.type = type;
    this.x = x;
    this.y = y;
    this.rows = rows;
    this.cols = cols;
    lowx = x - bounds;
    highx = x + bounds;
    lowy = y - bounds;
    highy = y + bounds;
    // define new boundaries
    if (lowx <= 0) 
      lowx = 1;
    if (lowy <= 0) 
      lowy = 1;
    if (highx >= rows) 
      highx = rows-2;
    if (highy >= cols) 
      highy = cols-2;
    rand = new Random(30); //seed to generate random numbers
  }

  public void reset(GameMap[][] land, int row, int col, int bounds) {
    //Teleport to new location
    if(type == 1) { //PLANTER
      int tempx = (rand.nextInt(Math.abs(row - 2) + 1)) + 1;
      int tempy = (rand.nextInt(Math.abs(col - 2) + 1)) + 1;
      if(((tempx - x) > bounds) || ((tempy - y) > bounds)) {
        // Quickly determine the boundaries for given row and column
        int templowx = tempx - bounds;
        int temphighx = tempx + bounds;
        int templowy = tempy - bounds;
        int temphighy = tempy + bounds;
        // define new boundaries
        if (templowx <= 0) 
          templowx = 1;
        if (templowy <= 0) 
          templowy = 1;
        if (temphighx >= row-1) 
          temphighx = row-2;
        if (temphighy >= col -1) 
          temphighy = col-2;

        //
        // Add Manual Prefetch
        //

      }
      x = tempx;
      y = tempy;
      goalx = -1;
      goaly = -1;
      setBoundary(bounds, row, col);
    } 

    if(type == 0) { //LUMBERJACK
      int trycount = 5; //try a few more times before teleporting 
      int i = 0;
      while(i<trycount) {
        int locx = (rand.nextInt(Math.abs(row - 2) + 1)) + 1;
        int locy = (rand.nextInt(Math.abs(col - 2) + 1)) + 1;
        if(!land[locx][locy].hasRock() && land[locx][locy].hasTree()) {
          goalx = locx;
          goaly = locy;
          state = 1; //1=> MOVING state
          return;
        }
        i++;
      }
      int tempx = (rand.nextInt(Math.abs(row - 2) + 1)) + 1;
      int tempy = (rand.nextInt(Math.abs(col - 2) + 1)) + 1;
      /*
      if(((tempx - x) > bounds) || ((tempy - y) > bounds)) {
        // Quickly determine the boundaries for given row and column
        int templowx = tempx - bounds;
        int temphighx = tempx + bounds;
        int templowy = tempy - bounds;
        int temphighy = tempy + bounds;
        // define new boundaries
        if (templowx <= 0) 
          templowx = 1;
        if (templowy <= 0) 
          templowy = 1;
        if (temphighx >= row-1) 
          temphighx = row-2;
        if (temphighy >= col -1) 
          temphighy = col-2;

        //
        // Add Manual Prefetch
        //

      }
      */
      x = tempx;
      y = tempy;
      goalx = -1;
      goaly = -1;
      setBoundary(bounds, row, col);
    }
  }

  public void setBoundary(int bounds, int rows, int cols) {
    lowx = x - bounds;
    highx = x + bounds;
    lowy = y - bounds;
    highy = y + bounds;
    // define new boundaries
    if (lowx <= 0) 
      lowx = 1;
    if (lowy <= 0) 
      lowy = 1;
    if (highx >= rows-1) 
      highx = rows-2;
    if (highy >= cols-1) 
      highy = cols-2;
    return;
  }

  /**
   ** @return if Player is lumberjack or a planter
   **/
  public int kind() {
    return type;
  }

  /**
   ** Sets the X and Y coordinate of the Player
   **/
  public void setPosition(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public void setNewPosition(int x, int y, int row, int col, int bounds) {
    setPosition(x, y);
    setBoundary(bounds, row, col);
    goalx = -1;
    goaly = -1;
  }

  public int getX() {
    return x;
  } 
  
  public int getY() { 
    return y; 
  }

  /** Sets state of the Player **/

  public void setState(int state) {
    this.state = state;
    return;
  }

  public int getState() {
    return this.state;
  }

  /** Randomly finds a goal in a given boundary for player
   ** @return 0 on success and -1 when you cannot find any new goal
   **/
  public int findGoal(GameMap[][] land) {
    /* Try setting the goal for try count times
     * if not possible, then select a completely new goal
     */
    int trycount = (highx - lowx) + (highy - lowy);
    int i;

    Random rand = new Random(0);
    for (i = 0; i < trycount; i++) {
      int row = (rand.nextInt(Math.abs(highx - lowx)) + 1) + lowx;
      int col = (rand.nextInt(Math.abs(highy - lowy)) + 1) + lowy;
      if (type == 1 && (land[row][col].hasTree() == false) && (land[row][col].hasRock() == false)) {
        goalx = row;
        goaly = col;
        return 0;
      }
      if (type == 0 && (land[row][col].hasTree() == true) && (land[row][col].hasRock() == false)) {
        goalx = row;
        goaly = col;
        return 0;
      }
    }
    return -1;
  }

  public void setGoal(int x, int y) {
    goalx = x;
    goaly = y;
  }

  public int getGoalX() {
    return goalx;
  }

  public int getGoalY() {
    return goaly;
  }

  /**
   ** Only for debugging
   **/
  public debugPlayer() {
    System.println("State= "+ state+ " Curr X=  "+ x + " Curr Y=  " + y + " Goal X=  "+ goalx + " Goal Y= "+ goaly + " Type = " + type);
  }

  /**
   ** @return true if reached the goal else return false
   **/
  public boolean atDest() {
    if (x == goalx && y == goaly) {
      return true;
    } 
    return false;
  }
}
