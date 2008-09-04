public class MMG {
    public MMG() {}
    
    public static void main(String[] args) {
	MMG mmg = new MMG();
	
	//System.printString("Task Startup\n");
	int nrofpacs = 4;
	int nrofghosts = 8;
	Map map = new Map(nrofpacs, nrofghosts);
	
	// Initiate the map
	//System.printString("Task initMap\n");
	map.init();
	//System.printString("Init finish\n");
	int i = 0;
	// create ghosts
	for(i = 0; i < map.m_nrofghosts; i++) {
	    Ghost ghost = new Ghost(7, 7, map);
	    ghost.m_index = i;
	    map.placeGhost(ghost);
	    map.m_ghosts[i] = ghost;
	}
	// create pacmen
	int tx = 14;
	int ty = 14;
	for(i = 0; i < map.m_nrofpacs; i++) {
	    Pacman pacman = new Pacman(5, 7, map);
	    pacman.setTarget(tx*(i/2), ty*(i%2));
	    pacman.m_index = i;
	    map.placePacman(pacman);
	    map.m_desX[i] = tx*(i/2);
	    map.m_desY[i] = ty*(i%2);
	    map.m_pacOriX[i] = pacman.m_locX;
	    map.m_pacOriY[i] = pacman.m_locY;
	    map.m_leftLives[i] = map.m_leftLevels[i] = 60;
	    pacman.m_leftLives = pacman.m_leftLevels = 60;
	    map.m_pacmen[i] = pacman;
	    //System.printString("destination: " + map.desX[i] + "," + map.desY[i] + "\n");
	}

	map.m_ghostcount = 0;
	map.m_paccount = 0;
	
	while(map.m_nrofpacs > 0) {
	    // try to move ghost
	    for(i = 0; i < nrofghosts; i++) {
		//System.printString("Task moveGhost\n");
		map.m_ghosts[i].tryMove();
	    }
	    // try to move pacmen
	    for(i = 0; i < nrofpacs; i++) {
		if(map.m_pacMenX[i] != -1) {
		    //System.printString("Task movePacman\n");
		    map.m_pacmen[i].tryMove();
		}
	    }
	    
	    // update ghosts
	    for(i = 0; i < nrofghosts; i++) {
		//System.printString("Task updateGhost\n");
		map.m_ghosts[i].doMove();
		map.placeGhost(map.m_ghosts[i]);
	    }
	    /*for(i = 0; i < nrofghosts; i++) {
		System.printString("(" + map.m_ghostsX[i] + "," + map.m_ghostsY[i] + ") ");
	    }
	    System.printString("\n");*/
	    // update pacmen
	    for(i = 0; i < nrofpacs; i++) {
		if(map.m_pacMenX[i] != -1) {
		    //System.printString("Task updatePac\n");
		    map.m_pacmen[i].doMove();
		    map.placePacman(map.m_pacmen[i]);
		    //System.printString("Pacman " + map.m_pacmen[i].m_index + ": (" + map.m_pacMenX[map.m_pacmen[i].m_index] + "," + map.m_pacMenY[map.m_pacmen[i].m_index] + ")\n");
		    boolean death = map.check(map.m_pacmen[i]);
		}
	    }
	    map.m_nrofpacs -= map.m_deathcount;
	    //System.printString(map.m_nrofpacs + " pacmen left. \n");
	    
	    // reset for next run
	    //System.printString("Task next\n");
	    map.m_paccount = 0;
	    map.m_deathcount = 0;
	}
	
	//System.printString("Task Finish\n");
    }
}
