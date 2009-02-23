// trial flight class with trial planning-related methods

class TrialFlight {

    Flight oldFlight, trialFlight;
    Route trialRoute;
    Trajectory trialTrajectory;
    double time;
    Fix fix1, fix2;
    int fixIndex;
    // differences between the old flight and the trial flight follow:
    double timeDiff, distDiff; // time and distance difference
    ConflictList oldConflicts, newConflicts; // the lists of conflicts
    int noNew, noRemoved; // the number of new and removed conflicts, respectively
    
    public TrialFlight(Flight flight, Fix fix1, Fix fix2)
    // constructor of a trial flight using a shortcut between two fixes.
    {
	int aux, fixIndex1, fixIndex2;
	oldFlight=flight;
	fixIndex1=oldFlight.fPlan.r.getIndexOf(fix1);
	fixIndex2=oldFlight.fPlan.r.getIndexOf(fix2);
	if (fixIndex1>fixIndex2) {
	    aux=fixIndex1;
	    fixIndex1=fixIndex2;
	    fixIndex2=aux;
	}
	trialFlight=Flight.copyOf(oldFlight);
	trialFlight.trialStatus=Flight.trialFlightStatus;
	this.changeToTrialRoute(fixIndex1, fixIndex2);
    }

    public TrialFlight(Flight flight, String fixName)
    // constructor for a trial flight using the current position of a plane
    {
	oldFlight=flight;
   
    }

    public TrialFlight(Flight flight, Point4d position, Fix fix)
    // constructor that uses an estimated position and a fix
    {
	int aux;
	oldFlight=flight;
	//	fixIndex1=oldFlight.fPlan.r.getIndexOf(fixName1);
	fixIndex=oldFlight.fPlan.r.getIndexOf(fix);

	//	System.out.println(oldFlight);
	trialFlight=Flight.copyOf(oldFlight);

       	trialFlight.trialStatus=Flight.trialFlightStatus;

	oldFlight.updateTrajectory(position.time);
	trialFlight.track=new Track(new Point4d(position), new Velocity(oldFlight.track.vel)); // assuming that the position given as parameter is the same as the first point in the trajectory
	//	trialFlight.track.vel=oldFlight.traject.getVelocity();
	trialFlight.fPlan=new FlightPlan(oldFlight.fPlan);
	
	//        System.out.println("old route:"+oldFlight.fPlan.r);
	//	System.out.println("new route:"+trialFlight.fPlan.r);

	

	changeToTrialRoute(position, fixIndex);


	//        System.out.println("old route:"+oldFlight.fPlan.r);
	//	System.out.println("new route:"+trialFlight.fPlan.r);



	trajectoryDiff(position.time);
	conflictsDiff(position.time);
        System.out.println("old route:"+oldFlight.fPlan.r);
       	System.out.println("new route:"+trialFlight.fPlan.r);
	//        System.out.println("old: "+oldFlight.trialStatus);
        trialFlight.trialStatus=-1;        
	//        System.out.println("old: "+oldFlight.trialStatus);
    }

    public void trajectoryDiff (double time)
    {
	trialFlight.updateTrajectory(time);
	oldFlight.updateTrajectory(time);

        
       	System.out.println("Flight "+trialFlight.flightID+":");
	distDiff=oldFlight.traject.distanceToDestination()-
	    trialFlight.traject.distanceToDestination();
	timeDiff=oldFlight.traject.timeToDestination(time)-
	    trialFlight.traject.timeToDestination(time);
	if (timeDiff<0) { timeDiff=0; }
       	System.out.println("Time difference: "+timeDiff);
       	System.out.println("Distance difference: "+distDiff);
    }
    
    public void conflictsDiff(double time)
    {
	int i, j;
	oldConflicts=Algorithm.getConflictsWith(time,oldFlight);
	newConflicts=Algorithm.getConflictsWith(time,trialFlight);
       	System.out.println("Flight "+trialFlight.flightID+":");
       	System.out.println("Conflicts for the old flight:");
       	System.out.println(oldConflicts);
       	System.out.println("Conflicts for the trial flight:");
       	System.out.println(newConflicts);
        noNew=0;
	for (i=0 ; i<newConflicts.noConflicts ; i++) {
	    Conflict conflict=newConflicts.conflictAt(i);
	    if (oldConflicts.findConflict(conflict.flight1, conflict.flight2)==null) {
		noNew++;
	    }
	}
	noRemoved=oldConflicts.noConflicts-
	    (newConflicts.noConflicts-noNew);
	//	System.out.println("No. of new conflicts: "+noNew);
	//	System.out.println("No. of removed conflicts: "+noRemoved);
    }

    public void changeToTrialRoute (Point4d pos, int index)
    {
	int i,count, index1=0;
	if (index>-1) {
	    trialRoute=new Route(oldFlight.fPlan.r.noFixes-index);
	    trialRoute.current=0;
	    for (i=index; i<oldFlight.fPlan.r.noFixes ; i++) {
		trialRoute.addFix(i-index, oldFlight.fPlan.r.getFixAt(i));
	    }
	    trialFlight.fPlan.r=trialRoute;
	}
    }


    public void changeToTrialRoute (int index1, int index2)
    {
	int i,count;
	if ((index1>-1) && (index2>-1) && (index2-index1>1)) {
	    trialRoute=new Route(oldFlight.fPlan.r.noFixes-
				 (index2-index1-1));
	    trialRoute.current=index1+1;
	    for (i=0 ; i<=index1 ; i++) {
		trialRoute.addFix(i, oldFlight.fPlan.r.getFixAt(i));
		if (oldFlight.fPlan.r.current==i) {
		    trialRoute.current=i;
		}
	    }

	    for (i=index2; i<oldFlight.fPlan.r.noFixes ; i++) {
		trialRoute.addFix(i-index2+index1+1,
				  oldFlight.fPlan.r.getFixAt(i));
		if (oldFlight.fPlan.r.current==i) {
		    trialRoute.current=i-index2+index1+1;
		}
	    }
	    trialFlight.fPlan.r=trialRoute;
	}
    }
}


