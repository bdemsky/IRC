/**
 ** A path finder implementation that uses the AStar heuristic based algorithm
 ** to determine a path. 
 ** New changes for our purposes
 **/
public class AStarPathFinder {

  /** The set of nodes that have been searched through */
  private Vector closed;

  /** The set of nodes that we do not yet consider fully searched */
  private SortedList open;

  /** The map being searched */
  GameMap[][] land;

  /** The maximum depth of search we're willing to accept before giving up */
  private int maxSearchDistance;

  /** The complete set of nodes across the map */
  private Node[][] nodes;

  /** True if we allow diagonal movement */
  private boolean allowDiagMovement;

  /** Map size where we are searching */
  private int rows, columns;

  /**
   ** Create a path finder with the default heuristic - closest to target.
   ** 
   ** @param land The map to be searched
   ** @param maxSearchDistance The maximum depth we'll search before giving up
   ** @param allowDiagMovement True if the search should try diagonal movement
   **/
  public AStarPathFinder(GameMap[][] land, int maxSearchDistance, boolean allowDiagMovement, int rows, int columns) {
    this.land = land;
    this.maxSearchDistance = maxSearchDistance;
    this.allowDiagMovement = allowDiagMovement;
    closed = new Vector();
    open = new SortedList();

    nodes = new Node[rows][columns];
    for (int x=0;x<rows;x++) {
      for (int y=0;y<columns;y++) {
        nodes[x][y] = new Node(x,y);
      }
    }
    this.rows = rows;
    this.columns = columns;
  }

  /**
   ** A description of an implementation that can find a path from one 
   ** location on a tile map to another based on information provided
   ** by that tile map.
   ** 
   ** Find a path from the starting location to the target
   ** location avoiding blockages and attempting to honour costs 
   ** provided by the land.
   **
   ** @return The path found from start to end, or null if no path can be found.
   **/

  public Path findPath(Player gamer) {
    int tx = gamer.getGoalX();
    int ty = gamer.getGoalY();
    int sx = gamer.getX();
    int sy = gamer.getY();

    int type = gamer.kind();

    // easy first check, if the destination is blocked, we can't get there

    if(type == 1) { //1 => PLANTER
      if(land[tx][ty].hasTree() || land[tx][ty].hasRock()) {
        return null;
      }
    } else { //LUMBERJACK
      if((!land[tx][ty].hasTree()) || land[tx][ty].hasRock()) {
        return null;
      }
    }

    // initial state for A*. The closed group is empty. Only the starting
    // tile is in the open list
    nodes[sx][sy].cost = 0;
    nodes[sx][sy].depth = 0;
    closed.clear();
    open.clear();
    open.add(nodes[sx][sy]);

    nodes[tx][ty].parent = null;

    // while we haven't exceeded our max search depth
    int maxDepth = 0;
    while ((maxDepth < maxSearchDistance) && (open.size() != 0)) {
      // pull out the first node in our open list, this is determined to 
      // be the most likely to be the next step based on our heuristic

      Node current = getFirstInOpen();

      if (current == nodes[tx][ty]) {
        break;
      }

      removeFromOpen(current);
      addToClosed(current);

      // search through all the neighbours of the current node evaluating
      // them as next steps

      for (int x=-1;x<2;x++) {
        for (int y=-1;y<2;y++) {
          // not a neighbour, its the current tile

          if ((x == 0) && (y == 0)) {
            continue;
          }

          // if we're not allowing diagonal movement then only 
          // one of x or y can be set

          if (!allowDiagMovement) {
            if ((x != 0) && (y != 0)) {
              continue;
            }
          }

          // determine the location of the neighbour and evaluate it

          int xp = x + current.x;
          int yp = y + current.y;

          if (isValidLocation(gamer,sx,sy,xp,yp)) {
            // the cost to get to this node is cost the current plus the movement
            // cost to reach this node. Note that the heursitic value is only used
            // in the sorted open list

            int nextStepCost = current.cost + getMovementCost(current.x, current.y, xp, yp);
            Node neighbour = nodes[xp][yp];

            // if the new cost we've determined for this node is lower than 
            // it has been previously makes sure the node has
            // determined that there might have been a better path to get to
            // this node so it needs to be re-evaluated

            if (nextStepCost < neighbour.cost) {
              if (inOpenList(neighbour)) {
                removeFromOpen(neighbour);
              }
              if (inClosedList(neighbour)) {
                removeFromClosed(neighbour);
              }
            }

            // if the node hasn't already been processed and discarded then
            // reset it's cost to our current cost and add it as a next possible
            // step (i.e. to the open list)
            if (!inOpenList(neighbour) && !(inClosedList(neighbour))) {
              neighbour.cost = nextStepCost;
              neighbour.heuristic = getHeuristicCost(xp, yp, tx, ty);
              maxDepth = Math.max(maxDepth, neighbour.setParent(current));
              addToOpen(neighbour);
            }
          }
        }
      }
    }

    // since we'e've run out of search 
    // there was no path. Just return null
    if (nodes[tx][ty].parent == null) {
      return null;
    }

    // At this point we've definitely found a path so we can uses the parent
    // references of the nodes to find out way from the target location back
    // to the start recording the nodes on the way.

    Path path = new Path();
    Node target = nodes[tx][ty];
    while (target != nodes[sx][sy]) {
      path.prependStep(target.x, target.y);
      target = target.parent;
    }

    // thats it, we have our path 
    return path;
  }

  /**
   ** Check if a given location is valid for the supplied gamer
   ** 
   ** @param gamer The Player moving in the map
   ** @param sx The starting x coordinate
   ** @param sy The starting y coordinate
   ** @param xp The x coordinate of the location to check
   ** @param yp The y coordinate of the location to check
   ** @return True if the location is valid for the given gamer
   **/


  public boolean isValidLocation(Player gamer, int sx, int sy, int xp, int yp) {
    boolean invalid = (xp <= 0) || (yp <= 0) || (xp >= rows-1) || (yp >= columns-1);

    if ((!invalid) && ((sx != xp) || (sy != yp))) {
      if(gamer.kind() == 1) { //1=> PLANTER
        if (land[xp][yp].hasTree() || land[xp][yp].hasRock()) {
          invalid = true;
        }
      } else { //LUMBERJACK
        if (land[xp][yp].hasRock()) {
          invalid = true;
        }
      }
    }
    return !invalid;
  }

  /**
   ** Get the first element from the open list. This is the next
   ** one to be searched.
   ** 
   ** @return The first element in the open list
   **/
  protected Node getFirstInOpen() {
    Node n = (Node) open.first();
    return n;
  }

  /**
   ** Remove a node from the open list
   ** 
   ** @param node The node to remove from the open list
   **/
  protected void removeFromOpen(Node node) {
    open.remove(node);
  }

  /**
   ** Add a node to the closed list
   ** 
   ** @param node The node to add to the closed list
   **/
  protected void addToClosed(Node node) {
    closed.addElement(node);
  }

  /**
   ** Remove a node from the closed list
   ** 
   ** @param node The node to remove from the closed list
   **/
  protected void removeFromClosed(Node node) {
    closed.remove(node);
  }

  /**
   ** Check if the node supplied is in the closed list
   ** 
   ** @param node The node to search for
   ** @return True if the node specified is in the closed list
   **/
  protected boolean inClosedList(Node node) {
    return closed.contains(node);
  }

  /**
   ** Check if a node is in the open list
   ** 
   ** @param node The node to check for
   ** @return True if the node given is in the open list
   **/
  protected boolean inOpenList(Node node) {
    return open.contains(node);
  }

  /**
   ** Add a node to the open list
   ** 
   ** @param node The node to be added to the open list
   **/
  protected void addToOpen(Node node) {
    open.add(node);
  }

  /**
   ** Get the cost to move through a given location
   ** 
   ** @param x The x coordinate of the tile whose cost is being determined
   ** @param y The y coordiante of the tile whose cost is being determined
   ** @param xp The x coordinate of the neighbor target location
   ** @param yp The y coordinate of the neighbor target location
   ** @return The cost of movement through the given tile
   **/
  public int getMovementCost(int x, int y, int xp, int yp) {
    if (Math.abs(xp - x) == 1 && Math.abs(yp - y) == 1) {
      return 14;
    }
    return 10;
  }

  /**
   *
   * Get the cost of moving through the given tile. This can be used to 
   ** make certain areas more desirable. 
   ** 
   ** @param xp The x coordinate of the tile we're moving from
   ** @param yp The y coordinate of the tile we're moving from
   ** @param tx The x coordinate of the tile we're moving to
   ** @param ty The y coordinate of the tile we're moving to
   ** @return The relative cost of moving across the given tile
   **/
  public int getHeuristicCost(int xp, int yp, int tx, int ty) {
    int heur = (Math.abs(tx - xp) + Math.abs(ty - yp)) * 10;
    return heur;
  }

  /**
   * Used only for debugging by printing the list element's
   * x and y coordinates
   **/

  public void debugClosedList() {
    for(int i=0; i<closed.size(); i++) {
      Node n = (Node) closed.elementAt(i);
      System.print("Element "+i+": n.getX()= "+n.getX()+" n.getY()= "+n.getY()+ "\n");
    }
    System.printString("\n");
  }
}
