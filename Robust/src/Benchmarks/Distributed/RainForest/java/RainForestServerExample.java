#define ROW                 100  /* columns in the map */
#define COLUMN              100  /* rows of in the map */

public class RainForestServerExample {
  private int numThreads;

  public RainForestServerExample() {

  }

  public static int main(String args[]) {
    int numThreads;
    if(args.length>0) {
      numThreads = Integer.parseInt(args[0]);
    }

    /**
     * Create shared Map 
     **/
    // Init land and place rocks in boundaries
    GameMap[][] world;
    world =  new GameMap[ROW][COLUMN];
    for (int i = 0; i < ROW; i++) {
      for (int j = 0; j < COLUMN; j++) {
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

    ServerSocket ss = new ServerSocket(9002);
    acceptConnection(ss, world, numThreads);
  }

  public static void acceptConnection(ServerSocket ss, GameMap[][] world, int numThreads) {
    //
    //Start Barrier server
    //
    BarrierServer mybarr = new BarrierServer(numThreads);
    mybarr.start();

    /* Set up threads */
    RainForestServerThread[] rft = new RainForestServerThread[numThreads];
    for(int i=0; i<numThreads; i++) {
      Socket s = ss.accept();
      rft[i] = new RainForestServerThread(world, s, ROW, COLUMN, i);
    }

    /* Wait for messages */
    boolean waitforthreaddone = true;
    while(waitforthreaddone) {
      if(mybarr.done)
        waitforthreaddone = false;
    }

    /* Start threads */
    for(int i = 0; i<numThreads; i++) {
      rft[i].start();
    }

    /* Join threads */
    for(int i = 0; i<numThreads; i++) {
      rft[i].join();
    }

    System.printString("Finished\n");
  }

  /**
   * Parse the command line options.
   **/
  public static void parseCmdLine(String args[], RainForestServerExample rf) {
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
}
