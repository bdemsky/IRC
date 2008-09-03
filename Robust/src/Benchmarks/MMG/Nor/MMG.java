task startup(StartupObject s{initialstate}) {
    //System.printString("Task startup\n");
    
    int nrofpacs = 4;
    int nrofghosts = 8;
    Map map = new Map(nrofpacs, nrofghosts){init};
    taskexit(s{!initialstate});
}

task initMap(Map map{init}) {
    //System.printString("Task initMap\n");
    
    map.init();
    
    int i = 0;
    // create ghosts
    for(i = 0; i < map.m_nrofghosts; i++) {
	Ghost ghost = new Ghost(7, 7, map){move};
	ghost.m_index = i;
	map.placeGhost(ghost);
    }
    // create pacmen
    int tx = 14;
    int ty = 14;
    for(i = 0; i < map.m_nrofpacs; i++) {
	  Pacman pacman = new Pacman(5, 7, map){move};
	  pacman.setTarget(tx*(i/2), ty*(i%2));
	  pacman.m_index = i;
	  map.placePacman(pacman);
	  map.m_desX[i] = tx*(i/2);
	  map.m_desY[i] = ty*(i%2);
    }
    
    map.m_ghostcount = 0;
    map.m_paccount = 0;
    
    taskexit(map{!init, updateGhost});
}

task moveGhost(Ghost g{move}) {
    //System.printString("Task moveGhost\n");
    
    g.tryMove();
    
    taskexit(g{!move, update});
}

task movePacman(Pacman p{move}) {
    //System.printString("Task movePacman\n");
    
    p.tryMove();
    
    taskexit(p{!move, update});
}

task updateGhost(Map map{updateGhost}, /*optional*/ Ghost g{update}) {
    //System.printString("Task updateGhost\n");
    
    //if(isavailable(g)) {
	g.doMove();
	map.placeGhost(g);
    /*} else {
	map.m_ghostcount++;
    }*/
    
    if(map.m_ghostcount == map.m_nrofghosts) {
	//map.m_nrofghosts -= map.m_failghostcount;
	map.m_ghostcount = 0;
	map.m_failghostcount = 0;
	/*for(int i = 0; i < map.m_ghostsX.length; i++) {
	    System.printString("(" + map.m_ghostsX[i] + "," + map.m_ghostsY[i] + ") ");
	}
	System.printString("\n");*/
	taskexit(map{updatePac, !updateGhost}, g{!update});
    }
    taskexit(g{!update});
}

task updatePac(Map map{updatePac}, /*optional*/ Pacman p{update}) {
    //System.printString("Task updatePac\n");
    
    //if(isavailable(p)) {
	p.doMove();
	map.placePacman(p);
	//System.printString("Pacman " + p.m_index + ": (" + map.m_pacMenX[p.m_index] + "," + map.m_pacMenY[p.m_index] + ")\n");
	boolean death = map.check(p);
	/*if(death) {
	    System.printString("Pacman " + p.m_index + " caught!\n");
	}*/
    /*} else {
	map.m_deathcount++;
	map.m_paccount++;
    }*/
    
    boolean finish = map.m_paccount == map.m_nrofpacs;
    
    if(finish) {
	map.m_nrofpacs -= map.m_deathcount;
	//System.printString(map.m_nrofpacs + " pacmen left. \n");
	if(map.isfinish()) {
	    taskexit(map{finish, !updatePac}, p{!update, !move});
	} else {
	    taskexit(map{next, !updatePac}, p{!update, !move});
	}
    } else {
	taskexit(p{!move, !update});
    }
}

task next(Map map{next}) {
    //System.printString("Task next\n");
    
    int i = 0;
    for(i = 0; i < map.m_nrofghosts; i++) {
	Ghost ghost = new Ghost(map.m_ghostsX[i], map.m_ghostsY[i], map){move};
	ghost.m_index = i;
	ghost.m_direction = map.m_ghostdirections[i];
    }
    for(i = 0; i < map.m_pacMenX.length; i++) {
	if(map.m_pacMenX[i] != -1) {
	    // still in the map
	    //System.printString("new Pacman\n");
	    Pacman pacman = new Pacman(map.m_pacMenX[i], map.m_pacMenY[i], map){move};
	    pacman.setTarget(map.m_desX[i], map.m_desY[i]);
	    pacman.m_index = i;
	    pacman.m_direction = map.m_directions[i];
	}
    }
    
    map.m_paccount = 0;
    map.m_deathcount = 0;
    
    taskexit(map{!next, updateGhost});
}

task finish(Map map{finish}) {
    System.printString("Task Finish\n");
    taskexit(map{!finish});
}