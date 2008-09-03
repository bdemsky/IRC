public class Map {
    flag init;
    flag updateGhost;
    flag updatePac;
    flag next;
    flag finish;
    
    // maze
    private int m_nrofblocks;
    public int[] m_map;
    
    // pacmen information
    public int m_nrofpacs;
    public int[] m_pacMenX;
    public int[] m_pacMenY;
    public int[] m_directions;
    public int[] m_desX;
    public int[] m_desY;
    public int m_paccount;
    public int m_deathcount;
    
    // ghosts information
    public int m_nrofghosts;
    public int[] m_ghostsX;
    public int[] m_ghostsY;
    public int[] m_ghostdirections;
    public int[] m_targets;
    public int m_ghostcount;
    public int m_failghostcount;
    
    // helper member
    public Random m_r;
    
    public Map(int nrofpacs, int nrofghosts) {
	//System.printString("step 1\n");
	this.m_nrofblocks = 15;
	this.m_map = new int[this.m_nrofblocks*this.m_nrofblocks];
	
	this.m_nrofpacs = nrofpacs;
	this.m_pacMenX = new int[this.m_nrofpacs];
	this.m_pacMenY = new int[this.m_nrofpacs];
	this.m_directions = new int[this.m_nrofpacs];
	this.m_desX = new int[this.m_nrofpacs];
	this.m_desY = new int[this.m_nrofpacs];
	this.m_paccount = 0;
	this.m_deathcount = 0;
	
	this.m_nrofghosts = nrofghosts;
	this.m_ghostsX = new int[this.m_nrofghosts];
	this.m_ghostsY = new int[this.m_nrofghosts];
	this.m_ghostdirections = new int[this.m_nrofghosts];
	this.m_targets = new int[this.m_nrofghosts];
	this.m_ghostcount = 0;
	this.m_failghostcount = 0;
	
	this.m_r = new Random();
	
	for(int i = 0; i < this.m_nrofblocks*this.m_nrofblocks; i++) {
	    this.m_map[i] = -1;
	}
	
	//System.printString("step 2\n");
	for(int i = 0; i < this.m_nrofpacs; i++) {
	    this.m_pacMenX[i] = this.m_pacMenY[i] = -1;
	    this.m_desX[i] = this.m_desY[i] = -1;
	}
	//System.printString("step 3\n");
	for(int i = 0; i < this.m_nrofghosts; i++) {
	    this.m_ghostsX[i] = this.m_ghostsY[i] = -1;
	    this.m_targets[i] = -1;
	}
	//System.printString("step 4\n");
    }
    
    public void init() {
	// initilize the maze
	int i = 0;
	this.m_map[i++]=3;this.m_map[i++]=10;this.m_map[i++]=10;this.m_map[i++]=6;this.m_map[i++]=9;this.m_map[i++]=12;this.m_map[i++]=3;this.m_map[i++]=10;this.m_map[i++]=6;this.m_map[i++]=9;this.m_map[i++]=12;this.m_map[i++]=3;this.m_map[i++]=10;this.m_map[i++]=10;this.m_map[i++]=6;
	this.m_map[i++]=5;this.m_map[i++]=11;this.m_map[i++]=14;this.m_map[i++]=1;this.m_map[i++]=10;this.m_map[i++]=10;this.m_map[i++]=4;this.m_map[i++]=15;this.m_map[i++]=1;this.m_map[i++]=10;this.m_map[i++]=10;this.m_map[i++]=4;this.m_map[i++]=11;this.m_map[i++]=14;this.m_map[i++]=5;
	this.m_map[i++]=1;this.m_map[i++]=10;this.m_map[i++]=10;this.m_map[i++]=4;this.m_map[i++]=11;this.m_map[i++]=6;this.m_map[i++]=1;this.m_map[i++]=10;this.m_map[i++]=4;this.m_map[i++]=3;this.m_map[i++]=14;this.m_map[i++]=1;this.m_map[i++]=10;this.m_map[i++]=10;this.m_map[i++]=4;
	this.m_map[i++]=5;this.m_map[i++]=3;this.m_map[i++]=6;this.m_map[i++]=9;this.m_map[i++]=6;this.m_map[i++]=5;this.m_map[i++]=5;this.m_map[i++]=7;this.m_map[i++]=5;this.m_map[i++]=5;this.m_map[i++]=3;this.m_map[i++]=12;this.m_map[i++]=3;this.m_map[i++]=6;this.m_map[i++]=5;
	this.m_map[i++]=5;this.m_map[i++]=9;this.m_map[i++]=8;this.m_map[i++]=14;this.m_map[i++]=5;this.m_map[i++]=13;this.m_map[i++]=5;this.m_map[i++]=5;this.m_map[i++]=5;this.m_map[i++]=13;this.m_map[i++]=5;this.m_map[i++]=11;this.m_map[i++]=8;this.m_map[i++]=12;this.m_map[i++]=5;
	this.m_map[i++]=9;this.m_map[i++]=2;this.m_map[i++]=10;this.m_map[i++]=2;this.m_map[i++]=8;this.m_map[i++]=2;this.m_map[i++]=12;this.m_map[i++]=5;this.m_map[i++]=9;this.m_map[i++]=2;this.m_map[i++]=8;this.m_map[i++]=2;this.m_map[i++]=10;this.m_map[i++]=2;this.m_map[i++]=12;
	this.m_map[i++]=6;this.m_map[i++]=5;this.m_map[i++]=7;this.m_map[i++]=5;this.m_map[i++]=7;this.m_map[i++]=5;this.m_map[i++]=11;this.m_map[i++]=8;this.m_map[i++]=14;this.m_map[i++]=5;this.m_map[i++]=7;this.m_map[i++]=5;this.m_map[i++]=7;this.m_map[i++]=5;this.m_map[i++]=3;
	this.m_map[i++]=4;this.m_map[i++]=5;this.m_map[i++]=5;this.m_map[i++]=5;this.m_map[i++]=5;this.m_map[i++]=5;this.m_map[i++]=10;this.m_map[i++]=10;this.m_map[i++]=10;this.m_map[i++]=5;this.m_map[i++]=5;this.m_map[i++]=5;this.m_map[i++]=5;this.m_map[i++]=5;this.m_map[i++]=1;
	this.m_map[i++]=12;this.m_map[i++]=5;this.m_map[i++]=13;this.m_map[i++]=5;this.m_map[i++]=13;this.m_map[i++]=5;this.m_map[i++]=11;this.m_map[i++]=10;this.m_map[i++]=14;this.m_map[i++]=5;this.m_map[i++]=13;this.m_map[i++]=5;this.m_map[i++]=13;this.m_map[i++]=5;this.m_map[i++]=9;
	this.m_map[i++]=3;this.m_map[i++]=8;this.m_map[i++]=10;this.m_map[i++]=8;this.m_map[i++]=10;this.m_map[i++]=0;this.m_map[i++]=10;this.m_map[i++]=2;this.m_map[i++]=10;this.m_map[i++]=0;this.m_map[i++]=10;this.m_map[i++]=8;this.m_map[i++]=10;this.m_map[i++]=8;this.m_map[i++]=6;
	this.m_map[i++]=5;this.m_map[i++]=3;this.m_map[i++]=2;this.m_map[i++]=2;this.m_map[i++]=6;this.m_map[i++]=5;this.m_map[i++]=15;this.m_map[i++]=5;this.m_map[i++]=15;this.m_map[i++]=5;this.m_map[i++]=3;this.m_map[i++]=2;this.m_map[i++]=2;this.m_map[i++]=6;this.m_map[i++]=5;
	this.m_map[i++]=5;this.m_map[i++]=9;this.m_map[i++]=8;this.m_map[i++]=8;this.m_map[i++]=4;this.m_map[i++]=1;this.m_map[i++]=10;this.m_map[i++]=8;this.m_map[i++]=10;this.m_map[i++]=4;this.m_map[i++]=1;this.m_map[i++]=8;this.m_map[i++]=8;this.m_map[i++]=12;this.m_map[i++]=5;
	this.m_map[i++]=1;this.m_map[i++]=10;this.m_map[i++]=10;this.m_map[i++]=6;this.m_map[i++]=13;this.m_map[i++]=5;this.m_map[i++]=11;this.m_map[i++]=2;this.m_map[i++]=14;this.m_map[i++]=5;this.m_map[i++]=13;this.m_map[i++]=3;this.m_map[i++]=10;this.m_map[i++]=10;this.m_map[i++]=4;
	this.m_map[i++]=5;this.m_map[i++]=11;this.m_map[i++]=14;this.m_map[i++]=1;this.m_map[i++]=10;this.m_map[i++]=8;this.m_map[i++]=6;this.m_map[i++]=13;this.m_map[i++]=3;this.m_map[i++]=8;this.m_map[i++]=10;this.m_map[i++]=4;this.m_map[i++]=11;this.m_map[i++]=14;this.m_map[i++]=5;
	this.m_map[i++]=9;this.m_map[i++]=10;this.m_map[i++]=10;this.m_map[i++]=12;this.m_map[i++]=3;this.m_map[i++]=6;this.m_map[i++]=9;this.m_map[i++]=10;this.m_map[i++]=12;this.m_map[i++]=3;this.m_map[i++]=6;this.m_map[i++]=9;this.m_map[i++]=10;this.m_map[i++]=10;this.m_map[i++]=12; // 15*15
    } 

    public void placePacman(Pacman t) {
	this.m_pacMenX[t.m_index] = t.m_locX;
	this.m_pacMenY[t.m_index] = t.m_locY;
	this.m_paccount++;
    }
    
    public void placeGhost(Ghost t) {
	this.m_ghostsX[t.m_index] = t.m_locX;
	this.m_ghostsY[t.m_index] = t.m_locY;
	this.m_ghostcount++;
    }
    
    public boolean check(Pacman t) {
	boolean death = false;
	int i = 0;
	while((!death) && (i < this.m_ghostsX.length)) {
	    if((t.m_locX == this.m_ghostsX[i]) && (t.m_locY == this.m_ghostsY[i])) {
		death = true;
	    }
	    i++;
	}
	if((!death) && (t.m_locX == t.m_tx) && (t.m_locY == t.m_ty)) {
	    // reach the destination
	    //System.printString("Hit destination!\n");
	    death = true;
	}
	if(death) {
	    // pacman caught by ghost
	    // set pacman as death
	    t.m_death = true;
	    // kick it out
	    //this.m_map[t.y * this.m_nrofblocks + t.x - 1] -= 16;
	    this.m_deathcount++;
	    this.m_pacMenX[t.m_index] = -1;
	    this.m_pacMenY[t.m_index] = -1;
	}
	return death;
    }
    
    public boolean isfinish() {
	return this.m_nrofpacs == 0;
    }
    
    public Vector getNeighbours(int index) {
	Vector neighbours = new Vector();
	int tmp = this.m_map[index];
	int locX = index % this.m_nrofblocks;
	int locY = index / this.m_nrofblocks;
	if((int)(tmp & 1) == 0) {
	    // can go left
	    if(locX == 0) {
		neighbours.addElement(new Integer(locY * this.m_nrofblocks + this.m_nrofblocks - 1));
	    } else {
		neighbours.addElement(new Integer(index - 1));
	    }
	} 
	if((int)(tmp & 2) == 0) {
	    // can go up
	    if(locY == 0) {
		neighbours.addElement(new Integer((this.m_nrofblocks - 1) * this.m_nrofblocks + locX));
	    } else {
		neighbours.addElement(new Integer((locY - 1) * this.m_nrofblocks + locX));
	    }
	}
	if((int)(tmp & 4) == 0) {
	    // can go right
	    if(locX == this.m_nrofblocks - 1) {
		neighbours.addElement(new Integer(locY * this.m_nrofblocks));
	    } else {
		neighbours.addElement(new Integer(index + 1));
	    }
	}
	if((int)(tmp & 8) == 0) {
	    // can go down
	    if(locY == this.m_nrofblocks - 1) {
		neighbours.addElement(new Integer(locX));
	    } else {
		neighbours.addElement(new Integer((locY + 1) * this.m_nrofblocks + locX));
	    }
	}
	return neighbours;
    }
}
