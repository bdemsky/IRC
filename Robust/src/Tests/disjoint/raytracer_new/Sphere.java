/**************************************************************************
*                                                                         *
*             Java Grande Forum Benchmark Suite - Version 2.0             *
*                                                                         *
*                            produced by                                  *
*                                                                         *
*                  Java Grande Benchmarking Project                       *
*                                                                         *
*                                at                                       *
*                                                                         *
*                Edinburgh Parallel Computing Centre                      *
*                                                                         *
*                email: epcc-javagrande@epcc.ed.ac.uk                     *
*                                                                         *
*                 Original version of this code by                        *
*            Florian Doyon (Florian.Doyon@sophia.inria.fr)                *
*              and  Wilfried Klauser (wklauser@acm.org)                   *
*                                                                         *
*      This version copyright (c) The University of Edinburgh, 1999.      *
*                         All rights reserved.                            *
*                                                                         *
**************************************************************************/



public class Sphere extends Primitive 
//implements java.io.Serializable 
{
  Vec      c;
  double   r, r2;
//  Vec      v,b; // temporary vecs used to minimize the memory load
  

  public Sphere(Vec center, double radius) {
	  super();
    c = center;
    r = radius;
    r2 = r*r;
//    v=new Vec();
//    b=new Vec();
  }
  
  public double dot(double x1, double y1, double z1, double x2, double y2, double z2){
	  
	  return x1*x2 + y1*y2 + z1*z2; 
	  
  }
     
  public Isect intersect(Ray ry) {
	  
	    
//	    Vec  v=new Vec();	     
	    double b, disc, t;
	    Isect ip;
//	    Vec v=Vec.sub(c, ry.P);
//	    v.sub2(c, ry.P);
	    
	    double x=c.x-ry.P.x;
	    double y=c.y-ry.P.y;
	    double z=c.z-ry.P.z;
	    
	    b=dot(x,y,z,ry.D.x,ry.D.y,ry.D.z);
//	    b = Vec.dot(v, ry.D);
	    
//	    disc = b*b - Vec.dot(v, v) + r2;
	    disc = b*b -dot(x,y,z,x,y,z) + r2;
	    if (disc < 0.0) {
	      return null;
	    }
	    disc = Math.sqrt(disc);
	    t = (b - disc < 1e-6) ? b + disc : b - disc;
	    if (t < 1e-6) {
	      return null;
	    }
	    ip = new Isect();
	    ip.t = t;
	    ip.enter = dot(x,y,z,x,y,z) > r2 + 1e-6 ? 1 : 0;
//	    ip.enter = Vec.dot(v, v) > r2 + 1e-6 ? 1 : 0;
	    ip.prim = this;
	    ip.surf = surf;
	    return ip;
	    
	  /*
    double b, disc, t;
    Isect ip;
    v.sub2(c, ry.P);
    b = Vec.dot(v, ry.D);
    disc = b*b - Vec.dot(v, v) + r2;
    if (disc < 0.0) {
      return null;
    }
    disc = Math.sqrt(disc);
    t = (b - disc < 1e-6) ? b + disc : b - disc;
    if (t < 1e-6) {
      return null;
    }
    ip = new Isect();
    ip.t = t;
    ip.enter = Vec.dot(v, v) > r2 + 1e-6 ? 1 : 0;
    ip.prim = this;
    ip.surf = surf;
    return ip;
    */
  }

  public Vec normal(Vec p) {
    Vec r;
    r = Vec.sub(p, c);
    r.normalize();
    return r;
  }

  public String toString() {
    return "Sphere {" + c.toString() + "," + r + "}";
  }
	
  public Vec getCenter() {
    return c;
  }
  public void setCenter(Vec c) {
    this.c = c;
  }
}

