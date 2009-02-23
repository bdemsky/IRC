// class that implements types of aircrafts

class Aircraft
{    
    String type;
    double maxLift, maxThrust;

    public Aircraft(String t, double maxL, double maxT)
    {
	type=t;
	maxLift=maxL;
	maxThrust=maxT;
    }

    boolean hasType(String type0)
    {
	return (type.compareTo(type0)==0);
    }

    public String toString()
    {
	return new String("Airplane: "+type+" "+maxLift+" "+maxThrust);
    }

}

