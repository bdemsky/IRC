// a 2d point - used for fixes

class Point2d {
  public double x,y;

  public Point2d () {
    x=0;
    y=0;
  }

  public Point2d (double xcoord , double ycoord) {
    this.x=xcoord;
    this.y=ycoord;
  }
  
  public static double horizDistance(Point2d p1, Point2d p2) {
    return (Math.sqrt(squaredDistance(p1,p2)));
  }

  public static double squaredDistance(Point2d p1, Point2d p2) {
    return (Math.pow(p1.x-p2.x,2)+Math.pow(p1.y-p2.y,2));
  }

  public String toString() {
    return new String("("+x+","+y+")");
  }
}
