//////////////////////////////////////////////
//
//  tileSearch is a program to solve the
//  following problem:
//
//  Find all arrangements of N square tiles
//  that evaluate to the highest possible score.
//
//  Each tile has an integer on its north, south
//  east and west faces.  Tiles faces may only
//  be adjacent to other tile faces with the
//  same number.
//
//  Tiles may not be rotated.
//
//  All tiles in the final arrangement must
//  be adjacent to at least one other tile.
//
//  The score of an arrangement is the sum of
//  all tile face values that are not adjacent
//  to another face.
//
//  Example input:
//
//  +-----+  +-----+  +-----+  +-----+
//  |  3  |  |  4  |  |  9  |  |  3  |
//  |2   1|  |5   5|  |1   1|  |5   2|
//  |  4  |  |  3  |  |  4  |  |  9  |
//  +-----+, +-----+, +-----+, +-----+
//
//  A valid arrangement could be:
//
//  +-----++-----+
//  |  3  ||  9  |
//  |2   1||1   1|
//  |  4  ||  4  |
//  +-----++-----+
//         +-----++-----+
//         |  4  ||  3  |
//         |5   5||5   2|
//         |  3  ||  9  |
//         +-----++-----+
//
//  Which scores:
//
//  3 + 9 + 1 + 3 + 2 + 9 + 3 + 5 + 4 + 2 = 41
//
//
//  What is the highest possible score for a
//  given tile input?
//
//////////////////////////////////////////////

public class TileSearch {

    public static void main(String args[]) {
	SubProblem top = new SubProblem();
	/*
	top.tilesToFit     = new Tile[2];
	top.tilesToFit[0]  = new Tile(  3,  2,  3,  1 );
	top.tilesToFit[1]  = new Tile(  2, -4, -4, -4 );

	top.tilesFitted    = new Tile[1];
	top.tilesFitted[0] = new Tile( -1, -1,  1, -1 );
	 */


	top.tilesToFit     = new Tile[3];
	top.tilesToFit[0]  = new Tile(  2,  1, -1,  0 );
	top.tilesToFit[1]  = new Tile(  1,  3,  0, -1 );
	top.tilesToFit[2]  = new Tile( -1,  1, -1,  0 );
	//top.tilesToFit[3]  = new Tile(  1,  2,  2, -1 );
	//top.tilesToFit[4]  = new Tile(  2,  2,  1,  2 );
	//top.tilesToFit[5]  = new Tile( -1,  1,  0,  1 );

	top.tilesFitted    = new Tile[1];
	top.tilesFitted[0] = new Tile(  1, -1,  0,  2 );

	top.indexToFit  = 0;
	top.indexFitted = 0;
	top.workingGrid = new TileGrid( (top.tilesToFit.length+5)*2 + 4 ); //new TileGrid( (top.tilesToFit.length+1)*2 + 1 );
	
	// put first fitted tile in the middle of the grid
	top.tilesFitted[0].x = top.workingGrid.gridSize/2;
	top.tilesFitted[0].y = top.workingGrid.gridSize/2;
	top.workingGrid.grid[top.tilesFitted[0].x]
	                     [top.tilesFitted[0].y] = 0;

	top.highScore = 0;
	
	int score = top.highestScore();
	System.printString("Highest score: " + score + "\n");
    }
    
}
