// This class contains the the main method of this project. 
// All it does is to initialize the input and output threads
// and kick off the algorithm

//import java.io.*;

public class D2 {
  public ReadWrite rw;

  private Static                singletonStatic               ; public Static                getStatic               () { return singletonStatic               ; }
  private AircraftList	        singletonAircraftList	      ; public AircraftList	     getAircraftList	     () { return singletonAircraftList	       ; }   
  private Algorithm	       	singletonAlgorithm	      ; public Algorithm	     getAlgorithm	     () { return singletonAlgorithm	       ; }
  private FixList	       	singletonFixList	      ; public FixList	             getFixList	             () { return singletonFixList	       ; }
  private Flight	       	singletonFlight	              ; public Flight  	             getFlight	             () { return singletonFlight	       ; }
  private FlightList	       	singletonFlightList	      ; public FlightList	     getFlightList	     () { return singletonFlightList	       ; }
  private MessageList	       	singletonMessageList	      ; public MessageList	     getMessageList	     () { return singletonMessageList	       ; }
  private TrajectorySynthesizer singletonTrajectorySynthesizer; public TrajectorySynthesizer getTrajectorySynthesizer() { return singletonTrajectorySynthesizer; }

  public D2() {
    singletonStatic                = new Static               ();
    singletonAircraftList	   = new AircraftList	      ();
    singletonFixList	       	   = new FixList	      ();
    singletonAlgorithm	       	   = new Algorithm	      ();
    singletonFlight                = new Flight               ( "" );
    singletonFlightList	       	   = new FlightList	      (); 
    singletonMessageList	   = new MessageList	      ();
    singletonTrajectorySynthesizer = new TrajectorySynthesizer();
  }

  public static void main(String arg[]) {
    System.out.println("D2 - Application started");

    D2 d2 = new D2();

    
    d2.rw=new ReadWrite();
    d2.rw.read(d2);
    

    d2.getMessageList().executeAll(d2);
    
    int count = 0;
    while( d2.getFlightList().anyPlanesAlive() ) {
	//      d2.getAlgorithm().doIteration(d2);
      
      count++;
      if( count % 10000 == 0 ) {
	//System.out.println( "iteration "+count );
      }

      if( count == 1000 ) {
	break;
      }
    }

    d2.rw.write(d2);
  }
}
