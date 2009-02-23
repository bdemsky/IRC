// the class that describes a flight

class Flight implements Cloneable
{
    public String flightID;  // the flight id
    public int trialStatus; // 
    public Aircraft aircraftType; // the type of aircraft
    public Track track; // data from radar
    public Trajectory traject; // the estimated trajectory
    public FlightPlan fPlan; // the associated flight plan
    public String flightType; // the type of flight
    private float horizAcc, vertAcc; // data used for estimating trajectory
    static int realFlightStatus=-1;
    static int trialFlightStatus=1;


    public Flight(String id)
    {
	this.flightID=id;
	this.trialStatus=realFlightStatus;
    }

    
    public void setAircraftType (Aircraft ac)
    {
	this.aircraftType=ac;
    }
  
    public void setFlightType(String flightType)
    { 
	this.flightType=flightType;
    }
  

    public void setTrack(Track newTrack)
    {
	this.track=newTrack;
    }

    public void setFlightPlan(FlightPlan fp)
    {
	fPlan=fp;
    }
    
    public void updateTrajectory(double time)
    {
	TrajectorySynthesizer.updateTrajectory(time, this);
    }

    //  public Trajectory getTrajectory (int time)
    //{
    //boolean condition=false;
    //if (condition) traject=computeTrajectory(time);

    //    return traject;
    //  }

    public boolean hasID (String id)
    {
	return (flightID.compareTo(id)==0);
    }

    public boolean isFlying (String flType)
    {
	return (flightType.compareTo(flType)==0);
    }

    public static Flight copyOf(Flight f)
    {
	try {
	    return (Flight) f.clone();
	} catch (Exception e) { System.out.println("Copying error !"); }
	return null;
    }
	
    public String toString()
    {
	return new String("Flight "+flightID+"  Aircraft:"+aircraftType.type+
			  "  Flight type:"+flightType+
			  "  Cruise Altitude:"+fPlan.cruiseAlt+
			  "  Cruise Speed:"+fPlan.cruiseSpeed+"\n"+fPlan.r);
    }

}









