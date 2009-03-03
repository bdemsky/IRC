import java.io.*;
import java.net.*;
import java.util.*;

public class LookUpServerThread extends Thread {
  HashMap hmap;
  Socket sock;

  public LookUpServerThread(Socket s, HashMap h) {
    hmap = h;
    sock = s;
  }

  public void run() {
      try{
	  sock.setTcpNoDelay(true);
      } catch (Exception e) {
	  e.printStackTrace();
      }
      Random r=new Random(0);
    while(true) {
      byte b[] = new byte[1];
      InputStream in = null;
      OutputStream out = null;
      int numbytes;
      try {
        in = sock.getInputStream();
        out = sock.getOutputStream(); 
        numbytes = in.read(b);
      } catch (IOException e) {
        System.out.println("Read failed " + e);
      }

      /* terminate if opcode sent is "t" */
      if(b[0] == (byte)'t') {
        try {
          in.close();
          out.close();
          sock.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
        break;
      } else {
        byte b1[] = new byte[4];
        try {
          numbytes = in.read(b1);
        } catch (IOException e) {
          System.out.println("Read failed " + e);
        }
        int val =  getKey(b1);
        Integer keyitem = new Integer(val);
        /* read from hashmap if opcode sent is "r" */
        if(b[0] == (byte)'r') {
          Integer tmpval = doRead(this, keyitem);
          //Write object to socket for client
          try {
            out.write(intToByteArray(tmpval.intValue()));
          } catch (Exception e) {
            e.printStackTrace();
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

  /*
   * Convert byte array into int type
   **/

  int getKey(byte[] b) {
    int val;
    val = ((b[0] & 0xFF) << 24) + ((b[1] & 0xFF) << 16) + ((b[2] & 0xFF) << 8) + (b[3] & 0xFF);
    return val;
  }

  byte[] intToByteArray(int value) {
    byte[] b = new byte[4];
    for (int i = 0; i < 4; i++) {
      int offset = (b.length - 1 - i) * 8;
      b[i] = (byte) ((value >> offset) & 0xFF);
    }
    return b;
  }
}
