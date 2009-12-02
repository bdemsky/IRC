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

task Startup( StartupObject s{ initialstate } )
{
  //System.printString("Top of task Startup\n");
  SubProblem top = new SubProblem(){ findingNewFits, main };


    // use this initialization to solve the above example
    //  +-----+  +-----+  +-----+  +-----+
    //  |  3  |  |  4  |  |  9  |  |  3  |
    //  |2   1|  |5   5|  |1   1|  |5   2|
    //  |  4  |  |  3  |  |  4  |  |  9  |
    //  +-----+, +-----+, +-----+, +-----+
    top.tilesToFit     = new Tile[3];
    top.tilesToFit[0]  = new Tile(  3,  4,  1,  2 );
    top.tilesToFit[1]  = new Tile(  4,  3,  5,  5 );
    top.tilesToFit[2]  = new Tile(  9,  4,  1,  1 );

    top.tilesFitted    = new Tile[1];
    top.tilesFitted[0] = new Tile(  3,  9,  2,  5 );



    /*
    top.tilesToFit     = new Tile[2];
    top.tilesToFit[0]  = new Tile(  3,  2,  3,  1 );
    top.tilesToFit[1]  = new Tile(  2, -4, -4, -4 );

    top.tilesFitted    = new Tile[1];
    top.tilesFitted[0] = new Tile( -1, -1,  1, -1 );
     */

    /*
    top.tilesToFit     = new Tile[3];
    top.tilesToFit[0]  = new Tile(  2,  1, -1,  0 );
    top.tilesToFit[1]  = new Tile(  1,  3,  0, -1 );
    top.tilesToFit[2]  = new Tile( -1,  1, -1,  0 );
    */

    //top.tilesToFit[3]  = new Tile(  1,  2,  2, -1 );
    //top.tilesToFit[4]  = new Tile(  2,  2,  1,  2 );
    //top.tilesToFit[5]  = new Tile( -1,  1,  0,  1 );

    /*
    top.tilesFitted    = new Tile[1];
    top.tilesFitted[0] = new Tile(  1, -1,  0,  2 );
    */

    top.indexToFit  = 0;
    top.indexFitted = 0;
    top.workingGrid = 
//	new TileGrid( (top.tilesToFit.length+1)*2 + 1 );
	new TileGrid( (top.tilesToFit.length+5)*2 + 4 );

    // put first fitted tile in the middle of the grid
    top.tilesFitted[0].x = top.workingGrid.gridSize/2;
    top.tilesFitted[0].y = top.workingGrid.gridSize/2;
    top.workingGrid.grid[top.tilesFitted[0].x]
                         [top.tilesFitted[0].y] = 0;

    top.highScore      = 0;
    GlobalCounter counter = new GlobalCounter() {Init};
    taskexit( s{ !initialstate } );
}

task findNewFits(/*optional*/ SubProblem sp{ findingNewFits }, GlobalCounter counter{ Init })
{
  /*
	if(!isavailable(sp)) {
		counter.partial = true;
		taskexit( sp{ !findingNewFits } );
	}
  */

	//System.printString("Top of task findNewFits\n");
    // if we have run out of iterations of the
    // findNewFits task, mark waitingForSubProblems
    if( sp.indexToFit == sp.tilesToFit.length )
    {
	//System.printString( "****************************************\nFinish iterating intermediate subproblem\n*********************************\n");
	taskexit( sp{ !findingNewFits } );
    }

    //System.printString( "###################################\n" );
    //sp.workingGrid.printGrid( sp.tilesFitted );
    //System.printString( "Want to add this tile:\n" );
    //sp.tilesToFit[sp.indexToFit].printTile();
    //System.printString( "to this tile:\n" );
    //sp.tilesFitted[sp.indexFitted].printTile();

    //System.printString( "+++++++++++++++++++++++++++++++++++\nchecking if there is a fit:\n" );

    if( sp.workingGrid.validFitNorth(
	    sp.tilesToFit [sp.indexToFit], 
	    sp.tilesFitted[sp.indexFitted],
	    sp.tilesFitted ) )
    {
	//System.printString( "North: \n" );
	SubProblem newSP = null;
	if(sp.tilesToFit.length == 1 ) {
	    newSP = new SubProblem() { !scored, leaf };
	    ++counter.counter;
	} else {
	    newSP = new SubProblem() { findingNewFits };
	}
	sp.initializeSubProblem( newSP, 1 );
	//System.printString( "match! new a SubProblem\n" );
    }

    if( sp.workingGrid.validFitSouth( 
	    sp.tilesToFit [sp.indexToFit], 
	    sp.tilesFitted[sp.indexFitted],
	    sp.tilesFitted ) )
    {
	//System.printString( "South: \n" );
	SubProblem newSP = null;
	if(sp.tilesToFit.length == 1) {
	    newSP = new SubProblem() { !scored, leaf };
	    ++counter.counter;
	} else {
	    newSP = new SubProblem() { findingNewFits };
	}
	sp.initializeSubProblem( newSP, 2 );
	//System.printString( "match! new a SubProblem\n" );
    }

    if( sp.workingGrid.validFitEast(
	    sp.tilesToFit [sp.indexToFit], 
	    sp.tilesFitted[sp.indexFitted],
	    sp.tilesFitted ) )
    {
	//System.printString( "East: \n" );
	SubProblem newSP = null; 
	if(sp.tilesToFit.length == 1) {
	    newSP = new SubProblem() { !scored, leaf };
	    ++counter.counter;
	} else {
	    newSP = new SubProblem() { findingNewFits };
	}
	sp.initializeSubProblem( newSP, 3 );
	//System.printString( "match! new a SubProblem\n" );
    }

    if( sp.workingGrid.validFitWest( 
	    sp.tilesToFit [sp.indexToFit], 
	    sp.tilesFitted[sp.indexFitted],
	    sp.tilesFitted ) )
    {
	//System.printString( "West:\n" );
	SubProblem newSP = null;
	if(sp.tilesToFit.length == 1) {
	    newSP = new SubProblem() { !scored, leaf };
	    ++counter.counter;
	} else {
	    newSP = new SubProblem() { findingNewFits };
	}
	sp.initializeSubProblem( newSP, 4 );
	//System.printString( "match! new a SubProblem\nSpawn finished! Go on find new fits.\n" );
    }

    //System.printString( "Spawn finished! Go on find new fits.\n++++++++++++++++++++++++++++++++++++++\n" );

    // otherwise perform another iteration of
    // the findNewFits task
    sp.incrementIndices();
    taskexit( sp{ findingNewFits } );
}

task scoreSubProbleam(SubProblem sp{ !scored && leaf }) {
	//System.printString("Top of task scoreSubProblem\n");
    sp.scoreWorkingGrid();
    taskexit(sp { scored });
}

//check the highest score
task findHighestScore(SubProblem pSp{ !scored && main }, /*optional*/ SubProblem cSp{ scored && leaf }, GlobalCounter counter{ Init } ) {
	//System.printString("Top of task findHighestScore\n");
    --counter.counter;
    //System.printString( "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" );
    //System.printString( "find highest score:\n" + counter.counter + "\n" );
    //System.printString( "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" );

    /*
    if(isavailable(cSp)) {
	if(pSp.highScore < cSp.highScore) {
	    pSp.highScore = cSp.highScore;
	}
	if((counter.partial == true) || (cSp.partial == true)) {
	    pSp.partial = true;
	}
	} else {
	pSp.partial = true;
    }
    */

    if(counter.counter == 0) {
	taskexit(pSp{ scored }, cSp{ !leaf });
    } else {
	taskexit(cSp{ !leaf });
    }
}

task printHighestScore(SubProblem sp{ scored && main }) {
	//System.printString("Top of task printHighestScore\n");
 // if(isavailable(sp)) {
    if(sp.partial == true) {
	System.printString ( "Result may not be the best one due to some failure during execution!\n" );
    }
    System.printString( "Found highest score: " + sp.highScore + "\n" );
    /*	} else {
		System.printString( "Fail to process\n" );
	}*/
    taskexit(sp{ !scored });
}
