// describes a route - number of beacons, the current position on the 
// route, and the beacons.

//import java.util.*;

public class Route {

  public int noFixes,current;
  public Vector fixes;

  Route(int no) {
    noFixes=no;
    current=0;
    fixes=new Vector(noFixes);
  }

  Route(int no, int cur) {
    noFixes=no;
    current=cur;
    fixes=new Vector(noFixes);
  }
  
  public void addFix (int pos, Fix f) {
    fixes.insertElementAt(f, pos);
  }

  public void addFix (D2 d2, int pos, String name) {
    addFix(pos, (Fix) d2.getFixList().getFix(name) );
  }

  public Fix getFixAt(int pos) {
    if ((pos>-1) && (pos<noFixes)) {
      return (Fix) fixes.elementAt(pos);
    }  
    return null;
  }
    
  public void setCurrent(int c) {
    current=c;
  }

  public Fix getCurrent() {
    return (Fix) fixes.elementAt(current);
  }

  public Point2d getCoordsOf (int i) {
    if ((i>-1) && (i<noFixes)) {
      Fix tmpFix=(Fix) fixes.elementAt(i);
      return (tmpFix.getFixCoord());
    }
    return null;
  }

  public int getIndexOf (String nameFix) {
    int index=-1;
    for (int i=0 ; i<noFixes ; i++) {      
      if (((Fix) fixes.elementAt(i)).hasName(nameFix)) {
	index=i;
	i=noFixes;
      }
    }
    return index;
  }

  public int getIndexOf (Fix f) {
    Fix tmp;
    int index=-1;
    for (int i=0 ; i<noFixes ; i++) {      
      tmp=(Fix) fixes.elementAt(i);
      if (tmp==f) {
	index=i;
	break;
      }
    }
    return index;
  }
	
  public boolean hasFix (Fix f) {
    int index=-1;
    for (int i=0 ; i<noFixes; i++) {
      if (((Fix) fixes.elementAt(i))==f) {
	index=i;
	break;
      }
    }
    return (index>-1);
  }

  public String toString() {
    return new String("No. Fixes:"+noFixes+":  "+fixes);
  }
}
