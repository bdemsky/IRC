// the class that describes coordinates the velocity of a plane

//import java.lang.*;

class Velocity {

  public Point4d vector;
  public double speed;

  Velocity() {
    Velocity(0,0,0);
  }
  
  Velocity(double newX, double newY, double newZ) {
    this.vector=new Point4d(newX, newY, newZ);
    this.speed=this.horizSpeed();
  } 

  public static Velocity copyOf(Velocity v) {    
    return new Velocity( v );
  }
  
  Velocity (Velocity v) {
    this.vector=new Point4d (v.vector);
    this.speed=this.horizSpeed();
  }

  public double horizSpeed() {
    return Math.sqrt(Math.pow(vector.x,2.0)+Math.pow(vector.y,2.0));
  }
  
  public String toString() {
    return vector.toString();
  }
}
