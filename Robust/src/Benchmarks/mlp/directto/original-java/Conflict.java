// This class memorizes a conflict

class Conflict 
{
    public Point4d coordinates; // its position
    public Flight flight1, flight2; // the two flights involved in the conflict

    public Conflict(Point4d coord, Flight f1, Flight f2)
    {
	coordinates=coord;
	flight1=f1;
	flight2=f2;
    }

    public boolean hasFlights(Flight f1, Flight f2)
    {
      if ( ((flight1.flightID==f1.flightID)&&(flight2.flightID==f2.flightID))||
	   ((flight1.flightID==f2.flightID)&&(flight2.flightID==f1.flightID)) )
	return true;
      else return false;
    }

  public String toString()
  {
    return ("Conflict at time "+coordinates.time+" position "+coordinates+" between "+
	    flight1.flightID+" and "+flight2.flightID+".");
  }
    
}
