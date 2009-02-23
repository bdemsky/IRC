import java.util.*;
import java.io.*;

class Algorithm
{
  public static double initialTime,time;
  public static double currIteration;
  public static ConflictList cList=new ConflictList();
  
  public static void setInitialTime(double time)
  {
    initialTime=time;
    currIteration=0;
  }
  
  public static boolean isConflict(Point4d p1, Point4d p2)
  {
    Point2d pAux1=new Point2d(p1.x,p1.y);
    Point2d pAux2=new Point2d(p2.x,p2.y);
    if ( (Point2d.squaredDistance(pAux1,pAux2)<=Math.pow(Static.radius,2)) && (Math.abs(p1.z-p2.z)<=Static.distance) )
      return true;
    else return false;
  }

  public static Point4d findConflict(Flight a, Flight b)
  {
      Point4d conflictPoint=new Point4d(Point4d.outOfRangeTime,0,0,0);
      if (a.flightID!=b.flightID) {
    ArrayList p1=a.traject.p;
    ArrayList p2=b.traject.p;
    
    int pos=0;
    boolean found=false;
    
    while ( (pos<p1.size()) && (pos<p2.size()) && (!found) )
      {
	Point4d point1=(Point4d) p1.get(pos);
	Point4d point2=(Point4d) p2.get(pos);
	if (isConflict(point1,point2))
	  { 
	    //	    System.out.println("CONFLICT at position "+point1);

	    System.out.println(point1+" "+point2);
	    found=true;
	    conflictPoint=point1;
	    //	    cList.newConflict(time+pos*Static.iterationStep,point1,a,b);
	  }
	pos++;
      }
      }
      return conflictPoint;
  }


  
    public static ConflictList getConflictsWith(double time, Flight flight)
    {
	ConflictList conflicts=new ConflictList();

	ArrayList flights=FlightList.f;
	int n=FlightList.noFlights,i,j;

	TrajectorySynthesizer.updateTrajectory(time, flight);
	for (i=0; i<n; i++) {
	    Flight aAux=(Flight) flights.get(i);
	    TrajectorySynthesizer.updateTrajectory(time, aAux);
	}

	Flight aux1=flight;
	for (i=0; i<n; i++) {
	    Flight aux2=(Flight) flights.get(i);
	    Point4d conflictPoint=findConflict(aux1,aux2);
	  if (!(conflictPoint.outOfRange())) {
	      conflicts.newConflict(conflictPoint,aux1,aux2);
	  }
	}
	return conflicts;
    }


  static public void doIteration() throws IOException
  {
    time=initialTime+currIteration*Static.iterationStep;
    currIteration++;
    System.out.println("In doIteration!");
    System.out.println("Time:"+time);
    

    cList.clear();
    ArrayList flights=FlightList.f;
    int n=FlightList.noFlights,i,j;

    for (i=0;i<n;i++)
      {	
	Flight aAux=(Flight) flights.get(i);
	TrajectorySynthesizer.updateTrajectory(time,aAux);
      }
    
    System.out.println("Does it get here? (after the trajectory update)");

    for (i=0;i<n;i++)
      for (j=i+1;j<n;j++)
	{
	  Flight aux1=(Flight) flights.get(i);
	  Flight aux2=(Flight) flights.get(j);
	  Point4d conflictPoint=findConflict(aux1,aux2);
	  if (!(conflictPoint.outOfRange())) {
	      cList.newConflict(conflictPoint,aux1,aux2);
	  }
	}
  }

}










