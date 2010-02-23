/** 
 ** The main Map that is shared across machines 
 ** The description for the data we're pathfinding over. This provides the contract
 ** between the data being searched (i.e. the in game map) and the path finding
 ** generic tools
 **/

public class GameMap {
  private TreeType tree;
  private RockType rock;

  public GameMap() {
    tree = null;
    rock = null;
  }

  public boolean hasTree() {
    if (tree != null) {
      return true;
    }
    return false;
  }

  public boolean hasRock() {
    if (rock != null) {
      return true;
    } 
    return false;
  }

  public void putTree(TreeType t) {
    tree = t;
  }

  public void putRock(RockType r) {
    rock = r;
  }

  public void cutTree() {
    tree = null;
  }

  public boolean isEmpty() {
    if (tree == null && rock == null) {
      return true;
    }
    return false;
  }

  /**
   ** Only for Debugging by printing the map
   ** Called after every round
   **/
  public void print() {
    if (tree != null) { 
      System.print("T ");
      return;
    } 
    if (rock != null) {
      System.print("o ");
      return;
    }
    System.print(". ");
  }
}
