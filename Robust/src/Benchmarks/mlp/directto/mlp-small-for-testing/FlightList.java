// This is the class that manages all the flights

//import java.util.*;

public class FlightList {
  public  int noFlights;
  public  Vector f;

  public FlightList() {
    noFlights=0;
    f=new Vector(100);
  }

  /*
  public void addFlight(int index, Flight flight) {
    f.addElement(index,flight);
  }
  */

  public  void addFlightPlan(D2 d2, int time, StringTokenizer st) { 
    Flight newFlight=disjoint flightAdd new Flight(st.nextToken());
    noFlights++;
    f.addElement(newFlight);

    FlightPlan fAux=new FlightPlan();
    Aircraft aAux=d2.getAircraftList().getAircraft(st.nextToken());      
    newFlight.setAircraftType(aAux);
  
    newFlight.setFlightType(st.nextToken());
    Route rAux=new Route(Integer.parseInt(st.nextToken()));
    for (int i=0;i<rAux.noFixes;i++)
      rAux.addFix(d2,i,st.nextToken());           
    fAux.setRoute(rAux);
    fAux.setCruiseParam(Double.parseDouble(st.nextToken()), Double.parseDouble(st.nextToken()));
    newFlight.setFlightPlan(fAux);
  }

  public  String getFlightName(int index) {
    Flight fAux=(Flight) f.elementAt(index);
    return fAux.flightID;
  }

  public  void amendFlightPlan(D2 d2, int time, StringTokenizer st) {
    Flight fAux=getFlight(st.nextToken());
    Route rAux=new Route(Integer.parseInt(st.nextToken()));    
    for (int i=0;i<rAux.noFixes;i++)
      rAux.addFix(d2,i,st.nextToken());      
    fAux.fPlan.setRoute(rAux);
    fAux.fPlan.setCruiseParam(Double.parseDouble(st.nextToken()), Double.parseDouble(st.nextToken()));
  }

    public  void amendFlightInfo(D2 d2, int time, StringTokenizer st) {
    Flight fAux=getFlight(st.nextToken());
    Aircraft aAux=d2.getAircraftList().getAircraft(st.nextToken());      
    fAux.setAircraftType(aAux);
    fAux.setFlightType(st.nextToken());
  }

    public  void sendingAircraft(D2 d2, int time, StringTokenizer st) {
    int noF=Integer.parseInt(st.nextToken());
    String id;
    Point4d pos;
    Velocity vel;
    Track t;
    String nameFix;
    Flight fAux;
    for (int counter=0; counter<noF; counter++) {
      id=st.nextToken();
      pos=new Point4d(time,
		      Double.valueOf(st.nextToken()).doubleValue(),
		      Double.valueOf(st.nextToken()).doubleValue(),
		      Double.valueOf(st.nextToken()).doubleValue());
      vel=new Velocity(Double.valueOf(st.nextToken()).doubleValue(),
		       Double.valueOf(st.nextToken()).doubleValue(),
		       Double.valueOf(st.nextToken()).doubleValue());
      t=new Track(pos, vel);
      nameFix=st.nextToken();
      fAux=getFlight(id);
      System.out.println(id+" Flight id: "+fAux.flightID);
      fAux.setTrack(t);
      System.out.println("Setting current fix ...");
      fAux.fPlan.setCurrentFix(nameFix);
      System.out.println("Sent flight "+
			 fAux.flightID+
			 "; position: "+
			 fAux.track.pos);
      d2.getTrajectorySynthesizer().updateTrajectory(d2, time, fAux);
      fAux.traject.printInfo();      
    }
  }  

  public  void removeFlightPlan(int time, StringTokenizer st) {
    String id=st.nextToken();
    int i=0;
    while ((i<noFlights) && (((Flight) f.elementAt(i)).hasID(id))) i++;
    if (i<noFlights) f.removeElementAt(i);
  }

  public  Flight getFlight(String id) {
    for( int i = 0; i < f.size(); ++i ) {
      Flight fAux=(Flight) f.elementAt(i);
      if (fAux.hasID(id))
	return fAux;
    }
    System.out.println("Flight not found - "+id);
    System.exit(-1);
    return null;
  }

  public  boolean anyPlanesAlive() {
    for( int i = 0; i < f.size(); ++i ) {
      Flight aAux=(Flight) f.elementAt(i);
      Vector p1=aAux.traject.p;
      Point4d pAux= (Point4d) p1.elementAt(0);
      if (!pAux.outOfRange())
	return true;
    }
    return false;
  }

  public  void printInfo() {
    System.out.println("\n\nThe number of flights:"+noFlights);
    System.out.println("The flights are:");
    for( int i = 0; i < f.size(); ++i ) {
      Flight fAux=(Flight) f.elementAt(i);
      System.out.println(fAux);
    }
  }
}
