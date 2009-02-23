// This is the class that manages all the flights

import java.util.*;

class FlightList
{
  public static int noFlights=0;
  public static ArrayList f=new ArrayList(100);


  public void addFlight(int index, Flight flight)
  {
    f.add(index,flight);
  }

  public static void addFlightPlan(int time, StringTokenizer st)
  { 
    Flight newFlight= new Flight(st.nextToken());
    noFlights++;
    f.add(newFlight);
    FlightPlan fAux=new FlightPlan();
    Aircraft aAux=AircraftList.getAircraft(st.nextToken());      
    newFlight.setAircraftType(aAux);
    newFlight.setFlightType(st.nextToken());
    Route rAux=new Route(Integer.parseInt(st.nextToken()));
    for (int i=0;i<rAux.noFixes;i++)
      rAux.addFix(i,st.nextToken());           
    fAux.setRoute(rAux);
    fAux.setCruiseParam(Double.parseDouble(st.nextToken()), Double.parseDouble(st.nextToken()));
    newFlight.setFlightPlan(fAux);
  }

  public static String getFlightName(int index)
  {
    Flight fAux=(Flight) f.get(index);
    return fAux.flightID;
  }

  public static void amendFlightPlan(int time, StringTokenizer st)
  {
    Flight fAux=getFlight(st.nextToken());
    Route rAux=new Route(Integer.parseInt(st.nextToken()));    
    for (int i=0;i<rAux.noFixes;i++)
      rAux.addFix(i,st.nextToken());      
    fAux.fPlan.setRoute(rAux);
    fAux.fPlan.setCruiseParam(Double.parseDouble(st.nextToken()), Double.parseDouble(st.nextToken()));
  }

  public static void amendFlightInfo(int time, StringTokenizer st)
  {
    Flight fAux=getFlight(st.nextToken());
    Aircraft aAux=AircraftList.getAircraft(st.nextToken());      
    fAux.setAircraftType(aAux);
    fAux.setFlightType(st.nextToken());
  }

  public static void sendingAircraft(int time, StringTokenizer st)
  {
      int noF=Integer.parseInt(st.nextToken());
      String id;
      Point4d pos;
      Velocity vel;
      Track t;
      String nameFix;
      Flight fAux;
      for (int counter=0 ; counter<noF ;counter ++) {
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
	  //	  int i=0;
	  //	  while ((i<noFlights) && (((Flight) f.get(i)).hasID(id))) i++;
	  fAux=getFlight(id);
	  System.out.println(id+" Flight id: "+fAux.flightID);
	  //	  if (i<noFlights) {
	      fAux.setTrack(t);
	      System.out.println("Setting current fix ...");
	      fAux.fPlan.setCurrentFix(nameFix);
	      //	      System.exit(0);
	      System.out.println("Sent flight "+
				 fAux.flightID+
				 "; position: "+
				 fAux.track.pos);
              TrajectorySynthesizer.updateTrajectory(time,
						     fAux);
	      fAux.traject.printInfo();
	       //	  }
      }
    // You don't read this !!! 
    //  String currentFix=st.nextToken();
  }


  public static void removeFlightPlan(int time, StringTokenizer st)
  {
    String id=st.nextToken();
    int i=0;
    while ((i<noFlights) && (((Flight) f.get(i)).hasID(id))) i++;
    if (i<noFlights) f.remove(i);
  }


  public static Flight getFlight(String id)
  {    
    Iterator iter=f.iterator();
    while (iter.hasNext())
      {
	Flight fAux=(Flight) iter.next();	    
	if (fAux.hasID(id))
	  return fAux;
      }
    throw new RuntimeException("Flight not found - "+id);
  }


  // only for th version w/o GUI
  public static boolean anyPlanesAlive()   
  {
      boolean aux=false;
      ArrayList flights=FlightList.f;

      for (int i=0;i<FlightList.noFlights;i++)
	  {	
	      Flight aAux=(Flight) flights.get(i);
	      ArrayList p1=aAux.traject.p;
	      Point4d pAux= (Point4d) p1.get(0);
	      if (!pAux.outOfRange())
		  aux=true;
	  }
      return aux;
  }


  public static void printInfo()
  // this is a test procedure
  {
    System.out.println("\n\nThe number of flights:"+noFlights);
    System.out.println("The flights are:");
    Iterator iter=f.iterator();
    while (iter.hasNext())
      {
	Flight fAux=(Flight) iter.next();	    
	System.out.println(fAux);
      }
  }
  
}



