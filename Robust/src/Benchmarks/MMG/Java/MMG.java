public class MMG {
    public MMG() {}
    
    public static void main(String[] args) {
	MMG mmg = new MMG();
	
	//System.printString("Startup\n");
	int nrofpacs = 4;
	int nrofghosts = 8;
	Map map = new Map(nrofpacs, nrofghosts);
	
	// Initiate the map
	map.init();
	    
	int i = 0;
	// create ghosts
	for(i = 0; i < map.nrofghosts; i++) {
	    Ghost ghost = new Ghost(7, 7, map);
	    ghost.setTarget(i%map.nrofpacs);
	    ghost.index = i;
	    map.placeGhost(ghost);
	    map.targets[i] = ghost.target;
	    map.ghosts[i] = ghost;
	}
	// create pacmen
	int tx = 14;
	int ty = 14;
	for(i = 0; i < map.nrofpacs; i++) {
	    Pacman pacman = new Pacman(5, 7, map);
	    pacman.setTarget(tx*(i/2), ty*(i%2));
	    pacman.index = i;
	    map.placePacman(pacman);
	    map.desX[i] = tx*(i/2);
	    map.desY[i] = ty*(i%2);
	    map.pacmen[i] = pacman;
	    //System.printString("destination: " + map.desX[i] + "," + map.desY[i] + "\n");
	}

	map.ghostcount = 0;
	map.paccount = 0;
	
	while(map.nrofpacs > 0) {
	    // try to move ghost
	    for(i = 0; i < map.nrofghosts; i++) {
		map.ghosts[i].tryMove();
	    }
	    // try to move pacmen
	    for(i = 0; i < map.nrofpacs; i++) {
		map.pacmen[i].tryMove();
	    }
	    
	    // update ghosts
	    for(i = 0; i < map.nrofghosts; i++) {
		map.ghosts[i].doMove();
		map.placeGhost(map.ghosts[i]);
	    }
	    /*for(i = 0; i < map.nrofghosts; i++) {
		System.printString("(" + map.ghostsX[i] + "," + map.ghostsY[i] + ") ");
	    }
	    System.printString("\n");*/
	    // update pacmen
	    for(i = 0; i < map.nrofpacs; i++) {
		map.pacmen[i].doMove();
		map.placePacman(map.pacmen[i]);
		//System.printString("Pacman " + map.pacmen[i].index + ": (" + map.pacMenX[map.pacmen[i].index] + "," + map.pacMenY[map.pacmen[i].index] + ")\n");
		boolean death = map.check(map.pacmen[i]);
		/*if(death) {
		    System.printString("Pacman " + map.pacmen[i].index + " caught!\n");
		}*/
	    }
	    map.nrofpacs -= map.deathcount;
	    //System.printString(map.nrofpacs + " pacmen left. \n");
	    
	    // reset for next run
	    map.paccount = 0;
	    map.deathcount = 0;
	}
	
	System.printString("Finish\n");
    }
}