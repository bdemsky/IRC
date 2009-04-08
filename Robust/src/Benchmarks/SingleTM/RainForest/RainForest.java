#define ROW                 100   /* columns in the map */
#define COLUMN              100   /* rows of in the map */
#define ROUNDS              256  /* Number of moves by each player */
#define PLAYERS             20    /* Number of Players when num Players != num of client machines */
#define RATI0               0.5   /* Number of lumberjacks to number of planters */
#define BLOCK               3     /* Area around the gamer to consider */
#define TREE_ZONE           0.4   /* Max percentage of trees in a zone */
#define AGEUPDATETHRESHOLD  16    /* How frequently/how many rounds to increment age of tree */
#define MAXAGE              100   /* Max age of a tree */


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
   ** The shared BarrierServer object updated when trees increment age
   ** only updated by one thread running server 
   **/
  BarrierServer barrserver;

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

  public RainForest(GameMap[][] land, BarrierServer barrserver, int threadid, int numThreads) {
    this.land = land;
    this.threadid = threadid;
    this.barrserver = barrserver;
    this.numThreads = numThreads;
  }

  public void run() {
    //Barrier for synchronizing moves
    Barrier barr;
    int id;
    atomic {
      id = threadid;
    }
    barr = new Barrier("127.0.0.1");

    Random rand = new Random(id);
    // Generate random numbers between 1 and row index/column index
    int maxValue = ROW - 1;
    int minValue = 1;
    int row = (rand.nextInt(Math.abs(maxValue - minValue) + 1)) + minValue;
    maxValue = COLUMN -1;
    int col = (rand.nextInt(Math.abs(maxValue - minValue) + 1)) + minValue;
    //
    //Add Manual Prefetches for this.land[lowx][lowy] to this.land[highx][highy]
    //
    // Quickly determine the boundaries for given row and column
    int lowx = row - BLOCK;
    int highx = row + BLOCK;
    int lowy = col - BLOCK;
    int highy = col + BLOCK;
    // define new boundaries
    if (lowx <= 0) 
      lowx = 1;
    if (lowy <= 0) 
      lowy = 1;
    if (highx >= ROW-1) 
      highx = ROW-2;
    if (highy >= COLUMN-1) 
      highy = COLUMN-2;


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
    short[] offsets1 = new short[6];
    for(int i = 0; i<ROUNDS; i++) {
      atomic {
        doOneMove(land, gamer);
      }
      if((i&15) == 0 && id == 0) { //same as i%AGEUPDATETHRESHOLD
        /* Update age of all trees in a Map */
        atomic {
          barrserver.updateAge(land, MAXAGE, ROW, COLUMN);
        }
      }
      Barrier.enterBarrier(barr);
          }
  }

  public static void main(String[] args) {
    // Parse args get number of threads
    RainForest tmprf = new RainForest();
    RainForest.parseCmdLine(args, tmprf);
    int numThreads= tmprf.numThreads;
    BarrierServer mybarr;

    // Init land and place rocks in boundaries
    GameMap[][] world;
    atomic {
      mybarr = new BarrierServer(numThreads);
      world = new GameMap[ROW][COLUMN];
      int i, j;
      for (i = 0; i < ROW; i++) {
        for (j = 0; j < COLUMN; j++) {
          world[i][j] = new GameMap();
          if (j == 0 || j == COLUMN-1) {
            RockType r = new RockType();
            world[i][j].putRock(r);
          }
          if (i == 0 || i == ROW-1) {
            RockType r = new RockType();
            world[i][j].putRock(r);
          }
        }
      }
    }

    mybarr.start();

    /* Set up threads */
    RainForest[] rf;
    atomic {
      rf = new RainForest[numThreads];
      for(int i=0; i<numThreads; i++) {
        rf[i] = new RainForest(world, mybarr, i, numThreads);
      }
    }

    /* Barrier Server waits for messages */
    boolean waitforthreaddone = true;
    while(waitforthreaddone) {
      atomic {
        if(mybarr.done)
          waitforthreaddone = false;
      }
    }

    /* Start threads */
    RainForest tmp;
    for(int i = 0; i<numThreads; i++) {
      tmp = rf[i];
      tmp.start();
    }

    /* Join threads */
    for(int i = 0; i<numThreads; i++) {
      tmp = rf[i];
      tmp.join();
    }
    System.printString("Finished\n");
  }

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
      } else {
        if(((gamer.getGoalX() - gamer.getX()) > BLOCK) || ((gamer.getGoalY() - gamer.getY()) > BLOCK)) {
          // Quickly determine the boundaries for given row and column
          int lowx = gamer.goalx - BLOCK;
          int highx = gamer.goalx + BLOCK;
          int lowy = gamer.goaly - BLOCK;
          int highy = gamer.goaly + BLOCK;
          // define new boundaries
          if (lowx <= 0) 
            lowx = 1;
          if (lowy <= 0) 
            lowy = 1;
          if (highx >= ROW-1) 
            highx = ROW-2;
          if (highy >= COLUMN-1) 
            highy = COLUMN-2;
          //
          // Add Manual Prefetch for land[lowx][lowy] to land[highx][highy]
          //
        }
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
              TreeType t = new TreeType();
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
    String arg;
    while(i < args.length && args[i].startsWith("-")) {
      arg = args[i++];
      //check options
      if(arg.equals("-N")) {
        if(i < args.length) {
          rf.numThreads = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-h")) {
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
    System.println("usage: ./RainForestN.bin master -N <threads>\n");
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
