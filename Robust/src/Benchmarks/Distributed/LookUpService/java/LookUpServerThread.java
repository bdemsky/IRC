public class LookUpServerThread extends Thread {
  HashMap hmap;
  Socket sock;
  SocketInputStream sin;

  public LookUpServerThread(Socket s, HashMap h) {
    hmap = h;
    sock = s;
    this.sin = new SocketInputStream(sock);
  }

  public void run() {
    Random r=new Random(0);
    byte b[] = new byte[1]; //1 byte to decide if terminate or continue
    byte b1[] = new byte[4];//4 bytes to get the Key to search 
    while(true) {
      String readStr = readFromSock(1);
      String str1 = readStr.subString(0, 1);
      /* terminate if opcode sent is "t" */
      if(str1.equalsIgnoreCase("t")) {
        sock.close();
        break;
      } else {
        readStr = readFromSock(4);
        b1 = readStr.getBytes();
        int val =  getKey(b1);
        Integer keyitem = new Integer(val);
        /* read from hashmap if opcode sent is "r" */
        if(str1.equalsIgnoreCase("r")) {
          Integer tmpval = doRead(this, keyitem);
          //Write object to socket for client
          if(tmpval == null) { //If Object not found in hash map
            sock.write(fillBytes(0));
          } else {
            sock.write(tmpval.intToByteArray());
          }
        } else {
          /* update hashmap if opcode sent is "w" */
          doUpdate(r, this, keyitem);
        }
      }
    }
  }

  /**
   * Synchromize threads accessing hashmap to read key
   **/

  synchronized Integer doRead(LookUpServerThread lusth, Integer key) {
    //Read object
    Object value = lusth.hmap.get(key);
    Integer val = (Integer) value;
    return val;
  }

  /**
   * Synchromize threads accessing hashmap to update key,value pair
   **/
    synchronized void doUpdate(Random rand, LookUpServerThread lusth, Integer key) {
    //Write into hmap
    int val = rand.nextInt(200);
    Integer value = new Integer(val);
    Object oldvalue = lusth.hmap.put(key, value);
    return;
  }

  byte[] fillBytes(int val) {
    byte[] b = new byte[4];
    for(int i = 0; i<4; i++) {
      int offset = (3 - i) * 8;
      b[i] = (byte) ((val >> offset) & 0xFF);
    }
    return b;
  }

  /*
   * Convert byte array into int type
   **/

  int getKey(byte[] b) {
    int val;
    val = ((b[0] & 0xFF) << 24) + ((b[1] & 0xFF) << 16) + ((b[2] & 0xFF) << 8) + (b[3] & 0xFF);
    return val;
  }

  /**
   ** Repeated read until you get all bytes
   **/
  String readFromSock(int maxBytes) {
    byte []b=new byte[maxBytes];
    int numbytes;
    if ((numbytes = sin.readAll(b))<0)
      System.out.println("Error\n");
    //
    // Debug
    // System.println("numbytes received= " +  numbytes +" b[0]= " + (char) b[0]);
    //
    return new String(b);
  }
}
