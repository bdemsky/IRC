// the data about a plane - current position and velocity

class Track
{

    Point4d pos;
    Velocity vel;

    public Track(Point4d p, Velocity v)
    {
	pos=p;
	vel=v;
    }

    public void setPosition (Point4d p)
    {
	pos=p;
    }

    public void setVelocity (Velocity v)
    {
	vel=v;
    }

    public void printInfo()
    {
	System.out.println("track: "+pos+"||"+vel);
    }

}
