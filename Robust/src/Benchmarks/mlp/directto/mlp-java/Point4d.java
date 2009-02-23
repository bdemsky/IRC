// point with 3 space coordinates and time label

//import java.text.*;

class Point4d extends Point2d {
  public double x,y,z,time;

  public static int outOfRangeTime() { return -1; }

  public Point4d () {
    x=0;y=0;z=0;
    time=0;
  }

  public Point4d(Point2d p) {
    x=p.x;
    y=p.y;	
    time=z=0;
  }

  public Point4d (double t, double xcoord, double ycoord, double zcoord) {
    x=xcoord; y=ycoord; z=zcoord;
    time=t;
  }

  public Point4d (double xcoord, double ycoord, double zcoord) {
    x=xcoord; y=ycoord; z=zcoord;
    time=0;
  }

  public Point4d (Point4d p) {
    x=p.x;
    y=p.y;
    z=p.z;
    time=p.time;
  }

  public void setTime (double t) {
    time=t;
  }

  public static double squaredDistance(Point4d p1,Point4d p2) {
    return (Math.pow(p1.x-p2.x,2)+Math.pow(p1.y-p2.y,2)+Math.pow(p1.z-p2.z,2));
  }

  public static double horizDistance(Point4d p1, Point4d p2) {
    return (Math.sqrt(Math.pow(p1.x-p2.x,2)+Math.pow(p1.y-p2.y,2)));
  }
    
  public int compareX(Point4d p) {
    if (this.x==p.x)
      return 0;
    else 
      if (this.x<p.x)
	return -1;
      else return 1;
  }

  public int compareY(Point4d p) {
    if (this.y==p.y)
      return 0;
    else 
      if (this.y<p.y)
	return -1;
      else return 1;
  }

  public int compareZ(Point4d p) {
    if (this.z==p.z)
      return 0;
    else 
      if (this.z<p.z)
	return -1;
      else return 1;
  }

  public boolean outOfRange() {
    return (this.time<0);
  }

  public Point2d toPoint2d() {
    return (new Point2d(this.x, this.y));
  }

  /*
  static public String decimalFormat(double n) {
    NumberFormat nf=NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(2);	
    return nf.format(n);
  }
  */
  
  public String toString() {
    String st="("+/*decimalFormat*/(x)+";"+/*decimalFormat*/(y)+";"+/*decimalFormat*/(z)+")";
    return st;
  }
}
