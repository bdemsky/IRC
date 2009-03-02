//import java.util.*;
//import java.io.*;

public class Algorithm {
  private D2 d2;
  
  public double initialTime,time;
  public double currIteration;
  public ConflictList cList;

  public Algorithm( D2 d2 ) {
    this.d2 = d2;
    cList=new ConflictList();    
  }
  
  public /*static*/ void setInitialTime(double time) {
    initialTime=time;
    currIteration=0;
  }
  
  public /*static*/ boolean isConflict(Point4d p1, Point4d p2) {
    Point2d pAux1=new Point2d(p1.x,p1.y);
    Point2d pAux2=new Point2d(p2.x,p2.y);
    if ( (Point2d.squaredDistance(pAux1,pAux2) <= 
	  Math.pow(d2.getStatic().radius(),2))
	 
	 && Math.abs(p1.z-p2.z) <= d2.getStatic().distance()
	 )
      return true;

    return false;
  }

  public /*static*/ Point4d findConflict(Flight a, Flight b) {
    Point4d conflictPoint=new Point4d(Point4d.outOfRangeTime(),0,0,0);
    if (a.flightID!=b.flightID) {
      Vector p1=a.traject.p;
      Vector p2=b.traject.p;
      
      int pos=0;
      boolean found=false;
      
      while ( (pos<p1.size()) && (pos<p2.size()) && (!found) ) {
	Point4d point1=(Point4d) p1.elementAt(pos);
	Point4d point2=(Point4d) p2.elementAt(pos);
	if (isConflict(point1,point2)) { 	      
	  System.out.println(point1+" "+point2);
	  found=true;
	  conflictPoint=point1;
	}
	pos++;
      }
    }
    return conflictPoint;
  }
    
  public /*static*/ ConflictList getConflictsWith(double time, Flight flight) {
    ConflictList conflicts=new ConflictList();

    Vector flights=d2.getFlightList().f;
    int n,i,j;
    n=d2.getFlightList().noFlights;

    d2.getTrajectorySynthesizer().updateTrajectory(time, flight);
    for (i=0; i<n; i++) {
      Flight aAux=(Flight) flights.elementAt(i);
      d2.getTrajectorySynthesizer().updateTrajectory(time, aAux);
    }

    Flight aux1=flight;
    for (i=0; i<n; i++) {
      Flight aux2=(Flight) flights.elementAt(i);
      Point4d conflictPoint=findConflict(aux1,aux2);
      if (!(conflictPoint.outOfRange())) {
	conflicts.newConflict(conflictPoint,aux1,aux2);
      }
    }
    return conflicts;
  }

  public /*static*/ void doIteration() {
    time=initialTime+currIteration*d2.getStatic().iterationStep();
    currIteration++;
    System.out.println("In doIteration!");
    System.out.println("Time:"+time);
    
    cList.clear();
    
    Vector flights=d2.getFlightList().f;
    int n=d2.getFlightList().noFlights;
    int i,j;

    for (i=0;i<n;i++) {	
      Flight aAux=(Flight) flights.elementAt(i);
      d2.getTrajectorySynthesizer().updateTrajectory(time,aAux);
    }
    
    System.out.println("Does it get here? (after the trajectory update)");

    for (i=0;i<n;i++)
      for (j=i+1;j<n;j++) {
	Flight aux1=(Flight) flights.elementAt(i);
	Flight aux2=(Flight) flights.elementAt(j);
	Point4d conflictPoint=findConflict(aux1,aux2);
	if (!(conflictPoint.outOfRange())) {
	  cList.newConflict(conflictPoint,aux1,aux2);
	}
      }
  }
}
