package RayTracer;

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
  float   r, r2;
  //Vec      v,b; // temporary vecs used to minimize the memory load


  public Sphere(Vec center, float radius) {
    super();
    c = center;
    r = radius;
    r2 = r*r;
    //  v=new Vec();
    //  b=new Vec();
  }

  public float dot(float x1, float y1, float z1, float x2, float y2, float z2){

    return x1*x2 + y1*y2 + z1*z2; 

  }

  public Isect intersect(Ray ry) {


    float b, disc, t;
    Isect ip;

    float x=c.x-ry.P.x;
    float y=c.y-ry.P.y;
    float z=c.z-ry.P.z;

    b=dot( x, y, z, ry.D.x, ry.D.y, ry.D.z);
    disc = (float) (b*b -dot(x,y,z,x,y,z) + r2);
    if (disc < 0.0) {
      return null;
    }
    disc = (float) Math.sqrtf((float)disc);
    t = (b - disc < 1e-6) ? b + disc : b - disc;
    if (t < 1e-6) {
      return null;
    }
    ip = new Isect();
    ip.t = t;
    ip.enter = dot(x,y,z,x,y,z) > r2 + 1e-6 ? 1 : 0;
    //  ip.enter = Vec.dot(v, v) > r2 + 1e-6 ? 1 : 0;
    ip.prim = this;
    ip.surf = surf;
    return ip;

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

