import java.io.*;

public class thing {

  static public void main( String arg[] ) {

    int z0 = 200000;
    int z1 = 198765;
    int x = (z0 >>> 9) | (z1 << 7) & 0xFFFF;

    System.out.println( "x = "+x );
  }

}
