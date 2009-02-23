/* 
  The class that estimates the trajectory of a plane, given its 
  current position, velocity and flight plan
*/

import java.util.*;
import java.lang.*;

public class TrajectorySynthesizer

{
    private static double horizTotalDist, currentDist;
    private static Velocity currentVelocity;
    private static Point4d currentPos;
    private static int nextFix, noFixes;
    private static int iterations;
    private static double thrust, lift, targetSpeed, targetAlt, timeF=0; 
    static Trajectory traject;

    private static double bigUglyConstant=1000000;
    //    private static double outOfRangeTime=-1;
    private static int limit=200;

    public static Trajectory updateTrajectory (int time, Flight flight)
    {
	Integer nTime=new Integer(time);
	return updateTrajectory (nTime.doubleValue(), flight);
    }

    public static Trajectory updateTrajectory (double time, Flight flight)
    /*
      the method that updates the estimated trajectory for a flight
    */
    {
		System.out.println("Updating trajectory for "+flight.flightID);
	int i;
	setInitialParameters(flight);
		System.out.println("Starting position: "+currentPos);
	if (currentPos.outOfRange()) {
	    traject.setNoPoints(1);
	    traject.setPoint(0, currentPos);
	    traject.distToDest=0;
	    traject.timeToDest=0;
	}
	else {
	for (i=0 ; (!currentPos.outOfRange()) && (i<limit) ; i++)
	    {
		//		if (currentPos.outOfRange()) {
		//   traject.setPoint(i, (Point4d) currentPos);
		//}
		//	else {
		    //		    System.out.println(i+"current position" +currentPos);
		    getTrajectoryPoint(flight, time+i*Static.iterationStep);
		    //		    System.out.println(currentVelocity);
		    //		    System.out.println(currentPos);
		    if (i==0) {
			System.out.println("current position: "+currentPos);
			traject.distToDest=horizTotalDist;
			traject.nextFixIndex=nextFix;
			traject.nextFix=(currentPos.outOfRange())? null : flight.fPlan.r.getFixAt(nextFix);
		    }
		    if (i==1) {
			traject.startVelocity=new Velocity(currentVelocity);
			//			System.out.println(currentVelocity+" "+traject.startVelocity);

		    }
		    traject.setPoint(i, (Point4d) currentPos);
		    //		}
		
	    }
	traject.setNoPoints(--i);
		System.out.println(traject.noPoints);
	traject.timeToDest=(i>0)? traject.getPointAt(i-1).time+timeF:time+timeF;
	//			System.out.println(currentVelocity+" "+traject.startVelocity);
	}
	flight.traject=traject;
		System.out.println("Finished updating trajectory ...");
	return traject;
    }

    private static void setInitialParameters(Flight flight)
    /*
      initialization & initial computation part
    */
    {
	//	System.out.println("Initial parameters ...");
	int i;
	Point2d p1, p2;
	//	Double temp=new Double(Static.noIterations);
	//	iterations=temp.intValue();
	traject=new Trajectory(10);
	currentPos=new Point4d(flight.track.pos.time, flight.track.pos.x,
			       flight.track.pos.y, flight.track.pos.z);
	currentVelocity=new Velocity(flight.track.vel);
	horizTotalDist=0;
	p1=new Point2d(currentPos.x, currentPos.y);
	for (i=flight.fPlan.r.current; i<flight.fPlan.r.noFixes; i++)
	    {
		p2=flight.fPlan.r.getCoordsOf (i);
		horizTotalDist+=Point2d.horizDistance(p1,p2);
		p1=p2;
	    }
	//	System.out.println("horiz total distance: "+horizTotalDist);

	//	traject.distToDestination=horizTotalDist;
	nextFix=flight.fPlan.r.current;
	noFixes=flight.fPlan.r.noFixes;
	thrust=flight.aircraftType.maxThrust;
	lift=flight.aircraftType.maxLift;
        targetSpeed=flight.fPlan.cruiseSpeed;
	targetAlt=flight.fPlan.cruiseAlt;
	//	System.out.println("thrust= "+thrust+"  lift= "+lift+
	//	System.out.println( " speed= "+targetSpeed+" alt= "+targetAlt);
    //	System.out.println("current velocity: "+currentVelocity);
    }

    private static void getTrajectoryPoint(Flight flight, double time)
    /*
      estimates the position at time given, using data computed so far
    */
    {
	if (currentPos.time<time) {
	double z=newVerticalCoord(flight, time);
	Point2d pXY=newHorizCoords (flight, time);
	//System.out.println ("XY : "+pXY);
	if (pXY.x<bigUglyConstant-1) {
	    currentPos=new Point4d(time, pXY.x, pXY.y, z);
	    //  System.out.println("currentPos="+currentPos+"  currentvel= "+currentVelocity+"  next fix: "+nextFix);
	}
	else { currentPos=new Point4d (Point4d.outOfRangeTime,0,0,0); }
	}
    }

    private static double newVerticalCoord (Flight flight, double time)
    /*
      - estimates the new vertical coordinate at the time given
      - also, updates current vertical velocity & acceleration
    */
    {
	double newZ;
	double acc;

	if (currentPos.z>=targetAlt) {
	    newZ=currentPos.z;
	    currentVelocity.vector.z=0;
	}
	else {
	    acc=neededAcceleration(currentPos.z, targetAlt, lift);
	    newZ=currentPos.z+currentVelocity.vector.z*(time-currentPos.time)+
		acc*Math.pow(time-currentPos.time,2)/2;
	    newZ=(newZ>=targetAlt)? targetAlt:newZ;
	    currentVelocity.vector.z=(newZ>=targetAlt)?
		0:(currentVelocity.vector.z+acc*(time-currentPos.time));
	}
	return newZ; // to be completed
    }

    private static double neededAcceleration(double speed, double nSpeed,
				      double thrust)
    {
	double accel=(speed<nSpeed)? thrust: (speed>nSpeed)? -thrust:0;
	return accel;
    }

    private static Point2d decompose (double scalar,
			       Point2d p1, Point2d p2)
    {
	double angle;
	if (p1.x==p2.x) {
	    angle=(p1.y<p2.y)? Math.PI/2 : -Math.PI/2;
	}
	else {
	    angle=(p1.x<p2.x)?
		Math.atan((p1.y-p2.y)/(p1.x-p2.x)):
		Math.PI+Math.atan((p1.y-p2.y)/(p1.x-p2.x));
	}
	//	System.out.println("scalar= "+scalar+" angle="+angle);
	return (new Point2d (scalar*Math.cos(angle),
			     scalar*Math.sin(angle)));
    }
    
    private static Point2d newHorizCoords (Flight flight, double time)
    /*
      - estimates the new horizontal coordinates at the time given
      - updates the horizontal velocity & acceleration
    */
    {
	double dt=time-currentPos.time;
	//        System.out.println("dt="+dt);
	//        System.out.println("time="+time);
	double hSpeed=currentVelocity.horizSpeed();
	double accel=neededAcceleration(hSpeed, targetSpeed, thrust);
	double accelTime=(accel==0)? 0:(targetSpeed-hSpeed)/accel;
	double distance, speed;
	Point2d current, temp, temp2;
	//	System.out.println("current:"+hSpeed+" accel="+accel+" time:"+accelTime);
	if (accelTime<dt) {
	    speed=targetSpeed;
	    distance=hSpeed*accelTime+accel*Math.pow(accelTime,2)/2+
		speed*(dt-accelTime);
	}
	else {
	    speed=hSpeed+accel*dt;
	    distance=hSpeed*dt+accel*Math.pow(dt,2)/2;
	}

	//        if ((horizTotalDist<0) || (hSpeed<0)) { hSpeed=0; }

	if ((distance>horizTotalDist)&&(horizTotalDist>0)) {
	    timeF=(accel<=0)?(horizTotalDist/hSpeed):
		(-hSpeed+Math.sqrt(hSpeed*hSpeed+2*accel*horizTotalDist))/accel;
	    	    System.out.println("TIMEF= "+timeF);
	}
	horizTotalDist-=distance;
	for (current=currentPos.toPoint2d();
	     (nextFix<noFixes) &&
		 (distance>Point2d.horizDistance(current,
					 flight.fPlan.r.getCoordsOf(nextFix)));
	     nextFix++) {
	    distance-=Point2d.horizDistance(current,
				    flight.fPlan.r.getCoordsOf(nextFix));
	    current=flight.fPlan.r.getCoordsOf(nextFix);
	}
	//	System.out.println("speed= "+speed+ "  distance: "+distance+" fix: "+
	//			   flight.fPlan.r.getCoordsOf(nextFix)+
	//			   " current: "+current);
	if (nextFix<noFixes) {
	    temp2=decompose(distance,
			      current, flight.fPlan.r.getCoordsOf(nextFix));
	    temp=decompose(speed,
			   current, flight.fPlan.r.getCoordsOf(nextFix));

	    current=new Point2d(temp2.x+current.x, temp2.y+current.y);

	    currentVelocity.vector.x=temp.x;
	    currentVelocity.vector.y=temp.y;
	}
	else {
	    current=new Point2d(bigUglyConstant,bigUglyConstant);
	}
	return current;
    }

}
