#define PLAYERS             20   /* Number of Players when num Players != num of client machines */
#define RATI0               0.5  /* Number of lumberjacks to number of planters */
#define BLOCK               3    /* Area around the gamer to consider */
#define TREE_ZONE           0.4  /* Max percentage of trees in a zone */
#define AGEUPDATETHRESHOLD  10   /* How frequently/how many rounds to increment age of tree */
#define MAXAGE              100  /* Max age of a tree */

public class RainForestServerThread extends Thread {
  GameMap[][] land;
  Socket sock;
  SocketInputStream si;
  int rows;
  int cols;
  int id;

  public RainForestServerThread(GameMap[][] land, Socket sock, int rows, int cols, int id) {
    this.land = land;
    this.sock = sock;
    this.rows = rows;
    this.cols = cols;
    this.id = id;
    this.si=new SocketInputStream(sock);
  }

  public void run() {
    /* Assign a player to be a lumberjack or planter */
    byte b[] = new byte[1];     //1 byte control to determine if lumberjack/planter
    if((id&1) != 0) 
      b[0] = (byte)'L';
    else
      b[0] = (byte)'P';
    sock.write(b);

    //
    //Debugging
    //printLand(land, rows, cols); 
    //

    byte[] buf = new byte[5];    //1 byte to decide if terminate or continue + 4 bytes for getting the round index
    byte[] buffer = new byte[9]; //1 byte presence of tree/rocks + 8 bytes for their x and y coordinates

    while(true) {
      /* Check for termination character */
      String readStr = readFromSock(5); 
      String str1 = readStr.subString(0, 1);

      /* terminate if opcode sent is "t" */
      if(str1.equalsIgnoreCase("T")) {
        break;
      } else if(str1.equalsIgnoreCase("U")) {
        buf = readStr.getBytes();
        int roundIndex = getX(buf); //Get the index from client

        /* Update age of all trees in a Map */
        if((roundIndex % AGEUPDATETHRESHOLD) == 0 && (id == 0)) { 
          updateAge(land, MAXAGE, rows, cols);
        }

        /* Send data representing presence/absence of trees */
        for(int i=0 ; i<rows; i++) {
          for(int j=0; j<cols; j++) {
            sock.write(fillBytes(land, i, j, buffer));
          }
        }

        /* Send special character "O" to end transfer of land coordinates */ 
        sock.write(fillBytes(0, 0, buffer));

        /* Read client's move */
        readStr = readFromSock(9);
        str1 = readStr.subString(0, 1);
        buffer = readStr.getBytes();

        /* Take actions such as cutting or planting or moving */
        if(str1.equalsIgnoreCase("C")) {
          int val;
          if((val = doTreeCutting(land, buffer)) == 0) {
            b[0] = (byte)'S'; //success in move
          } else {
            b[0] = (byte)'F';//failure in move
          }
          sock.write(b);
        }

        if(str1.equalsIgnoreCase("P")) {
          int val;
          if((val = doTreePlanting(land, buffer)) == 0) {
            b[0] = (byte)'S';
          } else {
            b[0] = (byte)'F';
          }
          sock.write(b);
        }

        if(str1.equalsIgnoreCase("M")) {
          b[0] = (byte)'S';
          sock.write(b);
        }
      }
    } //end while

    //
    //Debugging
    //printLand(land, rows, cols);
    //
    sock.close();

  }//end run()

  /**
   ** fill byte array
   **/
  byte[] fillBytes(GameMap[][] land, int x, int y, byte[] b) {
    if(land[x][y].hasTree()) 
      b[0] = (byte)'T';
    if(land[x][y].hasRock())
      b[0] = (byte)'R';
    if(!land[x][y].hasRock() && !land[x][y].hasTree())
      b[0] = (byte)'N';
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

  byte[] fillBytes(int x, int y, byte[] b) {
    if(x == 0 && y == 0) 
      b[0] = (byte)'O'; //land object coordinates transfer to client over
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
   * Synchronize threads that are cutting trees
   **/
  synchronized int doTreeCutting(GameMap[][] land, byte[] b) {
    int posX = getX(b);
    int posY = getY(b);

    /* Cut, if tree present */
    if(land[posX][posY].hasTree()) {
      land[posX][posY].cutTree();
      return 0;
    }

    return -1;
  }

  /**
   * Synchronize threads that are planting trees
   **/
  synchronized int doTreePlanting(GameMap[][] land, byte[] b) {
    int posX = getX(b);
    int posY = getY(b);

    /* Plant if no tree */
    if(!land[posX][posY].hasTree()) {
      TreeType t = new TreeType();
      land[posX][posY].putTree(t);
      return 0;
    }

    return -1;
  }

  /**
   ** Get X coordinate
   **/
  int getX(byte[] b) {
    int val;
    val = ((b[1] & 0xFF) << 24) + ((b[2] & 0xFF) << 16) + ((b[3] & 0xFF) << 8) + (b[4] & 0xFF);
    return val;
  }

  /**
   ** Get Y coordinate
   **/
  int getY(byte[] b) {
    int val;
    val = ((b[5] & 0xFF) << 24) + ((b[6] & 0xFF) << 16) + ((b[7] & 0xFF) << 8) + (b[8] & 0xFF);
    return val;
  }

  /**
   ** Update age of trees in a map
   ** @param land: The map
   ** @param maxage: The max age of a tree
   ** @param rows: Rows in the map
   ** @param cols: Cols in the map
   **/

  /**
   * Synchronize threads that are cutting trees
   **/
  synchronized void updateAge(GameMap[][] land, int maxage, int rows, int cols) {
    int countTrees = 0;
    for(int i = 0; i<rows; i++) {
      for(int j = 0; j<cols; j++) {
        if(land[i][j].hasTree()) {
          if(land[i][j].tree.getage() > maxage) {
            land[i][j].tree = null;
          } else {
            land[i][j].tree.incrementage();
          }
          countTrees++;
        }
      }
    }
    //
    //Debug
    //System.println("Tree count=  "+countTrees); 
    //
  }




  /**
   ** Repeated read until you get all bytes
   **/
  String readFromSock(int maxBytes) {
    byte []b=new byte[maxBytes];
    if (si.readAll(b)<0)
	System.out.println("Error\n");
    return new String(b);
  }
}
