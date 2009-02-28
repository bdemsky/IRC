#define ROW       10     /* columns in the map */
#define COLUMN    10    /* rows of in the map */
#define ROUNDS    100   /* Number of moves by each player */
#define PLAYERS   20    /* Number of Players when num Players != num of client machines */
#define RATI0     0.5   /* Number of lumberjacks to number of planters */
#define BLOCK     3     /* Area around the gamer to consider */
#define TREE_ZONE 0.4   /* Max percentage of trees in a zone */

#define LUMBERJACK 0
#define PLANTER    1

public class RainForest extends Thread {
  GameMap land;
  Player gamer;

  public RainForest(GameMap land, Player gamer) {
    this.land = land;
    this.gamer = gamer;
  }

  public void run() {
    // For N interations do one move and synchronise
    System.println("Start run\n");
    Barrier barr;
    barr = new Barrier("128.196.136.162");
    for(int i = 0; i<ROUNDS; i++) {
      atomic {
        doOneMove(land, gamer);
      }
      Barrier.enterBarrier(barr);
    }
    System.println("End of run\n");
  }

  public static void main(String[] args) {
    int numThreads= 1;
    BarrierServer mybarr;

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
    atomic {
      mybarr = global new BarrierServer(numThreads);
      world = global new GameMap[ROW][COLUMN];
      int i, j;
      for (i = 0; i < ROW; i++) {
        for (j = 0; j < COLUMN; j++) {
          world[i][j] = global new GameMap();
          if (j == 0 || j == COLUMN-1) {
            RockType r = global new RockType();
            world[i][j].putRock(r);
          }
        }
        if (i == 0 || i == ROW-1) {
          RockType r = global new RockType();
          world[i][j].putRock(r);
        }
      }
    }

    mybarr.start(mid[0]);

    // Create P players
    // Parse args get number of threads
    // For each thread, init either a lumberjack/planter
    Player[] players;
    atomic {
      players = global new Player[numThreads];
      for (int i = 0; i < numThreads; i++) {
        Random rand = new Random(i);
        int row = rand.nextInt(ROW-1);
        int col = rand.nextInt(COLUMN-1);
        int type = rand.nextInt(1);
        int person;
        if (type == 0) {
          person = LUMBERJACK;
        } else {
          person = PLANTER;
        }
        players[i] = global new Player(person, row, col, i, ROW, COLUMN, BLOCK);
      }
    }

    // Set up threads 
    RainForest[] rf;
    atomic {
      rf = global new RainForest[numThreads];
      for(int i=0; i<numThreads; i++) {
        Random rand = new Random(i);
        int row = rand.nextInt(ROW-1);
        int col = rand.nextInt(COLUMN-1);
        rf[i] = global new RainForest(world[row][col], players[i]);
      }
    }

    boolean waitforthreaddone = true;
    while(waitforthreaddone) {
      atomic {
        if(mybarr.done)
          waitforthreaddone = false;
      }
    }

    RainForest tmp;
    /* Start threads */
    for(int i = 0; i<numThreads; i++) {
      atomic {
        tmp = rf[i];
      }
      tmp.start(mid[i]);
    }

    /* Join threads */
    for(int i = 0; i<numThreads; i++) {
      atomic {
        tmp = rf[i];
      }
      tmp.join();
    }

    System.printString("Finished\n");

  }

  //TODO
  public void doOneMove(GameMap land, Player mover) {
    // 1. Get start(x, y) position of the player
    // 2. Get type of player (lumberjack or planter)
    // 3. Check if you have a goal(tx,ty)
    //     a.If yes 
    //        check retvalue = run A* from my location to goal(tx,ty) 
    //        if retvalue = empty
    //          pick new goal
    //          go to 3
    //          i.e wait here 
    //          go to next round
    //        else {
    //          if planter
    //            check if you are at goal
    //               if yes
    //                 can you plant tree there? i.e. 40% of block around is not full and there is no tree there
    //                   if yes
    //                     plant tree
    //                     reset start(x, y) to goal(tx, ty) position
    //                     set goal to empty
    //                     continue to next round
    //                   if no
    //                     reset start(x, y) to goal(tx, ty) position
    //                     set goal = pick new goal
    //                     go to 3
    //               if no
    //                 move one step i.e. update player position
    //                 continue to next round
    //          if lumberjack
    //            check if you are at goal
    //              if yes
    //                can you cut tree(i.e. Tree present at that location)
    //                  if yes 
    //                    cut tree
    //                    set start(x,y) to goal(tx, ty) position
    //                    set goal to empty
    //                    continue to next round
    //                  if no
    //                    reset start(x,y) to goal coordinates(tx,ty)
    //                    set goal = pick new goal
    //                    go to 3
    //              if no
    //                move one step i.e, update player position
    //                continue to next round
    //        }
    //      b.If no
    //         pick new goal
    //         go to 3
  }

  public int pickNewGoalX(Player mover) {
    //Randomly generate goal
    Random rand = new Random(mover.x);
    int row = rand.nextInt(ROW-1);
    //int col = rand.nextInt(COLUMN-1);
    return row;
  }

  public int pickNewGoalY(Player mover) {
    //Randomly generate goal
    Random rand = new Random(mover.y);
    int col = rand.nextInt(COLUMN-1);
    return col;
  }
}
