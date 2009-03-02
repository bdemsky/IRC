import java.util.*;

class Message
{
    int time;
    String type;
    StringTokenizer parameters;

    public Message(int time, String type, StringTokenizer parameters)
    {
	this.time=time;
	this.type=type;
	this.parameters=parameters;
    }

     public Message(Message m)
     {
    	this.time=m.time;
	this.type=m.type;
	this.parameters=m.parameters;
     }

    public void executeMessage()
    {
	System.out.println("Executing message of type "+type);
	//static messages
	if (type.compareTo("SET_MAP_SIZE")==0)
	    {
		System.out.println("Setting the map size...");
		Static.setMapSize(parameters);
	    }
	if (type.compareTo("SET_ITERATION_STEP")==0)
	    {
		System.out.println("Setting the iteration step...");
		Static.setIterationStep(parameters);		
	    }
	if (type.compareTo("SET_NO_OF_ITERATIONS")==0)
	    {
		System.out.println("Setting the no. of iterations...");
		Static.setNumberOfIterations(parameters);		
	    }
	if (type.compareTo("SET_CYLINDER")==0)
	    {
		System.out.println("Setting the cylinder of safety/unsafety...");
		Static.setCylinder(parameters);		
	    }
	if (type.compareTo("ADD_FIX")==0)
	    {
		System.out.println("Adding a new fix...");
		FixList.addFix(parameters);
	    }
	if (type.compareTo("REMOVE_FIX")==0)
	    {
		System.out.println("Removing a fix...");
		FixList.removeFix(parameters);
	    }
	if (type.compareTo("ADD_AIRCRAFT")==0)
	    {
 	        System.out.println("Adding an aircraft...");
	        AircraftList.addAircraft(parameters);
	    }
	if (type.compareTo("REMOVE_AIRCRAFT")==0)
	    {
 	        System.out.println("Removing an aircraft...");
	        AircraftList.removeAircraft(parameters);
	    }

	//dynamic messages
	if (type.compareTo("DO_WORK")==0)
	    Algorithm.setInitialTime(time);

	if (type.compareTo("ADD_FLIGHT_PLAN")==0)
	    {
		System.out.println("Adding flight plan...");
		FlightList.addFlightPlan(time,parameters);		
	    }
	if (type.compareTo("REMOVE_FLIGHT_PLAN")==0)
	    {
		System.out.println("Removing flight plan...");
		FlightList.removeFlightPlan(time,parameters);		
	    }
	if (type.compareTo("AMEND_FLIGHT_INFO")==0)
	    {
		System.out.println("Amending flight info...");
		FlightList.amendFlightInfo(time,parameters);
	    }		    
	if (type.compareTo("AMEND_FLIGHT_PLAN")==0)
	    {
		System.out.println("Amending flight plan...");
		FlightList.amendFlightPlan(time,parameters);	       
	    }
	if (type.compareTo("SENDING_AIRCRAFT")==0)
	    {
		System.out.println("Sending aircraft data...");
		FlightList.sendingAircraft(time,parameters);
	    }
    }
}


