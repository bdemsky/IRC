// this class stores the properties of a fix

public class Fix {
  private String name;
  private Point2d p;

  public Fix(String name0,Point2d p0) {
    name=name0;
    p=p0;
  }

  public Point2d getFixCoord() {
    return p;
  }

  public String getName() {
    return name;
  }

  boolean hasName(String name0) {
    return (name.compareTo(name0)==0);
  }

  public String toString() {
    return new String("Fix: "+name+" "+p);
  }
}
