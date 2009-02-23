// the class that describes coordinates the velocity of aplane


import java.lang.*;

class Velocity {

    Point4d vector;
    double speed;

    Velocity() {
	this(0,0,0);
    }
  
    Velocity(double newX, double newY, double newZ) {
	this.vector=new Point4d(newX, newY, newZ);
	this.speed=this.horizSpeed();
    } 

    static Velocity copyOf(Velocity v)
    {
	try{
	    return (Velocity) v.clone();
	} catch (Exception e) {System.out.println("Esti bou!");}
	return null;
    }

    Velocity (Velocity v)
    {
	this.vector=new Point4d (v.vector);
        this.speed=this.horizSpeed();
    }

    public double horizSpeed()
    {
	return Math.sqrt(Math.pow(vector.x,2)+Math.pow(vector.y,2));
    }

    public String toString()
    {
	//  Point4d pAux=new Point4d(0,x,y,z);
      return ""+vector;
    }

}



    
