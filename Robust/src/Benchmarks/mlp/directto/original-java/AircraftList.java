// This class memorizes all the existing aircrafts


import java.util.*;

final class AircraftList
{
    public static int noAircrafts=0; // the number of aircrafts
    private static ArrayList aircrafts=new ArrayList(100); // the aircrafts

    public static void setAircraft(String name,double lift,double thrust)
    // sets the parameters of the aircraft number "pos": its name, its lift and its thrust
    {
	aircrafts.add(new Aircraft(name,lift,thrust));
    }

    public static Aircraft getAircraft(String name)
    // returns the fix with the given name
    {
	Iterator it=aircrafts.iterator();
	while (it.hasNext())
	    {
		Aircraft aAux=(Aircraft) it.next();	    
		if (aAux.hasType(name))
		    return aAux;
	    }
	throw new RuntimeException("Aircraft not found - "+name);
    }

    public static Iterator getAircrafts()
    {
	return aircrafts.iterator();
    }
    
    public static void printInfo()
    // this is a test procedure
    {
	System.out.println("\n\nThe number of aircrafts:"+noAircrafts);
	System.out.println("The aircrafts are:");
	Iterator it=aircrafts.iterator();
	while (it.hasNext())
	    {
		Aircraft aAux=(Aircraft) it.next();
		System.out.println(aAux);
	    }
    }

    public static void addAircraft(StringTokenizer parameters)
    {
	setAircraft(parameters.nextToken(), Integer.parseInt(parameters.nextToken()), Integer.parseInt(parameters.nextToken()));
	noAircrafts++;
    }
    
    public static void removeAircraft(StringTokenizer parameters)
    {
	noAircrafts--;
	Aircraft aAux=getAircraft(parameters.nextToken());
	aircrafts.remove(aAux);
    }

}




