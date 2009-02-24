// This class memorizes the static data (besides fixes)

//import java.util.*;

public class Static {

  public /*static*/ double _width, _height; // the dimensions of the given area 
  public /*static*/ double _iterationStep, _noIterations;    
  public /*static*/ double _radius, _distance;

  public double width()        { return _width; }
  public double height()       { return _height; }
  public double iterationStep(){ return _iterationStep; }
  public double noIterations() { return _noIterations; }
  public double radius()       { return _radius; }
  public double distance()     { return _distance; }

  public Static() {}

  public /*static*/ void setMapSize(StringTokenizer st) {
    _width=Double.parseDouble(st.nextToken());
    _height=Double.parseDouble(st.nextToken());
  }

  public /*static*/ void setCylinder(StringTokenizer st) {
    _radius=Double.parseDouble(st.nextToken());
    _distance=Double.parseDouble(st.nextToken());
  }    

  public /*static*/ void setIterationStep(StringTokenizer st) {
    _iterationStep=Double.parseDouble(st.nextToken());
  }

  public /*static*/ void setNumberOfIterations(StringTokenizer st) {
    _noIterations=Integer.parseInt(st.nextToken());
  }

  // this is a test procedure
  public /*static*/ void printInfo() {
    //System.out.println("\n\nStatic Data:");
    //System.out.println("Width:"+_width+"        Height:"+_height);
    //System.out.println("Radius of safety/unsafety:"+_radius);
    //System.out.println("Distance of safety/unsafety:"+_distance);
    //System.out.println("Iteration step:"+_iterationStep+"     No. of Iterations:"+_noIterations);			  
  }  
}
