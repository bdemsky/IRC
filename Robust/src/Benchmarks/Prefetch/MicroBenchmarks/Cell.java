/*************************************************************************
*  Compilation:  javac Cell.java
*  Execution:    java Cell
*  
*
*************************************************************************/

public class Cell {
  private boolean spin;   // up (true) or down (false)

  public Cell(boolean spin) { 
    this.spin = spin;
  }

  // random spin - up with probability p
  public Cell(double p) { 
    spin = (Math.random() < p);
  }

  // flip the spin
  public void flip() { spin = !spin; }

  // +1 if up, -1 if down
  public double magnetization() {
    if (spin) return +1.0;
    else      return -1.0;
  }

  // draw cell according to value of spin
  public void draw(double x, double y) {
    /*
    if (spin) StdDraw.setPenColor(StdDraw.WHITE);
    else      StdDraw.setPenColor(StdDraw.BLUE);
    StdDraw.filledSquare(x, y, 1);
    */
  }

  // string representation
  public String toString() {
    if (spin) return "+";
    else      return "-";
  }



  public static void main(String[] args) {
  }
} 
