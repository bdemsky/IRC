public class Ghost {
    public int x;
    public int y;
    public int index;
    public int target;
    public int direction;  // 0:still, 1:up, 2:down, 3:left, 4:right
    int dx;
    int dy;
    int destinationX;
    int destinationY;
    Map map;
    
    public Ghost(int x, int y, Map map) {
	this.x = x;
	this.y = y;
	this.dx = this.dy = 0;
	this.index = -1;
	this.target = -1;
	this.direction = 0;
	this.destinationX = -1;
	this.destinationY = -1;
	this.map = map;
    }
    
    public void setTarget(int pacman) {
	this.target = pacman;
    }
    
    // 0:still, 1:up, 2:down, 3:left, 4:right
    public void tryMove() {
	//System.printString("step 1\n");
	//System.printString("target: " + this.target + "\n");
	
	// Don't let the ghost go back the way it came.
	int prevDirection = 0;

	// If there is a destination, then check if the destination has been reached.
	if (destinationX >= 0 && destinationY >= 0) {
	    // Check if the destination has been reached, if so, then
	    // get new destination.
	    if (destinationX == x && destinationY == y) {
		destinationX = -1;
		destinationY = -1;
		prevDirection = direction;
	    } else {
		// Otherwise, we haven't reached the destionation so
		// continue in same direction.
		return;
	    }
	}
	setNextDirection (prevDirection);
    }
    
    private void setNextDirection(int prevDirection) {
	// get target's position
	int targetx = this.map.pacMenX[this.target];
	//System.printString("aaa\n");
	int targety = this.map.pacMenY[this.target];
	int[] nextLocation = new int[2];
	nextLocation[0] = nextLocation[1] = -1;
	
	//System.printString("bbb\n");
	if(targetx == -1) {
	    //System.printString("a\n");
	    // already kicked off, choose another target
	    int i = 0;
	    boolean found = false;
	    while((!found) && (i < map.pacMenX.length)) {
		if(this.map.pacMenX[i] != -1) {
		    this.target = i;
		    targetx = this.map.pacMenX[i];
		    targety = this.map.pacMenY[i];
		    this.map.targets[i] = this.target;
		    found = true;
		}
		i++;
	    }
	    //System.printString("b\n");
	    if(i == this.map.pacMenX.length) {
		//System.printString("c\n");
		// no more pacmen to chase
		this.dx = 0;
		this.dy = 0;
		this.direction = 0;
		return;
	    }
	    //System.printString("d\n");
	}
	getDestination (this.map.pacmen[this.target].direction, targetx, targety, nextLocation);
	targetx = nextLocation[0];
	targety = nextLocation[1];
	
	//System.printString("step 2\n");
	// check the distance
	int deltax = this.x - targetx; // <0: move right; >0: move left
	int deltay = this.y - targety; // <0: move down; >0: move up
	// decide the priority of four moving directions
	int[] bestDirection = new int[4];
	//System.printString("dx: " + deltax + "; dy: " + deltay + "\n");
	if((Math.abs(deltax) > Math.abs(deltay)) && (deltay != 0)) {
	    // go first along y
	    if(deltay > 0) {
		bestDirection[0] = 1;
		bestDirection[3] = 2;
		if(deltax > 0) {
		    bestDirection[1] = 3;
		    bestDirection[2] = 4;
		} else {
		    bestDirection[1] = 4;
		    bestDirection[2] = 3;
		}
	    } else {
		bestDirection[0] = 2;
		bestDirection[3] = 1;
		if(deltax > 0) {
		    bestDirection[1] = 3;
		    bestDirection[2] = 4;
		} else {
		    bestDirection[1] = 4;
		    bestDirection[2] = 3;
		}
	    }
	} else {
	    if(deltax > 0) {
		bestDirection[0] = 3;
		bestDirection[3] = 4;
		if(deltay > 0) {
		    bestDirection[1] = 1;
		    bestDirection[2] = 2;
		} else {
		    bestDirection[1] = 2;
		    bestDirection[2] = 1;
		}
	    } else {
		bestDirection[0] = 4;
		bestDirection[3] = 3;
		if(deltay > 0) {
		    bestDirection[1] = 1;
		    bestDirection[2] = 2;
		} else {
		    bestDirection[1] = 2;
		    bestDirection[2] = 1;
		}
	    }
	}
	/*for(int i = 0; i < 4; i++) {
	    System.printString(bestDirection[i] + ",");
	}
	System.printString("\n");*/
	
	// There's a 50% chance that the ghost will try the sub-optimal direction first.
	// This will keep the ghosts from following each other and to trap Pacman.
	if (this.map.r.nextDouble() < .50) {  
	    int temp = bestDirection[0];
	    bestDirection[0] = bestDirection[1];
	    bestDirection[1] = temp;
	}
	      
	//System.printString("step 3\n");
	// try to move one by one
	int i = 0;
	boolean set = false;
	this.dx = 0;
	this.dy = 0;
	while((!set) && (i < 4)) {
	    if(bestDirection[i] == 1) {
		// try to move up
		if((prevDirection != 2) && ((int)(this.map.map[y * this.map.nrofblocks + x] & 2) == 0)) {
		    //System.printString("a\n");
		    if (getDestination (1, this.x, this.y, nextLocation)) {
			this.dx = 0;
			this.dy = -1;
			set = true;
		    }
		}
	    } else if (bestDirection[i] == 2) {
		// try to move down
		if((prevDirection != 1) && ((int)(this.map.map[y * this.map.nrofblocks + x] & 8) == 0)) {
		    //System.printString("b\n");
		    if (getDestination (2, this.x, this.y, nextLocation)) {
			this.dx = 0;
			this.dy = 1;
			set = true;
		    }
		}
	    } else if (bestDirection[i] == 3) {
		// try to move left
		if((prevDirection != 4) && ((int)(this.map.map[y * this.map.nrofblocks + x] & 1) == 0)) {
		    //System.printString("c\n");
		    if (getDestination (3, this.x, this.y, nextLocation)) {
			this.dx = -1;
			this.dy = 0;
			set = true;
		    }
		}
	    } else if (bestDirection[i] == 4) {
		// try to move right
		if((prevDirection != 3) && ((int)(this.map.map[y * this.map.nrofblocks + x] & 4) == 0)) {
		    //System.printString("d\n");
		    if (getDestination (4, this.x, this.y, nextLocation)) {
			this.dx = 1;
			this.dy = 0;
			set = true;
		    }
		}
	    }
	    i++;
	}
	//System.printString("step 4\n");
    }
    
    // This method will take the specified location and direction and determine
    // for the given location if the thing moved in that direction, what the
    // next possible turning location would be.
    boolean getDestination (int direction, int locX, int locY, int[] point) {
       // If the request direction is blocked by a wall, then just return the current location
       if ((direction == 1 && (this.map.map[locX + locY * this.map.nrofblocks] & 2) != 0) || // up
           (direction == 3 && (this.map.map[locX + locY * this.map.nrofblocks] & 1) != 0) ||  // left
           (direction == 2 && (this.map.map[locX + locY * this.map.nrofblocks] & 8) != 0) || // down
           (direction == 4 && (this.map.map[locX + locY * this.map.nrofblocks] & 4) != 0)) { // right 
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
           locY == this.map.nrofblocks ||
           locX == this.map.nrofblocks) {
	   return false;
       }
       
       boolean set = false;
       // Determine next turning location..
       while (!set) {
          if (direction == 1 || direction == 2) { 
              // up or down
              if ((this.map.map[locX + locY * this.map.nrofblocks] & 4) == 0 || // right
        	      (this.map.map[locX + locY * this.map.nrofblocks] & 1) == 0 || // left
        	      (this.map.map[locX + locY * this.map.nrofblocks] & 2) != 0 || // up
        	      (this.map.map[locX + locY * this.map.nrofblocks] & 8) != 0)  { // down
        	  point[0] = locX;
        	  point[1] = locY;
        	  set = true;
               } else {
        	   if (direction == 1) {
        	       // Check for Top Warp
        	       if (locY == 0) {
        		   point[0] = locX;
        		   point[1] = this.map.nrofblocks - 1;
        		   set = true;
        	       } else {
        		   locY--;
        	       }
        	   } else {
        	       // Check for Bottom Warp
        	       if (locY == this.map.nrofblocks - 1) {
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
              if ((this.map.map[locX + locY * this.map.nrofblocks] & 2) == 0 || // up
        	      (this.map.map[locX + locY * this.map.nrofblocks] & 8) == 0 || // down
        	      (this.map.map[locX + locY * this.map.nrofblocks] & 4) != 0 || // right
        	      (this.map.map[locX + locY * this.map.nrofblocks] & 1) != 0) { // left  
        	  point[0] = locX;
        	  point[1] = locY;
        	  set = true;
              } else {
        	  if (direction == 3) {
        	      // Check for Left Warp
        	      if (locX == 0) {
        		  point[0] = this.map.nrofblocks - 1;
        		  point[1] = locY;
        		  set = true;
        	      } else {
        		  locX--;
        	      }
        	  } else {
        	      // Check for Right Warp
        	      if (locX == this.map.nrofblocks - 1) {
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
	this.x += this.dx;
	this.y += this.dy;
	//this.dx = 0;
	//this.dy = 0;
    }
}