/**
 ** An object representing the entity in the game that
 ** is going to moving along the path. This allows us to pass around entity/state
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
  private int id;
  private int lowx, highx;
  private int lowy, highy;
  private int state;
  private int goalx, goaly;

  public Player(int type, int x, int y) {
    this.type = type;
    this.x = x;
    this.y = y;
    id = -1;
  }

  public Player(int type, int x, int y, int id, int rows, int cols, int bounds) {
    this.type = type;
    this.x = x;
    this.y = y;
    this.id = id;
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
  }

  public void reset(int row, int col, int bounds) {
    int seed = x + y;
    Random rand = new Random(seed);
    x = (rand.nextInt(Math.abs(row - 2) + 1)) + 1;
    y = (rand.nextInt(Math.abs(col - 2) + 1)) + 1;
    goalx = -1;
    goaly = -1;
    setBoundary(bounds, row, col);
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
     * if not possible , then select a completely new goal
     */
    int trycount = (highx - lowx) + (highy - lowy);
    int i;

    for (i = 0; i < trycount; i++) {
      Random rand = new Random(i);
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
    if (i == trycount) {
      /* System.println("Timeout trying ... \n") Only for Debugging */
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
    System.println("State= "+ state+ " Curr X=  "+ x + " Curr Y=  " + y + " Goal X=  "+ goalx + " Goal Y= "+ goaly + " Type = " + type + "\n");
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
