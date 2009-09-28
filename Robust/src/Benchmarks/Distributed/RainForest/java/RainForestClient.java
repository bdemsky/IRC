#define ROW                 400  /* columns in the map */
#define COLUMN              100  /* rows of in the map */
#define ROUNDS              512   /* Number of moves by each player */
#define PLAYERS             20   /* Number of Players when num Players != num of client machines */
#define RATI0               0.5  /* Number of lumberjacks to number of planters */
#define BLOCK               3    /* Area around the gamer to consider */
#define TREE_ZONE           0.4  /* Max percentage of trees in a zone */

#define LUMBERJACK          0    /* If lumberjack */
#define PLANTER             1    /* If a tree planter */
#define SHIFT               2    /* Shift to new location */

#define INIT                0    /* Initial state */
#define MOVING              1    /* When moving along the map */

public class RainForestClient {

  /**
   ** The random seed
   **/
  int seed;

  public RainForestClient(int seed) {
    this.seed = seed;
  }

  public RainForestClient() {

  }

  public static void main(String[] args) {
    RainForestClient rfc = new RainForestClient();
    RainForestClient.parseCmdLine(args, rfc);

    Random rand = new Random(rfc.seed);
    Socket sock = new Socket("dc-1.calit2.uci.edu",9002);
    SocketInputStream si=new SocketInputStream(sock);

    /* Read player type from Server */
    byte b[] = new byte[1]; //read planter or lumber jack
    String str1 = rfc.readFromSock(si, 1);
    String str = str1.subString(0, 1);
    int person;
    if(str.equalsIgnoreCase("L")) {
      person = LUMBERJACK;
    }
    if(str.equalsIgnoreCase("P")) {
      person = PLANTER;
    }

    // Barriers for syncronization
    Barrier barr;
    barr =  new Barrier("128.195.136.162");

    //Generate a random x and y coordinate to start with
    int maxValue = ROW - 1;
    int minValue = 1;
    int row = (rand.nextInt(Math.abs(maxValue - minValue) + 1)) + minValue;
    maxValue = COLUMN -1;
    int col = (rand.nextInt(Math.abs(maxValue - minValue) + 1)) + minValue;
    Player gamer = new Player(person, row, col, ROW, COLUMN, BLOCK);

    //
    // Debug
    // System.println("Person= "+person+" LocX= "+row+" LocY= "+col); 
    //

    GameMap[][] land = new GameMap[ROW][COLUMN];
    for(int i = 0; i<ROW; i++) {
      for(int j = 0; j<COLUMN; j++) {
        land[i][j] = new GameMap();
      }
    }
    byte buf[] = new byte[9];
    for(int i = 0; i<ROUNDS; i++) {
      String retval;
      do {
        retval = null;
        // 
        // Send the continue to read
        //
        sock.write(rfc.fillBytes("U",i));

        //
        //Read information of land object from Server
        //
        String rstr;
        while(true) {
          rstr = rfc.readFromSock(si, 9);
          buf = rstr.getBytes();
          str1 = rstr.subString(0, 1);

          /* terminate if opcode sent is "O" */
          if(str1.equalsIgnoreCase("O")) {
            break;
          } else {
            rfc.extractCoordinates(buf, land);
          }
        }

        //Debug 
        //rfc.printLand(land, ROW, COLUMN);

        //
        //Do rounds 
        //do one move per round and write to server
        //
        rfc.doOneMove(land, gamer, sock, rand);

        //Receive ACK from server and do player updates
        rstr = rfc.readFromSock(si, 1);
        retval = rstr.subString(0, 1);

        //
        //Update player position and player boundaries if success i.e. cut/plant tree
        //Continue to the next iteration
        //
        if(retval.equalsIgnoreCase("S") && ((gamer.action == 'C') || gamer.action == 'P')) {
          // 
          // Debug
          // if(gamer.action == 'C')
          //   System.println("Cutting");
          // if(gamer.action == 'P')
          //   System.println("Planting");
          //

          gamer.setNewPosition(ROW, COLUMN, BLOCK);
          gamer.setState(INIT);
          break;
        }

        //
        // If success and player moving ... continue till goal reached
        //
        if(retval.equalsIgnoreCase("S") && gamer.action == 'M') {
          //System.println("Moving");
          break;
        }

        //
        // If server returns failure then retry 
        // move with a new goal
        //
        if(retval.equalsIgnoreCase("F")) {
          // 
          // Debug
          // System.println("Failure....Retry\n");
          //
          gamer.setNewPosition(ROW, COLUMN, BLOCK);
          gamer.setState(INIT);
        }

      } while(retval.equalsIgnoreCase("F"));

      //
      //Synchronize threads
      //
      Barrier.enterBarrier(barr);
    }
    //
    //Special character "T" to terminate computation
    //
    sock.write(rfc.fillBytes("T", 0));
    sock.close();

  }

  /**
   ** Create the land Map at client's end
   ** @param b bytes read from client
   ** @param land The map to be filled
   **/
  public void extractCoordinates(byte[] b, GameMap[][] land) {
    int posX = getX(b); 
    int posY = getY(b);
    if(posX >= ROW && posY >= COLUMN) {
      System.println("Error: Trying to access elements out of bounds in the array");
    }

    if(b[0] == (byte) 'T') {
      land[posX][posY].putTree(new TreeType());
    }
    if(b[0] == (byte) 'R') {
      land[posX][posY].putRock(new RockType());
    }
  }

  int getX(byte[] b) {
    int val;
    val = ((b[1] & 0xFF) << 24) + ((b[2] & 0xFF) << 16) + ((b[3] & 0xFF) << 8) + (b[4] & 0xFF);
    return val;
  }

  int getY(byte[] b) {
    int val;
    val = ((b[5] & 0xFF) << 24) + ((b[6] & 0xFF) << 16) + ((b[7] & 0xFF) << 8) + (b[8] & 0xFF);
    return val;
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
   ** One move by the gamer
   ** @param land The map to be searched
   ** @param gamer The player making the move
   ** @return 0 is success , -1 otherwise
   **/
  public int doOneMove(GameMap[][] land, Player gamer, Socket sock, Random rand) {
    // 1. Get start(x, y) position of the player
    int currx = gamer.getX();
    int curry = gamer.getY();

    //
    //Debug
    //printLand(land, ROW, COLUMN); 
    //

    // 2. Get type of player (lumberjack or planter)
    int type = gamer.kind();

    //3. Change states
    if (gamer.getState() == INIT) {

      //gamer.debugPlayer(); 

      if (gamer.findGoal(land, rand) < 0) {
        gamer.reset(land, ROW, COLUMN, BLOCK, rand);
        gamer.action = 'M';
        sock.write(fillBytes(SHIFT, 0, 0));
        return 0;
      }

      //gamer.debugPlayer(); 

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

        gamer.reset(land, ROW, COLUMN, BLOCK, rand);
        gamer.setState(INIT);
        gamer.action = 'M';
        sock.write(fillBytes(SHIFT, 0, 0));
        return 0;
      }

      nextmove.setXY(newpath.getX(0), newpath.getY(0));
      gamer.setPosition(nextmove.getX(), nextmove.getY());
      //
      //Debug
      //gamer.debugPlayer();
      //
      currx = gamer.getX();
      curry = gamer.getY();
      if (gamer.atDest()) {
        if (gamer.kind() == LUMBERJACK) {
          if (land[currx][curry].hasTree()) {
            //Send next move to server
            gamer.action = 'C';
            sock.write(fillBytes(LUMBERJACK, currx, curry));
            return 0;
          } 
          sock.write(fillBytes(SHIFT, 0, 0));
          return 0;
        } else { // PLANTER
          if (land[currx][curry].hasTree() == false) {
            if(hasMoreTrees(land, currx, curry) == false) {
              //Send next move to server
              gamer.action = 'P';
              sock.write(fillBytes(PLANTER, currx, curry));
              return 0;
            }
            sock.write(fillBytes(SHIFT, 0, 0));
            return 0;
          } 
          sock.write(fillBytes(SHIFT, 0, 0));
          return 0;
        } 
      } else {
        if(land[currx][curry].hasTree() && gamer.kind() == LUMBERJACK) { //Cut trees along the way
          //
          //Send next move to server
          //
          gamer.action = 'C';
          sock.write(fillBytes(LUMBERJACK, currx, curry));
          return 0;
        }
        // Not at destination - do nothing
        gamer.action = 'M';
        sock.write(fillBytes(SHIFT, 0, 0));
      }
      
      return 0;
    }
  }

  /**
   ** Check the number of trees in a given area
   **
   ** @param land The map to be searched
   ** @param x The x coordinate to plant a tree
   ** @param y The y coordinate to plant a tree
   **
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
        if(land[i][j].hasTree()) 
          treeCount++;
        areaCount++;
      }
    }
    if(treeCount >= (TREE_ZONE * areaCount)) {
      return true;
    }
    return false;
  }

  /** Fill byte array
   ** @param type The player kind 1 => Planter 0=> Lumberjack 2=> Shift to new location
   ** @param x The x coordinate of player
   ** @param y The y coordinate of player
   **/
  byte[] fillBytes(int type, int x, int y) {
    byte[] b = new byte[9]; //1 byte for the move + 8 bytes for x and y
    if(type == PLANTER)
      b[0] = (byte)'P'; //planting
    if(type == LUMBERJACK)
      b[0] = (byte)'C'; // cutting
    if(type == SHIFT)
      b[0] = (byte)'M'; //moving
    for(int i = 1; i<5; i++) {
      int offset = (3-(i-1)) * 8;
      b[i] = (byte) ((x >> offset) & 0xFF);
    }
    for(int i = 5; i<9; i++) {
      int offset = (3-(i-5)) * 8;
      b[i] = (byte) ((y >> offset) & 0xFF);
    }
    return b;
  }

  /** Fill byte array for round index and termination
   ** @param x The x coordinate of player
   ** @param y The y coordinate of player
   **/
  byte[] fillBytes(String str, int index) {
    byte[] b = new byte[5]; //1 byte for the control msg + 4 bytes for roundIndex 
    if(str.equalsIgnoreCase("U")) {
      b[0] = (byte)'U';
    }

    if(str.equalsIgnoreCase("T")) {
      b[0] = (byte)'T';
    }

    for(int i = 1; i<5; i++) {
      int offset = (3-(i-1)) * 8;
      b[i] = (byte) ((index >> offset) & 0xFF);
    }

    return b;
  }


  /**
   ** Repeated read until you get all bytes
   **/
  String readFromSock(SocketInputStream si, int maxBytes) {
    byte []b=new byte[maxBytes];
    if (si.readAll(b)<0)
        System.out.println("Error\n");
    return new String(b);
  }

  /**
   * Parse the command line options.
   **/
  public static void parseCmdLine(String args[], RainForestClient rfc) {
    int i = 0;
    String arg;
    while(i < args.length && args[i].startsWith("-")) {
      arg = args[i++];
      //check options
      if(arg.equals("-seed")) {
        if(i < args.length) {
          rfc.seed = new Integer(args[i++]).intValue();
        }
      } else if(arg.equals("-h")) {
        rfc.usage();
      }
    }

    if(rfc.seed <= 0)
      rfc.usage();
  }

  /**
   * The usage routine which describes the program options.
   **/
  public void usage() {
    System.printString("usage: ./Client.bin -seed <seed value for Random variable>\n");
    System.printString("    -seed the seed value for Random\n");
    System.printString("    -h help with usage\n");
  }
}
