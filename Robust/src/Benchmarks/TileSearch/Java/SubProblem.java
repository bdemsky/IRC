public class SubProblem {

	public SubProblem(){
	partial = false;
    }

    public Tile[]   tilesToFit;
    public Tile[]   tilesFitted;
    public TileGrid workingGrid;

    // these indices are into the respective
    // tile arrays
    public int indexToFit;
    public int indexFitted;

    // this score represents the evaluation
    // of every arrangement of this sub-problem's
    // bestArrangements list
    public int highScore;

    public boolean partial;

    public void incrementIndices() {
	++indexFitted;
	if( indexFitted == tilesFitted.length ) {
	    indexFitted = 0;
	    ++indexToFit;
	}
    }

    public void initializeSubProblem( SubProblem nsp, int checkingFits ) {
	nsp.tilesToFit = new Tile[tilesToFit.length - 1];
	nsp.indexToFit = 0;

	int j = 0;
	for( int i = 0; i < tilesToFit.length; ++i ) {
	    // copy everything but the tile that
	    // is being moved to the fitted list
	    if( i != indexToFit ) {
		nsp.tilesToFit[j] = tilesToFit[i].copy();
		++j;
	    }
	}

	nsp.tilesFitted = new Tile[tilesFitted.length + 1];
	nsp.tilesFitted[nsp.tilesFitted.length - 1] = tilesToFit[indexToFit].copy();
	nsp.indexFitted = 0;
	for( int i = 0; i < tilesFitted.length; ++i ) {
	    nsp.tilesFitted[i] = tilesFitted[i].copy();
	    //	if((checkingFits == 1) || 
	    //			(checkingFits == 3)) {
	    nsp.tilesFitted[i].x = tilesFitted[i].x;
	    nsp.tilesFitted[i].y = tilesFitted[i].y;
	    //	}
	}

	// set fitted tiles position according to fit type
	if( checkingFits == 1 ) {
	    nsp.tilesFitted[nsp.tilesFitted.length - 1].x = 
		tilesFitted[indexFitted].x;
	    nsp.tilesFitted[nsp.tilesFitted.length - 1].y = 
		tilesFitted[indexFitted].y - 1;
	} else if( checkingFits == 2 ) {
	    nsp.tilesFitted[nsp.tilesFitted.length - 1].x = 
		tilesFitted[indexFitted].x;
	    nsp.tilesFitted[nsp.tilesFitted.length - 1].y = 
		tilesFitted[indexFitted].y + 1;
	} else if( checkingFits == 3 ) {
	    nsp.tilesFitted[nsp.tilesFitted.length - 1].x = 
		tilesFitted[indexFitted].x + 1;
	    nsp.tilesFitted[nsp.tilesFitted.length - 1].y = 
		tilesFitted[indexFitted].y;
	} else { // ( checkingFits == 4 ) 
	    nsp.tilesFitted[nsp.tilesFitted.length - 1].x = 
		tilesFitted[indexFitted].x - 1;
	    nsp.tilesFitted[nsp.tilesFitted.length - 1].y = 
		tilesFitted[indexFitted].y;
	}

	// copy grid and place newly fitted tile in sub-problem's
	// version of the grid
	nsp.workingGrid = workingGrid.copy();
	nsp.workingGrid.grid[nsp.tilesFitted[nsp.tilesFitted.length - 1].x]
	                     [nsp.tilesFitted[nsp.tilesFitted.length - 1].y] =
	                	 nsp.tilesFitted.length - 1;

	nsp.highScore      = highScore;

	/*
	System.printString( "-----------new sub-problem------------\n" );
	//System.printString( "raw grid\n" );
	//nsp.workingGrid.printGridRaw();
	System.printString( "tiles fitted:\n" );
	nsp.printTileArray( nsp.tilesFitted );
	System.printString( "tiles to fit:\n" );
	nsp.printTileArray( nsp.tilesToFit );
	//System.printString( "the nice grid:\n" );
	//nsp.workingGrid.printGrid( nsp.tilesFitted );
	System.printString( "nsp.indexToFit: " );
	System.printInt( nsp.indexToFit );
	System.printString( "\n" );
	System.printString( "nsp.indexFitted: " );
	System.printInt( nsp.indexFitted );
	System.printString( "\n" );
	System.printString( "-----------end sub-problem------------\n" );
	 */
    }

    public void scoreWorkingGrid() {
	highScore = 0;
	for( int i = 0; i < tilesFitted.length; ++i ) {
	    Tile tileToScore = tilesFitted[i];
	    // add those face values that are not adjacent to other face
	    // N
	    if(this.workingGrid.grid[tileToScore.x][tileToScore.y-1] == -1) {
		highScore += tileToScore.n;
	    }
	    // S
	    if(this.workingGrid.grid[tileToScore.x][tileToScore.y+1] == -1) {
		highScore += tileToScore.s;
	    }
	    // E
	    if(this.workingGrid.grid[tileToScore.x+1][tileToScore.y] == -1) {
		highScore += tileToScore.e;
	    }
	    // W
	    if(this.workingGrid.grid[tileToScore.x-1][tileToScore.y] == -1) {
		highScore += tileToScore.w;
	    }
	}
    }
    
    public int highestScore() {
	int score = 0;
	
	if( this.tilesToFit.length == 0 ){
	    scoreWorkingGrid();
	    score = this.highScore;
	} else {
	    while(indexToFit != tilesToFit.length) {
		if( workingGrid.validFitNorth(tilesToFit [indexToFit], tilesFitted[indexFitted], tilesFitted ) ) {
		    //System.printString( "North: \n" );
		    SubProblem newSP = new SubProblem();
		    initializeSubProblem( newSP, 1 );
		    int temp = newSP.highestScore();
		    if(score < temp) {
			score = temp;
		    }
		    //System.printString( "match! new a SubProblem\n" );
		}

		if( workingGrid.validFitSouth(tilesToFit [indexToFit], tilesFitted[indexFitted], tilesFitted ) ) {
		    //System.printString( "South: \n" );
		    SubProblem newSP = new SubProblem();
		    initializeSubProblem( newSP, 2 );
		    int temp = newSP.highestScore();
		    if(score < temp) {
			score = temp;
		    }
		    //System.printString( "match! new a SubProblem\n" );
		}

		if( workingGrid.validFitEast(tilesToFit [indexToFit], tilesFitted[indexFitted], tilesFitted ) ) {
		    //System.printString( "East: \n" );
		    SubProblem newSP = new SubProblem();
		    initializeSubProblem( newSP, 3 );
		    int temp = newSP.highestScore();
		    if(score < temp) {
			score = temp;
		    }
		    //System.printString( "match! new a SubProblem\n" );
		}

		if( workingGrid.validFitWest(tilesToFit [indexToFit], tilesFitted[indexFitted], tilesFitted ) ) {
		    //System.printString( "West: \n" );
		    SubProblem newSP = new SubProblem();
		    initializeSubProblem( newSP, 4 );
		    int temp = newSP.highestScore();
		    if(score < temp) {
			score = temp;
		    }
		    //System.printString( "match! new a SubProblem\nSpawn finished! Go on find new fits.\n" );
		}

		incrementIndices();
	    }
	}
	
	return score;
    }

    public void printTileArray( Tile tiles[] ) {
	for( int i = 0; i < tiles.length; ++i ) {
	    tiles[i].printRow0();
	    System.printString( "  " );
	}
	System.printString("\n");

	for( int i = 0; i < tiles.length; ++i ) {
	    tiles[i].printRow1();
	    System.printString( "  " );
	}
	System.printString("\n");

	for( int i = 0; i < tiles.length; ++i ) {
	    tiles[i].printRow2();
	    System.printString( "  " );
	}
	System.printString("\n");

	for( int i = 0; i < tiles.length; ++i ) {
	    tiles[i].printRow3();
	    System.printString( "  " );
	}
	System.printString("\n");

	for( int i = 0; i < tiles.length; ++i ) {
	    tiles[i].printRow4();
	    System.printString( ", " );
	}
	System.printString("\n");
    }    
}
