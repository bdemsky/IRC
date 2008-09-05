public class Ghost {
    flag move;
    flag update;

    public int m_locX;
    public int m_locY;
    public int m_index;
    public int m_target;
    public int m_direction;  // 0:still, 1:up, 2:down, 3:left, 4:right
    int m_dx;
    int m_dy;
    //int m_destinationX;
    //int m_destinationY;
    Map m_map;
    
    public Ghost(int x, int y, Map map) {
	this.m_locX = x;
	this.m_locY = y;
	this.m_dx = this.m_dy = 0;
	this.m_index = -1;
	this.m_target = -1;
	this.m_direction = 0;
	//this.m_destinationX = this.m_destinationY = -1;
	this.m_map = map;
    }
    
    // 0:still, 1:up, 2:down, 3:left, 4:right
    public void tryMove() {
	//System.printString("step 1\n");
	// reset the target
	this.m_target = -1;
	// find the shortest possible way to the chosen target
	setNextDirection();
    }
    
    private void setNextDirection() {
	// current position of the ghost
	int start = this.m_locY * this.m_map.m_nrofblocks + this.m_locX;
	boolean set = false;
	Vector cuts = new Vector();
	int tmptarget = 0;
	int tmpdx = 0;
	int tmpdy = 0;
	int tmpdirection = 0;
	//int tmpdestinationX = -1;
	//int tmpdestinationY = -1;
	boolean first = true;
	/*int[] candidateDesX = new int[this.m_map.m_destinationX.length];
	int[] candidateDesY = new int[this.m_map.m_destinationX.length];
	for(int i = 0; i < this.m_map.m_destinationX.length; i++) {
	    candidateDesX = this.m_map.m_destinationX[i];
	}*/
	
	while(!set) {
	    int parents[] = new int[this.m_map.m_nrofblocks * this.m_map.m_nrofblocks + 1];
	    for(int i = 0; i < parents.length; i++) {
		parents[i] = -1;
	    }
	    if(!BFS(start, parents, cuts)) {
		this.m_target = tmptarget;
		this.m_dx = tmpdx;
		this.m_dy = tmpdy;
		//this.m_destinationX = tmpdestinationX;
		//this.m_destinationY = tmpdestinationY;
		this.m_map.m_ghostdirections[this.m_index] = this.m_direction = tmpdirection;
		set = true;
		//System.printString("Use first choice: (" + this.m_dx + ", " + this.m_dy + ")\n");
	    } else {
		// Reversely go over the parents array to find the next node to reach
		int index = this.m_map.m_pacMenY[this.m_target] * this.m_map.m_nrofblocks + this.m_map.m_pacMenX[this.m_target];
		int steps = parents[parents.length - 1];
		if(steps == 0) {
		    // already caught one pacman, stay still
		    this.m_dx = this.m_dy = 0;
		    this.m_map.m_ghostdirections[this.m_index] = this.m_direction = 0;
		    //System.printString("Stay still\n");
		    set = true;
		} else {
		    boolean found = false;
		    while(!found) {
			int parent = parents[index];
			if(parent == start) {
			    found = true;
			} else {
			    index = parent;
			}
			// System.printString("parent: " + parent + "\n");
		    }
		    //System.printString("Index: " + index + "\n");

		    // set the chase direction
		    int nx = index % this.m_map.m_nrofblocks;
		    int ny = index / this.m_map.m_nrofblocks;
		    this.m_dx = nx - this.m_locX;
		    this.m_dy = ny - this.m_locY;
		    if(this.m_dx > 0) {
			// right
			this.m_direction = 4;
		    } else if(this.m_dx < 0) {
			// left
			this.m_direction = 3;
		    } else if(this.m_dy > 0) {
			// down
			this.m_direction = 2;
		    } else if(this.m_dy < 0) {
			// up
			this.m_direction = 1;
		    } else {
			// still
			this.m_direction = 0;
		    }
		    if(first) {
			tmptarget = this.m_target;
			tmpdx = this.m_dx;
			tmpdy = this.m_dy;
			tmpdirection = this.m_direction;
			first = false;
			//System.printString("First choice: (" + tmpdx + ", " + tmpdy + ")\n");
		    }
		}

		// check if this choice follows some other ghosts' path
		if(!isFollowing()) {
		    this.m_map.m_ghostdirections[this.m_index] = this.m_direction;
		    set = true;
		} else {
		    cuts.addElement(new Integer(index));
		    /*for( int h = 0; h < cuts.size(); h++) {
			System.printString(cuts.elementAt(h) + ", ");
		    }
		    System.printString("\n");*/
		}
	    }
	}
    }
    
    // This methos do BFS from start node to end node
    // If there is a path from start to end, return true; otherwise, return false
    // Array parents records parent for a node in the BFS search,
    // the last item of parents records the least steps to reach end node from start node
    // Vector cuts specifies which nodes can not be the first one to access in this BFS
    private boolean BFS(int start, int[] parents, Vector cuts) {
	//System.printString("aaa\n");
	int steps = 0;
	Vector toaccess = new Vector();
	toaccess.addElement(new Integer(start));
	while(toaccess.size() > 0) {
	    //System.printString("bbb\n");
	    // pull out the first one to access
	    int access = ((Integer)toaccess.elementAt(0)).intValue();
	    toaccess.removeElementAt(0);
	    for(int i = 0; i < this.m_map.m_pacMenX.length; i++) {
		if(((access%this.m_map.m_nrofblocks) == this.m_map.m_pacMenX[i]) && ((access/this.m_map.m_nrofblocks) == this.m_map.m_pacMenY[i])) {
		    // hit one pacman
		    this.m_target = i;
		    parents[parents.length - 1] = steps;
		    return true;
		}
	    }
	    steps++;
	    Vector neighbours = this.m_map.getNeighbours(access);
	    for(int i = 0; i < neighbours.size(); i++) {
		int neighbour = ((Integer)neighbours.elementAt(i)).intValue();
		if(parents[neighbour] == -1) {
		    // not accessed
		    boolean ignore = false;
		    if(access == start) {
			// start node, check if the neighbour node is in cuts
			int j = 0;
			while((!ignore) && (j < cuts.size())) {
			    int tmp = ((Integer)cuts.elementAt(j)).intValue();
			    if(tmp == neighbour) {
				ignore = true;
			    }
			    j++;
			}
		    }
		    if(!ignore) {
			parents[neighbour] = access;
			toaccess.addElement(new Integer(neighbour));
		    }
		}
	    }
	}
	//System.printString("ccc\n");
	parents[parents.length - 1] = -1;
	return false;
    }
    
    // This method returns true if this ghost is traveling to the same
    // destination with the same direction as another ghost.
    private boolean isFollowing () {
	boolean bFollowing = false;
	double  dRandom;

	// If the ghost is in the same location as another ghost
	// and moving in the same direction, then they are on
	// top of each other and should not follow.
	for (int i = 0; i < this.m_map.m_ghostsX.length; i++) {
	    // Ignore myself
	    if (this.m_index != i) {
		if (this.m_map.m_ghostsX[i] == this.m_locX &&
			this.m_map.m_ghostsY[i] == this.m_locY &&
			this.m_map.m_ghostdirections[i] == this.m_direction) {
		    return true;
		}
	    }
	}

	// This will allow ghosts to often
	// clump together for easier eating
	dRandom = this.m_map.m_r.nextDouble();
	if (dRandom < .90) {  
	    //if (m_bInsaneAI && dRandom < .25)
	    //   return false;
	    //else
	    return false;
	}

	// If ghost is moving to the same location and using the
	// same direction, then it is following another ghost.      
	for (int i = 0; i < this.m_map.m_ghostsX.length; i++) {        
	    // Ignore myself        
	    if (this.m_index != i) {
		if (this.m_map.m_targets[i] == this.m_target &&
			this.m_map.m_ghostdirections[i] == this.m_direction) {
		    return true;
		}
	    }
	}

	return bFollowing;
    }
    
    // This method will take the specified location and direction and determine
    // for the given location if the thing moved in that direction, what the
    // next possible turning location would be.
    private boolean getDestination (int direction, int locX, int locY, int[] point) {
       // If the request direction is blocked by a wall, then just return the current location
       if (((direction == 1) && ((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 2) != 0)) || // up
           ((direction == 3) && ((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 1) != 0)) ||  // left
           ((direction == 2) && ((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 8) != 0)) || // down
           ((direction == 4) && ((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 4) != 0))) { // right 
          point[0] = locX;
          point[1] = locY;
          return false;
       }
          
       // Start off by advancing one in direction for specified location
       if (direction == 1) {
	   // up
	   locY--;
       } else if (direction == 2) {
	   // down
	   locY++;
       } else if (direction == 3) {
	   // left
	   locX--;
       } else if (direction == 4) {
	   // right
	   locX++;
       }
       
       // If we violate the grid boundary,
       // then return false.
       if (locY < 0 ||
           locX < 0 ||
           locY == this.m_map.m_nrofblocks ||
           locX == this.m_map.m_nrofblocks) {
	   return false;
       }
       
       boolean set = false;
       // Determine next turning location.
       while (!set) {
          if (direction == 1 || direction == 2) { 
              // up or down
              if (((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 4) == 0) || // right
        	      ((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 1) == 0) || // left
        	      ((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 2) != 0) || // up
        	      ((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 8) != 0))  { // down
        	  point[0] = locX;
        	  point[1] = locY;
        	  set = true;
               } else {
        	   if (direction == 1) {
        	       // Check for Top Warp
        	       if (locY == 0) {
        		   point[0] = locX;
        		   point[1] = this.m_map.m_nrofblocks - 1;
        		   set = true;
        	       } else {
        		   locY--;
        	       }
        	   } else {
        	       // Check for Bottom Warp
        	       if (locY == this.m_map.m_nrofblocks - 1) {
        		   point[0] = locX;
        		   point[1] = 0;
        		   set = true;
        	       } else {
        		   locY++;
        	       }
        	   }
               }
          } else {
              // left or right
              if (((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 2) == 0) || // up
        	      ((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 8) == 0) || // down
        	      ((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 4) != 0) || // right
        	      ((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 1) != 0)) { // left  
        	  point[0] = locX;
        	  point[1] = locY;
        	  set = true;
              } else {
        	  if (direction == 3) {
        	      // Check for Left Warp
        	      if (locX == 0) {
        		  point[0] = this.m_map.m_nrofblocks - 1;
        		  point[1] = locY;
        		  set = true;
        	      } else {
        		  locX--;
        	      }
        	  } else {
        	      // Check for Right Warp
        	      if (locX == this.m_map.m_nrofblocks - 1) {
        		  point[0] = 0;
        		  point[1] = locY;
        		  set = true;
        	      } else {
        		  locX++;
        	      }
        	  }
              }
          }
       }
       return true;
    }
    
    public void doMove() {
	if((this.m_dx == -1) && (this.m_locX == 0)) {
	    // go left and currently this.m_locX is 0
	    this.m_locX = this.m_map.m_nrofblocks - 1;
	} else if((this.m_dx == 1) && (this.m_locX == this.m_map.m_nrofblocks - 1)) {
	    this.m_locX = 0;
	} else {
	    this.m_locX += this.m_dx;
	}

	if((this.m_dy == -1) && (this.m_locY == 0)) {
	    // go up and currently this.m_locY is 0
	    this.m_locY = this.m_map.m_nrofblocks - 1;
	} else if((this.m_dy == 1) && (this.m_locY == this.m_map.m_nrofblocks - 1)) {
	    this.m_locY = 0;
	} else {
	    this.m_locY += this.m_dy;
	}
	this.m_dx = 0;
	this.m_dy = 0;
	//System.printString("Ghost " + this.m_index + ": (" + this.m_locX + ", " + this.m_locY + ")\n");
    }
}