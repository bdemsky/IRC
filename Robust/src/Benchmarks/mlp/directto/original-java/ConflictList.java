// This class keeps a list of conflicts.
// The conflicts are updated at every moment of time 
// We detect only the first conflict between every pair of flights

import java.util.*;

final class ConflictList
{   
    public int noConflicts; // the number of conflicts
    private ArrayList conflicts; // the conflicts

	
    public ConflictList()
    {
	noConflicts=0;
	conflicts=new ArrayList(100);
    }
    
    public void clear()
    {		
	noConflicts=0;
	conflicts.clear();      
    }

    public Conflict conflictAt(int index)
    {
	return (Conflict) conflicts.get(index);
    }

    public Iterator getConflicts()
    {
	return conflicts.iterator();
    }
    
    public String printInfo()
    // this is a test procedure
    {
	String st;
	if (noConflicts==0)
	    st="No conflicts!";
	else 
	    {
		st=""+noConflicts+" conflicts\n";
		Iterator iter=getConflicts();
		while (iter.hasNext())
		    {	    
			Conflict cAux=(Conflict) iter.next();	    
			st=st+"\n"+cAux;	  
		    }
	    }
	return st;
    }

    public void newConflict(Point4d coord, Flight f1, Flight f2)
    {
	noConflicts++;
	conflicts.add(new Conflict(coord,f1,f2));
    }
  

    public Conflict findConflict(Flight f1, Flight f2)
    {	
	Iterator iter=getConflicts();
	while (iter.hasNext())
	    {
		Conflict cAux=(Conflict) iter.next();
		if (cAux.hasFlights(f1,f2))
		    return cAux;
	    }
	return null;
    }

    
    public void removeConflict(Flight f1, Flight f2)
    {
	noConflicts--;
	Conflict cAux=findConflict(f1,f2);
	conflicts.remove(cAux);
    }

}










