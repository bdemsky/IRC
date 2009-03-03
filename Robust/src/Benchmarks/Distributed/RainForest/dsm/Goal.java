/**
 ** Saves the X and Y coordinates of a single tile in a Map
 **/

public class Goal {
  private int locX;
  private int locY;

  public Goal() {
    locX = 0;
    locY = 0;
  }

  public Goal(int x, int y) {
    locX = x;
    locY = y;
  }

  public void setXY(int x, int y) {
    locX = x;
    locY = y;
  }

  public int getX() {
    return locX;
  }

  public int getY() {
    return locY;
  }
}
