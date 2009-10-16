// This class contains the the main method of this project. 
// All it does is to initialize the input and output threads
// and kick off the algorithm

//import java.io.*;

public class D2 {

  public ReadWrite             singletonReadWrite            ; public ReadWrite             getReadWrite            () { return singletonReadWrite            ; }
  public MessageList	       singletonMessageList	     ; public MessageList	    getMessageList	    () { return singletonMessageList	      ; }
  public Static                singletonStatic               ; public Static                getStatic               () { return singletonStatic               ; }
  public AircraftList	       singletonAircraftList	     ; public AircraftList	    getAircraftList	    () { return singletonAircraftList	      ; }   
  public FlightList	       singletonFlightList	     ; public FlightList	    getFlightList	    () { return singletonFlightList	      ; }
  public FixList               singletonFixList	             ; public FixList	            getFixList	            () { return singletonFixList	      ; }
  public Algorithm	       singletonAlgorithm            ; public Algorithm	            getAlgorithm	    () { return singletonAlgorithm	      ; }
  public TrajectorySynthesizer singletonTrajectorySynthesizer; public TrajectorySynthesizer getTrajectorySynthesizer() { return singletonTrajectorySynthesizer; }

  public D2() {
    singletonReadWrite             = new ReadWrite            ();
    singletonMessageList	   = new MessageList	      ();
    singletonStatic                = new Static               ();
    singletonAircraftList	   = new AircraftList	      ();
    singletonFlightList	       	   = new FlightList	      (); 
    singletonFixList	       	   = new FixList	      ();
    singletonAlgorithm	       	   = new Algorithm	      ();
    singletonTrajectorySynthesizer = new TrajectorySynthesizer();    
  }

  public static void main(String arg[]) {
    System.out.println("D2 - Application started");

    D2 d2 = new D2();
    
    d2.getReadWrite().read(d2);
        
    d2.getMessageList().executeAll(d2);
    
    /*
    while( d2.getFlightList().anyPlanesAlive() ) {
      d2.getAlgorithm().doIteration(d2);
    }

    d2.getReadWrite().write(d2);
    */

    System.out.println("D2 - Application finished");
  }
}
