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
	printRow0(); System.printString( "\n" );
	printRow1(); System.printString( "\n" );
	printRow2(); System.printString( "\n" );
	printRow3(); System.printString( "\n" );
	printRow4(); System.printString( "\n" );
    }

    public void printRow0() { 
	System.printString  ( "+-------+" );
    }
    
    public printRow1() { 
	if( n < 0 ) {
	    System.printString( "|  " );
	} else {
	    System.printString( "|   " ); 
	}
	System.printInt( n );
	System.printString(      "   |" ); 
    }
    
    public void printRow2() { 
	if( w < 0 ) {
	    System.printString( "|" );
	} else {
	    System.printString( "| " ); 
	}
	System.printInt        ( w );
	if( e < 0 ) {
	    System.printString(    "  " );
	} else {
	    System.printString(    "   " ); 
	}
	System.printInt            ( e );
	System.printString  (        " |" ); 
    }
    
    public void printRow3() { 
	if( s < 0 ) {
	    System.printString( "|  " );
	} else {
	    System.printString( "|   " ); 
	}
	System.printInt          ( s );
	System.printString  (      "   |" ); 
    }
    
    public void printRow4() { 
	System.printString  ( "+-------+" ); 
    }

    // position in the grid
    // this information is also represented by
    // the indices into a TileGrid, but it is
    // convenient to duplicate it
    int x, y;
}

public class TileGrid {
    public TileGrid( int gridSize ) {
	// make the grid size big enough
	// such that starting with a tile
	// in the middle and placing tiles
	// in one direction, that the grid
	// is big enough without requiring
	// bound-checking
	this.gridSize = gridSize;

	grid = new int[gridSize][];
	for( int i = 0; i < gridSize; ++i ) {
	    grid[i] = new int[gridSize];
	    for( int j = 0; j < gridSize; ++j ) {
		// use -1 to indicate no tile
		grid[i][j] = -1;
	    }
	}
    }

    public int gridSize;

    // each element of this grid is an integer
    // index into a tilesFitted array -- not
    // very good object-oriented style!
    public int grid[][];

    public TileGrid copy() {
	TileGrid tg = new TileGrid( gridSize );

	for( int i = 0; i < gridSize; ++i ) {
	    for( int j = 0; j < gridSize; ++j ) {
		tg.grid[i][j] = grid[i][j];
	    }
	}

	return tg;
    }

    public boolean anyValidFit( Tile   tileToFit, 
	    Tile   tileFitted,
	    Tile[] tilesFitted ) {
	//System.printString( "top fo anyValidFit\n" );
	return validFitNorth( tileToFit, tileFitted, tilesFitted ) ||
	validFitSouth( tileToFit, tileFitted, tilesFitted ) ||
	validFitEast ( tileToFit, tileFitted, tilesFitted ) ||
	validFitWest ( tileToFit, tileFitted, tilesFitted );
    }

    public boolean validFitNorth( Tile   tileToFit,
	    Tile   tileFitted,
	    Tile[] tilesFitted ) {
	//System.printString( "top of validFitNorth\n" );
	//System.printString( "tileToFit.s:" + tileToFit.s + "\n" );
	//System.printString( "tileFitted.n:" + tileFitted.n + "\n" );

	// when the tileToFit's S matches fitted N...
	if( tileToFit.s == tileFitted.n ) {
	    tileToFit.x = tileFitted.x;
	    tileToFit.y = tileFitted.y - 1;

	    /*
	    System.printString( "Check if can put it here\n" );
	    System.printString( "x: " + tileToFit.x + "; y: " + tileToFit.y + "\n" );
	    System.printInt( grid[tileToFit.x][tileToFit.y]  );
	    System.printString( "\n" );
	    System.printInt( grid[tileToFit.x][tileToFit.y-1] );
	    if(grid[tileToFit.x][tileToFit.y-1] != -1) {
	    	System.printString( " s:" + tilesFitted[grid[tileToFit.x][tileToFit.y-1]].s );
	    }
	    System.printString( "\n" );
	    System.printInt( grid[tileToFit.x+1][tileToFit.y] );
	    if(grid[tileToFit.x+1][tileToFit.y] != -1) {
	    	System.printString( " w:" + tilesFitted[grid[tileToFit.x+1][tileToFit.y]].w );
	    }
	    System.printString( "\n" );
	    System.printInt( grid[tileToFit.x-1][tileToFit.y] );
	    if(grid[tileToFit.x-1][tileToFit.y] != -1) {
	    	System.printString( " e:" + tilesFitted[grid[tileToFit.x-1][tileToFit.y]].e );
	    }
	    System.printString( "\n" );
	     */
	    //  check that the place to fit is empty  AND
	    // (place to fit + N is empty or matches) AND
	    // (place to fit + E is empty or matches) AND
	    // (place to fit + W is empty or matches)
	    if( grid[tileToFit.x][tileToFit.y]                   == -1           &&

		    (grid[tileToFit.x][tileToFit.y-1]                == -1 ||
			    tilesFitted[grid[tileToFit.x][tileToFit.y-1]].s == tileToFit.n) &&

			    (grid[tileToFit.x+1][tileToFit.y]                == -1 ||
				    tilesFitted[grid[tileToFit.x+1][tileToFit.y]].w == tileToFit.e) &&

				    (grid[tileToFit.x-1][tileToFit.y]                == -1 ||
					    tilesFitted[grid[tileToFit.x-1][tileToFit.y]].e == tileToFit.w)   ) {
		return true;
	    }
	}

	return false;
    }

    public boolean validFitSouth( Tile   tileToFit,
	    Tile   tileFitted,
	    Tile[] tilesFitted ) {
	//System.printString( "top of validFitSouth\n" );

	// when the tileToFit's N matches fitted S...
	if( tileToFit.n == tileFitted.s ) {
	    tileToFit.x = tileFitted.x;
	    tileToFit.y = tileFitted.y + 1;

	    //  check that the place to fit is empty  AND
	    // (place to fit + S is empty or matches) AND
	    // (place to fit + E is empty or matches) AND
	    // (place to fit + W is empty or matches)
	    if( grid[tileToFit.x][tileToFit.y]                   == -1           &&

		    (grid[tileToFit.x][tileToFit.y+1]                == -1 ||
			    tilesFitted[grid[tileToFit.x][tileToFit.y+1]].n == tileToFit.s) &&

			    (grid[tileToFit.x+1][tileToFit.y]                == -1 ||
				    tilesFitted[grid[tileToFit.x+1][tileToFit.y]].w == tileToFit.e) &&

				    (grid[tileToFit.x-1][tileToFit.y]                == -1 ||
					    tilesFitted[grid[tileToFit.x-1][tileToFit.y]].e == tileToFit.w)   ) {
		return true;
	    }
	}

	return false;
    }

    public boolean validFitEast( Tile   tileToFit,
	    Tile   tileFitted,
	    Tile[] tilesFitted ) {
	//System.printString( "top of validFitEast\n" );

	// when the tileToFit's W matches fitted E...
	if( tileToFit.w == tileFitted.e ) {
	    tileToFit.x = tileFitted.x + 1;
	    tileToFit.y = tileFitted.y;

	    /*
	    System.printString( "raw grid:\n" );
	    printGridRaw();

	    System.printString( "x: " );
	    System.printInt( tileToFit.x );
	    System.printString( "\n" );

	    System.printString( "y: " );
	    System.printInt( tileToFit.y );
	    System.printString( "\n" );

	    System.printString( "tile index 1: " );
	    System.printInt( grid[tileToFit.x][tileToFit.y-1] );
	    System.printString( "\n" );

	    System.printString( "tile index 2: " );
	    System.printInt( grid[tileToFit.x][tileToFit.y+1] );
	    System.printString( "\n" );

	    System.printString( "tile index 3: " );
	    System.printInt( grid[tileToFit.x+1][tileToFit.y] );
	    System.printString( "\n" );
	     */

	    //  check that the place to fit is empty  AND
	    // (place to fit + N is empty or matches) AND
	    // (place to fit + S is empty or matches) AND
	    // (place to fit + E is empty or matches)
	    if( grid[tileToFit.x][tileToFit.y]                   == -1           &&

		    (            grid[tileToFit.x][tileToFit.y-1]    == -1 ||
			    tilesFitted[grid[tileToFit.x][tileToFit.y-1]].s == tileToFit.n) &&

			    (            grid[tileToFit.x][tileToFit.y+1]    == -1 ||
				    tilesFitted[grid[tileToFit.x][tileToFit.y+1]].n == tileToFit.s) &&

				    (            grid[tileToFit.x+1][tileToFit.y]    == -1 ||
					    tilesFitted[grid[tileToFit.x+1][tileToFit.y]].w == tileToFit.e)   ) {
		return true;
	    }
	}

	return false;
    }

    public boolean validFitWest( Tile   tileToFit,
	    Tile   tileFitted,
	    Tile[] tilesFitted ) {
	//System.printString( "top of validFitWest\n" );

	// when the tileToFit's E matches fitted W...
	if( tileToFit.e == tileFitted.w ) {
	    tileToFit.x = tileFitted.x - 1;
	    tileToFit.y = tileFitted.y;

	    //  check that the place to fit is empty  AND
	    // (place to fit + N is empty or matches) AND
	    // (place to fit + S is empty or matches) AND
	    // (place to fit + W is empty or matches)
	    if( grid[tileToFit.x][tileToFit.y]                   == -1           &&

		    (grid[tileToFit.x][tileToFit.y-1]                == -1 ||
			    tilesFitted[grid[tileToFit.x][tileToFit.y-1]].s == tileToFit.n) &&

			    (grid[tileToFit.x][tileToFit.y+1]                == -1 ||
				    tilesFitted[grid[tileToFit.x][tileToFit.y+1]].n == tileToFit.s) &&

				    (grid[tileToFit.x-1][tileToFit.y]                == -1 ||
					    tilesFitted[grid[tileToFit.x-1][tileToFit.y]].e == tileToFit.w)   ) {
		return true;
	    }
	}

	return false;
    }


    // indices to represent the bounding
    // box of tiles placed in the grid
    public int x0, y0, x1, y1;

    public void printGridRaw() {
	for( int j = 0; j < gridSize; ++j ) {
	    for( int i = 0; i < gridSize; ++i ) {
		System.printInt( grid[i][j] );

		if( grid[i][j] < 0 ) {
		    System.printString( " " );
		}
		else {
		    System.printString( "  " );
		}
	    }
	    System.printString( "\n" );
	}
    }

    public void printGrid( Tile[] tilesFitted ) {
	/*	
	System.printString( "Printing a grid...\n" );
	printGridRaw();

	computeBoundingBox();

	for( int j = y0; j <= y1; ++j )
	{
	    for( int i = x0; i <= x1; ++i )
	    {
		System.printString( "i=" );
		System.printInt( i );
		System.printString( ", j=" );
		System.printInt( j );
		//System.printString( "\n" );

		if( grid[i][j] == -1 ) {
		    printEmptyTileRow();
		} else {
		    tilesFitted[grid[i][j]].printRow0();
		}
	    }
	    System.printString( "\n" );

	    for( int i = x0; i <= x1; ++i )
	    {
		System.printString( "i=" );
		System.printInt( i );
		System.printString( ", j=" );
		System.printInt( j );
		//System.printString( "\n" );

		if( grid[i][j] == -1 ) {
		    printEmptyTileRow();
		} else {
		    tilesFitted[grid[i][j]].printRow1();
		}
	    }
	    System.printString( "\n" );

	    for( int i = x0; i <= x1; ++i )
	    {
		System.printString( "i=" );
		System.printInt( i );
		System.printString( ", j=" );
		System.printInt( j );
		//System.printString( "\n" );

		if( grid[i][j] == -1 ) {
		    printEmptyTileRow();
		} else {
		    tilesFitted[grid[i][j]].printRow2();
		}
	    }
	    System.printString( "\n" );

	    for( int i = x0; i <= x1; ++i )
	    {
		System.printString( "i=" );
		System.printInt( i );
		System.printString( ", j=" );
		System.printInt( j );
		//System.printString( "\n" );

		if( grid[i][j] == -1 ) {
		    printEmptyTileRow();
		} else {
		    tilesFitted[grid[i][j]].printRow3();
		}
	    }
	    System.printString( "\n" );

	    for( int i = x0; i <= x1; ++i )
	    {
		System.printString( "i=" );
		System.printInt( i );
		System.printString( ", j=" );
		System.printInt( j );
		//System.printString( "\n" );

		if( grid[i][j] == -1 ) {
		    printEmptyTileRow();
		} else {
		    tilesFitted[grid[i][j]].printRow4();
		}
	    }
	    System.printString( "\n" );
	}
	 */
    }

    public void printEmptyTileRow() {
	System.printString( "         " );
    }

    public void computeBoundingBox() {
	System.printString( "Starting computeBoundingBox\n" );

	int i = 0;
	while( i < gridSize*gridSize ) {
	    int a = i % gridSize;
	    int b = i / gridSize;

	    if( grid[b][a] != -1 ) {
		x0 = b;

		// this statement is like "break"
		i = gridSize*gridSize;
	    }

	    ++i;
	}

	i = 0;
	while( i < gridSize*gridSize ) {
	    int a = i % gridSize;
	    int b = i / gridSize;

	    if( grid[a][b] != -1 )  {
		y0 = b;

		// this statement is like "break"
		i = gridSize*gridSize;
	    }

	    ++i;
	}

	i = 0;
	while( i < gridSize*gridSize ) {
	    int a = i % gridSize;
	    int b = i / gridSize;
	    int c = gridSize - 1 - b;

	    if( grid[c][a] != -1 ) {
		x1 = c;

		// this statement is like "break"
		i = gridSize*gridSize;
	    }

	    ++i;
	}

	i = 0;
	while( i < gridSize*gridSize ) {
	    int a = i % gridSize;
	    int b = i / gridSize;
	    int c = gridSize - 1 - b;

	    if( grid[a][c] != -1 ) {
		y1 = c;

		// this statement is like "break"
		i = gridSize*gridSize;
	    }

	    ++i;
	}

	System.printString( "Ending computeBoundingBox\n" );
    }
}
