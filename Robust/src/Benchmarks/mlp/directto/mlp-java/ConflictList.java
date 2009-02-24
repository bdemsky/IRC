// This class keeps a list of conflicts.
// The conflicts are updated at every moment of time 
// We detect only the first conflict between every pair of flights

//import java.util.*;

public class ConflictList 
{   
  public int noConflicts; // the number of conflicts
  private Vector conflicts; // the conflicts
	
  public ConflictList() {
    noConflicts=0;
    conflicts=new Vector(100);
  }
    
  public void clear() {		
    noConflicts=0;
    conflicts.clear();
  }

  public Conflict conflictAt(int index) {
    return (Conflict) conflicts.elementAt(index);
  }
    
  public String printInfo() {
    String st;
    if (noConflicts==0)
      st="No conflicts!";
    else {
      st=""+noConflicts+" conflicts\n";
      for( int i = 0; i < conflicts.size(); ++i ) {
	Conflict cAux=(Conflict) conflicts.elementAt(i);
	st=st+"\n"+cAux;
      }
    }
    return st;
  }
  
  public void newConflict(Point4d coord, Flight f1, Flight f2) {
    noConflicts++;
    conflicts.addElement(new Conflict(coord,f1,f2));
  }
  
  public Conflict findConflict(Flight f1, Flight f2) {	
    for( int i = 0; i < conflicts.size(); ++i ) {
      Conflict cAux=(Conflict) conflicts.elementAt(i);
      if (cAux.hasFlights(f1,f2))
	return cAux;
    }
    return null;
  }

  public int findConflictIndex(Flight f1, Flight f2) {	
    for( int i = 0; i < conflicts.size(); ++i ) {
      Conflict cAux=(Conflict) conflicts.elementAt(i);
      if (cAux.hasFlights(f1,f2))
	return i;
    }
    return -1;
  }
  
  public void removeConflict(Flight f1, Flight f2) {
    noConflicts--;
    int cAuxIndex=findConflictIndex(f1,f2);
    conflicts.removeElementAt(cAuxIndex);
  }
}
