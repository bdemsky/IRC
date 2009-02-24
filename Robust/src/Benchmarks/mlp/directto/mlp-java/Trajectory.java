// class that implements the trajectory and some methods of access

//import java.util.*;

public class Trajectory {

  public int noPoints;  // the number of points in the trajectory
  private int current;
  public double distToDest, timeToDest; // estimated time&distance to end fix
  public int nextFixIndex; // index of the next fix in the trajectory of the flight;
  public Fix nextFix; // the next fix in the trajectory of the flight
  public Velocity startVelocity; // velocity at the first point in the trajectory;
  public Vector p; // the points in the trajectory

  public Trajectory(int np) {
    noPoints=np;
    p=new Vector(noPoints);
    current=0;
  }

  // adds a point to the trajectory at the position "pos"
  public void setPoint (int pos, Point4d point) {
    p.insertElementAt((Point4d) point, pos);
  }
  
  public void setNoPoints (int noP) {
    noPoints=noP;
  }

  public Point4d getCurrent () {
    return (Point4d) p.elementAt(current);
  }
  
  public Point4d getPointAt (int index) {
    return (Point4d) p.elementAt(index);
  }

  public double distanceToDestination() {
	return distToDest;
  }
  
  public double timeToDestination(double time) {
    return (timeToDest-time);
  }

  public double timeToDestination(int time) {
    return (timeToDest-time);
  }

  public Velocity getVelocity() {
    return startVelocity;
  }

  public void printInfo() {
    //System.out.println("New trajectory: ");
    for (int i=0 ; i<noPoints ; i++) {
      //System.out.println(getPointAt(i));
    }    
  }  
}
