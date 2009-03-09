// This class memorizes all the fixes in the area.
// There are methods for setting the properties of a fix and 
// for getting a fix by a given name

//import java.util.*;

public class FixList {
  
  public /*static*/ int noFixes() { return _noFixes; }
  public /*static*/ Vector fixes() { return _fixes; }

  private int _noFixes;
  private Vector _fixes;

  public FixList() {
    _noFixes=0;
    _fixes=new Vector(100);
  }
  
  // sets the parameters of the fix number "pos": its name 
  // and its coordinates
  public /*static*/ void setFix(String name,float x,float y)
  {
    _fixes.addElement(new Fix(name,(Point2d) new Point2d(x,y)));
  }

  public /*static*/ String getFix(int index)
  {
    Fix fAux=(Fix) _fixes.elementAt(index);
    return (String) fAux.getName();
  }  

  public /*static*/ int getIndex(String name) {
    for( int i = 0; i < _fixes.size(); ++i ) {
      Fix fAux=(Fix) _fixes.elementAt( i );
      if (fAux.hasName(name))
	return i;      
    }
    System.out.println("Fix not found - "+name);
    System.exit(-1);
    return 0;
  }

  public /*static*/ Fix getFix(String name) {
    for( int i = 0; i < _fixes.size(); ++i ) {
      Fix fAux=(Fix) _fixes.elementAt( i );
      if (fAux.hasName(name))
	return fAux;      
    }
    System.out.println("Fix not found - "+name);
    System.exit(-1);
    return null;
  }

  public /*static*/ void printInfo() {
    System.out.println("\n\nThe number of fixes:"+_noFixes);
    System.out.println("The fixes are:");
    for( int i = 0; i < _fixes.size(); ++i ) {
      Fix bAux=(Fix) _fixes.elementAt( i );
      System.out.println(bAux);
    }
  }

  public /*static*/ void addFix(StringTokenizer parameters)
  {
    setFix(parameters.nextToken(), Integer.parseInt(parameters.nextToken()), Integer.parseInt(parameters.nextToken()));
    _noFixes++;
  }
    
  public /*static*/ void removeFix(StringTokenizer parameters)
  {
    _noFixes--;
    //Fix fAux=getFix(parameters.nextToken());
    int fixIndex=getIndex(parameters.nextToken());
    //_fixes.remove(fAux);
    _fixes.removeElementAt(fixIndex);
  }
}
