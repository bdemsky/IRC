// this class stores the properties of a fix

class Fix
{
    private String name; // its name
    private Point2d p; // its coordinates

    public Fix(String name0,Point2d p0)
    {
	name=name0;
	p=p0;
    }

    public Point2d getFixCoord()
    {
	return p;
    }

    public String getName()
    {
      return name;
    }

    boolean hasName(String name0)
    {
	return (name.compareTo(name0)==0);
    }

    public String toString()
    // for testing purposes
    {
	return new String("Fix: "+name+" "+p);
    }

}




