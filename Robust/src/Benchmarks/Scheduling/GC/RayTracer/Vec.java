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




/**
 * This class reflects the 3d vectors used in 3d computations
 */
public class Vec
//implements java.io.Serializable 
{

  /**
   * The x coordinate
   */
  public float x; 

  /**
   * The y coordinate
   */
  public float y;

  /**
   * The z coordinate
   */
  public float z;

  /**
   * Constructor
   * @param a the x coordinate
   * @param b the y coordinate
   * @param c the z coordinate
   */
  public Vec(float a, float b, float c) {
    x = a;
    y = b;
    z = c;
  }

  /**
   * Copy constructor
   */
  public Vec(Vec a) {
    x = a.x;
    y = a.y;
    z = a.z;
  }
  /**
   * Default (0,0,0) constructor
   */
  public Vec() {
    x = (float) 0.0;
    y = (float) 0.0; 
    z = (float) 0.0;
  }

  /**
   * Add a vector to the current vector
   * @param: a The vector to be added
   */
  public final void add(Vec a) {
    x+=a.x;
    y+=a.y;
    z+=a.z;
  }  

  /**
   * adds: Returns a new vector such as
   * new = sA + B
   */
  public static Vec adds(float s, Vec a, Vec b) {
    return new Vec(s * a.x + b.x, s * a.y + b.y, s * a.z + b.z);
  }

  /**
   * Adds vector such as:
   * this+=sB
   * @param: s The multiplier
   * @param: b The vector to be added
   */
  public final void adds(float s,Vec b){
    x+=s*b.x;
    y+=s*b.y;
    z+=s*b.z;
  }

  /**
   * Substracs two vectors
   */
  public static Vec sub(Vec a, Vec b) {
    return new Vec(a.x - b.x, a.y - b.y, a.z - b.z);
  }

  /**
   * Substracts two vects and places the results in the current vector
   * Used for speedup with local variables -there were too much Vec to be gc'ed
   * Consumes about 10 units, whether sub consumes nearly 999 units!! 
   * cf thinking in java p. 831,832
   */
  public final void sub2(Vec a,Vec b) {
    this.x=a.x-b.x;
    this.y=a.y-b.y;
    this.z=a.z-b.z;
  }

  public static Vec mult(Vec a, Vec b) {
    return new Vec(a.x * b.x, a.y * b.y, a.z * b.z);
  }

  public static Vec cross(Vec a, Vec b) {
    return
    new Vec(a.y*b.z - a.z*b.y,
        a.z*b.x - a.x*b.z,
        a.x*b.y - a.y*b.x);
  }

  public static float dot(Vec a, Vec b) {
    return a.x*b.x + a.y*b.y + a.z*b.z;
  }

  public static Vec comb(float a, Vec A, float b, Vec B) {
    return
    new Vec(a * A.x + b * B.x,
        a * A.y + b * B.y,
        a * A.z + b * B.z);
  }

  public final void comb2(float a,Vec A,float b,Vec B) {
    x=a * A.x + b * B.x;
    y=a * A.y + b * B.y;
    z=a * A.z + b * B.z;      
  }

  public final void scale(float t) {
    x *= t;
    y *= t;
    z *= t;
  }

  public final void negate() {
    x = -x;
    y = -y;
    z = -z;
  }

  public final float normalize() {
    float len;
    len =(float)  Math.sqrt(x*x + y*y + z*z);
    if (len > 0.0) {
      x /= len;
      y /= len;
      z /= len;
    }
    return len;
  }

  public final String toString() {
    return "<" + x + "," + y + "," + z + ">";
  }
}
