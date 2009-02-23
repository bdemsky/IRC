// describes a route - number of beacons, the current position on the 
// route, and the beacons.

import java.util.*;

class Route {

    public int noFixes,current;
    public ArrayList fixes;

    Route(int no) {
	noFixes=no;
	current=0;
	fixes=new ArrayList(noFixes);
    }

    Route(int no, int cur) {
	noFixes=no;
	current=cur;
	fixes=new ArrayList(noFixes);
    }

    public void addFix (int pos, Fix f)
    {
	fixes.add(pos, f);
    }

    public void addFix (int pos, String name) {
	addFix(pos, (Fix) FixList.getFix(name) );
    }

    public Fix getFixAt(int pos)
    {
	if ((pos>-1) && (pos<noFixes)) {
	    return (Fix) fixes.get(pos);
	}
	else return null;
    }
    
    public void setCurrent(int c) {
	current=c;
    }

    public Fix getCurrent()
    {
	return (Fix) fixes.get(current);
    }

    public Point2d getCoordsOf (int i)
    {
	if ((i>-1) && (i<noFixes)) {
	    Fix tmpFix=(Fix) fixes.get(i);
	    return (tmpFix.getFixCoord());
	}
	else return null;
    }

    public int getIndexOf (String nameFix)
    {
	int index=-1;
	for (int i=0 ; i<noFixes ; i++) {
	    //   System.out.println((Fix) fixes.get(i));
	    if (((Fix) fixes.get(i)).hasName(nameFix)) {
		index=i;
		i=noFixes;
	    }
	}
	return index;
    }

    public int getIndexOf (Fix f)
    {
	Fix tmp;
	int index=-1;
	for (int i=0 ; i<noFixes ; i++) {
	    //  System.out.println((Fix) fix.get(i));
	    tmp=(Fix) fixes.get(i);
	    if (tmp==f) {
		index=i;
		i=noFixes;
	    }
	}
	return index;
    }

	
    public boolean hasFix (Fix f)
    {
	int index=-1;
	for (int i=0 ; i<noFixes; i++) {
	    if (((Fix) fixes.get(i))==f) {
		index=i;
		i=noFixes;
	    }
	}
	return (index>-1);
    }

    public String toString()
    {
	return new String("No. Fixes:"+noFixes+":  "+fixes);
    }

}



