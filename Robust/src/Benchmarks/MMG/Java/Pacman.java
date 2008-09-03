public class Pacman {
    public int m_locX;
    public int m_locY;
    public boolean m_death;
    public int m_index;
    public int m_direction;  // 0:still, 1:up, 2:down, 3:left, 4:right
    int m_dx;
    int m_dy;
    public int m_tx;
    public int m_ty;
    Map m_map;
    
    public Pacman(int x, int y, Map map) {
	this.m_locX = x;
	this.m_locY = y;
	this.m_dx = this.m_dy = 0;
	this.m_death = false;
	this.m_index = -1;
	this.m_tx = this.m_ty = -1;
	this.m_direction = 0;
	this.m_map = map;
    }
    
    public void setTarget(int x, int y) {
	this.m_tx = x;
	this.m_ty = y;
    }
    
    public void tryMove() {
	// decide dx & dy
	
	// find the shortest possible way to the chosen target
	setNextDirection();
    }
    
    private void setNextDirection() {
	// current position of the ghost
	int start = this.m_locY * this.m_map.m_nrofblocks + this.m_locX;
	
	// get target's position
	int targetx = this.m_tx;
	int targety = this.m_ty;
	int[] nextLocation = new int[2];
	nextLocation[0] = nextLocation[1] = -1;
	
	// target's position
	int end = targety * this.m_map.m_nrofblocks + targetx;
	
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
		int index = end;
		while(!found) {
		    int parent = parents[index];
		    if(parent == start) {
			found = true;
		    } else {
			index = parent;
		    }
		}

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
		    tmpdx = this.m_dx;
		    tmpdy = this.m_dy;
		    tmpdirection = this.m_direction;
		    first = false;
		    //System.printString("First choice: (" + tmpdx + ", " + tmpdy + ")\n");
		}

		// check if this choice follows some other ghosts' path
		if(canFlee()) {
		    this.m_map.m_directions[this.m_index] = this.m_direction;
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
    private boolean BFS(int start, int end, int[] parents, Vector cuts) {
	int steps = 0;
	Vector toaccess = new Vector();
	toaccess.addElement(new Integer(start));
	while(toaccess.size() > 0) {
	    // pull out the first one to access
	    int access = ((Integer)toaccess.elementAt(0)).intValue();
	    toaccess.removeElementAt(0);
	    if(access == end) {
		// hit the end node
		parents[parents.length - 1] = steps;
		return true;
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
	parents[parents.length - 1] = -1;
	return false;
    }
    
    // This method returns true if this pacmen can flee in this direction.
    private boolean canFlee () {
	int steps = 0;
	int locX = this.m_locX;
	int locY = this.m_locY;
	int[] point = new int[2];
	point[0] = point[1] = -1;
	
	// Start off by advancing one in direction for specified location
	if (this.m_direction == 1) {
	    // up
	    locY--;
	} else if (this.m_direction == 2) {
	    // down
	    locY++;
	} else if (this.m_direction == 3) {
	    // left
	    locX--;
	} else if (this.m_direction == 4) {
	    // right
	    locX++;
	}
	steps++; 
	
	boolean set = false;
	// Determine next turning location.
	while (!set) {
	    if (this.m_direction == 1 || this.m_direction == 2) { 
		// up or down
		if (((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 4) == 0) || // right
			((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 1) == 0) || // left
			((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 2) != 0) || // up
			((int)(this.m_map.m_map[locX + locY * this.m_map.m_nrofblocks] & 8) != 0))  { // down
		    point[0] = locX;
		    point[1] = locY;
		    set = true;
		} else {
		    if (this.m_direction == 1) {
			// Check for Top Warp
			if (locY == 0) {
			    point[0] = locX;
			    point[1] = this.m_map.m_nrofblocks - 1;
			    set = true;
			} else {
			    locY--;
			    steps++;
			}
		    } else {
			// Check for Bottom Warp
			if (locY == this.m_map.m_nrofblocks - 1) {
			    point[0] = locX;
			    point[1] = 0;
			    set = true;
			} else {
			    locY++;
			    steps++;
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
		    if (this.m_direction == 3) {
			// Check for Left Warp
			if (locX == 0) {
			    point[0] = this.m_map.m_nrofblocks - 1;
			    point[1] = locY;
			    set = true;
			} else {
			    locX--;
			    steps++;
			}
		    } else {
			// Check for Right Warp
			if (locX == this.m_map.m_nrofblocks - 1) {
			    point[0] = 0;
			    point[1] = locY;
			    set = true;
			} else {
			    locX++;
			    steps++;
			}
		    }
		}
	    }
	}
	
	// check the least steps for the ghosts to reach point location
	int chasesteps = -1;
	int end = point[1] * this.m_map.m_nrofblocks + point[0];
	for(int i = 0; i < this.m_map.m_ghostsX.length; i++) {
	    int start = this.m_map.m_ghostsY[i] * this.m_map.m_nrofblocks + this.m_map.m_ghostsX[i];
	    int parents[] = new int[this.m_map.m_nrofblocks * this.m_map.m_nrofblocks + 1];
	    for(int j = 0; j < parents.length; j++) {
		parents[j] = -1;
	    }
	    if(BFS(start, end, parents, new Vector())) {
		if((chasesteps == -1) ||
			(chasesteps > parents[parents.length - 1])) {
		    chasesteps = parents[parents.length - 1];
		}
	    }
	}

	return ((chasesteps == -1) || (steps < chasesteps));
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
	//System.printString("Pacmen " + this.m_index + ": (" + this.m_locX + ", " + this.m_locY + ")\n");
    }
}