public class Player {
  private int type;
  private int x;
  private int y;
  private int id;
  private int lowx, highx;
  private int lowy, highy;

  public Player(int type) {
    this.type = type;
    x = -1;
    y = -1;
    id = -1;
  }

  public Player(int type, int x, int y) {
    this.type = type;
    this.x = x;
    this.y = y;
    id = -1;
  }

  public Player(int type, int x, int y, int id, int rows, int cols, int bounds) {
    this.type = type;
    this.x = x;
    this.y = y;
    this.id = id;
    lowx = x - bounds;
    highx = x + bounds;
    lowy = y - bounds;
    highy = y + bounds;
    // define new boundaries
    if (lowx <= 0) 
      lowx = 1;
    if (lowy <= 0) 
      lowy = 1;
    if (highx >= rows) 
      highx = rows-1;
    if (highy >= cols) 
      highx = cols-1;
  }

  public int kind() {
    return type;
  }

  public void setPosition(int x, int y) {
    this.x = x;
    this.y = y;
    return;
  }

  public int getX() {
    return x;
  } 
  
  public int getY() { 
    return y; 
  }

  public int getId() {
    return id;
  }

}

