// the class that describes a flight

public class Flight /*implements Cloneable*/ {
  
  private D2 d2;

  public String flightID;  // the flight id
  public int trialStatus; // 
  public Aircraft aircraftType; // the type of aircraft
  public Track track; // data from radar
  public Trajectory traject; // the estimated trajectory
  public FlightPlan fPlan; // the associated flight plan
  public String flightType; // the type of flight
  private float horizAcc, vertAcc; // data used for estimating trajectory

  public static int realFlightStatus(){ return -1;}
  public static int trialFlightStatus(){ return 1;}  

  public Flight(D2 d2, String id) {
    this.d2=d2;
    this.flightID=id;
    this.trialStatus=realFlightStatus();
  }
    
  public void setAircraftType (Aircraft ac) {
    this.aircraftType=ac;
  }
  
  public void setFlightType(String flightType) { 
    this.flightType=flightType;
  }
  
  public void setTrack(Track newTrack) {
    this.track=newTrack;
  }

  public void setFlightPlan(FlightPlan fp) {
    fPlan=fp;
  }
    
  public void updateTrajectory(double time) {
    d2.getTrajectorySynthesizer().updateTrajectory(time, this);
  }

  public boolean hasID (String id) {
    return (flightID.compareTo(id)==0);
  }

  public boolean isFlying (String flType) {
    return (flightType.compareTo(flType)==0);
  }

  public static Flight copyOf( D2 d2, Flight f) {
    Flight fNew       = new Flight(d2, f.flightID);
    fNew.trialStatus  = f.trialStatus;
    fNew.aircraftType = f.aircraftType;
    fNew.track        = f.track;
    fNew.traject      = f.traject; 
    fNew.fPlan        = f.fPlan;
    fNew.flightType   = f.flightType; 
    fNew.horizAcc     = f.horizAcc;
    fNew.vertAcc      = f.vertAcc;
    return fNew;
  }
	
  public String toString() {
    return new String("Flight "+flightID+"  Aircraft:"+aircraftType.type+
		      "  Flight type:"+flightType+
		      "  Cruise Altitude:"+fPlan.cruiseAlt+
		      "  Cruise Speed:"+fPlan.cruiseSpeed+"\n"+fPlan.r);
  }
}
