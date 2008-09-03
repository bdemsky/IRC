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
    Map m_map;
    
    public Ghost(int x, int y, Map map) {
	this.m_locX = x;
	this.m_locY = y;
	this.m_dx = this.m_dy = 0;
	this.m_index = -1;
	this.m_target = -1;
	this.m_direction = 0;
	this.m_map = map;
    }
    
    // 0:still, 1:up, 2:down, 3:left, 4:right
    public void tryMove() {
	//System.printString("step 1\n");
	int i = 0;

	// check the nearest pacman and set it as current target
	this.m_target = -1;
	int deltaX = this.m_map.m_nrofblocks;
	int deltaY = this.m_map.m_nrofblocks;
	int distance = deltaX * deltaX + deltaY * deltaY;
	for(i = 0; i < this.m_map.m_nrofpacs; i++) {
	    if(this.m_map.m_pacMenX[i] != -1) {
		int dx = this.m_locX - this.m_map.m_pacMenX[i];
		int dy = this.m_locY - this.m_map.m_pacMenY[i];
		int dd = dx*dx+dy*dy;
		if(distance > dd) {
		    this.m_target = i;
		    distance = dd;
		    deltaX = dx;
		    deltaY = dy;
		}
	    }
	}
	// System.printString("target: " + this.m_target + "\n");
	
	if(this.m_target == -1) {
	    // no more pacmen to chase, stay still
	    this.m_dx = 0;
	    this.m_dy = 0;
	    this.m_direction = this.m_map.m_ghostdirections[this.m_index] = 0;
	    return;
	}
	
	// find the shortest possible way to the chosen target
	setNextDirection();
    }
    
    private void setNextDirection() {
	// current position of the ghost
	Node start = this.m_map.m_mapNodes[this.m_locY * this.m_map.m_nrofblocks + this.m_locX];
	
	// get target's position
	int targetx = this.m_map.m_pacMenX[this.m_target];
	int targety = this.m_map.m_pacMenY[this.m_target];
	int[] nextLocation = new int[2];
	nextLocation[0] = nextLocation[1] = -1;
	// check the target pacman's possible destination
	getDestination (this.m_map.m_directions[this.m_target], targetx, targety, nextLocation);
	targetx = nextLocation[0];
	targety = nextLocation[1];
	// target's position
	Node end = this.m_map.m_mapNodes[targety * this.m_map.m_nrofblocks + targetx];
	// reset the target as index of the end node
	this.m_target = this.m_map.m_targets[this.m_index] = end.getIndex();
	
	// breadth-first traverse the graph view of the maze
	// check the shortest path for the start node to the end node
	boolean set = false;
	Vector cuts = new Vector();
	int tmpdx = 0;
	int tmpdy = 0;
	int tmpdirection = 0;
	boolean first = true;
	while(!set) {
	    int parents[] = new int[this.m_map.m_nrofblocks * this.m_map.m_nrofblocks + 1];
	    for(int i = 0; i < parents.length; i++) {
		parents[i] = -1;
	    }
	    if(!BFS(start, end, parents, cuts)) {
		this.m_dx = tmpdx;
		this.m_dy = tmpdy;
		this.m_map.m_ghostdirections[this.m_index] = this.m_direction = tmpdirection;
		set = true;
		//System.printString("Use first choice: (" + this.m_dx + ", " + this.m_dy + ")\n");
	    } else {
		// Reversely go over the parents array to find the next node to reach
		boolean found = false;
		int index = end.getIndex();
		while(!found) {
		    int parent = parents[index];
		    if(parent == start.getIndex()) {
			found = true;
		    } else {
			index = parent;
		    }
		}

		// set the chase direction
		int nx = this.m_map.m_mapNodes[index].getXLoc();
		int ny = this.m_map.m_mapNodes[index].getYLoc();
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
		    tmpdx = this.m_dx;
		    tmpdy = this.m_dy;
		    tmpdirection = this.m_direction;
		    first = false;
		    //System.printString("First choice: (" + tmpdx + ", " + tmpdy + ")\n");
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
//  the last item of parents records the least steps to reach end node from start node
    // Vector cuts specifies which nodes can not be the first one to access in this BFS
    private boolean BFS(Node start, Node end, int[] parents, Vector cuts) {
	int steps = 0;
	Vector toaccess = new Vector();
	toaccess.addElement(start);
	while(toaccess.size() > 0) {
	    // pull out the first one to access
	    Node access = (Node)toaccess.elementAt(0);
	    toaccess.removeElementAt(0);
	    if(access.getIndex() == end.getIndex()) {
		// hit the end node
		parents[parents.length - 1] = steps;
		return true;
	    }
	    steps++;
	    Vector neighbours = access.getNeighbours();
	    for(int i = 0; i < neighbours.size(); i++) {
		Node neighbour = (Node)neighbours.elementAt(i);
		if(parents[neighbour.getIndex()] == -1) {
		    // not accessed
		    boolean ignore = false;
		    if(access.getIndex() == start.getIndex()) {
			// start node, check if the neighbour node is in cuts
			int j = 0;
			while((!ignore) && (j < cuts.size())) {
			    int tmp = ((Integer)cuts.elementAt(j)).intValue();
			    if(tmp == neighbour.getIndex()) {
				ignore = true;
			    }
			    j++;
			}
		    }
		    if(!ignore) {
			parents[neighbour.getIndex()] = access.getIndex();
			toaccess.addElement(neighbour);
		    }
		}
	    }
	}
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
	this.m_locX += this.m_dx;
	this.m_locY += this.m_dy;
	this.m_dx = 0;
	this.m_dy = 0;
	//System.printString("Ghost " + this.m_index + ": (" + this.m_locX + ", " + this.m_locY + ")\n");
    }
}