public class TertiaryTest {
  static public void main( String[] args ) {
    int x = 3;
    int y = x<5 ? 6 : 1000;
    int z = x>1 ? y>7 ? 2000 : 8 : 3000;
    System.out.println( "x should be 3: "+x );
    System.out.print  ( "y should be 6: "+y+"\n" );
    System.out.println( "z should be 8: "+z );
  }
}