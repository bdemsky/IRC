// This class memorizes all the fixes in the area.
// There are methods for setting the properties of a fix and 
// for getting a fix by a given name


import java.util.*;

final class FixList
{
  public static int noFixes=0; // the number of fixes
  public static ArrayList fixes=new ArrayList (100); // the fixes

  public static void setFix(String name,float x,float y)
  // sets the parameters of the fix number "pos": its name 
  // and its coordinates
  {
    fixes.add(new Fix(name,(Point2d) new Point2d(x,y)));
  }


  public static String getFix(int index)
  {
    Fix fAux=(Fix) fixes.get(index);
    return (String) fAux.getName();
  }  

  public static int getIndex(String name)
  // returns the index of the fix with the given name
  {
    int index=0;
    Iterator it=getFixes();    
    while (it.hasNext())
      {	
	Fix fAux=(Fix) it.next();	    
	if (fAux.hasName(name))
	  return index;
	index++;
      }
    throw new RuntimeException("Fix not found - "+name);
  }

  public static Fix getFix(String name)
  // returns the fix with the given name
  {
    Iterator it=getFixes();
    while (it.hasNext())
      {
	Fix fAux=(Fix) it.next();	    
	if (fAux.hasName(name))
	  return fAux;
      }
    throw new RuntimeException("Fix not found - "+name);
  }

  public static Iterator getFixes()
  {
    return fixes.iterator();
  }
    
  public static void printInfo()
  // this is a test procedure
  {
    System.out.println("\n\nThe number of fixes:"+noFixes);
    System.out.println("The fixes are:");
    Iterator it=getFixes();
    while (it.hasNext())
      {
	  Fix bAux=(Fix) it.next();
	  System.out.println(bAux);
      }
  }

  public static void addFix(StringTokenizer parameters)
  {
    setFix(parameters.nextToken(), Integer.parseInt(parameters.nextToken()), Integer.parseInt(parameters.nextToken()));
    noFixes++;
  }
    
  public static void removeFix(StringTokenizer parameters)
  {
    noFixes--;
    Fix fAux=getFix(parameters.nextToken());
    fixes.remove(fAux);
  }

}






