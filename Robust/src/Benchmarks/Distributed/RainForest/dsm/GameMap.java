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

  public boolean isEmpty() {
    if (tree == null && rock == null) {
      return true;
    }
    return false;
  }
}
