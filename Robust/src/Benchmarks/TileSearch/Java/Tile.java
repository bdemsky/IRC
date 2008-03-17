public class Tile {
    public Tile( int n, int s, int e, int w ) {
	this.n = n;
	this.s = s;
	this.e = e;
	this.w = w;
    }

    // value of tile faces
    int n, s, e, w;

    public Tile copy() {
        Tile t = new Tile( n, s, e, w );
	return t;
    }

    public void printTile() {
	printRow0(); System.printString("\n");
	printRow1(); System.printString("\n");
	printRow2(); System.printString("\n");
	printRow3(); System.printString("\n");
	printRow4(); System.printString("\n");
    }

    public void printRow0(){ System.printString( "+-------+" ); }
    public void printRow1(){ if( n < 0 ) {
	                  System.printString( "|  " );
	                } else {
	                  System.printString( "|   " ); }
         	        System.printInt( n );
			System.printString(      "   |" ); }
    public void printRow2(){ if( w < 0 ) {
			  System.printString( "|" );
			} else {
			  System.printString( "| " ); }
	                System.printInt( w );
			if( e < 0 ) {
			  System.printString(    "  " );
			} else {
			  System.printString(    "   " ); }
	                System.printInt( e );
	                System.printString(        " |" ); }
    public void printRow3(){ if( s < 0 ) {
			  System.printString( "|  " );
			} else {
			  System.printString( "|   " ); }
         	        System.printInt( s );
			System.printString(      "   |" ); }
    public void printRow4(){ System.printString( "+-------+" ); }

    // position in the grid
    // this information is also represented by
    // the indices into a TileGrid, but it is
    // convenient to duplicate it
    int x, y;
}
