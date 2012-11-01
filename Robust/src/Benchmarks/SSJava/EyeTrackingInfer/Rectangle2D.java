public class Rectangle2D {

  double x;
  double y;
  double width;
  double height;

  public Rectangle2D(double x, double y, double w, double h) {
    this.x = x;
    this.y = y;
    this.width = w;
    this.height = h;
  }

  public double getX() {
    return x;
  }

  public double getY() {
    return y;
  }

  public double getWidth() {
    return width;
  }

  public double getHeight() {
    return height;
  }

  public String toString() {
    return "(" + x + "," + y + "," + width + "," + height + ")";
  }
}
