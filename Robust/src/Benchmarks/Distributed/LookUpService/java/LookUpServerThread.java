public class LookUpServerThread extends Thread {
  HashMap hmap;
  Socket sock;

  public LookUpServerThread(Socket s, HashMap h) {
    hmap = h;
    sock = s;
  }

  public void run() {
      Random r=new Random(0);
    while(true) {
      byte b[] = new byte[1];
      int numbytes = sock.read(b);
      String str1 = (new String(b)).subString(0, numbytes);
      /* terminate if opcode sent is "t" */
      if(str1.equalsIgnoreCase("t")) {
        sock.close();
        break;
      } else {
        byte b1[] = new byte[4];
        numbytes = sock.read(b1);
        int val =  getKey(b1);
        Integer keyitem = new Integer(val);
        /* read from hashmap if opcode sent is "r" */
        if(str1.equalsIgnoreCase("r")) {
          Integer tmpval = doRead(this, keyitem);
          //Write object to socket for client
          sock.write(tmpval.intToByteArray());
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

  /*
   * Convert byte array into int type
   **/

  int getKey(byte[] b) {
    int val;
    val = ((b[0] & 0xFF) << 24) + ((b[1] & 0xFF) << 16) + ((b[2] & 0xFF) << 8) + (b[3] & 0xFF);
    return val;
  }
}
