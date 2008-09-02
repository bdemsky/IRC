public class Map {
    flag init;
    flag updateGhost;
    flag updatePac;
    flag next;
    flag finish;
    
    public int[] map;
    public int[] pacMenX;
    public int[] pacMenY;
    public int[] directions;
    public int[] ghostsX;
    public int[] ghostsY;
    public int[] ghostdirections;
    public int[] targets;
    public int[] desX;
    public int[] desY;
    
    public int nrofghosts;
    public int nrofpacs;
    private int nrofblocks;
    //public boolean toupdate;
    public int ghostcount;
    public int paccount;
    public int deathcount;
    public int failghostcount;
    
    public Random r;
    
    public Map(int nrofpacs, int nrofghosts) {
	//System.printString("step 1\n");
	this.nrofblocks = 15;
	this.map = new int[this.nrofblocks*this.nrofblocks];
	this.nrofpacs = nrofpacs;
	this.nrofghosts = nrofghosts;
	this.pacMenX = new int[this.nrofpacs];
	this.pacMenY = new int[this.nrofpacs];
	this.directions = new int[this.nrofpacs];
	this.ghostsX = new int[this.nrofghosts];
	this.ghostsY = new int[this.nrofghosts];
	this.ghostdirections = new int[this.nrofghosts];
	this.targets = new int[this.nrofghosts];
	this.desX = new int[this.nrofpacs];
	this.desY = new int[this.nrofpacs];
	//this.toupdate = false;
	this.ghostcount = 0;
	this.paccount = 0;
	this.deathcount = 0;
	this.failghostcount = 0;
	
	this.r = new Random();
	
	//System.printString("step 2\n");
	for(int i = 0; i < this.nrofpacs; i++) {
	    this.pacMenX[i] = this.pacMenY[i] = -1;
	    this.desX[i] = this.desY[i] = -1;
	}
	//System.printString("step 3\n");
	for(int i = 0; i < this.nrofghosts; i++) {
	    this.ghostsX[i] = this.ghostsY[i] = -1;
	    this.targets[i] = -1;
	}
	//System.printString("step 4\n");
    }
    
    public void init() {
	int i = 0;
	this.map[i++]=3;this.map[i++]=10;this.map[i++]=10;this.map[i++]=6;this.map[i++]=9;this.map[i++]=12;this.map[i++]=3;this.map[i++]=10;this.map[i++]=6;this.map[i++]=9;this.map[i++]=12;this.map[i++]=3;this.map[i++]=10;this.map[i++]=10;this.map[i++]=6;
	this.map[i++]=5;this.map[i++]=11;this.map[i++]=14;this.map[i++]=1;this.map[i++]=10;this.map[i++]=10;this.map[i++]=4;this.map[i++]=15;this.map[i++]=1;this.map[i++]=10;this.map[i++]=10;this.map[i++]=4;this.map[i++]=11;this.map[i++]=14;this.map[i++]=5;
	this.map[i++]=1;this.map[i++]=10;this.map[i++]=10;this.map[i++]=4;this.map[i++]=11;this.map[i++]=6;this.map[i++]=1;this.map[i++]=10;this.map[i++]=4;this.map[i++]=3;this.map[i++]=14;this.map[i++]=1;this.map[i++]=10;this.map[i++]=10;this.map[i++]=4;
	this.map[i++]=5;this.map[i++]=3;this.map[i++]=6;this.map[i++]=9;this.map[i++]=6;this.map[i++]=5;this.map[i++]=5;this.map[i++]=7;this.map[i++]=5;this.map[i++]=5;this.map[i++]=3;this.map[i++]=12;this.map[i++]=3;this.map[i++]=6;this.map[i++]=5;
	this.map[i++]=5;this.map[i++]=9;this.map[i++]=8;this.map[i++]=14;this.map[i++]=5;this.map[i++]=13;this.map[i++]=5;this.map[i++]=5;this.map[i++]=5;this.map[i++]=13;this.map[i++]=5;this.map[i++]=11;this.map[i++]=8;this.map[i++]=12;this.map[i++]=5;
	this.map[i++]=9;this.map[i++]=2;this.map[i++]=10;this.map[i++]=2;this.map[i++]=8;this.map[i++]=2;this.map[i++]=12;this.map[i++]=5;this.map[i++]=9;this.map[i++]=2;this.map[i++]=8;this.map[i++]=2;this.map[i++]=10;this.map[i++]=2;this.map[i++]=12;
	this.map[i++]=6;this.map[i++]=5;this.map[i++]=7;this.map[i++]=5;this.map[i++]=7;this.map[i++]=5;this.map[i++]=11;this.map[i++]=8;this.map[i++]=14;this.map[i++]=5;this.map[i++]=7;this.map[i++]=5;this.map[i++]=7;this.map[i++]=5;this.map[i++]=3;
	this.map[i++]=4;this.map[i++]=5;this.map[i++]=5;this.map[i++]=5;this.map[i++]=5;this.map[i++]=5;this.map[i++]=10;this.map[i++]=10;this.map[i++]=10;this.map[i++]=5;this.map[i++]=5;this.map[i++]=5;this.map[i++]=5;this.map[i++]=5;this.map[i++]=1;
	this.map[i++]=12;this.map[i++]=5;this.map[i++]=13;this.map[i++]=5;this.map[i++]=13;this.map[i++]=5;this.map[i++]=11;this.map[i++]=10;this.map[i++]=14;this.map[i++]=5;this.map[i++]=13;this.map[i++]=5;this.map[i++]=13;this.map[i++]=5;this.map[i++]=9;
	this.map[i++]=3;this.map[i++]=8;this.map[i++]=10;this.map[i++]=8;this.map[i++]=10;this.map[i++]=0;this.map[i++]=10;this.map[i++]=2;this.map[i++]=10;this.map[i++]=0;this.map[i++]=10;this.map[i++]=8;this.map[i++]=10;this.map[i++]=8;this.map[i++]=6;
	this.map[i++]=5;this.map[i++]=3;this.map[i++]=2;this.map[i++]=2;this.map[i++]=6;this.map[i++]=5;this.map[i++]=15;this.map[i++]=5;this.map[i++]=15;this.map[i++]=5;this.map[i++]=3;this.map[i++]=2;this.map[i++]=2;this.map[i++]=6;this.map[i++]=5;
	this.map[i++]=5;this.map[i++]=9;this.map[i++]=8;this.map[i++]=8;this.map[i++]=4;this.map[i++]=1;this.map[i++]=10;this.map[i++]=8;this.map[i++]=10;this.map[i++]=4;this.map[i++]=1;this.map[i++]=8;this.map[i++]=8;this.map[i++]=12;this.map[i++]=5;
	this.map[i++]=1;this.map[i++]=10;this.map[i++]=10;this.map[i++]=6;this.map[i++]=13;this.map[i++]=5;this.map[i++]=11;this.map[i++]=2;this.map[i++]=14;this.map[i++]=5;this.map[i++]=13;this.map[i++]=3;this.map[i++]=10;this.map[i++]=10;this.map[i++]=4;
	this.map[i++]=5;this.map[i++]=11;this.map[i++]=14;this.map[i++]=1;this.map[i++]=10;this.map[i++]=8;this.map[i++]=6;this.map[i++]=13;this.map[i++]=3;this.map[i++]=8;this.map[i++]=10;this.map[i++]=4;this.map[i++]=11;this.map[i++]=14;this.map[i++]=5;
	this.map[i++]=9;this.map[i++]=10;this.map[i++]=10;this.map[i++]=12;this.map[i++]=3;this.map[i++]=6;this.map[i++]=9;this.map[i++]=10;this.map[i++]=12;this.map[i++]=3;this.map[i++]=6;this.map[i++]=9;this.map[i++]=10;this.map[i++]=10;this.map[i++]=12; // 15*15    
    } 

    public void placePacman(Pacman t) {
	this.pacMenX[t.index] = t.x;
	this.pacMenY[t.index] = t.y;
	//this.map[t.y * this.nrofblocks + t.x - 1] |= 16;
	this.paccount++;
    }
    
    public void placeGhost(Ghost t) {
	this.ghostsX[t.index] = t.x;
	this.ghostsY[t.index] = t.y;
	//this.map[t.y * this.nrofblocks + t.x - 1] |= 32;
	this.ghostcount++;
    }
    
    public boolean check(Pacman t) {
	boolean death = false;
	int i = 0;
	while((!death) && (i < this.nrofghosts)) {
	    if((t.x == this.ghostsX[i]) && (t.y == this.ghostsY[i])) {
		death = true;
	    }
	    i++;
	}
	if((!death) && (t.x == t.tx) && (t.y == t.ty)) {
	    // reach the destination
	    //System.printString("Hit destination!\n");
	    death = true;
	}
	if(death) {
	    // pacman caught by ghost
	    // set pacman as death
	    t.death = true;
	    // kick it out
	    //this.map[t.y * this.nrofblocks + t.x - 1] -= 16;
	    this.deathcount++;
	    this.pacMenX[t.index] = -1;
	    this.pacMenY[t.index] = -1;
	}
	return death;
    }
    
    public boolean isfinish() {
	return nrofpacs == 0;
    }
}
