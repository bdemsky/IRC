#define ROW                 400   /* columns in the map */
#define COLUMN              100   /* rows of in the map */
#define ROUNDS              200 /* Number of moves by each player */
#define PLAYERS             20    /* Number of Players when num Players != num of client machines */
#define RATI0               0.5   /* Number of lumberjacks to number of planters */
#define BLOCK               3     /* Area around the gamer to consider */
#define TREE_ZONE           0.4   /* Max percentage of trees in a zone */
#define AGEUPDATETHRESHOLD  16    /* How frequently/how many rounds to increment age of tree */
#define MAXAGE              200   /* Max age of a tree */


#define LUMBERJACK 0            /* If lumberjack */
#define PLANTER    1            /* If a tree planter */

#define INIT      0             /* Initial state */
#define MOVING    1             /* When moving along the map */

public class RainForest extends Thread {
  /**
   * The grid where player is playing
   **/
  GameMap[][] land;

  /**
   * The thread id involved 
   **/
  int threadid;

  /**
   * The total number of threads
   **/
  int numThreads;

  public RainForest() {

  }

  public RainForest(GameMap[][] land, int threadid, int numThreads) {
    this.land = land;
    this.threadid = threadid;
    this.numThreads = numThreads;
  }

  /**
   ** Update states as the player does one move in the map
   **/
  public void execute() {
    int id = threadid;

    Random rand = new Random(id);
    // Generate random numbers between 1 and row index/column index
    int maxValue = ROW - 1;
    int minValue = 1;
    int row = (rand.nextInt(Math.abs(maxValue - minValue) + 1)) + minValue;
    maxValue = COLUMN -1;
    int col = (rand.nextInt(Math.abs(maxValue - minValue) + 1)) + minValue;
    int person;
    if((id&1) != 0) { //same as id%2
      person = LUMBERJACK;
    } else {
      person = PLANTER;
    }
    Player gamer = new Player(person, row, col, ROW, COLUMN, BLOCK);

    // 
    // Debug
    // System.println("Player= "+ person+ " PosX= "+row+"  PosY= "+col);
    //

    //Do N rounds 
    //do one move per round and synchronise
    for(int i = 0; i<ROUNDS; i++) {
      //System.out.println("iteration= " + i);
      {
        doOneMove(land, gamer);
      }
      if((i&15) == 0 && id == 0) { //same as i%AGEUPDATETHRESHOLD
        /* Update age of all trees in a Map */
        {
          updateAge(land, MAXAGE, ROW, COLUMN);
        }
      }
    }
  }

  /**
   ** Update the age of all trees in a given map
   ** @param land The map to be searched
   ** @param maxage The maxage of a tree
   ** @param rows The number of rows in the map
   ** @param cols The number of columns in the map
   **/
  public void updateAge(GameMap[][] land, int maxage, int rows, int cols) {
    int countTrees = 0;
    //System.out.println("updateAge -> maxAge : "+maxage + " rows : " + rows + " cols : "+ cols);
    for(int i = 0; i<rows; i++) {
      for(int j = 0; j<cols; j++) {
        if(land[i][j].tree != null) {
          if(land[i][j].tree.getage() > maxage) {
            land[i][j].tree = null;
          } else {
            land[i][j].tree.incrementage();
          }
          countTrees++;
        }
      }
    }
  }

  public static void main(String[] args) {
    // Parse args get number of threads
    RainForest tmprf = new RainForest();
    RainForest.parseCmdLine(args, tmprf);
    int numThreads= tmprf.numThreads;

    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162;//dc-1
    mid[1] = (128<<24)|(195<<16)|(136<<8)|163;//dc-2
    mid[2] = (128<<24)|(195<<16)|(136<<8)|164;//dc-3
    mid[3] = (128<<24)|(195<<16)|(136<<8)|165;//dc-4
    mid[4] = (128<<24)|(195<<16)|(136<<8)|166;//dc-5
    mid[5] = (128<<24)|(195<<16)|(136<<8)|167;//dc-6
    mid[6] = (128<<24)|(195<<16)|(136<<8)|168;//dc-7
    mid[7] = (128<<24)|(195<<16)|(136<<8)|169;//dc-8


    // Init land and place rocks in boundaries
    GameMap[][] world;

    {
      world =  new GameMap[ROW][COLUMN];
      int i, j;
      for (i = 0; i < ROW; i++) {
        for (j = 0; j < COLUMN; j++) {
          world[i][j] =  new GameMap();
          if (j == 0 || j == COLUMN-1) {
            RockType r =  new RockType();
            world[i][j].putRock(r);
          }
          if (i == 0 || i == ROW-1) {
            RockType r =  new RockType();
            world[i][j].putRock(r);
          }
        }
      }
    }

    /* Set up threads */
    RainForest[] rf;
    {
      rf =  new RainForest[numThreads];
      for(int i=0; i<numThreads; i++) {
        rf[i] =  new RainForest(world, i, numThreads);
      }
    }

    /* Start threads */
    RainForest tmp;
    for(int i = 0; i<numThreads; i++) {
      {
        tmp = rf[i];
      }
      tmp.execute();
    }

    System.printString("Finished\n");
  }

  /**
   ** Player makes a move on his turn and updates the state of the map
   ** @param land The map to be searched
   ** @param gamer player and his state of play to decide the next move
   **/
  public void doOneMove(GameMap[][] land, Player gamer) {
    // 1. Get start(x, y) position of the player
    int currx = gamer.getX();
    int curry = gamer.getY();

    /* printLand(land, ROW, COLUMN); */

    // 2. Get type of player (lumberjack or planter)
    int type = gamer.kind();

    /* gamer.debugPlayer(); */
    //3. Change states
    if (gamer.getState() == INIT) {
      if (gamer.findGoal(land) < 0) {
        gamer.reset(land, ROW, COLUMN, BLOCK);
        return;
      }
      gamer.setState(MOVING);
    } 

    if (gamer.getState() == MOVING) {
      Goal nextmove = new Goal();
      int maxSearchDistance = 10;
      boolean allowDiagMovement = true;
      /* Find shortest path using AStar algo from start to goal */
      AStarPathFinder apath =  new  AStarPathFinder(land, maxSearchDistance, allowDiagMovement, ROW, COLUMN);
      Path newpath = apath.findPath(gamer);

      /* Reset state if there in no path from start to goal */
      if(newpath == null) {
        // 
        // Debug
        // System.println("Path from ("+currx+","+curry+") to ("+gamer.getGoalX()+","+gamer.getGoalY()+") is null");
        //

        gamer.reset(land, ROW, COLUMN, BLOCK);
        gamer.setState(INIT);
        return;
      }

      nextmove.setXY(newpath.getX(0), newpath.getY(0));
      gamer.setPosition(nextmove.getX(), nextmove.getY());
      currx = gamer.getX();
      curry = gamer.getY();
      if (gamer.atDest()) {
        if (gamer.kind() == LUMBERJACK) {
          //If tree present, cut 
          if (land[currx][curry].hasTree()) {
            land[currx][curry].cutTree();
            //
            // Debug
            // System.println("Cut tree");
            //
          } 
        } else { // PLANTER
          // If empty, plant tree 
          if (land[currx][curry].hasTree() == false) {
            if(hasMoreTrees(land, currx, curry) == false) {
              TreeType t =  new TreeType();
              land[currx][curry].putTree(t);
              //
              // Debug
              // System.println("Put tree");
              //
            }
          } 
        }
        gamer.setNewPosition(currx, curry, ROW, COLUMN, BLOCK);
        gamer.setState(INIT);
      } else if(land[currx][curry].hasTree() && gamer.kind() == LUMBERJACK) { //Cut trees along the way
        land[currx][curry].cutTree();
        // 
        // Debug
        // System.println("Cut tree while moving");
        //
      }
      // Not at destination - do nothing
      return;
    }
  }

  /**
   ** Only for Debugging 
   **/
  public void printLand(GameMap[][] land, int row, int col) {
    for (int i = 0; i < row; i++) {
      for (int j = 0; j < col; j++) {
        land[i][j].print();
      }
      System.println("");
    }
  }

  /**
   * Parse the command line options.
   **/
  public static void parseCmdLine(String args[], RainForest rf) {
    int i = 0;
    rf.numThreads = new Integer(args[0]).intValue();
    String arg;
    while(i < args.length && args[i].startsWith("-")) {
      arg = args[i++];
      //check options
      if(arg.equals("-h")) {
        rf.usage();
      }
    }

    if(rf.numThreads == 0)
      rf.usage();
  }

  /**
   * The usage routine which describes the program options.
   **/
  public void usage() {
    System.println("usage: ./Game.bin -N <threads>\n");
    System.printString("    -N the number of threads\n");
    System.printString("    -h help with usage\n");
  }

  /**
   ** Check the number of trees in a given area
   ** @return true if area covered more than the zone for trees 
   **/
  public boolean hasMoreTrees(GameMap[][] land, int x, int y) {
    int lowx = x - BLOCK;
    int highx = x + BLOCK;
    int lowy = y - BLOCK;
    int highy = y + BLOCK;
    // define new boundaries
    if (lowx <= 0) 
      lowx = 1;
    if (lowy <= 0) 
      lowy = 1;
    if (highx >= ROW-1) 
      highx = ROW-2;
    if (highy >= COLUMN-1) 
      highy = COLUMN-2;
    int treeCount = 0;
    int areaCount = 0;
    for(int i = lowx; i < highx; i++) {
      for(int j = lowy; j < highy; j++) {
        if(land[i][j].tree != null) 
          treeCount++;
        areaCount++;
      }
    }
    if(treeCount >= (TREE_ZONE * areaCount)) {
      return true;
    }
    return false;
  }
}
