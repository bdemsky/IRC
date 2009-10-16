//import java.util.*;

public class Message {
  int time;
  String type;
  StringTokenizer parameters;

  public Message(int time, String type, StringTokenizer parameters) {
    this.time=time;
    this.type=type;
    this.parameters=parameters;
  }

  public void executeMessage(D2 d2) {
    System.out.println("Executing message of type "+type);

    //static messages
    if (type.compareTo("SET_MAP_SIZE")==0) {
      System.out.println("Setting the map size...");
      d2.getStatic().setMapSize(parameters);
    }
    else if (type.compareTo("SET_ITERATION_STEP")==0) {
      System.out.println("Setting the iteration step...");
      d2.getStatic().setIterationStep(parameters);		
    }
    else if (type.compareTo("SET_NO_OF_ITERATIONS")==0) {
      System.out.println("Setting the no. of iterations...");
      d2.getStatic().setNumberOfIterations(parameters);		
    }
    else if (type.compareTo("SET_CYLINDER")==0) {
      System.out.println("Setting the cylinder of safety/unsafety...");
      d2.getStatic().setCylinder(parameters);		
    }
    else if (type.compareTo("ADD_FIX")==0) {
      System.out.println("Adding a new fix...");
      d2.getFixList().addFix(parameters);
    }
    else if (type.compareTo("REMOVE_FIX")==0) {
      System.out.println("Removing a fix...");
      d2.getFixList().removeFix(parameters);
    }
    else if (type.compareTo("ADD_AIRCRAFT")==0) {
      System.out.println("Adding an aircraft...");
      d2.getAircraftList().addAircraft(parameters);
    }
    else if (type.compareTo("REMOVE_AIRCRAFT")==0) {
      System.out.println("Removing an aircraft...");
      d2.getAircraftList().removeAircraft(parameters);
    }

    //dynamic messages
    if (type.compareTo("DO_WORK")==0)
      d2.getAlgorithm().setInitialTime(time);

    if (type.compareTo("ADD_FLIGHT_PLAN")==0) {
      System.out.println("Adding flight plan...");
      d2.getFlightList().addFlightPlan(d2,time,parameters);
    }
    else if (type.compareTo("REMOVE_FLIGHT_PLAN")==0) {
      System.out.println("Removing flight plan...");
      d2.getFlightList().removeFlightPlan(time,parameters);		
    }
    else if (type.compareTo("AMEND_FLIGHT_INFO")==0) {
      System.out.println("Amending flight info...");
      d2.getFlightList().amendFlightInfo(d2, time,parameters);
    }		    
    else if (type.compareTo("AMEND_FLIGHT_PLAN")==0) {
      System.out.println("Amending flight plan...");
      d2.getFlightList().amendFlightPlan(d2, time,parameters);	       
    }
    else if (type.compareTo("SENDING_AIRCRAFT")==0) {
      System.out.println("Sending aircraft data...");
      d2.getFlightList().sendingAircraft(d2, time,parameters);
    }
  }
}
