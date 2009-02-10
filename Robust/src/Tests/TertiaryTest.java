public class TertiaryTest {
  static public void main( String[] args ) {
    int x = 3;
    int y = x<5 ? 6 : 1000;
    int z = x>1 ? y>7 ? 2000 : 8 : 3000;
    System.printString( "x should be 3: "+x+"\n" );
    System.printString( "y should be 6: "+y+"\n" );
    System.printString( "z should be 8: "+z+"\n" );
  }
}