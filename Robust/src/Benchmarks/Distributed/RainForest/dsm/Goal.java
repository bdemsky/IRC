public class Goal {
  private int LocX;
  private int LocY;

  public Goal() {
    LocX = 0;
    LocY = 0;
  }

  public Goal(int x, int y) {
    LocX = x;
    LocY = y;
  }

  public void setXY(int x, int y) {
    LocX = x;
    LocY = y;
  }

  public int getX() {
    return LocX;
  }

  public int getY() {
    return LocY;
  }
}
