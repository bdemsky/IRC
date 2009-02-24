public class LookUpServerThread extends Thread {
  HashMap hmap;
  Socket sock;

  public LookUpServerThread(Socket s, HashMap h) {
    hmap = h;
    sock = s;
  }

  public void run() {
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
        int val = b1[3];
        Integer keyitem = new Integer(val);
        /* read from hashmap if opcode sent is "r" */
        if(str1.equalsIgnoreCase("r")) {
          doRead(this, keyitem);
        } else {
        /* update hashmap if opcode sent is "w" */
          doUpdate(this, keyitem);
        }
      }
    }
  }

  /**
   * Synchromize threads accessing hashmap to read key
   **/

  synchronized void doRead(LookUpServerThread lusth, Integer key) {
    //Read object
    Object value = lusth.hmap.get(key);
    Integer val = (Integer) value;
    //Write object to socket for client
    lusth.sock.write(val.intToByteArray());
    return;
  }

  /**
   * Synchromize threads accessing hashmap to update key,value pair
   **/
  synchronized void doUpdate(LookUpServerThread lusth, Integer key) {
    //Write into hmap
    Random rand = new Random(0);
    int val = rand.nextInt(200);
    Integer value = new Integer(val);
    Object oldvalue = lusth.hmap.put(key, value);
    return;
  }
}
