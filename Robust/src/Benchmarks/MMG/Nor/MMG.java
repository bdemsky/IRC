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
    for(i = 0; i < map.nrofghosts; i++) {
	Ghost ghost = new Ghost(7, 7, map){move};
	ghost.setTarget(i%map.nrofpacs);
	ghost.index = i;
	map.placeGhost(ghost);
	map.targets[i] = ghost.target;
    }
    // create pacmen
    int tx = 14;
    int ty = 14;
    for(i = 0; i < map.nrofpacs; i++) {
	  Pacman pacman = new Pacman(5, 7, map){move};
	  pacman.setTarget(tx*(i/2), ty*(i%2));
	  pacman.index = i;
	  map.placePacman(pacman);
	  map.desX[i] = tx*(i/2);
	  map.desY[i] = ty*(i%2);
    }
    
    map.ghostcount = 0;
    map.paccount = 0;
    
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
	map.ghostdirections[g.index] = g.direction;
    /*} else {
	System.printString("FAILURE ghost!!!\n");
	//map.failghostcount++;
	map.ghostcount++;
    }*/
    
    if(map.ghostcount == map.nrofghosts) {
	//map.nrofghosts -= map.failghostcount;
	map.ghostcount = 0;
	map.failghostcount = 0;
	/*for(int i = 0; i < map.ghostsX.length; i++) {
	    System.printString("(" + map.ghostsX[i] + "," + map.ghostsY[i] + ") ");
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
	map.directions[p.index] = p.direction;
	//System.printString("Pacman " + p.index + ": (" + map.pacMenX[p.index] + "," + map.pacMenY[p.index] + ")\n");
	boolean death = map.check(p);
	/*if(death) {
	    System.printString("Pacman " + p.index + " caught!\n");
	}*/
    /*} else {
	System.printString("FAILURE pacman!!!\n");
	map.deathcount++;
	map.paccount++;
    }*/
    
    boolean finish = map.paccount == map.nrofpacs;
    
    if(finish) {
	map.nrofpacs -= map.deathcount;
	//System.printString(map.nrofpacs + " pacmen left. \n");
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
    for(i = 0; i < map.nrofghosts; i++) {
	Ghost ghost = new Ghost(map.ghostsX[i], map.ghostsY[i], map){move};
	ghost.setTarget(map.targets[i]);
	ghost.index = i;
	ghost.direction = map.ghostdirections[i];
    }
    for(i = 0; i < map.pacMenX.length; i++) {
	if(map.pacMenX[i] != -1) {
	    // still in the map
	    //System.printString("new Pacman\n");
	    Pacman pacman = new Pacman(map.pacMenX[i], map.pacMenY[i], map){move};
	    pacman.setTarget(map.desX[i], map.desY[i]);
	    pacman.index = i;
	    pacman.direction = map.directions[i];
	}
    }
    
    map.paccount = 0;
    map.deathcount = 0;
    
    taskexit(map{!next, updateGhost});
}

task finish(Map map{finish}) {
    System.printString("Task Finish\n");
    taskexit(map{!finish});
}
