// This class memorizes all the existing aircrafts

//import java.util.*;

public class AircraftList {
  public int noAircrafts;
  private Vector aircrafts;

  public AircraftList() {
    noAircrafts=0; // the number of aircrafts
    aircrafts=new Vector(100); // the aircrafts
  }

  // sets the parameters of the aircraft number "pos": its name, its lift and its thrust
  public void setAircraft(String name,double lift,double thrust) {
    aircrafts.addElement(new Aircraft(name,lift,thrust));
  }

  public Aircraft getAircraft(String name) {
    for( int i = 0; i < aircrafts.size(); ++i ) {
      Aircraft aAux=(Aircraft) aircrafts.elementAt(i);
      if (aAux.hasType(name))
	return aAux;
    }

    System.out.println("Aircraft not found - "+name);
    System.exit(-1);
  }

  public int getAircraftIndex(String name) {
    for( int i = 0; i < aircrafts.size(); ++i ) {
      Aircraft aAux=(Aircraft) aircrafts.elementAt(i);
      if (aAux.hasType(name))
	return i;
    }

    System.out.println("Aircraft not found - "+name);
    System.exit(-1);
  }

  public void printInfo() {
    //System.out.println("\n\nThe number of aircrafts:"+noAircrafts);
    //System.out.println("The aircrafts are:");
    for( int i = 0; i < aircrafts.size(); ++i ) {
      Aircraft aAux=(Aircraft) aircrafts.elementAt(i);
      //System.out.println(aAux);
    }
  }

  public void addAircraft(StringTokenizer parameters) {
    setAircraft(parameters.nextToken(), Integer.parseInt(parameters.nextToken()), Integer.parseInt(parameters.nextToken()));
    noAircrafts++;
  }
    
  public void removeAircraft(StringTokenizer parameters) {
    noAircrafts--;
    int aAuxIndex=getAircraftIndex(parameters.nextToken());
    aircrafts.removeElementAt(aAuxIndex);
  }
}
